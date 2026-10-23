package com.dantech.skimbrowser.ui.browsing

import android.webkit.WebViewClient
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakeTabEngine.Call
import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.ui.components.SiteIndicator
import com.dantech.skimbrowser.ui.shell.AiFabMode
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The browsing screen's view model over the real tab manager (real database, settings and stores),
 * with a fake engine standing for the WebViews: the test plays what a WebView reports (pages
 * started and finished, errors, find counts) and reads what was asked of it. Time is the test's:
 * the 100 ms rest of the find text passes only when the test moves the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BrowsingViewModelTest {

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

    // The address bar

    @Test
    fun aPageShowsItsHostWithoutWwwBehindTheLock() = runTest {
        val (f, id) = page("https://www.DaylightJournal.com/energy/heat-pumps-cold?ref=home#top")
        val browsing = browsing(f, id)

        val state = browsing.state.value
        assertEquals("daylightjournal.com", state.address)
        assertEquals(SiteIndicator.Secure, state.indicator)
        assertNull(state.loadError)
    }

    @Test
    fun anHttpPageShowsTheGlobe() = runTest {
        val (f, id) = page("http://plain.example/news")

        assertEquals(SiteIndicator.NotSecure, browsing(f, id).state.value.indicator)
        assertEquals("plain.example", browsing(f, id).state.value.address)
    }

    @Test
    fun anInternationalizedHostShowsInPunycodeSoAnotherScriptCannotPassForAKnownSite() {
        assertEquals("xn--bcher-kva.de", pageAddressOf("https://www.b\u00FCcher.de/neu"))
        // The Cyrillic small letter a (U+0430) in place of the Latin one.
        assertEquals("xn--pple-43d.com", pageAddressOf("https://\u0430pple.com/"))
        assertEquals("xn--bcher-kva.de", errorAddressOf("https://b\u00FCcher.de/"))
    }

    @Test
    fun aPageWithoutAWebAddressShowsItsUrlAsPlainText() {
        assertEquals("about:blank", pageAddressOf("about:blank"))
        assertEquals("", pageAddressOf(null))
        val long = "data:text/html," + "a".repeat(10_000)
        assertEquals(256, pageAddressOf(long).length)
    }

    @Test
    fun aFailedPageShowsItsUrlWithoutTheSchemeBehindTheErrorIcon() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)

        f.engine.failPage(id, ERROR_PAGE, WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        runCurrent()

        val state = browsing.state.value
        assertEquals("nightbus-kansai.jp/timetable", state.address)
        assertEquals(SiteIndicator.Error, state.indicator)
        assertEquals(LoadError.Unreachable(ERROR_PAGE, "ERR_NAME_NOT_RESOLVED"), state.loadError)
    }

    @Test
    fun aFailedUrlKeepsItsPathQueryAndPortButNeverItsUserInfo() {
        assertEquals("www.example.com/A/b?q=1#top", errorAddressOf("https://www.Example.com/A/b?q=1#top"))
        assertEquals("example.com", errorAddressOf("https://example.com/"))
        assertEquals("example.com:8443/app", errorAddressOf("https://example.com:8443/app"))
        assertEquals("example.com/app", errorAddressOf("http://example.com:80/app"))
        assertEquals("the page's site, not the bank", "evil.example/login", errorAddressOf("https://bank.example:pw@evil.example/login"))
        assertEquals("[::1]/x", errorAddressOf("http://[::1]/x"))
        assertEquals("not a web address: as plain text after its scheme", "files/a b", errorAddressOf("ftp://files/a\u0000b"))
    }

    @Test
    fun anAddressNeverCarriesInvisibleOrReorderingCharacters() {
        val shown = listOf(
            errorAddressOf("https://example.com/invoice\u202Efdp.exe"),
            errorAddressOf("intent://scan/\u2066x\u2069\u200B#Intent;end"),
            pageAddressOf("about:blank\u202E\u200Dx\u0007"),
        )

        assertEquals(listOf("example.com/invoice%E2%80%AEfdp.exe", "scan/x#Intent;end", "about:blankx"), shown)
        shown.forEach { text -> assertTrue(text, text.none { it.code in 0x200B..0x200F || it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 || it.code < 0x20 }) }
    }

    @Test
    fun aLongFailedUrlIsCutToWhatTheBarCanUse() {
        val shown = errorAddressOf("https://example.com/" + "segment/".repeat(10_000))

        assertEquals(256, shown.length)
        assertTrue(shown.startsWith("example.com/segment/"))
    }

    @Test
    fun readerViewIsOfferedOnlyForAnArticleThatLoaded() = runTest {
        val (f, id) = page(PAGE)
        f.engine.evaluator = { _, _ -> "true" }
        val browsing = browsing(f, id)
        assertFalse(browsing.state.value.readerAvailable)

        f.engine.openPage(id, PAGE)
        runCurrent()
        assertTrue(browsing.state.value.readerAvailable)

        f.engine.failPage(id, ERROR_PAGE, WebViewClient.ERROR_TIMEOUT, "net::ERR_CONNECTION_TIMED_OUT")
        runCurrent()
        assertFalse("nothing to read on a page that failed", browsing.state.value.readerAvailable)
    }

    @Test
    fun theProgressOfALoadShowsUntilItIsDone() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)

        f.manager.events.onProgress(id, 58)
        runCurrent()
        assertEquals(0.58f, browsing.state.value.progress)

        f.manager.events.onProgress(id, 100)
        runCurrent()
        assertNull(browsing.state.value.progress)
    }

    // The toolbar

    @Test
    fun backIsOnWhenSystemBackHasSomethingToDoInTheTab() = runTest {
        val f = fixture()
        val plain = f.manager.newTab(incognito = false, url = PAGE)
        f.engine.openPage(plain, PAGE)
        val fromHome = f.manager.newTab(incognito = false)
        f.manager.load(fromHome, PAGE)
        f.engine.openPage(fromHome, PAGE)
        val external = f.manager.newTab(incognito = false, url = PAGE, openedByExternalApp = true)
        f.engine.openPage(external, PAGE)
        runCurrent()

        assertFalse("the first page of a plain tab: Back leaves", browsing(f, plain).state.value.backEnabled)
        assertTrue("back to the New tab page", browsing(f, fromHome).state.value.backEnabled)
        assertTrue("closes the tab and returns to the other app", browsing(f, external).state.value.backEnabled)

        f.engine.reportHistory(plain, canGoBack = true, canGoForward = false)
        runCurrent()
        assertTrue("the page before", browsing(f, plain).state.value.backEnabled)
    }

    @Test
    fun forwardFollowsTheTabsHistoryAndGoesForwardInIt() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)
        assertFalse(browsing.state.value.forwardEnabled)

        f.engine.reportHistory(id, canGoBack = true, canGoForward = true)
        runCurrent()
        assertTrue(browsing.state.value.forwardEnabled)

        browsing.goForward()
        assertEquals(Call.GoForward(id), f.engine.calls.last())
    }

    @Test
    fun theTabCountCountsOnlyTheTabsOfTheSameKind() = runTest {
        val (f, id) = page(PAGE)
        f.manager.newTab(incognito = false, url = PAGE, background = true)
        val incognito = f.manager.newTab(incognito = true, url = PAGE, background = true)
        runCurrent()

        assertEquals(2, browsing(f, id).state.value.tabCount)
        assertEquals(1, browsing(f, incognito).state.value.tabCount)
        assertTrue(browsing(f, incognito).state.value.incognito)
    }

    @Test
    fun theAiFabIsOnForAPageOffInIncognitoAndUnavailableUntilAFailedPageLoads() = runTest {
        val (f, id) = page(PAGE)
        val incognito = f.manager.newTab(incognito = true, url = PAGE, background = true)
        val browsing = browsing(f, id)
        assertEquals(AiFabMode.Enabled, browsing.state.value.aiFab)
        assertEquals(AiFabMode.OffInIncognito, browsing(f, incognito).state.value.aiFab)

        f.engine.failPage(id, ERROR_PAGE, WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_INTERNET_DISCONNECTED")
        runCurrent()
        assertEquals(AiFabMode.UnavailableUntilLoaded, browsing.state.value.aiFab)
    }

    // Find in page

    @Test
    fun findInPageOpensWhenItStartsForTheTab() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)
        assertNull(browsing.state.value.find)

        // The More menu starts it with an empty text.
        f.manager.find(id, "")
        runCurrent()

        assertEquals(FindUiState(countedText = ""), browsing.state.value.find)
        assertEquals("", browsing.findQuery.text.toString())
    }

    @Test
    fun theTextIsSearchedOnceItRestsFor100Ms() = runTest {
        val (f, id) = openFind()
        val browsing = browsing(f, id)
        f.engine.calls.clear()

        type(browsing, "h")
        advanceTimeBy(60)
        type(browsing, "heat")
        advanceTimeBy(99)
        runCurrent()
        assertTrue("nothing searched while typing", f.engine.callsOf<Call.Find>().isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(Call.Find(id, "heat")), f.engine.callsOf<Call.Find>())
    }

    @Test
    fun theCountComesFromTheWebViewForTheTextItSearched() = runTest {
        val (f, id) = openFind()
        val browsing = browsing(f, id)
        type(browsing, "heat pump")
        passTheRest()

        f.manager.events.onFindResult(id, 1, 6)
        runCurrent()

        val find = checkNotNull(browsing.state.value.find)
        assertEquals(FindUiState(countedText = "heat pump", activeMatch = 2, matchCount = 6), find)
        assertTrue(find.showsCount("heat pump"))
        assertFalse("not the count of a text typed since", find.showsCount("heat pumps"))
        assertFalse("no count for an empty field", FindUiState(countedText = "").showsCount(""))
    }

    @Test
    fun withoutAMatchTheCountIsZeroOfZeroAndNothingSteps() = runTest {
        val (f, id) = openFind()
        val browsing = browsing(f, id)
        type(browsing, "glacier")
        passTheRest()
        f.manager.events.onFindResult(id, 0, 0)
        runCurrent()

        val find = checkNotNull(browsing.state.value.find)
        assertEquals(0, find.activeMatch)
        assertEquals(0, find.matchCount)
        assertTrue("0/0 shows", find.showsCount("glacier"))
        assertFalse(find.canStep("glacier"))
    }

    @Test
    fun previousAndNextStepAndWrapAroundAtEitherEnd() = runTest {
        val (f, id) = openFind()
        val browsing = browsing(f, id)
        type(browsing, "heat")
        passTheRest()
        f.manager.events.onFindResult(id, 5, 6)
        runCurrent()
        assertTrue("on at the last match", checkNotNull(browsing.state.value.find).canStep("heat"))
        f.engine.calls.clear()

        browsing.findNext()
        // The WebView wraps around to the first match.
        f.manager.events.onFindResult(id, 0, 6)
        runCurrent()
        assertTrue("on at the first match", checkNotNull(browsing.state.value.find).canStep("heat"))
        browsing.findPrevious()

        assertEquals(listOf(Call.FindNext(id, forward = true), Call.FindNext(id, forward = false)), f.engine.calls)
        assertEquals(1, checkNotNull(browsing.state.value.find).activeMatch)
    }

    @Test
    fun nextRightAfterTypingSearchesTheTextInsteadOfSteppingThroughTheOldOne() = runTest {
        val (f, id) = openFind()
        val browsing = browsing(f, id)
        type(browsing, "heat")
        passTheRest()
        f.manager.events.onFindResult(id, 0, 6)
        type(browsing, "heat pump")
        f.engine.calls.clear()

        browsing.findNext()

        assertEquals(listOf<Call>(Call.Find(id, "heat pump")), f.engine.calls)
        passTheRest()
        assertEquals("searched once", 1, f.engine.callsOf<Call.Find>().size)
    }

    @Test
    fun closeClearsTheHighlightsTheTextAndTheBar() = runTest {
        val (f, id) = openFind()
        val browsing = browsing(f, id)
        type(browsing, "heat")
        passTheRest()
        type(browsing, "heat pumps")
        f.engine.calls.clear()

        browsing.closeFind()
        runCurrent()

        assertEquals(listOf<Call>(Call.ClearFind(id)), f.engine.calls)
        assertNull(browsing.state.value.find)
        assertNull(f.manager.runtime(id).value.find)
        assertEquals("", browsing.findQuery.text.toString())
        passTheRest()
        assertTrue("a text typed just before is not searched after", f.engine.callsOf<Call.Find>().isEmpty())
    }

    @Test
    fun aNewDocumentIsSearchedAgainForTheSameTextOnceItLoaded() = runTest {
        val (f, id) = openFind()
        val browsing = browsing(f, id)
        type(browsing, "heat")
        passTheRest()
        f.engine.calls.clear()

        // A link on the page: the new document starts, then finishes.
        f.engine.openPage(id, OTHER_PAGE, finish = false)
        runCurrent()
        assertTrue("the bar stays", browsing.state.value.find != null)
        assertTrue("not before the document loaded", f.engine.callsOf<Call.Find>().isEmpty())

        f.engine.finishPage(id, OTHER_PAGE)
        runCurrent()

        assertEquals(listOf(Call.Find(id, "heat")), f.engine.callsOf<Call.Find>())
        assertEquals("heat", f.manager.runtime(id).value.find?.query)
        f.manager.events.onFindResult(id, 0, 2)
        runCurrent()
        assertEquals(2, checkNotNull(browsing.state.value.find).matchCount)
    }

    @Test
    fun aDocumentSearchedWhileItLoadedIsSearchedAgainOnceItLoaded() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)
        f.engine.openPage(id, OTHER_PAGE, finish = false)
        f.manager.find(id, "")
        runCurrent()
        type(browsing, "heat")
        passTheRest()
        f.engine.calls.clear()

        f.engine.finishPage(id, OTHER_PAGE)
        runCurrent()

        assertEquals("the matches of the whole document", listOf(Call.Find(id, "heat")), f.engine.callsOf<Call.Find>())
    }

    @Test
    fun findInPageClosesWhenThePageFailsToLoad() = runTest {
        val (f, id) = openFind()
        val browsing = browsing(f, id)
        type(browsing, "heat")
        passTheRest()
        f.engine.calls.clear()

        f.engine.failPage(id, ERROR_PAGE, WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        runCurrent()

        assertNull(browsing.state.value.find)
        assertTrue(f.engine.calls.contains(Call.ClearFind(id)))
        assertTrue("never searched in the error page", f.engine.callsOf<Call.Find>().isEmpty())
    }

    // Try again and pull to refresh

    @Test
    fun tryAgainLoadsThePageAgainAndTheErrorGoesOnceItStarts() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)
        f.engine.failPage(id, ERROR_PAGE, WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        runCurrent()

        browsing.tryAgain()
        runCurrent()
        assertEquals(Call.Reload(id), f.engine.calls.last())
        assertNotNull("the WebView still shows its own error page until the load starts", browsing.state.value.loadError)

        f.engine.openPage(id, ERROR_PAGE)
        runCurrent()
        assertNull(browsing.state.value.loadError)
        assertEquals(SiteIndicator.Secure, browsing.state.value.indicator)
    }

    @Test
    fun tryAgainOnAPageThatStoppedWorkingClearsTheErrorAtOnce() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)
        repeat(3) {
            f.engine.loseRenderer(id)
            advanceTimeBy(1_000)
        }
        runCurrent()
        assertTrue(browsing.state.value.loadError is LoadError.Crashed)

        browsing.tryAgain()
        runCurrent()

        assertNull(browsing.state.value.loadError)
        assertEquals(Call.Reload(id), f.engine.calls.last())
    }

    @Test
    fun aPullReloadsAndTheIndicatorShowsUntilTheReloadIsDone() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)

        browsing.refresh()
        runCurrent()
        assertEquals(Call.Reload(id), f.engine.calls.last())
        assertTrue(browsing.state.value.refreshing)

        f.manager.events.onProgress(id, 10)
        runCurrent()
        f.engine.openPage(id, PAGE, finish = false)
        runCurrent()
        assertTrue("still loading", browsing.state.value.refreshing)

        f.engine.finishPage(id, PAGE)
        runCurrent()
        assertFalse(browsing.state.value.refreshing)
    }

    @Test
    fun aReloadThatNeverStartsLetsTheIndicatorGoAfterAMoment() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)

        browsing.refresh()
        advanceTimeBy(1_999)
        runCurrent()
        assertTrue(browsing.state.value.refreshing)

        advanceTimeBy(1)
        runCurrent()
        assertFalse(browsing.state.value.refreshing)
    }

    @Test
    fun aReloadThatFailsEndsTheRefresh() = runTest {
        val (f, id) = page(PAGE)
        val browsing = browsing(f, id)

        browsing.refresh()
        f.manager.events.onProgress(id, 10)
        runCurrent()
        f.engine.failPage(id, PAGE, WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_INTERNET_DISCONNECTED")
        runCurrent()

        assertFalse(browsing.state.value.refreshing)
        assertTrue(browsing.state.value.loadError is LoadError.NoInternet)
    }

    // Helpers

    private fun TestScope.fixture(): TabManagerFixture {
        val f = TabManagerFixture(this, folder.root).also { fixtures += it }
        f.manager.attach(f.engine)
        return f
    }

    /** A current tab showing [url], loaded. */
    private fun TestScope.page(url: String): Pair<TabManagerFixture, String> {
        val f = fixture()
        val id = f.manager.newTab(incognito = false, url = url)
        f.engine.openPage(id, url)
        runCurrent()
        return f to id
    }

    /** A loaded page with Find in page open, as the More menu opens it. */
    private fun TestScope.openFind(): Pair<TabManagerFixture, String> {
        val (f, id) = page(PAGE)
        f.manager.find(id, "")
        runCurrent()
        return f to id
    }

    /** The view model of [tabId]'s screen while the screen shows it: its state is collected. */
    private fun TestScope.browsing(f: TabManagerFixture, tabId: String): BrowsingViewModel {
        val viewModel = kept { BrowsingViewModel(tabId, f.manager) }
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private inline fun <reified T : ViewModel> kept(noinline make: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { make() } })["view-model-${viewModelCount++}", T::class]

    /** Types [text] into the field as the input field would, then lets the view model see it. */
    private fun TestScope.type(browsing: BrowsingViewModel, text: String) {
        browsing.findQuery.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    /** Lets the find text rest the 100 ms after which it is searched. */
    private fun TestScope.passTheRest() {
        advanceTimeBy(100)
        runCurrent()
    }

    private companion object {
        const val PAGE = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val OTHER_PAGE = "https://daylightjournal.com/energy/batteries"
        const val ERROR_PAGE = "https://nightbus-kansai.jp/timetable"
    }
}
