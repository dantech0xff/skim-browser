package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import android.app.UiAutomation
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.dantech.skimbrowser.browser.page.PageScripts
import com.dantech.skimbrowser.browser.page.escapeForScript
import com.dantech.skimbrowser.browser.page.parsePageContent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue

/** How long a device test waits for a page, a callback or a view by default. */
internal const val DEVICE_TIMEOUT_MS = 15_000L

/** Between two checks of a condition the main thread and the WebView renderer run. */
private const val POLL_MS = 20L

/** Runs [block] on the main thread and returns its result; what it throws is thrown here. */
internal fun <T> onMain(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) return block()
    var result: Result<T>? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
    return checkNotNull(result) { "The main thread did not run the block" }.getOrThrow()
}

/** Checks [condition] every few milliseconds until it holds; fails naming [what] after [timeoutMs]. */
internal fun waitUntil(what: String, timeoutMs: Long = DEVICE_TIMEOUT_MS, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (!condition()) {
        if (SystemClock.uptimeMillis() > deadline) throw AssertionError("Timed out after $timeoutMs ms waiting for $what")
        pause()
    }
}

/**
 * True when [condition] held at every check for [durationMs]: how a test shows that something does
 * not happen. Returns false at the first check that fails.
 */
internal fun holdsFor(durationMs: Long, condition: () -> Boolean): Boolean {
    val end = SystemClock.uptimeMillis() + durationMs
    while (SystemClock.uptimeMillis() < end) {
        if (!condition()) return false
        pause()
    }
    return condition()
}

/** The first value of [flow] that matches [predicate]; fails naming [what] after [timeoutMs]. */
internal fun <T> awaitValue(flow: Flow<T>, what: String, timeoutMs: Long = DEVICE_TIMEOUT_MS, predicate: (T) -> Boolean): T =
    runBlocking {
        try {
            withTimeout(timeoutMs) { flow.first(predicate) }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Timed out after $timeoutMs ms waiting for $what", e)
        }
    }

/**
 * The instrumentation's UiAutomation, for shell commands and runtime permissions. From Android 12
 * it connects without accessibility: an accessibility client makes the process's WebViews build
 * their accessibility trees, which would change how pages run in the tests that follow.
 */
internal fun shellAutomation(): UiAutomation {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY)
    } else {
        instrumentation.uiAutomation
    }
}

/** Runs [command] in the device's shell, as the shell user, and returns what it printed once it ended. */
internal fun shell(command: String): String =
    ParcelFileDescriptor.AutoCloseInputStream(shellAutomation().executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }

/** Returns once the main thread drew its next frame; a gesture moves one step per frame. */
internal fun awaitNextFrame() {
    val drawn = CountDownLatch(1)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        Choreographer.getInstance().postFrameCallback { drawn.countDown() }
    }
    check(drawn.await(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "No frame was drawn within $FRAME_TIMEOUT_MS ms" }
}

private const val FRAME_TIMEOUT_MS = 2_000L

// The poll interval of the waits above: they check a condition, they do not wait for a fixed time.
private fun pause() = SystemClock.sleep(POLL_MS)

/** Every view of class [T] in the tree under [root], in drawing order. */
internal inline fun <reified T : View> viewsIn(root: View): List<T> = viewTree(root).filterIsInstance<T>()

/** [root] and every view under it, depth first in drawing order. */
internal fun viewTree(root: View): List<View> {
    val found = mutableListOf(root)
    if (root is ViewGroup) (0 until root.childCount).forEach { found += viewTree(root.getChildAt(it)) }
    return found
}

/** The value of a JavaScript result from `evaluateJavascript`: a JSON value, as a Kotlin value. */
internal fun jsValue(json: String?): Any? = json?.let { JSONTokener(it).nextValue() }.takeUnless { it == JSONObject.NULL }

/**
 * What the device's WebView can do, for the tests that need more than every WebView Skim runs on
 * has: Android 8 and 9 may still run the WebView they shipped with (Chromium 66 on Android 9),
 * without profiles and without most of androidx.webkit's features. A test that cannot run on this
 * WebView is skipped with `assumeTrue`, its reason naming what is missing and the WebView's
 * version; it is never failed. Every check asks the WebView itself (its features, or a script run
 * in a page), never the Android version. Call them from the test thread, never the main thread.
 */
internal object WebViewSupport {

