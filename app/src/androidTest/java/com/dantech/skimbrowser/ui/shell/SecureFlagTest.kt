package com.dantech.skimbrowser.ui.shell

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.Profile
import androidx.webkit.WebViewCompat
import com.dantech.skimbrowser.domain.tab.HitTarget
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.WebViewSupport
import com.dantech.skimbrowser.browser.holdsFor
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.shell
import com.dantech.skimbrowser.browser.shellAutomation
import com.dantech.skimbrowser.browser.waitUntil
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.WelcomeKey
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToLong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FLAG_SECURE in the running app: on before the first frame that shows incognito content (a page,
 * a sheet or a screen of an incognito tab, Incognito tabs), on for as long as such content is
 * drawn, including while it animates away, and off once none is left; and the Recents preview
 * hidden while any incognito tab is open. Every frame drawn while an incognito page is on screen
 * is checked for the flag as it is drawn, and screenshots show that the system really blanks the
 * window. Incognito needs WebView profiles, so the tests skip on a WebView without them.
 */
@RunWith(AndroidJUnit4::class)
class SecureFlagTest {

    @get:Rule
    val skim = SkimAppRule()

    private val page: String get() = skim.pages.url("/plain.html")

    @Before
    fun incognitoNeedsProfiles() {
        WebViewSupport.assumeProfiles()
    }

    @Test
    fun anIncognitoTabOpenedThroughTheNavigatorIsSecureBeforeItsFirstFrame() {
        skim.setOnboarded(true)
        skim.openTab(page)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        awaitPageOnScreen(activity, incognito = false)
        assertFalse(onMain { activity.isSecure() })

        IncognitoFrames.watch(activity) { frames ->
            // Checked in the main-thread task that opens the tab: no frame can be drawn in between.
            val securedAtOnce = onMain {
                navigator.openNewTab(incognito = true, url = page)
                activity.isSecure()
            }
            assertTrue("FLAG_SECURE is on as the incognito tab opens", securedAtOnce)
            val tab = checkNotNull(skim.tabs.state.value.currentTab)
            assertTrue(tab.incognito)
            assertTrue(runBlocking { skim.tabs.awaitPageReady(tab.id) })
            frames.awaitIncognitoFrame()
        }
    }

    @Test
    fun aSheetOfAnIncognitoTabKeepsTheWindowSecureOverAnyScreen() {
        skim.setOnboarded(true)
        val normal = skim.openTab(page)
        val incognito = skim.openTab(page, incognito = true)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        awaitPageOnScreen(activity, incognito = true)

        IncognitoFrames.watch(activity) {
            val sheets = listOf<SkimNavigator.() -> Unit>(
                { openMoreMenu(incognito) },
                { openLinkOptions(incognito, HitTarget.Link(page, "A link")) },
                { openSiteInfo(incognito) },
            )
            for (open in sheets) {
                assertTrue("secure as the sheet opens", onMain { navigator.open(); activity.isSecure() })
                assertTrue("secure while the sheet shows", holdsFor(screenChangeMs()) { onMain { activity.isSecure() } })
                onMain { navigator.back() }
                awaitPageOnScreen(activity, incognito = true)
                assertTrue(onMain { activity.isSecure() })
            }

            onMain { navigator.showTab(normal) }
            waitUntil("the normal tab to show without FLAG_SECURE") { onMain { !activity.isSecure() } }
            val securedOverNormal = onMain {
                navigator.openMoreMenu(incognito)
                activity.isSecure()
            }
            assertTrue("a sheet of the incognito tab over a normal tab is secure", securedOverNormal)
            assertTrue(holdsFor(screenChangeMs()) { onMain { activity.isSecure() } })
            onMain { navigator.back() }
            waitUntil("FLAG_SECURE to go once that sheet has gone") { onMain { !activity.isSecure() } }
        }
    }

