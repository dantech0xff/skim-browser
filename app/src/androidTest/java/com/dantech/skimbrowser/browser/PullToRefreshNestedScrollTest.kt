package com.dantech.skimbrowser.browser

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.ui.WebViewHost
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pull to refresh over a tab's WebView, as the browser screen will have it: the WebView in
 * Compose's `PullToRefreshBox`, real touches on the screen. A pull at the top of a page refreshes,
 * with the drag and its release reaching the box the right way up; a page scrolled down scrolls
 * instead, and a page that keeps the drag for itself (a map) is never pulled.
 */
@RunWith(AndroidJUnit4::class)
class PullToRefreshNestedScrollTest {

    @get:Rule
    val rig = BrowserRig()

    private val refreshes = AtomicInteger()
    private val seen = NestedScrollRecorder()

    @Test
    fun pullingDownAtTheTopOfAPageRefreshesIt() {
        val (id, view) = showInPullToRefresh("/long.html")

        Finger.drag(view, fromY = PULL_FROM, toY = PULL_TO)

        waitUntil("the refresh") { refreshes.get() == 1 }
        assertTrue("the drag reached the box: $seen", seen.preScrolls.isNotEmpty() && seen.postScrolls.isNotEmpty())
        assertTrue("a pull down is a positive delta: $seen", seen.preScrolls.all { it > 0f } && seen.postScrolls.all { it > 0f })
        assertTrue("released moving down: $seen", seen.preFlings.single() > 0f)
        assertEquals("the page itself did not move", 0, scrollY(id))
        val touch = JSONObject(checkNotNull(rig.evaluate(id, "window.skimTouch")))
        assertTrue("the page saw its touch cancelled: $touch", touch.getInt("cancel") >= 1 && touch.getInt("end") == 0)
    }

    @Test
    fun pullingDownOnAPageScrolledAwayFromTheTopScrollsItInstead() {
        val (id, view) = showInPullToRefresh("/long.html")
        rig.evaluate(id, "window.scrollTo(0, $SCROLLED_Y)")
        waitUntil("the page to scroll down") { scrollY(id) == SCROLLED_Y }

        Finger.drag(view, fromY = PULL_FROM, toY = PULL_TO)

        waitUntil("the page to scroll back up a little") { scrollY(id) in 1 until SCROLLED_Y }
        assertTrue("no refresh", holdsFor(SETTLE_MS) { refreshes.get() == 0 })
        assertTrue("nothing was handed to the box: $seen", seen.preScrolls.isEmpty() && seen.postScrolls.isEmpty())
    }

    @Test
    fun aPageThatKeepsTheDragForItselfIsNeverPulledThoughTheSamePageCanBe() {
        val (id, view) = showInPullToRefresh("/map.html")

        dragTheMapWhereItCanKeepTheDrag(id, view)

        assertTrue("no refresh", holdsFor(SETTLE_MS) { refreshes.get() == 0 })

        val free = rig.pages.url("/map.html?free")
        onMain { rig.tabs.load(id, free) }
        rig.awaitLoaded(id, free)
        awaitTouchesReachThePage(id, view)
        Finger.drag(view, fromY = PULL_FROM, toY = PULL_TO)
        waitUntil("the same page without its own drag to be pulled") { refreshes.get() == 1 }
    }

    private fun showInPullToRefresh(path: String): Pair<String, SkimWebView> {
        val url = rig.pages.url(path)
        val id = rig.newTab(url)
        rig.show {
            // The recorder is the outer parent: Compose offers a pre-scroll and a pre-fling to the
            // outermost parent first, so it sees them whole, before the box takes its part.
            Box(Modifier.fillMaxSize().nestedScroll(seen)) {
                PullToRefreshBox(isRefreshing = false, onRefresh = { refreshes.incrementAndGet() }, modifier = Modifier.fillMaxSize()) {
                    WebViewHost(id, Modifier.fillMaxSize())
                }
            }
        }
        rig.awaitLoaded(id, url)
        waitUntil("the WebView on screen") { rig.webViewsInWindow().size == 1 }
        val view = rig.webViewsInWindow().single()
        awaitTouchesReachThePage(id, view)
        return id to view
    }

