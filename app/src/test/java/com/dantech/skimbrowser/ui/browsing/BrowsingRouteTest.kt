package com.dantech.skimbrowser.ui.browsing

import android.content.pm.PackageInfo
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.ReleaseProcessResources
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.TestStorage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import com.dantech.skimbrowser.ai.AiGate
import com.dantech.skimbrowser.browser.BrowserHost
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.FakeTabEngine
import com.dantech.skimbrowser.browser.FakeTabEngine.Call
import com.dantech.skimbrowser.domain.tab.HitTarget
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.browser.SkimWebView
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabEngineAttachment
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TabRuntime
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.browser.BrowserRoute
import com.dantech.skimbrowser.ui.navigation.AskTabsKey
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.LinkOptionsKey
import com.dantech.skimbrowser.ui.navigation.ReaderKey
import com.dantech.skimbrowser.ui.navigation.SiteInfoKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.TabsKey
import com.dantech.skimbrowser.ui.search.SearchViewModel
import com.dantech.skimbrowser.ui.search.latestDialog
import com.dantech.skimbrowser.ui.search.pass
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dantech.skimbrowser.HiltRouteActivity
import com.dantech.skimbrowser.ui.search.SEARCH_VIEW_MODEL_KEY
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
import org.robolectric.shadows.ShadowWebView