    @Test
    fun incognitoTabsKeepTheWindowSecureUntilTheyHaveFadedOut() {
        assumeTrue("Animations are off on this device, so screens change without a fade", ValueAnimator.areAnimatorsEnabled())
        skim.setOnboarded(true)
        skim.openTab(page)
        skim.openTab(page, incognito = true, background = true)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        awaitPageOnScreen(activity, incognito = false)

        assertTrue("Incognito tabs are secure as they open", onMain { navigator.openTabs(incognito = true); activity.isSecure() })
        waitUntil("Incognito tabs to replace the page") { onMain { activity.webViewsOnScreen().isEmpty() } }

        onMain { navigator.openTabs(incognito = false) }
        // The fade lasts the whole transition: the flag holds through most of it, checked every few ms.
        assertTrue("secure while Incognito tabs fade out", holdsFor(fadeMs() * FADE_WATCHED / FADE_PARTS) { onMain { activity.isSecure() } })
        waitUntil("FLAG_SECURE to go once Incognito tabs are gone") { onMain { !activity.isSecure() } }
    }

    @Test
    fun anIncognitoPageIsSecureInEveryFrameItFadesOutIn() {
        skim.setOnboarded(true)
        skim.openTab(page)
        val incognito = skim.openTab(page, incognito = true)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        assertTrue(runBlocking { skim.tabs.awaitPageReady(incognito) })
        awaitPageOnScreen(activity, incognito = true)

        IncognitoFrames.watch(activity) { frames ->
            val before = frames.incognitoFrames
            onMain { navigator.openTabs(incognito = false) }
            waitUntil("the incognito page to fade out") { onMain { activity.webViewsOnScreen().isEmpty() } }
            assertTrue("frames were drawn while the page faded out", frames.incognitoFrames > before)
            waitUntil("FLAG_SECURE to go once the page is gone") { onMain { !activity.isSecure() } }
        }
    }

    @Test
    fun backToAnIncognitoPageIsSecureFromItsFirstFrame() {
        skim.setOnboarded(true)
        skim.openTab(page, incognito = true)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        awaitPageOnScreen(activity, incognito = true)
        onMain { navigator.openSettings() }
        waitUntil("Settings to replace the incognito page and FLAG_SECURE to go") {
            onMain { activity.webViewsOnScreen().isEmpty() && !activity.isSecure() }
        }

        IncognitoFrames.watch(activity) { frames ->
            skim.pressBack()
            frames.awaitIncognitoFrame()
            waitUntil("the browser to be back") { skim.keysOf(scenario) == listOf(BrowserKey) }
            assertTrue(onMain { activity.isSecure() })
        }
    }

    @Test
    fun backFromAnIncognitoPageKeepsTheWindowSecureUntilThePageIsGone() {
        skim.setOnboarded(false)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        onMain { navigator.openNewTab(incognito = true, url = page) }
        assertEquals(listOf(WelcomeKey, BrowserKey), skim.keysOf(scenario))
        val incognito = checkNotNull(skim.tabs.state.value.currentTab).id
        assertTrue(runBlocking { skim.tabs.awaitPageReady(incognito) })
        awaitPageOnScreen(activity, incognito = true)

        IncognitoFrames.watch(activity) { frames ->
            val before = frames.incognitoFrames
            skim.pressBack()
            waitUntil("Welcome to replace the incognito page") {
                skim.keysOf(scenario) == listOf(WelcomeKey) && onMain { activity.webViewsOnScreen().isEmpty() }
            }
            assertTrue("frames were drawn while the page went away", frames.incognitoFrames > before)
            waitUntil("FLAG_SECURE to go once the page is gone") { onMain { !activity.isSecure() } }
        }
        assertTrue("the tab itself stays open", skim.tabs.state.value.tab(incognito) != null)
    }

    @Test
    fun closingTheLastIncognitoTabAndShowingANormalOneLetsTheFlagGo() {
        skim.setOnboarded(true)
        val normal = skim.openTab(page)
        val incognito = skim.openTab(page, incognito = true)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        awaitPageOnScreen(activity, incognito = true)

        IncognitoFrames.watch(activity) {
            val close = onMain {
                skim.tabs.close(listOf(incognito)).also { navigator.showTab(normal) }
            }
            awaitPageOnScreen(activity, incognito = false)
            waitUntil("FLAG_SECURE to go") { onMain { !activity.isSecure() } }
            onMain { skim.tabs.finalizeClose(close) }
            assertEquals(IncognitoSession.State.None, skim.container.incognitoSession.state.value)
            assertFalse(onMain { activity.isSecure() })
        }
    }