    /** The WebView package and its version, as skip reasons name it. */
    val webView: String by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WebViewCompat.getCurrentWebViewPackage(context)?.let { "${it.packageName} ${it.versionName}" } ?: "no WebView package"
    }

    /** Whether the WebView has androidx.webkit's [feature]. */
    fun has(feature: String): Boolean = WebViewFeature.isFeatureSupported(feature)

    /** Skips the test unless the WebView has [feature]; [neededFor] says what needs it ("which ..."). */
    fun assumeFeature(feature: String, neededFor: String) {
        assumeTrue("This WebView ($webView) lacks $feature, which $neededFor", has(feature))
    }

    /** Skips the test unless the WebView has profiles: every incognito tab needs one of its own. */
    fun assumeProfiles() = assumeFeature(WebViewFeature.MULTI_PROFILE, "every incognito tab needs")

    /** Skips the test where the WebView has profiles: it is about a WebView without incognito. */
    fun assumeNoProfiles() {
        assumeFalse("This WebView ($webView) has profiles (MULTI_PROFILE), so incognito is available", has(WebViewFeature.MULTI_PROFILE))
    }

    /**
     * Skips the test unless Skim's page scripts run in this WebView's pages. Reader view and Skim
     * read a page through Readability.js, whose syntax (`??`) needs Chromium 80: an older WebView
     * cannot even parse the script, and Skim reads no page there ([TabManager.extractPage] is null).
     */
    fun assumePageScriptsRun() {
        val problem = pageScriptProblem
        assumeTrue("Skim's page scripts do not run on this WebView ($webView): $problem", problem == null)
    }

    /** Whether Skim's page scripts run in this WebView's pages: the app's extract script parsed and ran. */
    val pageScriptsRun: Boolean get() = pageScriptProblem == null

    /** Skips the test where the page scripts run: it is about a WebView too old to run them. */
    fun assumePageScriptsDoNotRun() {
        assumeFalse("Skim's page scripts run on this WebView ($webView)", pageScriptsRun)
    }

    /**
     * Whether the WebView reads what a link with a `download` attribute points to before it hands
     * the download over: it then reports the response's type, and over http the address after the
     * server's redirects and the Content-Disposition. Chromium 66 hands such a link over at once,
     * unrequested: its own address, with no type, no length and no Content-Disposition.
     */
    val readsDownloadLinksFirst: Boolean by lazy { probeDownloadLink() }

    /** Why the app's extract script did not read a page of this WebView; null when it did. */
    private val pageScriptProblem: String? by lazy { probePageScripts() }

    /**
     * Clicks a `download` link to a `data:` URL in a WebView of its own and tells whether the
     * download listener got the URL's type (the WebView read the link first) or none.
     */
    // Only the probe's own click runs in this WebView.
    @SuppressLint("SetJavaScriptEnabled")
    private fun probeDownloadLink(): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val types = CopyOnWriteArrayList<String>()
        var finished = false
        val view = onMain {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        finished = true
                    }
                }
                setDownloadListener { _, _, _, mimeType, _ -> types += mimeType.orEmpty() }
                loadDataWithBaseURL(PROBE_ORIGIN, DOWNLOAD_LINK_PAGE, "text/html", "utf-8", null)
            }
        }
        try {
            waitUntil("the page of the download link probe to load") { onMain { finished } }
            evaluateInWebView(view, "document.getElementById('link').click()")
            waitUntil("the probe's download link to be handed over") { types.isNotEmpty() }
            return types.first().isNotEmpty()
        } finally {
            onMain { view.destroy() }
        }
    }

    /**
     * Runs the app's own extract script on a page of a few words in a WebView of its own and
     * returns what kept it from reading the page: the error the WebView threw (a syntax error when
     * it cannot parse the script), or a reading Skim cannot use. The script runs through an
     * indirect eval inside a try, so a script the WebView cannot parse still answers.
     */
    // Only the app's extract script and the probe around it run in this WebView.
    @SuppressLint("SetJavaScriptEnabled")
    private fun probePageScripts(): String? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val script = escapeForScript(JSONObject.quote(PageScripts(context.assets).extractScript()))
        var finished = false
        val view = onMain {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        finished = true
                    }
                }
                loadDataWithBaseURL(PROBE_ORIGIN, PROBE_PAGE, "text/html", "utf-8", null)
            }
        }
        try {
            waitUntil("the page of the script probe to load") { onMain { finished } }
            val answer = jsValue(
                evaluateInWebView(view, "(function () { try { return { read: (0, eval)($script) }; } catch (e) { return { error: String(e) }; } })()"),
            ) as? JSONObject ?: return "the probe itself did not run"
            if (answer.has("error")) return answer.getString("error")
            val read = answer.optString("read")
            return if (parsePageContent(read, committedUrl = PROBE_ORIGIN, loadNonce = 0) == null) "it read nothing Skim can use" else null
        } finally {
            onMain { view.destroy() }
        }
    }

    private const val PROBE_ORIGIN = "http://127.0.0.1/"
    private const val PROBE_PAGE =
        "<!doctype html><html lang=\"en\"><head><title>Probe</title></head><body><p>A page for the script probe.</p></body></html>"
    private const val DOWNLOAD_LINK_PAGE =
        "<!doctype html><html lang=\"en\"><head><title>Probe</title></head><body>" +
            "<a id=\"link\" download href=\"data:text/plain;base64,AAEC\">A file</a></body></html>"
}

