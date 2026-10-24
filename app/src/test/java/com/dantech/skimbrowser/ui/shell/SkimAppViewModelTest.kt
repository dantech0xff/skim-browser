package com.dantech.skimbrowser.ui.shell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.domain.ai.AskPageAccess
import com.dantech.skimbrowser.domain.ai.AskPageDecision
import com.dantech.skimbrowser.domain.browsing.WindowPrivacy
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.settings.SkimSettings
import com.dantech.skimbrowser.domain.tab.RestoreResult
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRestore
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.MoreMenuKey
import com.dantech.skimbrowser.ui.navigation.ReaderKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.TabsKey
import com.dantech.skimbrowser.ui.navigation.WelcomeKey
import com.dantech.skimbrowser.ui.navigation.skimNavConfig
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.PolymorphicSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The shell's view model over the real tab manager (over an in-memory database): how the app
 * starts, the back stack it saves, the links other apps send, and the window its navigator reaches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SkimAppViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val fixtures = mutableListOf<TabManagerFixture>()
    private val viewModels = ViewModelStore()
    private var viewModelCount = 0

    @After
    fun tearDown() = runBlocking {
        viewModels.clear()
        fixtures.forEach { it.close() }
    }

    // Starting

    @Test
    fun theFirstLaunchOpensOnWelcome() = runTest {
        val app = app()
        runCurrent()

        assertTrue(app.model.state.value.started)
        assertEquals(listOf(WelcomeKey), app.keys())
    }

    @Test
    fun aLaunchAfterOnboardingOpensOnTheBrowser() = runTest {
        val app = app(settings = FakeSettingsRepository(SkimSettings(onboardingCompleted = true)))
        runCurrent()

        assertTrue(app.model.state.value.started)
        assertEquals(listOf(BrowserKey), app.keys())
    }

    @Test
    fun theWindowTakesTheThemeOfTheSettings() = runTest {
        val app = app(settings = FakeSettingsRepository(SkimSettings(theme = ThemeSetting.Dark)))
        runCurrent()

        assertEquals(ThemeSetting.Dark, app.model.state.value.theme)
    }

    @Test
    fun theAppWaitsForTheSavedTabs() = runTest {
        val restored = CompletableDeferred<RestoreResult>()
        val app = app(restoreTabs = { restored.await() })
        runCurrent()

        assertFalse(app.model.state.value.started)
        assertTrue(app.keys().isEmpty())

        restored.complete(RestoreResult.Restored(0))
        runCurrent()

        assertTrue(app.model.state.value.started)
    }

    @Test
    fun settingsThatAreNotReadInTimeCountAsAFirstLaunch() = runTest {
        val app = app(settings = NeverRead())
        advanceTimeBy(SkimAppViewModel.START_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(app.model.state.value.started)

        advanceTimeBy(2)
        runCurrent()

        assertEquals(SkimAppUiState(ThemeSetting.System, started = true), app.model.state.value)
        assertEquals(listOf(WelcomeKey), app.keys())
    }

    @Test
    fun savedTabsThatAreNotRestoredInTimeStillOpenTheBrowser() = runTest {
        val app = app(
            settings = FakeSettingsRepository(SkimSettings(onboardingCompleted = true)),
            restoreTabs = { awaitCancellation() },
        )
        advanceTimeBy(SkimAppViewModel.START_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(app.model.state.value.started)

        advanceTimeBy(2)
        runCurrent()

        assertTrue(app.model.state.value.started)
        assertEquals(listOf(BrowserKey), app.keys())
    }

    @Test
    fun aRestoreThatFailsStillOpensTheBrowser() = runTest {
        val app = app(
            settings = FakeSettingsRepository(SkimSettings(onboardingCompleted = true)),
            restoreTabs = { throw IOException("unreadable") },
        )
        runCurrent()

        assertTrue(app.model.state.value.started)
        assertEquals(listOf(BrowserKey), app.keys())
    }

    // The saved back stack

    @Test
    fun aSavedBackStackWinsOnceTheKeysOfTabsThatAreGoneAreDropped() = runTest {
        val f = fixture()
        val open = f.manager.newTab(incognito = false, url = PAGE)
        runCurrent()

        val app = app(f, savedState = saved(BrowserKey, ReaderKey(open), MoreMenuKey("gone")))
        runCurrent()

        assertEquals(listOf(BrowserKey, ReaderKey(open)), app.keys())
    }

    // Links from other apps

    @Test
    fun aLinkFromAnotherAppOpensInANewNormalTabShownAtOnce() = runTest {
        val app = app(settings = FakeSettingsRepository(SkimSettings(onboardingCompleted = true)))
        runCurrent()
        app.model.navigator.openSettings()

        app.model.openExternalLink(PAGE)

        val tab = app.fixture.manager.state.value.currentTab
        assertEquals(PAGE, tab?.url)
        assertFalse(tab!!.incognito)
        assertTrue(tab.openedByExternalApp)
        assertEquals(listOf(BrowserKey), app.keys())
    }

    // The window

    @Test
    fun theShellReadsTheTabsFromTheControllersOwnFlow() = runTest {
        val app = app()

        // A copy of the flow would reach the shell a frame after the screens, with FLAG_SECURE decided late.
        assertSame(app.fixture.manager.state, app.model.tabs)
    }

    @Test
    fun theNavigatorTurnsFlagSecureOnInTheAttachedWindowOnly() = runTest {
        val app = app(settings = FakeSettingsRepository(SkimSettings(onboardingCompleted = true)))
        runCurrent()
        val first = RecordingWindow()
        val second = RecordingWindow()

        app.model.attachWindow(first)
        app.model.navigator.openTabs(incognito = true)
        assertEquals(listOf(INCOGNITO_SURFACE_OWNER to true), first.secure)

        app.model.attachWindow(second)
        app.model.detachWindow(first)
        app.model.navigator.openTabs(incognito = true, focusTabId = "x")
        assertEquals(1, first.secure.size)
        assertEquals(listOf(INCOGNITO_SURFACE_OWNER to true), second.secure)

        app.model.detachWindow(second)
        app.model.navigator.openTabs(incognito = true)
        assertEquals(1, second.secure.size)
        assertEquals(listOf(BrowserKey, TabsKey(incognito = true)), app.keys())
    }

    private class App(val model: SkimAppViewModel, val fixture: TabManagerFixture) {
        fun keys(): List<NavKey> = model.backStack.toList()
    }

    private fun TestScope.app(
        f: TabManagerFixture = fixture(),
        settings: SettingsRepository = FakeSettingsRepository(),
        restoreTabs: suspend () -> RestoreResult = { RestoreResult.Restored(0) },
        savedState: SavedStateHandle = SavedStateHandle(),
    ): App {
        val undo = UndoRegistry(backgroundScope)
        val snackbar = SnackbarController(undo)
        val navigators = object : SkimNavigator.Factory {
            override fun create(backStack: NavBackStack<NavKey>, secure: WindowPrivacy, scope: CoroutineScope) =
                SkimNavigator(backStack, f.manager, f.incognito, secure, snackbar, AllowEverySite, scope)
        }
        val restorer = object : TabRestore {
            override suspend fun restore(): RestoreResult = restoreTabs()
        }
        val model = kept { SkimAppViewModel(savedState, settings, f.manager, restorer, snackbar, undo, navigators) }
        return App(model, f)
    }

    private fun TestScope.fixture(): TabManagerFixture = TabManagerFixture(this, folder.root).also { fixtures += it }

    /** What the Activity saved for a view model whose back stack was [keys]. */
    private fun saved(vararg keys: NavKey): SavedStateHandle = SavedStateHandle(
        mapOf(SkimAppViewModel.BACK_STACK_KEY to encodeToSavedState(BACK_STACK, NavBackStack(*keys), skimNavConfig)),
    )

    private inline fun <reified T : ViewModel> kept(noinline make: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { make() } })["vm-${viewModelCount++}", T::class]

    /** Settings that never come: the store cannot be read. */
    private class NeverRead : SettingsRepository by FakeSettingsRepository() {
        override val settings: Flow<SkimSettings> = flow { awaitCancellation() }
    }

    private object AllowEverySite : AskPageAccess {
        override suspend fun decide(tab: Tab): AskPageDecision = AskPageDecision.Allowed
    }

    /** The FLAG_SECURE changes a window was asked for, in order. */
    private class RecordingWindow : WindowPrivacy {
        val secure = mutableListOf<Pair<String, Boolean>>()

        override fun setSecure(owner: String, secure: Boolean) {
            this.secure += owner to secure
        }

        override fun setRecentsHidden(owner: String, hidden: Boolean) = Unit
    }

    private companion object {
        const val PAGE = "https://daylightjournal.com/heat-pumps"
        val BACK_STACK = NavBackStackSerializer(PolymorphicSerializer(NavKey::class))
    }
}
