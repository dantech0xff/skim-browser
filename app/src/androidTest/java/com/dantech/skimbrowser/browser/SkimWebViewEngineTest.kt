package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.dantech.skimbrowser.browser.page.HighlightRequest
import com.dantech.skimbrowser.browser.page.PageContent
import com.dantech.skimbrowser.browser.page.PageScripts
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The browser engine on the device's own WebView (Chromium): the page scripts on real pages, find
 * in page, saved state, Desktop site, renderer loss, tabs that are not shown, and the order in which
 * the WebView reports a load.
 */
@RunWith(AndroidJUnit4::class)
class SkimWebViewEngineTest {

    @get:Rule
    val rig = BrowserRig()

    // Page scripts

    @Test
    fun theExtractorReadsOnlyTheVisibleTextAndKeepsEachBlocksIdAcrossReadings() {
        WebViewSupport.assumePageScriptsRun()
        val url = rig.pages.url("/article.html")
        val id = rig.newTab(url)

        val first = checkNotNull(rig.extract(id)) { "the article was not read" }
        val second = checkNotNull(rig.extract(id)) { "the article was not read again" }

        assertEquals(url, first.url)
        assertEquals("read from the document the tab shows", rig.runtime(id).loadNonce, first.loadNonce)
        assertEquals("Heat pumps in the cold", first.title)
        assertEquals("Ana Lima", first.byline)
        assertEquals("Daylight Journal", first.siteName)
        assertEquals("en-GB", first.lang)
        assertTrue(first.readerable)
        assertEquals(Fixtures.ARTICLE_BLOCKS, first.blocks.map { it.tag to it.text })
        assertTrue(first.blocks.size >= 5)
        assertNoHiddenOrInvisibleText(first)
        assertEquals("the same blocks keep their ids", first.blocks.map { it.id }, second.blocks.map { it.id })
        assertEquals("ids are unique", first.blocks.size, first.blocks.map { it.id }.toSet().size)
    }

    @Test
    fun textThatSitsDirectlyInDivsIsReadAsParagraphsAndHiddenPostsAreLeftOut() {
        WebViewSupport.assumePageScriptsRun()
        val id = rig.newTab(rig.pages.url("/divs.html"))

        val first = checkNotNull(rig.extract(id))
        val second = checkNotNull(rig.extract(id))

        assertTrue(first.readerable)
        assertEquals(Fixtures.DIVS_BLOCKS, first.blocks.map { it.tag to it.text })
        assertTrue("the comment is not part of the article", first.blocks.none { "Comment text" in it.text })
        assertNoHiddenOrInvisibleText(first)
        assertEquals(first.blocks.map { it.id }, second.blocks.map { it.id })
    }

    @Test
    fun looseTextDivsBetweenParagraphsKeepTheirPlaceInTheArticle() {
        WebViewSupport.assumePageScriptsRun()
        val id = rig.newTab(rig.pages.url("/mixed.html"))

        val first = checkNotNull(rig.extract(id))
        val second = checkNotNull(rig.extract(id))

        assertTrue(first.readerable)
        assertEquals(Fixtures.MIXED_BLOCKS, first.blocks.map { it.tag to it.text })
        assertEquals(first.blocks.map { it.id }, second.blocks.map { it.id })
    }

    @Test
    fun anArticleIsReaderableAndASettingsPageIsNot() {
        WebViewSupport.assumePageScriptsRun()
        val article = rig.pages.url("/article.html")
        val plain = rig.pages.url("/plain.html")
        val id = rig.newTab(article)
        rig.showCurrentTab()
        awaitValue(rig.tabs.runtime(id), "the article to be readerable") { it.readerable && it.committedUrl == article }

        onMain { rig.tabs.load(id, plain) }
        rig.awaitLoaded(id, plain)

        assertTrue("checked after the load and still no article", holdsFor(READERABLE_SETTLE_MS) { !rig.runtime(id).readerable })
        val content = checkNotNull(rig.extract(id))
        assertFalse(content.readerable)
        assertEquals("the whole page is read instead", Fixtures.PLAIN_BLOCKS, content.blocks.map { it.tag to it.text })
        assertNoHiddenOrInvisibleText(content)
    }

