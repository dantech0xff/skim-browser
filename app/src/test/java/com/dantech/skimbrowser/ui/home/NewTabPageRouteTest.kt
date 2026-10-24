package com.dantech.skimbrowser.ui.home

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.TestStorage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabsState
import com.dantech.skimbrowser.browser.WindowSecureController
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.navigation.AskPageKey
import com.dantech.skimbrowser.ui.navigation.AskTabsKey
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.MoreMenuKey
import com.dantech.skimbrowser.ui.navigation.ShortcutDialogKey
import com.dantech.skimbrowser.ui.navigation.SkimHistoryKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.TabsKey
import com.dantech.skimbrowser.ui.search.SearchViewModel
import com.dantech.skimbrowser.ui.search.latestDialog
import com.dantech.skimbrowser.ui.search.pass
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dantech.skimbrowser.HiltRouteActivity
import com.dantech.skimbrowser.ui.search.SEARCH_VIEW_MODEL_KEY
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.text.UiText
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The New tab page in the browser's slot over the app as it runs: a real container on a temporary
 * directory (database, settings, tab manager, snackbar and undo), the real navigator over a back
 * stack, and the Activity's window for FLAG_SECURE. Speech input is answered by a registry of the
 * test's own. An incognito tab cannot be opened without WebView profiles, so the incognito New tab
 * page is shown for a tab made by the test.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = PHONE_QUALIFIERS)
class NewTabPageRouteTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    @get:Rule(order = 2)
    val storage = object : TestWatcher() {
        override fun starting(description: Description) {
            TestStorage.current.set(AppStorage.temp(folder.newFolder()))
        }
    }

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<HiltRouteActivity>()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Inject lateinit var graph: ProcessGraph
    private lateinit var navigator: SkimNavigator
    private val backStack = NavBackStack<NavKey>(BrowserKey)
    private lateinit var gateScope: CoroutineScope

    /** The browser destination's store. Extras come from the Hilt activity so assisted view models can be created. */
    private val destination = object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = composeRule.activity.defaultViewModelProviderFactory
        override val defaultViewModelCreationExtras: CreationExtras
            get() = composeRule.activity.defaultViewModelCreationExtras
    }
    private val speech = SpeechRegistry()
    private lateinit var search: SearchViewModel
    private var shown by mutableStateOf(true)

    @Before
    fun setUp() {
        gateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        hiltRule.inject()
        val window = composeRule.activity.window
        navigator = SkimNavigator(
            backStack,
            graph.tabManager(),
            graph.incognitoSession(),
            WindowSecureController(window, setRecentsScreenshotEnabled = null),
            graph.snackbarController(),
            graph.aiGate(),
            gateScope,
        )
    }

    @After
    fun tearDown() {
        destination.viewModelStore.clear()
        gateScope.cancel()
        if (::graph.isInitialized) graph.close()
        TestStorage.current.set(null)
    }

    // The toolbar and the AI FAB

    @Test
    fun onHomeTabsOpensTabsAndMoreOptionsTheMoreMenuOfTheTab() {
        val tabId = showHome()

        composeRule.onNodeWithContentDescription("Back").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Forward").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Tabs, 1 open").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()

        assertEquals(listOf(BrowserKey, TabsKey(incognito = false), MoreMenuKey(tabId)), keys())
    }

    @Test
    fun theToolbarCountsOnlyTheTabsOfItsOwnKind() {
        graph.tabManager().newTab(incognito = false, url = "https://example.com/", background = true)
        graph.tabManager().newTab(incognito = false, url = "https://example.org/", background = true)
        showIncognito()

        composeRule.onNodeWithContentDescription("Incognito tabs, 0 open").assertExists()
    }

    @Test
    fun theAiFabOfHomeOpensAskSkimAcrossTheTabs() {
        showHome()

        composeRule.onNodeWithContentDescription("Ask Skim").performClick()

        assertEquals(listOf(BrowserKey, AskTabsKey()), keys())
    }

    @Test
    fun onTheIncognitoNewTabPageTabsOpensIncognitoTabsAndTheAiFabIsOff() {
        val tab = showIncognito()

        composeRule.onNodeWithContentDescription("Incognito tabs", substring = true).performClick()
        composeRule.onNodeWithContentDescription("Ask Skim, off in incognito").assertHasNoClickAction().performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()

        assertEquals(listOf(BrowserKey, TabsKey(incognito = true), MoreMenuKey(tab.id)), keys())
        composeRule.onNodeWithContentDescription("Back").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Forward").assertIsNotEnabled()
    }

    // The search layer

    @Test
    fun theSearchBarOfHomeOpensTheLayerForANormalTab() {
        showHome()

        composeRule.onNodeWithText("Search or ask anything").performClick()
        type("heat pump")

        assertFalse(search.state.value.incognito)
        composeRule.onNodeWithText("Ask Skim").assertExists()
        assertFalse(isSecure(latestDialog().window!!.attributes))
    }

    @Test
    fun theSearchBarOfTheIncognitoNewTabPageOpensTheLayerForAnIncognitoTab() {
        showIncognito()

        composeRule.onNodeWithText("Search or type URL").performClick()
        type("heat pump")

        assertTrue(search.state.value.incognito)
        composeRule.onAllNodesWithText("Ask Skim").assertCountEquals(0)
        assertTrue("the layer's window is secure", isSecure(latestDialog().window!!.attributes))
    }

    @Test
    fun anAddressEnteredOpensInThisTabAndTheLayerCloses() {
        val tabId = showHome()

        composeRule.onNodeWithText("Search or ask anything").performClick()
        type("example.com")
        field().performImeAction()
        composeRule.waitForIdle()

        assertEquals("https://example.com", graph.tabManager().state.value.tab(tabId)?.url)
        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
        assertEquals("nothing typed is kept", "", search.query.text.toString())
        assertEquals(listOf<NavKey>(BrowserKey), keys())
    }

    @Test
    fun theAskSkimRowAsksAboutTheTabsWithTheTextAndTheLayerCloses() {
        showHome()

        composeRule.onNodeWithText("Search or ask anything").performClick()
        type("heat pump")
        composeRule.onNodeWithText("Ask Skim").performClick()

        assertEquals(listOf(BrowserKey, AskTabsKey(prompt = "heat pump")), keys())
        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
    }

    @Test
    fun backClosesTheLayerAndLeavesTheTabAsItWas() {
        val tabId = showHome()
        composeRule.onNodeWithText("Search or ask anything").performClick()
        type("heat pump")

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
        composeRule.onNodeWithText("Search or ask anything").assertExists()
        assertNull(graph.tabManager().state.value.tab(tabId)?.url)
        assertFalse(composeRule.activity.isFinishing)
        assertEquals(listOf<NavKey>(BrowserKey), keys())
    }

    @Test
    fun theLayerDoesNotComeBackOpenWhenTheScreenIsRestored() {
        val tabId = graph.tabManager().newTab(incognito = false)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            SkimTheme(ThemeSetting.Light, dynamicColor = false) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides destination) {
                    search = hiltViewModel(key = SEARCH_VIEW_MODEL_KEY)
                    val tabs by graph.tabManager().state.collectAsStateWithLifecycle()
                    tabs.tab(tabId)?.let { tab -> NewTabPageRoute(tab, navigator) }
                }
            }
        }
        composeRule.onNodeWithText("Search or ask anything").performClick()
        type("heat pump")
        composeRule.onNodeWithContentDescription("Close search").assertExists()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
        composeRule.onNodeWithText("Search or ask anything").assertExists()
    }

    // Home

    @Test
    fun theHeroCardAsksSkimToSummarizeTheOpenTabs() {
        graph.tabManager().newTab(incognito = false, url = "https://daylightjournal.com/", background = true)
        showHome()

        // The tab Home shows has no page yet: only the other one counts.
        composeRule.onNodeWithText("Summarize my 1 open tab").performClick()

        assertEquals(listOf(BrowserKey, AskTabsKey(prompt = "Summarize my 1 open tab")), keys())
    }

    @Test
    fun addOpensTheAddShortcutDialog() {
        showHome()

        composeRule.onNodeWithText("Add").performClick()

        assertEquals(listOf(BrowserKey, ShortcutDialogKey(shortcutId = null)), keys())
    }

    @Test
    fun theShortcutDialogDestinationSavesThenLeavesTheBackStack() {
        navigator.openDialog(ShortcutDialogKey())
        showShortcutDialog(ShortcutDialogKey())

        composeRule.onNode(hasSetTextAction() and hasText("URL")).performTextInput("daylightjournal.com")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.waitForIdle()
            keys() == listOf<NavKey>(BrowserKey)
        }
        val saved = runBlocking { withTimeout(5_000) { graph.shortcutRepository().observe().first { it.isNotEmpty() } } }
        assertEquals(listOf("daylightjournal.com" to "https://daylightjournal.com"), saved.map { it.name to it.url })
    }

    @Test
    fun theShortcutDialogDestinationEditsTheShortcutOfItsKey() {
        val id = addShortcut("Wikipedia", "https://www.wikipedia.org")
        navigator.openDialog(ShortcutDialogKey(id))
        showShortcutDialog(ShortcutDialogKey(id))

        awaitText("https://www.wikipedia.org")
        composeRule.onNodeWithText("Edit shortcut").assertExists()
    }

    @Test
    fun cancelInTheShortcutDialogDestinationLeavesTheBackStack() {
        navigator.openDialog(ShortcutDialogKey())
        showShortcutDialog(ShortcutDialogKey())

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf<NavKey>(BrowserKey), keys())
    }

    @Test
    fun aShortcutOpensInThisTab() {
        addShortcut("Wikipedia", "https://www.wikipedia.org")
        val tabId = showHome()
        awaitText("Wikipedia")

        composeRule.onNodeWithText("Wikipedia").performClick()

        assertEquals("https://www.wikipedia.org", graph.tabManager().state.value.tab(tabId)?.url)
    }

    @Test
    fun editShortcutOpensTheDialogForThatShortcut() {
        val id = addShortcut("Wikipedia", "https://www.wikipedia.org")
        showHome()
        awaitText("Wikipedia")

        composeRule.onNodeWithText("Wikipedia").performTouchInput { longClick() }
        composeRule.onNodeWithText("Edit shortcut").performClick()

        assertEquals(listOf(BrowserKey, ShortcutDialogKey(shortcutId = id)), keys())
    }

    @Test
    fun removeDeletesTheShortcutAtOnceAndItsUndoStillWorksOnceHomeIsGone() {
        addShortcut("Wikipedia", "https://www.wikipedia.org")
        showHome()
        awaitText("Wikipedia")

        composeRule.onNodeWithText("Wikipedia").performTouchInput { longClick() }
        composeRule.onNodeWithText("Remove").performClick()
        val message = runBlocking { withTimeout(5_000) { graph.snackbarController().current.filterNotNull().first() } }
        assertEquals(UiText.Res(R.string.snackbar_shortcut_removed), message.text)
        assertTrue(message.action is SnackbarAction.Undo)
        assertTrue(runBlocking { withTimeout(5_000) { graph.shortcutRepository().observe().first { it.isEmpty() } } }.isEmpty())

        // Home leaves the screen and its view model is gone, as after opening another shortcut.
        shown = false
        composeRule.waitForIdle()
        destination.viewModelStore.clear()
        graph.snackbarController().onActionClicked(message.id)

        val back = runBlocking { withTimeout(5_000) { graph.shortcutRepository().observe().first { it.isNotEmpty() } } }
        assertEquals(listOf("Wikipedia" to "https://www.wikipedia.org"), back.map { it.name to it.url })
    }

    @Test
    fun aContinueCardOpensItsSavedChatAndSeeAllOpensSkimHistory() {
        val chatId = runBlocking {
            graph.chatRepository().createChat(
                SavedChat(
                    kind = ChatKind.PAGE,
                    url = "https://daylightjournal.com/energy",
                    host = "daylightjournal.com",
                    title = "Why heat pumps are winning in cold climates",
                    snippet = "Cold-climate models now hold their efficiency well below freezing.",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        showHome()
        awaitText("Why heat pumps are winning in cold climates")

        composeRule.onNodeWithText("Why heat pumps are winning in cold climates").performScrollTo().performClick()
        assertEquals(listOf(BrowserKey, AskPageKey(savedChatId = chatId)), keys())

        navigator.back()
        composeRule.onNodeWithText("See all").performScrollTo().performClick()
        assertEquals(listOf(BrowserKey, SkimHistoryKey), keys())
    }

    // Voice search

    @Test
    fun withoutAnAppForSpeechInputHomeHasNoMic() {
        showHome()

        composeRule.onAllNodesWithContentDescription("Voice search").assertCountEquals(0)
    }

    @Test
    fun withoutAnAppForSpeechInputTheIncognitoNewTabPageHasNoMic() {
        showIncognito()

        composeRule.onAllNodesWithContentDescription("Voice search").assertCountEquals(0)
    }

    @Test
    fun theMicOfHomeOpensTheLayerWithWhatWasSaid() {
        installSpeechInput()
        speech.answer = "heat pump"
        showHome()

        composeRule.onNodeWithContentDescription("Voice search").performClick()
        composeRule.pass(150)

        field().assert(hasText("heat pump"))
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange("heat pump".length)))
        assertFalse(search.state.value.incognito)
        composeRule.onNodeWithText("Ask Skim").assertExists()
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH, speech.launched.single().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
    }

    @Test
    fun theMicOfTheIncognitoNewTabPageOpensTheIncognitoLayer() {
        installSpeechInput()
        speech.answer = "heat pump"
        showIncognito()

        composeRule.onNodeWithContentDescription("Voice search").performClick()
        composeRule.pass(150)

        field().assert(hasText("heat pump"))
        assertTrue(search.state.value.incognito)
        composeRule.onAllNodesWithText("Ask Skim").assertCountEquals(0)
    }

    @Test
    fun cancelledSpeechInputOpensNothing() {
        installSpeechInput()
        speech.answer = null
        showHome()

        composeRule.onNodeWithContentDescription("Voice search").performClick()
        composeRule.waitForIdle()

        assertEquals(1, speech.launched.size)
        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
    }

    @Test
    fun speechInputThatCannotStartAfterAllDoesNothing() {
        installSpeechInput()
        speech.failure = ActivityNotFoundException("No recognizer")
        showHome()

        composeRule.onNodeWithContentDescription("Voice search").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
        composeRule.onNodeWithText("Search or ask anything").assertExists()
    }

    // Helpers

    /** Shows Home for a new normal tab, the current one, and returns its id. */
    private fun showHome(): String {
        val tabId = graph.tabManager().newTab(incognito = false)
        setRoute { tabs -> tabs.tab(tabId) }
        return tabId
    }

    /** Shows the incognito New tab page for an incognito tab of the test's own. */
    private fun showIncognito(): Tab {
        val tab = HomeFixtures.newTabPage(id = "incognito-1", incognito = true)
        setRoute(incognito = true) { tab }
        return tab
    }

    private fun setRoute(incognito: Boolean = false, tabOf: (TabsState) -> Tab?) {
        composeRule.setSkimContent(incognito = incognito) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides destination,
                LocalActivityResultRegistryOwner provides speech.owner,
            ) {
                search = hiltViewModel(key = SEARCH_VIEW_MODEL_KEY)
                val tabs by graph.tabManager().state.collectAsStateWithLifecycle()
                val tab = tabOf(tabs)
                // As in the browser's slot: the New tab page shows while the tab has no page.
                if (shown && tab != null && tab.url == null) NewTabPageRoute(tab, navigator)
            }
        }
        composeRule.waitForIdle()
    }

    /** Shows the dialog destination of [key] in the window the navigation's dialog scene opens for it. */
    private fun showShortcutDialog(key: ShortcutDialogKey) {
        composeRule.setSkimContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides destination) {
                Dialog(onDismissRequest = navigator::back, properties = ShortcutDialogProperties) {
                    ShortcutDialogRoute(key, navigator)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun keys(): List<NavKey> = backStack.toList()

    private fun field() = composeRule.onNode(hasSetTextAction() and hasContentDescription("Search", substring = true))

    private fun type(text: String) {
        field().performTextInput(text)
        composeRule.pass(150)
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun addShortcut(name: String, url: String): Long = runBlocking {
        graph.shortcutRepository().add(name, url)
        withTimeout(5_000) { graph.shortcutRepository().observe().first { list -> list.any { it.url == url } } }.single { it.url == url }.id
    }

    private fun installSpeechInput() {
        val recognizer = ComponentName("com.example.speech", "com.example.speech.Recognizer")
        val packages = shadowOf(app.packageManager)
        packages.addActivityIfNotPresent(recognizer)
        packages.addIntentFilterForActivity(
            recognizer,
            IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )
    }

    private fun isSecure(attrs: WindowManager.LayoutParams) = attrs.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    /**
     * Answers speech input at once, as the recognizer would: [answer] as its best result, or a
     * cancel for null; or the launch fails with [failure]. Records each request.
     */
    private inner class SpeechRegistry : ActivityResultRegistry() {
        var answer: String? = null
        var failure: Exception? = null
        val launched = mutableListOf<Intent>()

        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry get() = this@SpeechRegistry
        }

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            failure?.let { throw it }
            launched += contract.createIntent(app, input)
            val spoken = answer
            if (spoken == null) {
                dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
            } else {
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(spoken)))
            }
        }
    }
}