    @Test
    fun recentsKeepsNoPictureOfSkimWhileAnIncognitoTabIsOpen() {
        assumeTrue(
            "Hiding the Recents picture of a window that is not secure needs Activity.setRecentsScreenshotEnabled " +
                "(API 33); this device runs API ${Build.VERSION.SDK_INT}",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
        assumeFalse("This device keeps no task pictures for Recents", shell("dumpsys window").contains("mSnapshotEnabled=false"))
        skim.setOnboarded(true)
        skim.openTab(page)
        assertTrue("without an incognito tab Recents keeps a real picture of the page", recentsPictureIsReal())

        skim.openTab(page, incognito = true, background = true)
        assertFalse("with an incognito tab open behind the page Recents keeps none", recentsPictureIsReal())
    }

    @Test
    fun aScreenshotShowsNothingOfAnIncognitoTab() {
        skim.setOnboarded(true)
        val normal = skim.openTab(page)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        assertTrue(runBlocking { skim.tabs.awaitPageReady(normal) })
        awaitPageOnScreen(activity, incognito = false)
        waitUntil("a screenshot to show the normal page") { litShare(screenshotOfContent(activity)) > LIT_PAGE_SHARE }

        onMain { navigator.openNewTab(incognito = true, url = page) }
        val incognito = checkNotNull(skim.tabs.state.value.currentTab).id
        assertTrue(runBlocking { skim.tabs.awaitPageReady(incognito) })
        awaitPageOnScreen(activity, incognito = true)
        val shot = screenshotOfContent(activity)
        assertEquals("pixels of the incognito tab a screenshot shows (of ${shot.size})", 0, shot.count { it.isLit() })

        onMain { navigator.showTab(normal) }
        waitUntil("a screenshot to show the normal page again") { litShare(screenshotOfContent(activity)) > LIT_PAGE_SHARE }
    }

    /**
     * Launches the browser on the current tab, leaves for the home screen and returns whether
     * Recents kept a real picture of Skim's task (false: a blank one in the app's colors).
     */
    private fun recentsPictureIsReal(): Boolean {
        val scenario = skim.launch()
        skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        awaitPageOnScreen(activity, incognito = false)
        assertFalse("the page on screen is not incognito, so the window is not secure", onMain { activity.isSecure() })
        val task = onMain { activity.taskId }
        val earlier = recentsPicture(task)
        shell("input keyevent KEYCODE_HOME")
        waitUntil("Skim to be in the background") { scenario.state == Lifecycle.State.CREATED }
        var picture: RecentsPicture? = null
        waitUntil("Recents to take its picture of Skim's task") {
            picture = recentsPicture(task)?.takeIf { it.id != earlier?.id }
            picture != null
        }
        scenario.close()
        return checkNotNull(picture).isReal
    }

    /** The picture Recents keeps of task [taskId], as the window manager lists it; null when it has none. */
    private fun recentsPicture(taskId: Int): RecentsPicture? {
        val lines = shell("dumpsys window").lines()
        val cache = lines.indexOfFirst { it.trim() == "SnapshotCache Task" }
        if (cache < 0) return null
        val entry = lines.drop(cache + 1).takeWhile { !it.trim().startsWith("SnapshotCache ") }
            .dropWhile { it.trim() != "Entry token=$taskId" }
        val snapshot = entry.drop(1).take(SNAPSHOT_ENTRY_LINES).firstOrNull { "TaskSnapshot{" in it } ?: return null
        val id = SNAPSHOT_ID.find(snapshot)?.groupValues?.get(1) ?: return null
        val real = REAL_SNAPSHOT.find(snapshot)?.groupValues?.get(1) ?: return null
        return RecentsPicture(id, real.toBooleanStrict())
    }

    private class RecentsPicture(val id: String, val isReal: Boolean)

    /** Waits until a WebView of an incognito tab, or of a normal one, is on screen. */
    private fun awaitPageOnScreen(activity: Activity, incognito: Boolean) {
        waitUntil("the ${if (incognito) "incognito" else "normal"} tab's page to be on screen") {
            onMain { activity.webViewsOnScreen().any { isIncognito(it) == incognito } }
        }
    }

    /** The colors a screenshot shows of the window's content, between the system bars, every few pixels. */
    private fun screenshotOfContent(activity: Activity): IntArray {
        val content = onMain {
            val decor = activity.window.decorView
            val bars = checkNotNull(ViewCompat.getRootWindowInsets(decor)).getInsets(WindowInsetsCompat.Type.systemBars())
            val origin = IntArray(2).also(decor::getLocationOnScreen)
            Rect(origin[0], origin[1] + bars.top, origin[0] + decor.width, origin[1] + decor.height - bars.bottom)
        }
        val shot = checkNotNull(shellAutomation().takeScreenshot()) { "The system took no screenshot" }
        val pixels = shot.copy(Bitmap.Config.ARGB_8888, false)
        shot.recycle()
        check(content.intersect(0, 0, pixels.width, pixels.height)) { "The window is not on the screenshot" }
        try {
            val row = IntArray(content.width())
            val sampled = ArrayList<Int>()
            for (y in content.top until content.bottom step SAMPLE_STEP) {
                pixels.getPixels(row, 0, content.width(), content.left, y, content.width(), 1)
                for (x in row.indices step SAMPLE_STEP) sampled += row[x]
            }
            return sampled.toIntArray()
        } finally {
            pixels.recycle()
        }
    }

    private fun litShare(pixels: IntArray): Double = pixels.count { it.isLit() }.toDouble() / pixels.size

    /** The default screen change of the app: a crossfade of [FADE_MS], as long as animations are scaled. */
    private fun fadeMs(): Long = (FADE_MS * ValueAnimator.getDurationScale()).roundToLong()

    /** Long enough for a screen change to finish, with time for its last frames. */
    private fun screenChangeMs(): Long = fadeMs() + SETTLE_MS

    /**
     * Checks every frame [activity]'s window draws while it watches: a frame drawn while the
     * WebView of an incognito tab is on screen must be drawn with FLAG_SECURE set.
     */
    private class IncognitoFrames(private val activity: Activity) : ViewTreeObserver.OnDrawListener {

        /** How many frames were drawn with an incognito page on screen. */
        @Volatile
        var incognitoFrames = 0
            private set

        /** The frames, numbered from the start of the watch, drawn with an incognito page on screen and without the flag. */
        val leaks = CopyOnWriteArrayList<Int>()
        private var frames = 0

        override fun onDraw() {
            frames++
            if (activity.webViewsOnScreen().none(::isIncognito)) return
            incognitoFrames++
            if (!activity.isSecure()) leaks += frames
        }

        /** Waits for one more frame drawn with an incognito page on screen. */
        fun awaitIncognitoFrame() {
            val seen = incognitoFrames
            waitUntil("a frame with the incognito page on screen") { incognitoFrames > seen }
        }

        companion object {
            /** Runs [block] while every frame of [activity] is watched, then fails if one leaked. */
            fun watch(activity: Activity, block: (IncognitoFrames) -> Unit) {
                val watcher = IncognitoFrames(activity)
                val observer = onMain { activity.window.decorView.viewTreeObserver.also { it.addOnDrawListener(watcher) } }
                try {
                    block(watcher)
                } finally {
                    onMain { if (observer.isAlive) observer.removeOnDrawListener(watcher) }
                }
                assertTrue("Frames drawn with an incognito page on screen but without FLAG_SECURE: ${watcher.leaks}", watcher.leaks.isEmpty())
            }
        }
    }

    private companion object {
        /** Navigation 3's default screen change: a 700 ms crossfade. */
        const val FADE_MS = 700L
        const val FADE_WATCHED = 4L
        const val FADE_PARTS = 5L
        const val SETTLE_MS = 500L

        /** A page shows on most of the content area; a secure window shows none of it. */
        const val LIT_PAGE_SHARE = 0.5
        const val SAMPLE_STEP = 4

        const val SNAPSHOT_ENTRY_LINES = 3
        val SNAPSHOT_ID = Regex("""\bmId=(\d+)""")
        val REAL_SNAPSHOT = Regex("""\bmIsRealSnapshot=(true|false)""")

        /** Whether [view] browses in an incognito profile: every profile but the default one is incognito's. */
        fun isIncognito(view: WebView): Boolean = WebViewCompat.getProfile(view).name != Profile.DEFAULT_PROFILE_NAME

        /** A pixel anything but black: what a screenshot shows of a window that is not secure. */
        fun Int.isLit(): Boolean = maxOf(Color.red(this), Color.green(this), Color.blue(this)) > DARK_LIMIT

        const val DARK_LIMIT = 16
    }
}
