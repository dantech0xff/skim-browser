package com.dantech.skimbrowser.ui.navigation

import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ai.AiGate
import com.dantech.skimbrowser.domain.tab.HitTarget
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.browser.WindowSecureController
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The navigator over a real back stack, the real tab manager (over an in-memory database) and the
 * real window privacy switch of an Activity. The window reports every change of its attributes, so
 * the test sees what the app showed at the very moment FLAG_SECURE turned on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SkimNavigatorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val window: Window = activity.get().window
    private val secure = WindowSecureController(window, setRecentsScreenshotEnabled = null)
    private val fixtures = mutableListOf<TabManagerFixture>()

    /** The current tab and the back stack when FLAG_SECURE turned on, each time it did. */
    private val shownWhenSecured = mutableListOf<Shown>()

    private data class Shown(val keys: List<NavKey>, val currentTabId: String?, val tabIds: List<String>)

    @After
    fun tearDown() = runBlocking {
        fixtures.forEach { it.close(closeDatabase = false) }
        fixtures.map { it.db }.distinct().forEach { it.close() }
        activity.pause().stop().destroy()
        Unit
    }

    // Onboarding and the back stack

    @Test
    fun finishingOnboardingLeavesOnlyTheBrowser() = runTest {
        val app = app(WelcomeKey, SetupKey)

        app.navigator.finishOnboarding()

        assertEquals(listOf(BrowserKey), app.keys())
    }

    @Test
    fun finishingOnboardingOverTheBrowserLeavesOnlyTheBrowser() = runTest {
        val app = app(WelcomeKey, BrowserKey, SetupKey)

        app.navigator.finishOnboarding()

        assertEquals(listOf(BrowserKey), app.keys())
    }

    @Test
    fun removeTakesOutThatKeyAndNothingElse() = runTest {
        val app = app(BrowserKey, ReaderKey("tab-1"), MoreMenuKey("tab-1"), SettingsKey)

        app.navigator.remove(ReaderKey("tab-1"))

        assertEquals(listOf(BrowserKey, MoreMenuKey("tab-1"), SettingsKey), app.keys())
    }

    @Test
    fun removeTakesOutTheTopmostOfTwoEqualKeys() = runTest {
        val app = app(BrowserKey, SettingsKey, HistoryKey, SettingsKey)

        app.navigator.remove(SettingsKey)

        assertEquals(listOf(BrowserKey, SettingsKey, HistoryKey), app.keys())
    }

    @Test
    fun removingAKeyThatIsNotThereChangesNothing() = runTest {
        val app = app(BrowserKey, ReaderKey("tab-1"))

        app.navigator.remove(ReaderKey("tab-2"))

        assertEquals(listOf(BrowserKey, ReaderKey("tab-1")), app.keys())
    }

    @Test
    fun removingTheOnlyKeyLeavesTheBrowser() = runTest {
        val app = app(ReaderKey("tab-1"))

        app.navigator.remove(ReaderKey("tab-1"))

        assertEquals(listOf(BrowserKey), app.keys())
    }

    @Test
    fun backNeverRemovesTheLastDestination() = runTest {
        val app = app(WelcomeKey, SetupKey)

        app.navigator.back()
        app.navigator.back()

        assertEquals(listOf(WelcomeKey), app.keys())
    }

    @Test
    fun pushingTheKeyOnTopAgainDoesNothing() = runTest {
        val app = app(WelcomeKey)

        app.navigator.push(SetupKey)
        app.navigator.push(SetupKey)

        assertEquals(listOf(WelcomeKey, SetupKey), app.keys())
    }

    @Test
    fun replaceTopSwapsOnlyTheTopKey() = runTest {
        val app = app(BrowserKey, SettingsKey)

        app.navigator.replaceTop(HistoryKey)

        assertEquals(listOf(BrowserKey, HistoryKey), app.keys())
    }

    @Test
    fun showingTheBrowserClosesWhatIsOverIt() = runTest {
        val app = app(WelcomeKey, BrowserKey, TabsKey(incognito = false), MoreMenuKey("tab-1"))

        app.navigator.showBrowser()

        assertEquals(listOf(WelcomeKey, BrowserKey), app.keys())
    }

    @Test
    fun showingTheBrowserFromOnboardingPutsItOnTop() = runTest {
        val app = app(WelcomeKey, SetupKey)

        app.navigator.showBrowser()

        assertEquals(listOf(WelcomeKey, SetupKey, BrowserKey), app.keys())
    }

    @Test
    fun theTabsScreenSwitchesBetweenOpenAndIncognitoInPlace() = runTest {
        val app = app(BrowserKey, TabsKey(incognito = false))

        app.navigator.openTabs(incognito = true)

        assertEquals(listOf(BrowserKey, TabsKey(incognito = true)), app.keys())
    }

    @Test
    fun destinationsOpenWithTheirParameters() = runTest {
        val app = app(BrowserKey)
        val n = app.navigator
        val id = app.tabs.newTab(incognito = false, url = "https://daylightjournal.com/energy/heat-pumps-cold")
        app.fixture.settings.setAskBeforeSending(false)

        n.openReader(id)
        n.openAskPage(id, fromReader = true).join()
        n.openBookmarks(folderId = 4)
        n.openSavedTabsChat(9)
        n.openDialog(MoveToFolderKey(bookmarkId = 2))

        assertEquals(
            listOf(
                BrowserKey,
                ReaderKey(id),
                AskPageKey(tabId = id, fromReader = true, approvedHost = "daylightjournal.com"),
                BookmarksKey(folderId = 4),
                AskTabsKey(savedChatId = 9),
                MoveToFolderKey(bookmarkId = 2),
            ),
            app.keys(),
        )
    }

    @Test
    fun askSkimAsksTheFirstTimeAndOpensOnceTheSiteMayBeRead() = runTest {
        val app = app(BrowserKey)
        val id = app.tabs.newTab(incognito = false, url = "https://daylightjournal.com/energy/heat-pumps-cold")

        app.navigator.openAskPage(id).join()
        assertEquals(listOf(BrowserKey, AiPermissionKey(id)), app.keys())

        app.navigator.back()
        app.fixture.settings.setAskBeforeSending(false)
        app.navigator.openAskPage(id, fromReader = true).join()
        assertEquals(
            listOf(BrowserKey, AskPageKey(tabId = id, fromReader = true, approvedHost = "daylightjournal.com")),
            app.keys(),
        )
    }

    @Test
    fun askSkimDoesNothingOnAnIncognitoTab() = runTest {
        val app = app(BrowserKey)
        val id = app.tabs.newTab(incognito = true, url = "https://daylightjournal.com/private")

        app.navigator.openAskPage(id).join()

        assertEquals(listOf(BrowserKey), app.keys())
    }

    // Tabs

    @Test
    fun showingATabMakesItCurrentAndShowsTheBrowser() = runTest {
        val app = app(BrowserKey, TabsKey(incognito = false))
        val first = app.tabs.newTab(incognito = false, url = "https://example.com/1")
        val second = app.tabs.newTab(incognito = false, url = "https://example.com/2")

        app.navigator.showTab(first)

        assertEquals(first, app.tabs.state.value.currentTabId)
        assertEquals(listOf(BrowserKey), app.keys())
        assertTrue(second in app.tabIds())
    }

    @Test
    fun showingATabThatIsNotOpenDoesNothing() = runTest {
        val app = app(BrowserKey, SettingsKey)
        val open = app.tabs.newTab(incognito = false, url = "https://example.com/")

        app.navigator.showTab("closed-meanwhile")

        assertEquals(open, app.tabs.state.value.currentTabId)
        assertEquals(listOf(BrowserKey, SettingsKey), app.keys())
    }

    @Test
    fun aNewTabOpensInFrontOfTheBrowser() = runTest {
        val app = app(BrowserKey, TabsKey(incognito = false))

        app.navigator.openNewTab(incognito = false, url = "https://example.com/")

        val current = app.tabs.state.value.currentTab ?: throw AssertionError("no current tab")
        assertEquals("https://example.com/", current.url)
        assertFalse(current.incognito)
        assertEquals(listOf(BrowserKey), app.keys())
    }

    @Test
    fun aTabOpenedInTheBackgroundStaysBehindTheCurrentOne() = runTest {
        val app = app(BrowserKey, LinkOptionsKey("tab-1", HitTarget.Link("https://example.com/", null)))
        val current = app.tabs.newTab(incognito = false, url = "https://example.com/")

        val opened = app.navigator.openInBackground("https://example.com/next", incognito = false)

        assertEquals(current, app.tabs.state.value.currentTabId)
        assertTrue(opened in app.tabIds())
        assertEquals(2, app.keys().size)
        assertFalse(secure.isSecure)
    }

    // FLAG_SECURE comes before incognito content

    @Test
    fun showingAnIncognitoTabTurnsTheFlagOnBeforeItBecomesCurrent() = runTest {
        val app = app(BrowserKey)
        val normal = app.tabs.newTab(incognito = false, url = "https://example.com/")
        val incognito = app.tabs.newTab(incognito = true, url = "https://example.com/private", background = true)

        app.navigator.showTab(incognito)

        assertEquals("secured while the normal tab was current", Shown(listOf(BrowserKey), normal, listOf(normal, incognito)), shownWhenSecured.single())
        assertEquals(incognito, app.tabs.state.value.currentTabId)
        assertTrue(secure.isSecure)
    }

    @Test
    fun aNewIncognitoTabTurnsTheFlagOnBeforeItOpens() = runTest {
        val app = app(BrowserKey, TabsKey(incognito = false))
        val normal = app.tabs.newTab(incognito = false, url = "https://example.com/")

        app.navigator.openNewTab(incognito = true)

        assertEquals(Shown(listOf(BrowserKey, TabsKey(incognito = false)), normal, listOf(normal)), shownWhenSecured.single())
        assertTrue(app.tabs.state.value.currentTab?.incognito == true)
        assertEquals(listOf(BrowserKey), app.keys())
    }

    @Test
    fun theIncognitoTabsTurnTheFlagOnBeforeTheyShow() = runTest {
        val app = app(BrowserKey)
        val normal = app.tabs.newTab(incognito = false, url = "https://example.com/")

        app.navigator.openTabs(incognito = true)

        assertEquals(Shown(listOf(BrowserKey), normal, listOf(normal)), shownWhenSecured.single())
        assertEquals(listOf(BrowserKey, TabsKey(incognito = true)), app.keys())
    }

    @Test
    fun aSheetOfAnIncognitoTabTurnsTheFlagOnBeforeItShows() = runTest {
        val app = app(BrowserKey)
        val normal = app.tabs.newTab(incognito = false, url = "https://example.com/")
        val incognito = app.tabs.newTab(incognito = true, url = "https://example.com/private", background = true)

        app.navigator.openMoreMenu(incognito)

        assertEquals(Shown(listOf(BrowserKey), normal, listOf(normal, incognito)), shownWhenSecured.single())
        assertEquals(listOf(BrowserKey, MoreMenuKey(incognito)), app.keys())
    }

    @Test
    fun goingBackToAnIncognitoTabTurnsTheFlagOnFirst() = runTest {
        val app = app(BrowserKey, SettingsKey)
        val incognito = app.tabs.newTab(incognito = true, url = "https://example.com/private")

        app.navigator.back()

        assertEquals(Shown(listOf(BrowserKey, SettingsKey), incognito, listOf(incognito)), shownWhenSecured.single())
        assertEquals(listOf(BrowserKey), app.keys())
    }

    @Test
    fun theNavigatorCountsEveryTimeItTurnsTheFlagOn() = runTest {
        val app = app(BrowserKey)
        app.tabs.newTab(incognito = false, url = "https://example.com/")

        app.navigator.openSettings()
        app.navigator.back()
        assertEquals("normal destinations leave the flag alone", 0, app.navigator.secureArming)
        assertFalse(secure.isSecure)

        app.navigator.openTabs(incognito = true)
        assertTrue(app.navigator.secureArming > 0)
    }

    @Test
    fun withoutIncognitoSupportANewIncognitoTabOnlyShowsTheSnackbar() = runTest {
        val app = app(BrowserKey, TabsKey(incognito = false), supportsIncognito = false)
        val normal = app.tabs.newTab(incognito = false, url = "https://example.com/")

        app.navigator.openNewTab(incognito = true)

        assertEquals(listOf(normal), app.tabIds())
        assertEquals(listOf(BrowserKey, TabsKey(incognito = false)), app.keys())
        assertFalse(secure.isSecure)
        val shown = app.snackbar.current.value ?: throw AssertionError("no snackbar")
        assertEquals(UiText.Res(R.string.incognito_unavailable), shown.text)
        assertNull(shown.action)
    }

    @Test
    fun withoutIncognitoSupportNothingOpensInAnIncognitoTabBehind() = runTest {
        val app = app(BrowserKey, supportsIncognito = false)
        val normal = app.tabs.newTab(incognito = false, url = "https://example.com/")

        assertNull(app.navigator.openInBackground("https://example.com/next", incognito = true))

        assertEquals(listOf(normal), app.tabIds())
        assertEquals(UiText.Res(R.string.incognito_unavailable), app.snackbar.current.value?.text)
    }

    private class App(
        val navigator: SkimNavigator,
        val backStack: NavBackStack<NavKey>,
        val fixture: TabManagerFixture,
        val snackbar: SnackbarController,
    ) {
        val tabs get() = fixture.manager

        fun keys(): List<NavKey> = backStack.toList()

        fun tabIds(): List<String> = fixture.tabIds()
    }

    private fun TestScope.app(vararg keys: NavKey, supportsIncognito: Boolean = true): App {
        val fixture = TabManagerFixture(this, folder.root, supportsIncognito = supportsIncognito).also { fixtures += it }
        val backStack = NavBackStack(*keys)
        val snackbar = SnackbarController(UndoRegistry(backgroundScope))
        val sites = SitePermissionRepository(fixture.db.sitePermissionDao(), fixture.db)
        val navigator = SkimNavigator(
            backStack,
            fixture.manager,
            fixture.incognito,
            secure,
            snackbar,
            AiGate(sites, fixture.settings),
            this,
        )
        recordWhenSecured(backStack, fixture)
        return App(navigator, backStack, fixture, snackbar)
    }

    /** Records what is shown each time the window becomes secure, as the window reports it. */
    private fun recordWhenSecured(backStack: NavBackStack<NavKey>, fixture: TabManagerFixture) {
        val original = window.callback
        var wasSecure = isSecure(window.attributes)
        window.callback = object : Window.Callback by original {
            override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams) {
                val nowSecure = isSecure(attrs)
                if (nowSecure && !wasSecure) {
                    val tabs = fixture.manager.state.value
                    shownWhenSecured += Shown(backStack.toList(), tabs.currentTabId, tabs.tabs.map { it.id })
                }
                wasSecure = nowSecure
                original.onWindowAttributesChanged(attrs)
            }
        }
    }

    private fun isSecure(attrs: WindowManager.LayoutParams) = attrs.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}
