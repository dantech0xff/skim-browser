package com.dantech.skimbrowser.browser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A page's own dialogs on the device's WebView. A tab in the background has its `alert`, `confirm`
 * and `prompt` answered at once, as cancelled, with no dialog on screen, and leaves its page when
 * told to even if the page asks to stay. The tab on screen shows the WebView's dialogs, three per
 * document; the rest of a page's dialogs are answered without showing, so a loop of alerts cannot
 * lock the app. A dialog on screen takes the window focus from the app's window, which is how the
 * test sees one; it answers a dialog with Back.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class JsDialogDeviceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rig = BrowserRig()

    @Test
    fun aTabInTheBackgroundGetsItsDialogsAnsweredAtOnceAndNoneShows() {
        rig.showCurrentTab()
        val shown = rig.newTab(rig.pages.url("/plain.html"))
        rig.awaitLoaded(shown)
        val background = rig.newTab(rig.pages.url("/dialogs.html"), background = true)
        assertTrue(runBlocking { rig.tabs.awaitPageReady(background) })
        assertTrue("the app's window has the focus", appHasFocus())

        val asked = SystemClock.elapsedRealtime()
        rig.evaluate(background, "skimAskAll()")
        val tookMs = SystemClock.elapsedRealtime() - asked

        assertEquals("alert, confirm (cancelled), prompt (null)", "[\"undefined\",false,null]", jsValue(rig.evaluate(background, "window.skimAnswers")))
        assertTrue("answered at once, not by anyone: $tookMs ms", tookMs < HOLD_MS)
        assertTrue("no dialog took the screen", holdsFor(SETTLE_MS) { appHasFocus() })
        val ticks = ticks(background)
        waitUntil("the page to run on") { ticks(background) > ticks }
        assertEquals(shown, rig.tabs.state.value.currentTabId)
    }

    @Test
    fun aTabInTheBackgroundThatAsksToStayIsLeftAnyway() {
        rig.showCurrentTab()
        val page = rig.pages.url("/dialogs.html")
        val id = rig.newTab(page)
        rig.awaitLoaded(id, page)
        // Chromium only asks before leaving a page the user interacted with.
        val view = rig.shownWebView()
        waitUntil("the page to hear a tap") {
            Finger.tap(view)
            holdsFor(TAP_WAIT_MS) { tapped(id) == 0 }.not()
        }
        rig.evaluate(id, "skimHold()")

        // On screen, the WebView asks first; Back answers "stay".
        onMain { rig.tabs.load(id, rig.pages.url("/plain.html?left")) }
        waitUntil("the WebView to ask before the page is left") { !appHasFocus() }
        assertTrue(holdsFor(HOLD_MS) { !appHasFocus() })
        pressBack()
        waitUntil("the dialog to go") { appHasFocus() }
        assertTrue("the page stayed", holdsFor(SETTLE_MS) { rig.runtime(id).committedUrl == page })

        // In the background nobody is asked, and the page is left.
        val other = rig.newTab(rig.pages.url("/plain.html"))
        rig.awaitLoaded(other)
        val away = rig.pages.url("/plain.html?away")
        onMain { rig.tabs.load(id, away) }
        rig.awaitLoaded(id, away)
        assertTrue("no dialog took the screen", holdsFor(SETTLE_MS) { appHasFocus() })
    }

    @Test
    fun theTabOnScreenShowsThreeDialogsOfALoopAndAnswersTheRestItself() {
        rig.showCurrentTab()
        val page = rig.pages.url("/dialogs.html")
        val id = rig.newTab(page)
        rig.awaitLoaded(id, page)

        rig.evaluate(id, "setTimeout(function () { skimAlerts($ALERTS); }, 0)")

        val answered = answerDialogsUntilDone(id)
        assertEquals("three dialogs showed, the first three", listOf(1, 2, 3), answered)
        val times = JSONArray(jsValue(rig.evaluate(id, "JSON.stringify(window.skimAlertTimes)")) as String)
        val took = (0 until times.length()).map { times.getLong(it) }
        assertEquals(ALERTS, took.size)
        assertTrue("each shown dialog waited for its answer: $took", took.take(3).all { it >= HOLD_MS })
        assertTrue("the rest returned at once: $took", took.drop(3).all { it < HOLD_MS })
        waitUntil("the app's window to have the focus back") { appHasFocus() }
        assertTrue("and no dialog came after", holdsFor(SETTLE_MS) { appHasFocus() })
    }

    /**
     * Answers with Back every dialog of the page's alert loop that shows, until the page says the
     * loop is done, and returns the numbers of the alerts answered. Back only goes while the page
     * is still in alert k (its title says so) and the app's window has lost the focus for a while:
     * that is alert k's dialog, never the app's own window.
     */
    private fun answerDialogsUntilDone(tabId: String): List<Int> {
        val answered = mutableListOf<Int>()
        while (true) {
            var seen: String? = null
            waitUntil("a dialog of the loop, or its end (answered so far: $answered)", LOOP_TIMEOUT_MS) {
                seen = title(tabId)
                seen == DONE || (alertOf(seen)?.let { it !in answered } == true && !appHasFocus())
            }
            if (seen == DONE) return answered
            val alert = checkNotNull(alertOf(seen))
            if (!holdsFor(HOLD_MS) { !appHasFocus() && title(tabId) == seen }) continue
            pressBack()
            answered += alert
            waitUntil("alert $alert to be answered") { title(tabId) != seen }
        }
    }

    private fun title(tabId: String): String? = rig.tab(tabId)?.title

    /** The number k of a page titled "Alert k", in alert k of its loop. */
    private fun alertOf(title: String?): Int? = title?.takeIf { it.startsWith(ALERT_TITLE) }?.removePrefix(ALERT_TITLE)?.toIntOrNull()

    private fun appHasFocus(): Boolean = onMain { rig.activity.hasWindowFocus() }

    private fun pressBack() {
        shell("input keyevent 4")
    }

    private fun ticks(tabId: String): Int = (jsValue(rig.evaluate(tabId, "window.skimTicks")) as Number).toInt()

    private fun tapped(tabId: String): Int = (jsValue(rig.evaluate(tabId, "window.skimTapped")) as Number).toInt()

    private companion object {
        const val ALERTS = 6
        const val ALERT_TITLE = "Alert "
        const val DONE = "Alerts done"

        /** How long a dialog stays up before the test answers it; a dialog answered by Skim returns far sooner. */
        const val HOLD_MS = 500L
        const val SETTLE_MS = 1_000L
        const val TAP_WAIT_MS = 1_000L
        const val LOOP_TIMEOUT_MS = 30_000L
    }
}