    @Test
    fun aWebViewTooOldForThePageScriptsOffersNoPageAsReadableAndReadsNone() {
        WebViewSupport.assumePageScriptsDoNotRun()
        val url = rig.pages.url("/article.html")
        val id = rig.newTab(url)
        rig.showCurrentTab()
        rig.awaitLoaded(id, url)

        assertTrue("an article is not offered as one", holdsFor(READERABLE_SETTLE_MS) { !rig.runtime(id).readerable })
        assertNull("and no page is read", rig.extract(id))
    }

    @Test
    fun theAppKnowsWhetherThisWebViewRunsThePageScripts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("the version check agrees with what the scripts do here", WebViewSupport.pageScriptsRun, PageScripts.runOnDevice(context))
    }

    @Test
    fun aHighlightRunsOnlyOnTheDocumentItsCitationWasReadFrom() {
        WebViewSupport.assumePageScriptsRun()
        val article = rig.pages.url("/article.html")
        val id = rig.newTab(article)
        val content = checkNotNull(rig.extract(id))
        val block = content.blocks[1]
        val citation = HighlightRequest(content.url, content.loadNonce, block.id, block.text)

        assertTrue(rig.highlight(id, citation))

        // The same address loaded again is another document: the citation is stale.
        onMain { rig.tabs.reload(id) }
        awaitValue(rig.tabs.runtime(id), "the reload to finish") { it.pageLoaded && it.loadNonce != content.loadNonce }
        assertFalse(rig.highlight(id, citation))
        val reloaded = checkNotNull(rig.extract(id))
        assertTrue(rig.highlight(id, citation.copy(expectedNonce = reloaded.loadNonce)))

        // Another page.
        val other = rig.pages.url("/long.html")
        onMain { rig.tabs.load(id, other) }
        rig.awaitLoaded(id, other)
        assertFalse(rig.highlight(id, citation.copy(expectedNonce = reloaded.loadNonce)))
        assertFalse(rig.highlight(id, citation.copy(expectedUrl = other, expectedNonce = rig.runtime(id).loadNonce)))
    }

    @Test
    fun aNavigationThatCommitsAfterTheTabManagersCheckStillStopsTheHighlight() {
        WebViewSupport.assumePageScriptsRun()
        val article = rig.pages.url("/article.html")
        val id = rig.newTab(article)
        val content = checkNotNull(rig.extract(id))
        val citation = HighlightRequest(content.url, content.loadNonce, content.blocks[1].id, null)
        val answer = CompletableDeferred<Boolean>()

        val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        onMain {
            // One main-thread task: the page moves to another address, then the highlight is asked for
            // while the tab manager has not heard of the move yet. Its check passes; the page's own
            // check of its address must refuse.
            main.launch(start = CoroutineStart.UNDISPATCHED) { rig.pool.evaluate(id, "history.pushState(null, '', '/elsewhere.html')") }
            main.launch(start = CoroutineStart.UNDISPATCHED) { answer.complete(rig.tabs.highlightBlock(id, citation)) }
            assertEquals("the tab manager still sees the article", article, rig.runtime(id).committedUrl)
        }

        try {
            assertFalse(runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { answer.await() } })
        } finally {
            main.cancel()
        }
        awaitValue(rig.tabs.runtime(id), "the move to reach the tab manager") { it.committedUrl == rig.pages.url("/elsewhere.html") }
        assertEquals("a same-document move keeps the document", content.loadNonce, rig.runtime(id).loadNonce)
        assertFalse(rig.highlight(id, citation))
    }

    // Find in page

    @Test
    fun findCountsTheMatchesOfThePageAndStepsThroughThem() {
        val url = rig.pages.url("/long.html")
        val id = rig.newTab(url)
        rig.showCurrentTab()
        rig.awaitLoaded(id, url)

        onMain { rig.tabs.find(id, "lighthouse") }
        val found = awaitFind(id, "every match to be counted") { it?.matchCount == Fixtures.LIGHTHOUSE_MATCHES }
        assertEquals("lighthouse", found?.query)
        assertEquals(0, found?.activeMatchIndex)

        onMain { rig.tabs.findNext(id, forward = true) }
        awaitFind(id, "the second match") { it?.activeMatchIndex == 1 }
        onMain { rig.tabs.findNext(id, forward = false) }
        awaitFind(id, "back to the first match") { it?.activeMatchIndex == 0 }
        onMain { rig.tabs.findNext(id, forward = false) }
        awaitFind(id, "around to the last match") { it?.activeMatchIndex == Fixtures.LIGHTHOUSE_MATCHES - 1 }

        onMain { rig.tabs.find(id, "zeppelin") }
        // "0/0" from the start; no count may come in for a word the page does not have.
        assertTrue(holdsFor(FIND_SETTLE_MS) { rig.runtime(id).find == FindState("zeppelin", activeMatchIndex = 0, matchCount = 0) })
        onMain { rig.tabs.clearFind(id) }
        assertNull(rig.runtime(id).find)
    }

    @Test
    fun aStepAskedForWhileTheMatchesAreStillCountedStillMovesToTheNextMatch() {
        val url = rig.pages.url("/long.html")
        val id = rig.newTab(url)
        rig.showCurrentTab()
        rig.awaitLoaded(id, url)

        // The step leaves before the WebView reported anything about the search.
        onMain {
            rig.tabs.find(id, "lighthouse")
            rig.tabs.findNext(id, forward = true)
        }

        awaitFind(id, "the second match") { it?.activeMatchIndex == 1 && it.matchCount == Fixtures.LIGHTHOUSE_MATCHES }
        assertTrue("and it stays there", holdsFor(FIND_SETTLE_MS) { rig.runtime(id).find?.activeMatchIndex == 1 })
    }

    // Saved state

    @Test
    fun aTabPushedOutOfMemoryComesBackWithItsBackHistoryAndScrollPositions() {
        val shown = rig.newTab(rig.pages.url("/plain.html"))
        rig.showCurrentTab()
        rig.awaitLoaded(shown)
        val first = rig.pages.url("/long.html?page=1")
        val second = rig.pages.url("/long.html?page=2")
        val id = rig.newTab(first, background = true)
        assertTrue(runBlocking { rig.tabs.awaitPageReady(id) })
        rig.scrollAndKeep(id, FIRST_SCROLL_Y)
        onMain { rig.tabs.load(id, second) }
        rig.awaitLoaded(id, second)
        rig.scrollAndKeep(id, SECOND_SCROLL_Y)
        val generation = onMain { rig.pool.generation(id) }
        val before = generation.value

        @Suppress("DEPRECATION") // Still sent before API 34, and the level the pool evicts at.
        onMain { rig.pool.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) }

        assertNotEquals("its WebView was destroyed", before, generation.value)
        assertFalse(rig.runtime(id).pageLoaded)
        assertNotNull("its state is on disk", runBlocking { rig.container.tabStateStore.load(id, incognito = false) })
        onMain { rig.pool.onStart() }

        assertTrue(runBlocking { rig.tabs.awaitPageReady(id) })
        assertEquals(second, rig.runtime(id).committedUrl)
        assertTrue(rig.runtime(id).canGoBack)
        assertEquals("two entries in its history", 2, jsValue(rig.evaluate(id, "history.length")))
        waitUntil("the scroll position to come back") { rig.isScrolledTo(id, SECOND_SCROLL_Y) }

        assertTrue(onMain { rig.tabs.goBack(id) })
        rig.awaitLoaded(id, first)
        waitUntil("the earlier page's scroll position to come back") { rig.isScrolledTo(id, FIRST_SCROLL_Y) }
    }

    // Desktop site

    @Test
    fun desktopSiteChangesTheUserAgentAndLayoutWidthAndTurningItOffGivesTheDefaultsBack() {
        val id = showUaPage()
        val defaultUa = onMain { WebSettings.getDefaultUserAgent(rig.activity) }

        assertSeen(id, ua = defaultUa, wide = false)
        assertTrue(defaultUa.contains("Mobile") && defaultUa.contains("; wv"))

        reloadWith(id) { rig.tabs.setDesktopSite(id, true) }
        val desktopUa = desktopUserAgent(defaultUa)
        assertTrue(desktopUa.contains("(X11; Linux x86_64)") && !desktopUa.contains("Mobile") && !desktopUa.contains("; wv"))
        assertSeen(id, ua = desktopUa, wide = true)
        assertTrue(rig.tab(id)!!.desktopSite)

        reloadWith(id) { rig.tabs.setDesktopSite(id, false) }
        assertSeen(id, ua = defaultUa, wide = false)
    }

    @Test
    fun desktopSiteChangesTheClientHintsAndTurningItOffGivesTheWebViewsOwnBack() {
        WebViewSupport.assumeFeature(WebViewFeature.USER_AGENT_METADATA, "Skim needs to set the client hints sites see")
        val id = showUaPage()
        val view = rig.webViewOf(id)
        val defaultHints = onMain { WebSettingsCompat.getUserAgentMetadata(view.settings) }

        assertHints(id, mobile = true, platform = "Android")
        reloadWith(id) { rig.tabs.setDesktopSite(id, true) }
        assertHints(id, mobile = false, platform = "Linux")
        reloadWith(id) { rig.tabs.setDesktopSite(id, false) }
        assertHints(id, mobile = true, platform = "Android")
        assertEquals("the WebView's own hints are back", defaultHints, onMain { WebSettingsCompat.getUserAgentMetadata(view.settings) })
    }

    // Renderer loss

    @Test
    fun aKilledRendererComesBackTwiceThenTheTabShowsItsErrorUntilTryAgain() {
        WebViewSupport.assumeFeature(WebViewFeature.GET_WEB_VIEW_RENDERER, "the test needs to reach the page's renderer")
        WebViewSupport.assumeFeature(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE, "the test needs to end the renderer")
        val url = rig.pages.url("/article.html")
        val id = rig.newTab(url)
        rig.showCurrentTab()
        rig.awaitLoaded(id, url)
        val generation = onMain { rig.pool.generation(id) }

        repeat(2) { round ->
            val killed = rig.shownWebView()
            val before = generation.value
            assertTrue("round $round", terminateRenderer(killed))
            awaitValue(generation, "a new WebView, round $round") { it != null && it != before }
            waitUntil("the host to show the new WebView, round $round") { rig.webViewsInWindow().singleOrNull()?.let { it !== killed } == true }
            assertFalse(onMain { killed.isAttachedToWindow })
            rig.awaitLoaded(id, url)
            assertNull(rig.runtime(id).loadError)
        }

        assertTrue(terminateRenderer(rig.shownWebView()))
        awaitValue(generation, "the tab to give up on its WebView") { it == null }
        val error = rig.runtime(id).loadError
        assertTrue("$error", error is LoadError.Crashed)
        assertEquals(url, error?.url)
        assertEquals("ended by the app, not crashed", "RENDERER_KILLED", error?.codeName)
        waitUntil("the host to show nothing") { rig.webViewsInWindow().isEmpty() }

        onMain { rig.tabs.reload(id) }
        awaitValue(generation, "Try again to bring a WebView back") { it != null }
        waitUntil("the host to show it") { rig.webViewsInWindow().size == 1 }
        rig.awaitLoaded(id, url)
        assertNull(rig.runtime(id).loadError)
    }

    // Tabs that are not shown

    @Test
    fun aTabThatIsNotShownLoadsAndIsReadOffScreen() {
        val shown = rig.newTab(rig.pages.url("/plain.html"))
        rig.showCurrentTab()
        rig.awaitLoaded(shown)
        val hidden = rig.newTab(rig.pages.url("/article.html"), background = true)

        assertTrue(runBlocking { rig.tabs.awaitPageReady(hidden) })
        val view = rig.webViewOf(hidden)
        onMain {
            val frame = view.parent as FrameLayout
            assertSame(rig.activity.findViewById<ViewGroup>(android.R.id.content), frame.parent)
            assertTrue(view.isAttachedToWindow)
            assertTrue("beside the screen", frame.translationX >= rig.activity.resources.displayMetrics.widthPixels)
            assertFalse("no part of it on screen", view.getGlobalVisibleRect(Rect()))
        }

        // Loading off screen works on every WebView; reading the page needs the page scripts.
        WebViewSupport.assumePageScriptsRun()
        val content = checkNotNull(rig.extract(hidden))
        assertEquals(shown, rig.tabs.state.value.currentTabId)
        assertEquals(Fixtures.ARTICLE_BLOCKS, content.blocks.map { it.tag to it.text })
    }

    // The order of a load's reports

    @Test
    fun aWebViewReportsALoadInTheOrderTheTabManagerReadsItIn() {
        val article = rig.pages.url("/article.html")
        val long = rig.pages.url("/long.html")
        val moved = rig.pages.url("/long.html?section=2")
        val fragment = "$moved#entry-3"
        val unreachable = "http://127.0.0.1:1/"
        val raw = recordRawCallbacks(article, long, moved, unreachable)

        // Chromium reports each new document as started, then visited, then finished.
        listOf(article, long).forEach { url ->
            val started = raw.indexOf("started $url")
            val visited = raw.indexOf("visited $url")
            val finished = raw.indexOf("finished $url")
            assertTrue("$url: $raw", started in 0 until visited && visited < finished)
        }
        assertFalse("a same-document move starts no page: $raw", raw.contains("started $moved"))
        assertTrue(raw.contains("visited $moved"))

        // The same loads through the tab manager.
        val id = rig.newTab(article)
        rig.showCurrentTab()
        rig.awaitLoaded(id, article)
        rig.evaluate(id, "location.href = '/long.html'")
        rig.awaitLoaded(id, long)
        rig.evaluate(id, "history.pushState(null, '', '/long.html?section=2')")
        awaitValue(rig.tabs.runtime(id), "the pushed address") { it.committedUrl == moved }
        rig.evaluate(id, "location.hash = 'entry-3'")
        awaitValue(rig.tabs.runtime(id), "the fragment") { it.committedUrl == fragment }
        val nonceOfLong = rig.runtime(id).loadNonce
        rig.evaluate(id, "location.href = '$unreachable'")
        val failed = awaitValue(rig.tabs.runtime(id), "the failed load") { it.loadError != null && it.pageLoaded }
        // The events reach the test's collector on another thread, after the runtime changed.
        waitUntil("the failed load's events") {
            rig.pageEvents.any { it is PageEvent.PageFinished && it.url == unreachable } &&
                rig.pageEvents.any { it is PageEvent.Navigated && it.url == unreachable }
        }

        val events = rig.pageEvents.filter { it.tabId == id }
        val starts = events.filterIsInstance<PageEvent.PageStarted>()
        val finishes = events.filterIsInstance<PageEvent.PageFinished>()
        val visits = events.filterIsInstance<PageEvent.Navigated>()
        assertEquals("$events", listOf(article, long, unreachable), starts.map { it.url })
        assertEquals("$events", listOf(article to false, long to false, moved to true, fragment to true), visits.take(4).map { it.url to it.isSameDocument })
        starts.forEach { start ->
            val finish = finishes.firstOrNull { it.url == start.url }
            assertNotNull("${start.url} finished: $events", finish)
            assertEquals("one nonce from start to finish of ${start.url}", start.loadNonce, finish?.loadNonce)
            assertTrue("${start.url}: started before it finished", events.indexOf(start) < events.indexOf(finish))
        }
        assertEquals("same-document moves keep the nonce", starts[1].loadNonce, nonceOfLong)
        assertTrue("$failed", failed.loadError is LoadError.Unreachable)
        assertEquals(unreachable, failed.loadError?.url)
        assertTrue("the error stays once the error page is in: $events", holdsFor(ERROR_SETTLE_MS) { rig.runtime(id).loadError is LoadError.Unreachable })

        runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { rig.tabs.awaitWrites() } }
        val history = runBlocking { rig.container.historyRepository.observe(null).first() }.map { it.url }.toSet()
        assertTrue("$history", history.containsAll(listOf(article, long, moved)))
        assertFalse("a failed load is no visit: $history", unreachable in history)
    }

    private fun assertNoHiddenOrInvisibleText(content: PageContent) {
        content.blocks.forEach { block ->
            assertFalse("hidden text read: ${block.text}", "HIDDEN" in block.text || "IGNORE" in block.text)
            assertTrue("invisible characters left in: ${block.text}", block.text.none { it in Fixtures.INVISIBLE })
        }
    }

    /** Waits until [tabId]'s find state matches [predicate]; a timeout says what the state was then. */
    private fun awaitFind(tabId: String, what: String, predicate: (FindState?) -> Boolean): FindState? = try {
        awaitValue(rig.tabs.runtime(tabId), what) { predicate(it.find) }.find
    } catch (e: AssertionError) {
        throw AssertionError("$what: find is ${rig.runtime(tabId).find}", e)
    }

    /** Runs [change] on the main thread and waits for the reload it causes to finish. */
    private fun reloadWith(tabId: String, change: () -> Unit) {
        val before = rig.runtime(tabId).loadNonce
        onMain(change)
        awaitValue(rig.tabs.runtime(tabId), "the reload to finish") { it.pageLoaded && it.loadNonce != before }
    }

    /** Opens the page that shows what the browser tells sites about itself in a new tab on screen and returns the tab. */
    private fun showUaPage(): String {
        val url = rig.pages.url("/ua.html")
        val id = rig.newTab(url)
        rig.showCurrentTab()
        rig.awaitLoaded(id, url)
        return id
    }

    /** Checks the user agent and layout width that the page and the server saw on the tab's last load. */
    private fun assertSeen(tabId: String, ua: String, wide: Boolean) {
        val seen = JSONObject(checkNotNull(rig.evaluate(tabId, UA_PROBE)))
        assertEquals(ua, seen.getString("ua"))
        val width = seen.getInt("width")
        if (wide) assertEquals("laid out on a desktop-wide viewport", DESKTOP_VIEWPORT_WIDTH, width) else assertTrue("device wide: $width", width < DESKTOP_VIEWPORT_WIDTH / 2)
        assertEquals(ua, rig.pages.requests("/ua.html").last().getHeader("User-Agent"))
    }

    /** Checks the client hints that the page and the server saw on the tab's last load. */
    private fun assertHints(tabId: String, mobile: Boolean, platform: String) {
        val seen = JSONObject(checkNotNull(rig.evaluate(tabId, UA_PROBE)))
        assertEquals(mobile, seen.getBoolean("mobile"))
        assertEquals(platform, seen.getString("platform"))
        val request = rig.pages.requests("/ua.html").last()
        assertEquals(if (mobile) "?1" else "?0", request.getHeader("Sec-CH-UA-Mobile"))
        assertEquals("\"$platform\"", request.getHeader("Sec-CH-UA-Platform"))
    }

    private fun terminateRenderer(view: WebView): Boolean = onMain {
        checkNotNull(WebViewCompat.getWebViewRenderProcess(view)) { "WebView runs in a single process" }.terminate()
    }

    /**
     * Loads [first], follows a link to [second], moves to [moved] with `history.pushState` and then
     * to an [unreachable] address in a plain WebView whose clients record what they hear, and returns
     * the record. The WebView is destroyed afterwards.
     */
    // Only the fixtures' scripts and the navigations below run in this WebView.
    @SuppressLint("SetJavaScriptEnabled")
    private fun recordRawCallbacks(first: String, second: String, moved: String, unreachable: String): List<String> {
        val record = CopyOnWriteArrayList<String>()
        val view = onMain {
            WebView(rig.activity).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        record += "started $url"
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        record += "visited $url"
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        record += "finished $url"
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) record += "error ${request.url}"
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        if (newProgress == FULL_PROGRESS) record += "progress 100"
                    }
                }
                rig.activity.addContentView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                loadUrl(first)
            }
        }
        try {
            waitUntil("$first to finish") { "finished $first" in record }
            onMain { view.evaluateJavascript("location.href = '$second'", null) }
            waitUntil("$second to finish") { "finished $second" in record }
            onMain { view.evaluateJavascript("history.pushState(null, '', '$moved')", null) }
            waitUntil("the pushed address") { "visited $moved" in record }
            onMain { view.evaluateJavascript("location.href = '$unreachable'", null) }
            waitUntil("the failed load to end") { "error $unreachable" in record && "finished $unreachable" in record }
            // What follows the end of a failed load (the error page's own reports) is part of the record.
            var size = -1
            while (size != record.size) {
                size = record.size
                holdsFor(QUIET_MS) { record.size == size }
            }
            Log.i(TAG, "Callbacks: $record")
            return record.toList()
        } finally {
            onMain {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
        }
    }

    private companion object {
        const val TAG = "SkimEngineTest"
        const val FIRST_SCROLL_Y = 1600
        const val SECOND_SCROLL_Y = 2400
        const val READERABLE_SETTLE_MS = 1_500L
        const val ERROR_SETTLE_MS = 1_000L
        const val QUIET_MS = 500L
        const val FIND_SETTLE_MS = 1_000L
        const val FULL_PROGRESS = 100

        /**
         * The layout width of a page without a viewport tag on a wide viewport, as
         * `document.documentElement.clientWidth` reads it. The visual viewport's width
         * (`innerWidth`) is that width zoomed out to fit the screen, which an older WebView rounds
         * up (981 on Chromium 66).
         */
        const val DESKTOP_VIEWPORT_WIDTH = 980

        const val UA_PROBE = "(function () { var data = navigator.userAgentData; return { ua: navigator.userAgent, " +
            "mobile: data ? data.mobile : null, platform: data ? data.platform : null, width: document.documentElement.clientWidth }; })()"
    }
}
