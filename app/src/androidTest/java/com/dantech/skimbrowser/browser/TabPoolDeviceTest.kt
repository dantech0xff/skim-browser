package com.dantech.skimbrowser.browser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The WebView pool with many real tabs: ten tabs opened one after another keep at most three
 * WebViews alive, a tab whose WebView went away comes back on its page and at its scroll position,
 * and a saved state file cut in half is dropped and the tab loads its page afresh.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TabPoolDeviceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rig = BrowserRig()

    @Test
    fun tenTabsKeepAtMostThreeWebViewsAliveAndTheFirstComesBackOnItsPageAndPosition() {
        val since = System.currentTimeMillis()
        rig.showCurrentTab()
        val first = rig.pages.url("/long.html?tab=1")
        val firstId = rig.newTab(first)
        rig.awaitLoaded(firstId, first)
        rig.scrollAndKeep(firstId, SCROLL_Y)
        val ids = mutableListOf(firstId)

        for (n in 2..TABS) {
            val url = rig.pages.url("/long.html?tab=$n")
            ids += rig.newTab(url)
            rig.awaitLoaded(ids.last(), url)
            val live = ids.count { rig.runtime(it).pageLoaded }
            assertTrue("$live tabs with a live page after $n tabs", live in 1..MAX_LIVE)
        }

        assertFalse("the first tab's WebView went away", rig.runtime(firstId).pageLoaded)
        val logged = loggedLiveCounts(since)
        assertTrue("the pool evicted and said so: $logged", logged.isNotEmpty())
        assertTrue("never more than $MAX_LIVE live: $logged", logged.all { it <= MAX_LIVE })

        onMain { rig.tabs.select(firstId) }
        rig.awaitLoaded(firstId, first)
        assertEquals(first, rig.tab(firstId)?.url)
        waitUntil("the first tab's scroll position to come back") { rig.isScrolledTo(firstId, SCROLL_Y) }
        assertTrue(ids.count { rig.runtime(it).pageLoaded } <= MAX_LIVE)
    }

    @Test
    fun aSavedStateFileCutInHalfIsDroppedAndTheTabLoadsItsPageAfresh() {
        rig.showCurrentTab()
        val first = rig.pages.url("/long.html?tab=1")
        val second = rig.pages.url("/long.html?tab=1&page=2")
        val id = rig.newTab(first)
        rig.awaitLoaded(id, first)
        rig.loadIn(id, second)
        rig.scrollAndKeep(id, SCROLL_Y)
        pushOutOfThePool(id)
        val file = File(rig.storageDir, "tabstate/$id.bin")
        assertTrue("its state was saved to its file", file.length() > 0)

        val whole = file.readBytes()
        file.writeBytes(whole.copyOf(whole.size / 2))
        onMain { rig.tabs.select(id) }

        rig.awaitLoaded(id, second)
        assertEquals("loaded afresh, without the history the file held", 1, jsValue(rig.evaluate(id, "history.length")))
        assertFalse(rig.runtime(id).canGoBack)
        assertTrue("at the top of the page", rig.isScrolledTo(id, 0))
        waitUntil("a new state to be saved for it") { runBlocking { rig.graph.tabStateStore().load(id, incognito = false) } != null }
    }

    /** Opens new tabs until [tabId]'s WebView is pushed out of the pool, then waits until its state is on disk. */
    private fun pushOutOfThePool(tabId: String) {
        var n = 0
        while (rig.runtime(tabId).pageLoaded) {
            check(n++ < TABS) { "The tab's WebView stayed alive behind $TABS tabs" }
            val url = rig.pages.url("/plain.html?filler=$n")
            rig.awaitLoaded(rig.newTab(url), url)
        }
        runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { rig.graph.tabStateStore().awaitIdle() } }
    }

    /**
     * The live WebView counts the pool logged under its tag (`Evicted n, m live, ...`, `Memory warning
     * ..., m live`) by this process since [sinceMs], on the device's clock.
     */
    private fun loggedLiveCounts(sinceMs: Long): List<Int> {
        val pid = Process.myPid()
        return shell("logcat -d -v epoch -s $POOL_TAG:I").lines().mapNotNull { line ->
            val match = POOL_LINE.find(line) ?: return@mapNotNull null
            val (seconds, millis, linePid, live) = match.destructured
            val at = seconds.toLong() * 1_000 + millis.toLong()
            live.toInt().takeIf { linePid.toInt() == pid && at >= sinceMs }
        }
    }

    private companion object {
        const val TABS = 10
        const val MAX_LIVE = 3
        const val SCROLL_Y = 2400
        const val POOL_TAG = "SkimWebViewPool"

        /** A line of `logcat -v epoch`: seconds.millis, pid, tid, level, tag, then the pool's message. */
        val POOL_LINE = Regex("""^\s*(\d+)\.(\d{3})\s+(\d+)\s+\d+\s+I\s+$POOL_TAG\s*:.*?(\d+) live""")
    }
}