/**
 * The browsing screen in the browser's slot over the app as it runs: a real container on a
 * temporary directory, the real navigator over a back stack, the Activity's browser host (its pool
 * shows the tab's WebView). The tab manager drives a fake engine instead of that pool, so the test
 * plays what the page reports and reads what was asked of it. Incognito tabs cannot open without
 * WebView profiles, so an incognito tab is one of the test's own.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE_QUALIFIERS)
class BrowsingRouteTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    @get:Rule(order = 2)
    val storage = object : TestWatcher() {
        override fun starting(description: Description) {
            ShadowWebView.setCurrentWebViewPackage(
                PackageInfo().apply {
                    packageName = "com.google.android.webview"
                    versionName = "120.0.6099.230"
                },
            )
            TestStorage.current.set(AppStorage.temp(folder.newFolder()))
        }
    }

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<HiltRouteActivity>()

    @Inject lateinit var resources: ReleaseProcessResources
    @Inject lateinit var tabs: TabManager
    @Inject lateinit var tabEngine: TabEngineAttachment
    @Inject lateinit var incognitoSession: IncognitoSession
    @Inject lateinit var snackbarController: SnackbarController
    @Inject lateinit var aiGate: AiGate
    private lateinit var host: BrowserHost
    private lateinit var navigator: SkimNavigator
    private lateinit var engine: FakeTabEngine
    private val backStack = NavBackStack<NavKey>(BrowserKey)

    /**
     * Ask Skim reads the site's permission on this dispatcher. The compose test is itself inside
     * runTest, so work posted to Main never runs while the test is blocked in waitUntil. This
     * scheduler is advanced from the wait.
     */
    private val gateDispatcher = StandardTestDispatcher()
    private lateinit var gateScope: CoroutineScope
    private var gateError: Throwable? = null

    /** The browser destination's store. Extras come from the Hilt activity so assisted view models can be created. */
    private val destination = object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = composeRule.activity.defaultViewModelProviderFactory
        override val defaultViewModelCreationExtras: CreationExtras
            get() = composeRule.activity.defaultViewModelCreationExtras
    }
    private lateinit var search: SearchViewModel

    @Before
    fun setUp() {
        // Robolectric reports no WebView package, so page scripts would stay off and Reader view
        // would never be offered. A current package makes the check run, as it does on a device.
        gateError = null
        gateScope = CoroutineScope(
            SupervisorJob() + gateDispatcher + CoroutineExceptionHandler { _, error -> gateError = error },
        )
        hiltRule.inject()
        host = composeRule.activity.host
        engine = FakeTabEngine(tabs.events)
        tabEngine.attach(engine)
        navigator = SkimNavigator(
            backStack,
            tabs,
            incognitoSession,
            host.secure,
            snackbarController,
            aiGate,
            gateScope,
        )
    }

    @After
    fun tearDown() {
        destination.viewModelStore.clear()
        host.destroy()
        tabEngine.detach(engine)
        gateScope.cancel()
        if (::resources.isInitialized) resources.release()
        TestStorage.current.set(null)
    }

    // The address and Search & suggestions

    @Test
    fun theAddressOpensTheSearchWithThePagesUrlSelected() {
        tabs.newTab(incognito = false, url = PAGE)
        showBrowser()

        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").performClick()
        composeRule.waitForIdle()

        field().assert(hasText(PAGE))
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, PAGE.length)))
        assertFalse(search.state.value.incognito)
        assertFalse(isSecure(latestDialog().window!!.attributes))
    }

    @Test
    fun anIncognitoTabsAddressOpensTheIncognitoSearch() {
        val tab = Tab(id = "incognito-1", incognito = true, url = PAGE, title = "", groupId = null, position = 0, createdAt = 0, lastAccessedAt = 0)
        composeRule.setSkimContent(incognito = true) {
            Provided { BrowsingRoute(tab, TabRuntime(), navigator) }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription("Edit address", substring = true)).performClick()
        composeRule.waitForIdle()

        field().assert(hasText(PAGE))
        assertTrue(search.state.value.incognito)
        assertTrue("the layer's window is secure", isSecure(latestDialog().window!!.attributes))
    }

    @Test
    fun anAddressEnteredInTheSearchLoadsInThisTabAndTheSearchCloses() {
        val id = tabs.newTab(incognito = false, url = PAGE)
        showBrowser()
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").performClick()

        field().performTextReplacement("example.org")
        field().performImeAction()
        composeRule.waitForIdle()

        assertEquals("https://example.org", tabs.state.value.tab(id)?.url)
        assertEquals(Call.Load(id, "https://example.org"), engine.calls.last())
        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
        assertEquals("nothing typed is kept", "", search.query.text.toString())
    }

    @Test
    fun askSkimInTheSearchAsksAcrossTheTabsAndTheSearchCloses() {
        tabs.newTab(incognito = false, url = PAGE)
        showBrowser()
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").performClick()

        field().performTextReplacement("heat pump")
        composeRule.pass(150)
        composeRule.onNodeWithText("Ask Skim").performClick()

        assertEquals(listOf(BrowserKey, AskTabsKey(prompt = "heat pump")), keys())
        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
    }

    // Long press, the bar and the toolbar

    @Test
    fun aLongPressOnThisTabsPageOpensLinkOrImageOptionsForWhatItHit() {
        val id = tabs.newTab(incognito = false, url = PAGE)
        val other = tabs.newTab(incognito = false, url = OTHER_PAGE, background = true)
        showBrowser()
        val link = HitTarget.Link("https://daylightjournal.com/energy/batteries", "Batteries")
        val image = HitTarget.Image("https://daylightjournal.com/photo.jpg", alt = null, linkUrl = null)

        tabs.events.onHitTarget(other, link)
        composeRule.waitForIdle()
        assertEquals("another tab's long press opens nothing here", listOf<NavKey>(BrowserKey), keys())

        tabs.events.onHitTarget(id, link)
        composeRule.waitForIdle()
        assertEquals(listOf(BrowserKey, LinkOptionsKey(id, link)), keys())

        navigator.back()
        tabs.events.onHitTarget(id, image)
        composeRule.waitForIdle()
        assertEquals(listOf(BrowserKey, LinkOptionsKey(id, image)), keys())
    }

    @Test
    fun theBarAndTheToolbarOpenTheirDestinationsForThisTab() {
        val id = tabs.newTab(incognito = false, url = PAGE)
        engine.evaluator = { _, _ -> "true" }
        engine.openPage(id, PAGE)
        showBrowser()

        val opened = listOf(
            "Site information, connection is secure",
            "Reader view",
            "Tabs, 1 open",
        ).map { label ->
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription(label).performClick()
            composeRule.waitForIdle()
            keys().last().also { navigator.back() }
        }

        assertEquals(
            listOf(SiteInfoKey(id), ReaderKey(id), TabsKey(incognito = false)),
            opened,
        )
    }

    @Test
    fun forwardGoesForwardInTheTab() {
        val id = tabs.newTab(incognito = false, url = PAGE)
        engine.openPage(id, PAGE)
        engine.reportHistory(id, canGoBack = false, canGoForward = true)
        showBrowser()

        composeRule.onNodeWithContentDescription("Forward").performClick()

        assertEquals(Call.GoForward(id), engine.calls.last())
    }

    @Test
    fun theToolbarsBackDoesWhatSystemBackDoesInTheTab() {
        val id = tabs.newTab(incognito = false)
        tabs.load(id, PAGE)
        engine.openPage(id, PAGE)
        engine.reportHistory(id, canGoBack = true, canGoForward = false)
        showBrowser()

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals("the page before", Call.GoBack(id), engine.calls.last())

        engine.reportHistory(id, canGoBack = false, canGoForward = false)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        assertNull("the first page of a tab from the New tab page goes back to it", tabs.state.value.tab(id)?.url)
        composeRule.onNodeWithTag(NEW_TAB_PAGE).assertExists()
    }

    // Find in page

    @Test
    fun systemBackClosesFindInPageBeforeGoingBackInTheTab() {
        val id = tabs.newTab(incognito = false, url = PAGE)
        engine.openPage(id, PAGE)
        engine.reportHistory(id, canGoBack = true, canGoForward = false)
        showBrowser()
        tabs.find(id, "")
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Close find in page").assertExists()
        engine.calls.clear()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(listOf<Call>(Call.ClearFind(id)), engine.calls)
        composeRule.onAllNodesWithContentDescription("Close find in page").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").assertExists()

        Espresso.pressBack()
        composeRule.waitForIdle()
        assertEquals(Call.GoBack(id), engine.calls.last())
    }

    // Page failed to load

    @Test
    fun anErrorCoversTheTabsWebViewWithoutBuildingItAgain() {
        val id = tabs.newTab(incognito = false, url = PAGE)
        showBrowser()
        val webView = webViewsInWindow().single()

        engine.failPage(id, FAILED_PAGE, WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Can't reach this site").assertExists()
        composeRule.onNodeWithContentDescription("Edit address, nightbus-kansai.jp/timetable").assertExists()
        assertSame("the page stays below the error", webView, webViewsInWindow().single())
        assertTrue(webView.isAttachedToWindow)
        assertFalse(shadowOf(webView).wasDestroyCalled())

        engine.openPage(id, PAGE)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Page didn't load").assertCountEquals(0)
        assertSame(webView, webViewsInWindow().single())
    }

    @Test
    fun tryAgainLoadsThePageAgain() {
        val id = tabs.newTab(incognito = false, url = PAGE)
        engine.failPage(id, FAILED_PAGE, WebViewClient.ERROR_TIMEOUT, "net::ERR_CONNECTION_TIMED_OUT")
        showBrowser()
        engine.calls.clear()

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf<Call>(Call.Reload(id)), engine.calls)
    }

    @Test
    fun internetSettingsOpensTheInternetPanel() {
        val id = tabs.newTab(incognito = false, url = PAGE)
        engine.failPage(id, FAILED_PAGE, WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_INTERNET_DISCONNECTED")
        showBrowser()

        composeRule.onNodeWithText("Internet settings").performClick()

        assertEquals(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, shadowOf(composeRule.activity).nextStartedActivity?.action)
    }

    // Helpers

    /** Shows the browser with its page slot, as the browser destination does. */
    private fun showBrowser() {
        composeRule.setSkimContent {
            Provided {
                BrowserRoute(
                    navigator = navigator,
                    newTabPage = { Box(Modifier.fillMaxSize().testTag(NEW_TAB_PAGE)) },
                    page = { tab, runtime -> BrowsingRoute(tab, runtime, navigator) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Provided(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalBrowserHost provides host, LocalViewModelStoreOwner provides destination) {
            search = hiltViewModel(key = SEARCH_VIEW_MODEL_KEY)
            content()
        }
    }

    private fun keys(): List<NavKey> = backStack.toList()

    private fun field() = composeRule.onNode(hasSetTextAction() and hasContentDescription("Search", substring = true))

    private fun isSecure(attrs: WindowManager.LayoutParams) = attrs.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    /** The tabs' WebViews attached anywhere in the Activity's window. */
    private fun webViewsInWindow(): List<SkimWebView> {
        val found = mutableListOf<SkimWebView>()
        fun visit(view: View) {
            if (view is SkimWebView) found += view
            if (view is ViewGroup) (0 until view.childCount).forEach { visit(view.getChildAt(it)) }
        }
        visit(composeRule.activity.window.decorView)
        return found
    }

    private companion object {
        const val PAGE = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val OTHER_PAGE = "https://daylightjournal.com/energy/batteries"
        const val FAILED_PAGE = "https://nightbus-kansai.jp/timetable"
        const val NEW_TAB_PAGE = "new-tab-page"
    }
}
