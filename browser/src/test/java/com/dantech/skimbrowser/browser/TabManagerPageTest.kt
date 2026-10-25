package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.domain.tab.HitTarget
import com.dantech.skimbrowser.domain.tab.PageEvent

import android.os.Bundle
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakeTabEngine.Call
import com.dantech.skimbrowser.browser.page.HighlightRequest
import com.dantech.skimbrowser.browser.page.PageBlock
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TabManagerPageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val fixtures = mutableListOf<TabManagerFixture>()

    @After
    fun tearDown() = runBlocking {
        fixtures.forEach { it.close() }
    }

    // Page reports

    @Test
    fun pageReportsUpdateTheRuntimeAndTheTab() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false)
        f.manager.load(id, "https://kotlinlang.org/")
        assertEquals(listOf(Call.Load(id, "https://kotlinlang.org/")), f.engine.callsOf<Call.Load>())
        assertTrue("it started on the New tab page", f.tab(id)?.fromNewTabPage == true)

        val nonce = f.engine.openPage(id, "https://kotlinlang.org/docs/", "Kotlin docs", finish = false)
        f.events.onProgress(id, 40)
        f.events.onNavigationState(id, canGoBack = true, canGoForward = false)

        val loading = f.manager.runtime(id).value
        assertEquals(0.4f, loading.progress)
        assertEquals("https://kotlinlang.org/docs/", loading.committedUrl)
        assertEquals(nonce, loading.loadNonce)
        assertTrue(loading.isSecure)
        assertTrue(loading.canGoBack)
        assertFalse(loading.pageLoaded)
        assertEquals("https://kotlinlang.org/docs/", f.tab(id)?.url)
        assertEquals("Kotlin docs", f.tab(id)?.title)

        f.engine.finishPage(id, "https://kotlinlang.org/docs/")
        assertTrue(f.manager.runtime(id).value.pageLoaded)
        assertNull(f.manager.runtime(id).value.progress)

        f.engine.openPage(id, "http://plain.example/")
        assertFalse(f.manager.runtime(id).value.isSecure)
    }

    @Test
    fun aNewDocumentClearsTheOldTitleUntilThePageSetsOne() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/", "A")

        f.engine.openPage(id, "https://b.example/")

        assertEquals("", f.tab(id)?.title)
    }

    @Test
    fun aTitleLosesTheCharactersThatMakeItReadAsSomethingElse() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/")

        // A right-to-left override shows "elgoog" as "google"; zero-width and line breaks hide words.
        f.events.onTitle(id, "Sign in to \u202Eelgoog\u202C\u200B\nBank\u2066\u2069")

        assertEquals("Sign in to elgoog Bank", f.tab(id)?.title)
        advanceTimeBy(5_000)
        f.settle()
        assertEquals("Sign in to elgoog Bank", f.historyEntries().single().title)
    }

    @Test
    fun aHugeTitleIsCutWithoutSplittingACharacter() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/")

        f.events.onTitle(id, "a".repeat(Tab.MAX_TITLE_LENGTH - 1) + "😀" + "b".repeat(1_000_000))

        val title = f.tab(id)?.title.orEmpty()
        assertEquals(Tab.MAX_TITLE_LENGTH - 1, title.length)
        assertFalse(title.last().isHighSurrogate())
        advanceTimeBy(5_000)
        f.settle()
        assertEquals(title, f.openRows().single().title)
        assertEquals(title, f.historyEntries().single().title)
    }

    @Test
    fun pageEventsTellSameDocumentNavigationsFromNewDocuments() = runTest {
        val f = attached()
        val events = mutableListOf<PageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.manager.pageEvents.toList(events) }
        val id = f.manager.newTab(incognito = false, url = "https://spa.example/")

        val first = f.engine.openPage(id, "https://spa.example/")
        f.engine.navigateInDocument(id, "https://spa.example/inbox")
        val second = f.engine.openPage(id, "https://other.example/")

        assertEquals(
            listOf(
                PageEvent.PageStarted(id, "https://spa.example/", first),
                PageEvent.Navigated(id, "https://spa.example/", isSameDocument = false),
                PageEvent.PageFinished(id, "https://spa.example/", first),
                PageEvent.Navigated(id, "https://spa.example/inbox", isSameDocument = true),
                PageEvent.PageStarted(id, "https://other.example/", second),
                PageEvent.Navigated(id, "https://other.example/", isSameDocument = false),
                PageEvent.PageFinished(id, "https://other.example/", second),
            ),
            events,
        )
        assertTrue(second > first)
    }

    @Test
    fun aSameDocumentNavigationKeepsTheLoadNonceAndMovesTheCommittedUrl() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://spa.example/")
        val nonce = f.engine.openPage(id, "https://spa.example/", "Mail")

        f.engine.navigateInDocument(id, "https://spa.example/inbox/7")

        val runtime = f.manager.runtime(id).value
        assertEquals(nonce, runtime.loadNonce)
        assertEquals("https://spa.example/inbox/7", runtime.committedUrl)
        assertEquals("https://spa.example/inbox/7", f.tab(id)?.url)
        assertEquals("Mail", f.tab(id)?.title)
    }

    // History

    @Test
    fun historyRecordsVisitsOfNormalWebPagesWithTheirTitles() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        val private = f.manager.newTab(incognito = true, url = "https://private.example/")

        f.engine.openPage(id, "https://a.example/", "First")
        f.engine.navigateInDocument(id, "https://a.example/#part-2")
        f.engine.openPage(id, "about:blank")
        f.engine.openPage(id, "data:text/html,hi")
        f.engine.failPage(id, "https://down.example/", WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        f.engine.openPage(id, "https://b.example/" + "x".repeat(9_000), "Long")
        f.engine.openPage(private, "https://private.example/", "Private")
        f.engine.openPage(id, "https://c.example/")
        f.events.onTitle(id, "Third")
        f.settle()

        assertEquals(
            mapOf("https://a.example/" to "First", "https://a.example/#part-2" to "First", "https://c.example/" to "Third"),
            f.historyEntries().associate { it.url to it.title },
        )
    }

    @Test
    fun aTitleAnimationReachesHistoryOnlyAFewTimesAndTheTabOnce() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://chat.example/")
        f.engine.openPage(id, "https://chat.example/")
        f.settle()

        repeat(20) { index -> f.events.onTitle(id, "($index) Chat") }
        advanceTimeBy(5_000)
        f.settle()

        assertEquals("(4) Chat", f.historyEntries().single().title)
        assertEquals("(19) Chat", f.openRows().single().title)
    }

    @Test
    fun aVeryLongUrlStaysWithTheTabButIsStoredShorter() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        val long = "https://a.example/page?state=" + "q".repeat(20_000) + "#" + "f".repeat(20_000)

        f.engine.openPage(id, long)
        f.settle()

        assertEquals(long, f.tab(id)?.url)
        assertEquals("https://a.example/page", f.openRows().single().url)
        assertTrue(f.historyEntries().isEmpty())
    }

    // Load errors and the network

    @Test
    fun aFailedLoadShowsItsErrorStateUntilTheNextLoad() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://down.example/")

        f.engine.failPage(id, "https://down.example/", WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        assertEquals(LoadError.Unreachable("https://down.example/", "ERR_NAME_NOT_RESOLVED"), f.manager.runtime(id).value.loadError)

        f.network.hasActiveNetwork = false
        f.engine.failPage(id, "https://down.example/", WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        assertEquals(LoadError.NoInternet("https://down.example/", "ERR_NAME_NOT_RESOLVED"), f.manager.runtime(id).value.loadError)

        f.network.hasActiveNetwork = true
        f.engine.openPage(id, "https://down.example/")
        assertNull(f.manager.runtime(id).value.loadError)
    }

    @Test
    fun theShownTabReloadsByItselfOnceTheNetworkIsBackForTwoSeconds() = runTest {
        val f = attached()
        val shown = f.manager.newTab(incognito = false, url = "https://news.example/")
        val behind = f.manager.newTab(incognito = false, url = "https://mail.example/", background = true)
        f.network.goOffline()
        runCurrent()
        f.engine.failPage(shown, "https://news.example/", WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_INTERNET_DISCONNECTED")
        f.engine.failPage(behind, "https://mail.example/", WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_INTERNET_DISCONNECTED")

        f.network.comeBack()
        runCurrent()
        advanceTimeBy(1_500)
        f.network.goOffline()
        runCurrent()
        f.network.comeBack()
        runCurrent()
        advanceTimeBy(1_999)
        assertTrue("a network that came back only briefly reloads nothing", f.engine.callsOf<Call.Reload>().isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf(Call.Reload(shown)), f.engine.callsOf<Call.Reload>())

        f.manager.select(behind)
        assertEquals("the other tab reloads when it is shown", listOf(Call.Reload(shown), Call.Reload(behind)), f.engine.callsOf<Call.Reload>())
    }

    @Test
    fun aNetworkThatIsUpButNotValidatedReloadsNothing() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://news.example/")
        f.network.goOffline()
        runCurrent()
        f.engine.failPage(id, "https://news.example/", WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_INTERNET_DISCONNECTED")

        f.network.hasActiveNetwork = true
        advanceTimeBy(10_000)
        f.manager.select(id)

        assertTrue(f.engine.callsOf<Call.Reload>().isEmpty())
    }

    // Renderer crashes

    @Test
    fun aTabWhoseRendererKeepsDyingShowsTheErrorUntilTryAgain() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://heavy.example/")
        f.engine.openPage(id, "https://heavy.example/", "Heavy")

        assertEquals(RendererGoneAction.RESTORE, f.engine.loseRenderer(id))
        assertNull(f.manager.runtime(id).value.loadError)
        assertFalse(f.manager.runtime(id).value.pageLoaded)
        advanceTimeBy(1_000)
        assertEquals(RendererGoneAction.RESTORE, f.engine.loseRenderer(id))
        advanceTimeBy(1_000)
        assertEquals(RendererGoneAction.SHOW_ERROR, f.engine.loseRenderer(id))
        assertEquals(LoadError.Crashed("https://heavy.example/", "RENDERER_CRASHED"), f.manager.runtime(id).value.loadError)
        assertEquals(RendererGoneAction.SHOW_ERROR, f.engine.loseRenderer(id, didCrash = false))
        assertEquals("RENDERER_KILLED", f.manager.runtime(id).value.loadError?.codeName)

        f.manager.reload(id)
        assertNull("Try again clears the error", f.manager.runtime(id).value.loadError)
        assertEquals(listOf(Call.Reload(id)), f.engine.callsOf<Call.Reload>())

        advanceTimeBy(60_000)
        assertEquals("a minute later it recovers by itself again", RendererGoneAction.RESTORE, f.engine.loseRenderer(id))
    }

    @Test
    fun aTabThatShowsTheCrashErrorHasNoHistoryToGoBackInUntilTryAgain() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://heavy.example/")
        f.engine.openPage(id, "https://heavy.example/", "Heavy")
        f.engine.reportHistory(id, canGoBack = true, canGoForward = true)

        assertEquals(RendererGoneAction.RESTORE, f.engine.loseRenderer(id))
        assertTrue("a restored WebView brings its history back", f.manager.runtime(id).value.canGoBack)
        assertTrue(f.manager.runtime(id).value.canGoForward)
        advanceTimeBy(1_000)
        assertEquals(RendererGoneAction.RESTORE, f.engine.loseRenderer(id))
        advanceTimeBy(1_000)
        assertEquals(RendererGoneAction.SHOW_ERROR, f.engine.loseRenderer(id))

        assertFalse("there is no WebView to go back in", f.manager.runtime(id).value.canGoBack)
        assertFalse(f.manager.runtime(id).value.canGoForward)

        f.manager.reload(id)
        f.engine.reportHistory(id, canGoBack = true, canGoForward = false)
        assertTrue("Try again brings the history back", f.manager.runtime(id).value.canGoBack)
    }

    // Commands to the engine

    @Test
    fun commandsWaitForTheEngineWhileNoActivityIsAttached() = runTest {
        val f = fixture()
        val a = f.manager.newTab(incognito = false)
        val b = f.manager.newTab(incognito = false, url = "https://b.example/")

        f.manager.load(a, "https://a.example/")
        f.manager.reload(b)
        f.manager.reload(a)
        assertEquals("https://a.example/", f.tab(a)?.url)
        assertFalse(f.manager.goBack(a))

        f.engines.attach(f.engine)

        assertEquals(listOf(Call.Load(a, "https://a.example/"), Call.Reload(b)), f.engine.calls)
    }

    @Test
    fun commandsReachTheEngine() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/")

        f.engine.canGoBack = false
        assertFalse(f.manager.goBack(id))
        f.engine.canGoBack = true
        assertTrue(f.manager.goBack(id))
        f.manager.goForward(id)
        f.manager.setDesktopSite(id, true)
        f.manager.setDesktopSite(id, true)

        assertEquals(listOf(Call.GoBack(id), Call.GoBack(id), Call.GoForward(id), Call.SetDesktop(id, true)), f.engine.calls.filterNot { it is Call.Evaluate })
        assertTrue(f.tab(id)?.desktopSite == true)
        f.settle()
        assertTrue(f.openRows().single().desktopSite)
    }

    @Test
    fun findInPageKeepsItsStateInTheRuntime() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/")

        f.manager.findNext(id, forward = true)
        assertTrue("nothing to move through yet", f.engine.callsOf<Call.FindNext>().isEmpty())
        f.manager.find(id, "heat pump")
        assertEquals(FindState("heat pump"), f.manager.runtime(id).value.find)
        f.events.onFindResult(id, activeMatchIndex = 2, matchCount = 5)
        assertEquals(FindState("heat pump", 2, 5), f.manager.runtime(id).value.find)
        f.manager.findNext(id, forward = false)
        f.manager.clearFind(id)
        f.events.onFindResult(id, activeMatchIndex = 0, matchCount = 1)

        assertNull("a late result does not reopen it", f.manager.runtime(id).value.find)
        assertEquals(
            listOf(Call.Find(id, "heat pump"), Call.FindNext(id, false), Call.ClearFind(id)),
            f.engine.calls.filter { it is Call.Find || it is Call.FindNext || it is Call.ClearFind },
        )
    }

    @Test
    fun backToTheNewTabPageForgetsThePageAndItsSavedState() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false)
        f.manager.load(id, "https://a.example/")
        f.engine.openPage(id, "https://a.example/", "A")
        f.engine.evict(id)
        f.engine.openPage(id, "https://a.example/", "A")
        f.thumbnails.put(id, android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888), incognito = false)
        f.settle()
        assertTrue(File(f.stateDir, "$id.bin").isFile)

        f.manager.returnToNewTabPage(id)
        f.settle()

        val tab = f.tab(id) ?: throw AssertionError("the tab stays")
        assertNull(tab.url)
        assertEquals("", tab.title)
        assertFalse(tab.fromNewTabPage)
        assertTrue(Call.SaveAndDestroy(id) in f.engine.calls)
        assertFalse("the state the destroyed WebView handed over is not kept", File(f.stateDir, "$id.bin").exists())
        assertFalse(File(f.thumbsDir, "$id.webp").exists())
        assertNull(f.openRows().single().url)
    }

    // Popups and long-press

    @Test
    fun aPopupOpensAsATabOfItsOpenerAndClosesBackToIt() = runTest {
        val f = attached()
        val opener = f.manager.newTab(incognito = false, url = "https://shop.example/")

        val popup = f.events.onPopupRequested(opener) ?: throw AssertionError("refused")

        assertEquals(popup, f.manager.state.value.currentTabId)
        assertEquals(opener, f.tab(popup)?.openerTabId)
        assertEquals("about:blank", f.tab(popup)?.url)
        assertFalse(f.tab(popup)?.incognito ?: true)

        f.events.onPopupClosed(opener)
        assertNotNull("a tab that is no popup does not close itself", f.tab(opener))

        f.events.onPopupClosed(popup)
        assertNull(f.tab(popup))
        assertFalse("closed for good, without Undo", popup in f.manager.pendingCloseIds())
        assertEquals(opener, f.manager.state.value.currentTabId)
        assertNull(f.events.onPopupRequested("unknown"))
    }

    @Test
    fun closingAPopupReturnsToItsOpenerEvenWhenAnotherTabWasUsedMoreRecently() = runTest {
        val f = attached()
        val opener = f.manager.newTab(incognito = false, url = "https://shop.example/")
        advanceTimeBy(10)
        val other = f.manager.newTab(incognito = false, url = "https://other.example/")
        advanceTimeBy(10)
        f.manager.select(opener)
        advanceTimeBy(10)
        val popup = f.events.onPopupRequested(opener) ?: throw AssertionError("refused")
        advanceTimeBy(10)
        f.manager.select(other)
        advanceTimeBy(10)
        f.manager.select(popup)

        f.events.onPopupClosed(popup)

        assertEquals(opener, f.manager.state.value.currentTabId)
    }

    @Test
    fun aPopupOfAnIncognitoTabIsIncognito() = runTest {
        val f = attached()
        val opener = f.manager.newTab(incognito = true, url = "https://bank.example/")

        val popup = f.events.onPopupRequested(opener) ?: throw AssertionError("refused")

        assertTrue(f.tab(popup)?.incognito == true)
        f.settle()
        assertEquals(0L, f.rowCount())
    }

    @Test
    fun longPressTargetsReachCollectorsWithinBounds() = runTest {
        val f = attached()
        val hits = mutableListOf<Pair<String, HitTarget>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.manager.hitTargets.toList(hits) }
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")

        f.events.onHitTarget(id, HitTarget.Link("https://b.example/", "B" + "x".repeat(5_000)))
        f.events.onHitTarget(id, HitTarget.Image("data:image/png;base64," + "A".repeat(100_000), alt = null, linkUrl = null))
        f.events.onHitTarget("unknown", HitTarget.Link("https://c.example/", null))
        f.events.onHitTarget(id, HitTarget.Image("https://a.example/i.png", alt = "Photo", linkUrl = "https://a.example/"))

        assertEquals(2, hits.size)
        assertEquals(1_024, (hits[0].second as HitTarget.Link).text?.length)
        assertEquals(id to HitTarget.Image("https://a.example/i.png", "Photo", "https://a.example/"), hits[1])
    }

    // Saved state

    @Test
    fun savedStateGoesToDiskForNormalTabsAndStaysInMemoryForIncognitoTabs() = runTest {
        val f = attached()
        val normal = f.manager.newTab(incognito = false, url = "https://a.example/")
        val private = f.manager.newTab(incognito = true, url = "https://b.example/")
        f.engine.openPage(normal, "https://a.example/")
        f.engine.openPage(private, "https://b.example/")

        f.engine.evict(normal)
        f.engine.evict(private)
        f.settle()

        assertEquals(setOf("$normal.bin"), f.stateDir.list().orEmpty().toSet())
        assertEquals("history of $private", f.stateStore.load(private, incognito = true)?.getString(FakeTabEngine.STATE_KEY))
        assertFalse(f.manager.runtime(normal).value.pageLoaded)
    }

    @Test
    fun aStateForTheNewTabPageOrForAGoneTabIsNotKept() = runTest {
        val f = attached()
        val blank = f.manager.newTab(incognito = false)
        val gone = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.manager.finalizeClose(f.manager.close(listOf(gone)))

        f.events.onStateSaved(blank, Bundle().apply { putString("k", "v") })
        f.events.onStateSaved(gone, Bundle().apply { putString("k", "v") })
        f.settle()

        assertFalse(f.stateDir.exists() && f.stateDir.list().orEmpty().isNotEmpty())
    }

    @Test
    fun detachingForgetsTheLivePages() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/")
        f.manager.find(id, "x")

        f.engines.detach(f.engine)

        val runtime = f.manager.runtime(id).value
        assertFalse(runtime.pageLoaded)
        assertNull(runtime.find)
        assertEquals("https://a.example/", runtime.committedUrl)
    }

    @Test
    fun anEngineThatWasReplacedCannotDetachItsSuccessor() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/")
        val replaced = FakeTabEngine(f.manager.events)

        f.engines.detach(replaced)
        f.manager.load(id, "https://b.example/")

        assertTrue(f.engines.isAttached(f.engine))
        assertTrue("the page is still live", f.manager.runtime(id).value.pageLoaded)
        assertEquals("commands still reach the attached engine", FakeTabEngine.Call.Load(id, "https://b.example/"), f.engine.calls.last())
    }

    @Test
    fun anEngineThatTakesOverFromALiveOneHasNoLoadedPageUntilItLoadsOne() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/")
        val newer = FakeTabEngine(f.manager.events)
        newer.onWebViewCreated = { tabId ->
            backgroundScope.launch(f.main) {
                delay(300)
                newer.openPage(tabId, "https://a.example/")
            }
        }

        f.engines.attach(newer)

        assertFalse("the page the older engine shows is none of the newer one's", f.manager.runtime(id).value.pageLoaded)
        assertTrue(f.manager.awaitPageReady(id))
        assertEquals("ready once the newer engine's own WebView loaded it", 300L, currentTime)
        f.engines.detach(f.engine)
        assertTrue("the older engine going away later changes nothing", f.manager.runtime(id).value.pageLoaded)
    }

    @Test
    fun aWebViewThatCannotRunThePageScriptsOffersNoPageAsReadableAndReadsNone() = runTest {
        val f = attached()
        f.scripts.supported = false
        val evaluated = mutableListOf<String>()
        f.engine.evaluator = { _, script ->
            evaluated += script
            "true"
        }
        val id = f.manager.newTab(incognito = false, url = "https://blog.example/")

        f.engine.openPage(id, "https://blog.example/post")
        f.engine.finishPage(id, "https://blog.example/post")
        runCurrent()

        assertFalse("not readable where Readability cannot run", f.manager.runtime(id).value.readerable)
        assertNull(f.manager.extractPage(id))
        assertEquals("no script ran, and nothing waited for a page", 0L, currentTime)
        assertTrue("no page script ran: $evaluated", evaluated.isEmpty())
    }

    @Test
    fun anEngineAttachedAgainKeepsItsPagesLoaded() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.openPage(id, "https://a.example/")

        f.engines.attach(f.engine)

        assertTrue("the same engine still holds the page it loaded", f.manager.runtime(id).value.pageLoaded)
    }

    // Readerable check

    @Test
    fun theReaderableCheckRunsOncePerDocument() = runTest {
        val f = attached()
        f.engine.evaluator = { _, script -> if (script == FakePageScripts.READERABLE) "true" else null }
        val id = f.manager.newTab(incognito = false, url = "https://blog.example/")

        f.engine.openPage(id, "https://blog.example/post")
        runCurrent()
        assertTrue(f.manager.runtime(id).value.readerable)
        f.engine.finishPage(id, "https://blog.example/post")
        f.engine.navigateInDocument(id, "https://blog.example/post#comments")
        runCurrent()
        assertTrue(f.manager.runtime(id).value.readerable)
        assertEquals(1, readerableChecks(f))

        f.engine.openPage(id, "https://blog.example/next")
        assertFalse("a new document starts without an answer", f.manager.runtime(id).value.readerable)
        runCurrent()
        assertTrue(f.manager.runtime(id).value.readerable)
        assertEquals(2, readerableChecks(f))

        f.engine.openPage(id, "about:blank")
        f.engine.failPage(id, "https://down.example/", WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        runCurrent()
        assertEquals("pages that are not web pages, and error pages, are not checked", 2, readerableChecks(f))
        assertFalse(f.manager.runtime(id).value.readerable)
    }

    // Page ready, extraction, highlight

    @Test
    fun aTabWithoutAWebViewGetsOneOffScreenAndIsReadyOnceItsPageLoads() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://blog.example/post")
        f.engine.onWebViewCreated = { tabId ->
            backgroundScope.launch(f.main) {
                delay(300)
                f.engine.openPage(tabId, "https://blog.example/post", "Post")
            }
        }

        assertTrue(f.manager.awaitPageReady(id))

        assertEquals(300L, currentTime)
        assertEquals(listOf(Call.EnsureWebView(id, offscreen = true)), f.engine.callsOf<Call.EnsureWebView>())
        assertTrue("a loaded page is ready at once", f.manager.awaitPageReady(id))
        assertEquals(300L, currentTime)
    }

    @Test
    fun aPageThatFailsOrNeverLoadsIsNotReady() = runTest {
        val f = attached()
        val failing = f.manager.newTab(incognito = false, url = "https://down.example/")
        val silent = f.manager.newTab(incognito = false, url = "https://slow.example/")
        val blank = f.manager.newTab(incognito = false)
        f.engine.onWebViewCreated = { tabId ->
            if (tabId == failing) f.engine.failPage(tabId, "https://down.example/", WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED")
        }

        assertFalse(f.manager.awaitPageReady(failing))
        assertFalse(f.manager.awaitPageReady(blank))
        assertFalse(f.manager.awaitPageReady("unknown"))
        val start = currentTime
        assertFalse(f.manager.awaitPageReady(silent))
        assertEquals(TabManager.PAGE_READY_TIMEOUT_MS, currentTime - start)
    }

    @Test
    fun aPageIsReadyOnlyOnceAnActivityAttaches() = runTest {
        val f = fixture()
        val id = f.manager.newTab(incognito = false, url = "https://a.example/")
        f.engine.onWebViewCreated = { tabId -> f.engine.openPage(tabId, "https://a.example/") }

        val ready = async { f.manager.awaitPageReady(id) }
        advanceTimeBy(1_000)
        assertFalse(ready.isCompleted)
        f.engines.attach(f.engine)

        assertTrue(ready.await())
    }

    @Test
    fun extractingReadsTheCommittedPageWithItsNonce() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://blog.example/post")
        val nonce = f.engine.openPage(id, "https://blog.example/post#top", "Post")
        f.engine.evaluator = { _, script ->
            if (script == FakePageScripts.EXTRACT) """{"title":"Post","readerable":true,"blocks":[{"id":"0","tag":"p","text":"Hello"}]}""" else null
        }

        val content = f.manager.extractPage(id) ?: throw AssertionError("nothing read")

        assertEquals("https://blog.example/post#top", content.url)
        assertEquals(nonce, content.loadNonce)
        assertEquals(listOf(PageBlock("0", "p", "Hello")), content.blocks)
        assertTrue(content.readerable)
    }

    @Test
    fun aPageThatNavigatesWhileBeingReadGivesNothing() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://blog.example/post")
        f.engine.openPage(id, "https://blog.example/post")
        f.engine.evaluator = { tabId, script ->
            if (script == FakePageScripts.EXTRACT) {
                f.engine.openPage(tabId, "https://evil.example/")
                """{"blocks":[{"id":"0","tag":"p","text":"Not the page you asked about"}]}"""
            } else {
                null
            }
        }

        assertNull(f.manager.extractPage(id))
    }

    @Test
    fun aPageThatReloadsItselfWhileBeingReadGivesNothing() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://blog.example/post")
        f.engine.openPage(id, "https://blog.example/post")
        f.engine.evaluator = { tabId, script ->
            if (script == FakePageScripts.EXTRACT) {
                f.engine.openPage(tabId, "https://blog.example/post")
                """{"blocks":[{"id":"0","tag":"p","text":"Text of the document that was replaced"}]}"""
            } else {
                null
            }
        }

        assertNull("the same URL is a new document", f.manager.extractPage(id))
    }

    @Test
    fun aHighlightRunsOnlyOnTheDocumentTheCitationCameFrom() = runTest {
        val f = attached()
        val id = f.manager.newTab(incognito = false, url = "https://blog.example/post")
        val nonce = f.engine.openPage(id, "https://blog.example/post", "Post")
        f.engine.evaluator = { _, script -> if (script.startsWith(FakePageScripts.HIGHLIGHT)) "true" else null }
        val request = HighlightRequest("https://blog.example/post", nonce, blockId = "3", citedText = "heat \"moves\" </script>")

        assertTrue(f.manager.highlightBlock(id, request))
        assertEquals(
            "${FakePageScripts.HIGHLIGHT}({\"id\":\"3\",\"cited\":\"heat \\\"moves\\\" \\u003c/script\\u003e\"," +
                "\"url\":\"https://blog.example/post\"})",
            highlights(f).single(),
        )

        f.engine.navigateInDocument(id, "https://blog.example/post#comments")
        assertTrue("a fragment change keeps the document", f.manager.highlightBlock(id, request))
        assertFalse("digits only", f.manager.highlightBlock(id, request.copy(blockId = "3\"]")))
        f.engine.navigateInDocument(id, "https://blog.example/other")
        assertFalse("another page of the same document", f.manager.highlightBlock(id, request))
        f.engine.openPage(id, "https://mail.example/")
        assertFalse("another document", f.manager.highlightBlock(id, request.copy(expectedUrl = "https://mail.example/")))
        assertEquals("nothing ran on a page that changed", 2, highlights(f).size)

        f.manager.close(listOf(id))
        assertFalse(f.manager.highlightBlock(id, request))
    }

    private fun TestScope.fixture() = TabManagerFixture(this, folder.root).also { fixtures += it }

    private fun TestScope.attached() = fixture().also { it.engines.attach(it.engine) }

    private val TabManagerFixture.events get() = manager.events

    private fun readerableChecks(f: TabManagerFixture) =
        f.engine.callsOf<Call.Evaluate>().count { it.script == FakePageScripts.READERABLE }

    private fun highlights(f: TabManagerFixture) =
        f.engine.callsOf<Call.Evaluate>().map { it.script }.filter { it.startsWith(FakePageScripts.HIGHLIGHT) }
}