/**
 * A finger on the touch screen, through the system's input pipeline: the events reach the window
 * like real touches do, with real timing, so the WebView's gesture detection (long press, scroll,
 * overscroll) runs as on a phone. Coordinates are screen pixels; never call this on the main thread.
 */
internal object Finger {

    /** A tap at the center of [view]'s area from [fractionX], [fractionY] of its size. */
    fun tap(view: View, fractionX: Float = 0.5f, fractionY: Float = 0.5f) {
        val (x, y) = pointIn(view, fractionX, fractionY)
        val down = SystemClock.uptimeMillis()
        send(down, down, MotionEvent.ACTION_DOWN, x, y)
        send(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)
    }

    /**
     * Presses at [view]'s point from [fractionX], [fractionY] and keeps the finger down until
     * [heldLongEnough] or [timeoutMs], then lifts it. Returns whether [heldLongEnough] became true.
     */
    fun longPress(view: View, fractionX: Float, fractionY: Float, timeoutMs: Long = LONG_PRESS_TIMEOUT_MS, heldLongEnough: () -> Boolean): Boolean {
        val (x, y) = pointIn(view, fractionX, fractionY)
        val down = SystemClock.uptimeMillis()
        send(down, down, MotionEvent.ACTION_DOWN, x, y)
        val held = try {
            waitUntil("a long press to be noticed", timeoutMs, heldLongEnough)
            true
        } catch (_: AssertionError) {
            false
        }
        send(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)
        return held
    }

    /**
     * A vertical drag on [view] at [fractionX] of its width, from [fromY] to [toY] (fractions of its
     * height), in [steps] moves, one per frame, as a finger moves. The events are stamped
     * [STEP_MS] apart, as a steady finger would make them, whatever the delay in delivering them:
     * velocity trackers read those stamps, so the release speed does not depend on a busy device.
     */
    fun drag(view: View, fromY: Float, toY: Float, fractionX: Float = 0.5f, steps: Int = DRAG_STEPS) {
        val (x, startY) = pointIn(view, fractionX, fromY)
        val (_, endY) = pointIn(view, fractionX, toY)
        val down = SystemClock.uptimeMillis()
        send(down, down, MotionEvent.ACTION_DOWN, x, startY)
        for (step in 1..steps) {
            awaitNextFrame()
            send(down, stamp(down, step), MotionEvent.ACTION_MOVE, x, startY + (endY - startY) * step / steps)
        }
        awaitNextFrame()
        send(down, stamp(down, steps + 1), MotionEvent.ACTION_UP, x, endY)
    }

    /** The time of a drag's [step]th event; never later than now, as no event comes from the future. */
    private fun stamp(down: Long, step: Int): Long = minOf(down + step * STEP_MS, SystemClock.uptimeMillis())

    private fun pointIn(view: View, fractionX: Float, fractionY: Float): Pair<Float, Float> = onMain {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        (location[0] + view.width * fractionX) to (location[1] + view.height * fractionY)
    }

    private fun send(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event)
        } finally {
            event.recycle()
        }
    }

    private const val LONG_PRESS_TIMEOUT_MS = 4_000L
    private const val DRAG_STEPS = 24

    /** One frame at 60 Hz, rounded down so a stamp never runs ahead of the frames waited for. */
    private const val STEP_MS = 16L
}