    /**
     * Taps the page until it gets the tap's touchstart, then zeroes its touch counters. Chromium hands
     * a page its touches only once the renderer told it where the page listens, a moment after load;
     * a drag before that never reaches the page's listeners at all.
     */
    private fun awaitTouchesReachThePage(tabId: String, view: SkimWebView) {
        waitUntil("the page to get touches") {
            Finger.tap(view, fractionY = TAP_AT)
            holdsFor(TAP_WAIT_MS) { touchStarts(tabId) == 0 }.not()
        }
        rig.evaluate(tabId, RESET_TOUCHES)
    }

    /**
     * Drags down over the map until the page was handed the whole drag as touch events it could
     * cancel, which the map answers with preventDefault. Chromium sends a touch sequence as one the
     * page cannot cancel when the page was slow to answer the one before (a busy device): no page
     * can keep such a drag, the pull is right to start, and the drag shows nothing about a page that
     * keeps its drag. That drag is forgotten and repeated.
     */
    private fun dragTheMapWhereItCanKeepTheDrag(tabId: String, view: SkimWebView) {
        repeat(MAX_DRAGS) { attempt ->
            rig.evaluate(tabId, RESET_TOUCHES)
            Finger.drag(view, fromY = PULL_FROM, toY = PULL_TO)
            waitUntil("the map to see the end of the drag") { mapCount(tabId, "mapEnds") > 0 }
            if (mapCount(tabId, "mapMoves") > 0 && mapCount(tabId, "mapUncancelable") == 0) return
            Log.i(TAG, "The map was handed a drag it could not cancel (try ${attempt + 1} of $MAX_DRAGS); dragging again")
            // A pull that started has ended with the release; forget the refresh it asked for.
            awaitNextFrame()
            awaitNextFrame()
            refreshes.set(0)
        }
        throw AssertionError("The map was never handed a drag it could cancel in $MAX_DRAGS tries")
    }

    private fun mapCount(tabId: String, name: String): Int = (jsValue(rig.evaluate(tabId, "window.$name")) as Number).toInt()

    private fun touchStarts(tabId: String): Int = (jsValue(rig.evaluate(tabId, TOUCH_STARTS)) as Number).toInt()

    private fun scrollY(tabId: String): Int? = (jsValue(rig.evaluate(tabId, "Math.round(window.scrollY)")) as? Number)?.toInt()

    /** What a child's nested scroll hands its parents, recorded on the way through without consuming any. */
    private class NestedScrollRecorder : NestedScrollConnection {
        val preScrolls = CopyOnWriteArrayList<Float>()
        val postScrolls = CopyOnWriteArrayList<Float>()
        val preFlings = CopyOnWriteArrayList<Float>()

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) preScrolls += available.y
            return Offset.Zero
        }

        // A parent's post-scroll comes after its children's: what they consumed is part of the drag.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) postScrolls += consumed.y + available.y
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            preFlings += available.y
            return Velocity.Zero
        }

        override fun toString() = "pre=$preScrolls post=$postScrolls flings=$preFlings"
    }

    private companion object {
        const val TAG = "SkimPullTest"

        /** A drag down over half the page, from 25% to 85% of its height. */
        const val PULL_FROM = 0.25f
        const val PULL_TO = 0.85f
        const val SCROLLED_Y = 3000
        const val SETTLE_MS = 1_500L
        const val TAP_AT = 0.1f
        const val TAP_WAIT_MS = 1_000L

        /** How many times the map is dragged before it is clear that it was never handed a drag it could cancel. */
        const val MAX_DRAGS = 5

        /** The touch starts the page counted: long.html counts in skimTouch, map.html in mapStarts. */
        const val TOUCH_STARTS = "window.skimTouch ? window.skimTouch.start : window.mapStarts"
        const val RESET_TOUCHES = "if (window.skimTouch) { window.skimTouch = { start: 0, move: 0, end: 0, cancel: 0 }; } " +
            "else { window.mapStarts = 0; window.mapMoves = 0; window.mapEnds = 0; window.mapUncancelable = 0; }"
    }
}
