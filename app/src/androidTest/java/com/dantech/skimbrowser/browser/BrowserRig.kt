package com.dantech.skimbrowser.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.browser.downloads.DownloadEvent
import com.dantech.skimbrowser.browser.page.HighlightRequest
import com.dantech.skimbrowser.browser.page.PageContent
import com.dantech.skimbrowser.browser.ui.WebViewHost
import com.dantech.skimbrowser.createBrowserHost
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import org.junit.rules.ExternalResource

/**
 * The browser as the app wires it, for one test: a container of its own on a temporary directory
 * (database, settings, tab state, thumbnails), and a browser host made by MainActivity's own
 * [createBrowserHost], in a plain activity launched for the test. What the screens of later
 * phases do is up to each test: [show] puts any composable over the host, [showCurrentTab] shows
 * the current tab like the browser screen will.
 *
 * Everything the test opened is closed afterwards: every tab, the incognito session, the pool, the
 * container, the page server, the temporary directory, and the default profile's cookies, storage
 * and cache, which every WebView of the process shares.
 */
class BrowserRig : ExternalResource() {

    lateinit var activity: ComponentActivity
        private set
    lateinit var pool: WebViewPool
        private set
    lateinit var host: BrowserHost
        private set
    lateinit var container: AppContainer
        private set

    val pages = TestPages()
    val activityHost = TestActivityHost()

    /** Every page event, long-press target and download end of the test, in order. */
    val pageEvents = CopyOnWriteArrayList<PageEvent>()
    val hits = CopyOnWriteArrayList<Pair<String, HitTarget>>()
    val downloadEvents = CopyOnWriteArrayList<DownloadEvent>()

    val tabs: TabManager get() = container.tabManager
    val incognito: IncognitoSession get() = container.incognitoSession

    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var dir: File
    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun before() {
        pages.start()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity = it }
        dir = File(activity.cacheDir, "browser-test-${UUID.randomUUID()}")
        container = AppContainer(activity.application, AppStorage.temp(dir))
        runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { tabs.restore() } }
        collectors.launch(start = CoroutineStart.UNDISPATCHED) { tabs.pageEvents.collect { pageEvents += it } }
        collectors.launch(start = CoroutineStart.UNDISPATCHED) { tabs.hitTargets.collect { hits += it } }
        collectors.launch(start = CoroutineStart.UNDISPATCHED) { container.downloadTracker.events.collect { downloadEvents += it } }
        onMain {
            host = createBrowserHost(activity, container, activityHost)
            pool = host.pool
            tabs.attach(pool)
            pool.onStart()
        }
    }

    override fun after() {
        try {
            onMain {
                activity.setContent {}
                val open = tabs.state.value.tabs.map { it.id }
                if (open.isNotEmpty()) tabs.finalizeClose(tabs.close(open))
                tabs.finalizePendingCloses()
                if (incognito.state.value != IncognitoSession.State.None) incognito.end(pool)
                pool.withTemporaryWebView(profile = null) { it.clearCache(true) }
                host.destroy()
                tabs.detach(pool)
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                WebStorage.getInstance().deleteAllData()
            }
            runBlocking {
                withTimeout(DEVICE_TIMEOUT_MS) {
                    tabs.awaitWrites()
                    container.tabStateStore.awaitIdle()
                    container.thumbnails.awaitIdle()
                }
            }
        } finally {
            collectors.cancel()
            container.close()
            scenario.close()
            pages.close()
            dir.deleteRecursively()
        }
    }

    /** Opens a tab (on the main thread) and returns its id. */
    fun newTab(url: String?, incognito: Boolean = false, background: Boolean = false): String =
        onMain { tabs.newTab(incognito = incognito, url = url, background = background) }

    fun tab(tabId: String): Tab? = tabs.state.value.tab(tabId)

    fun runtime(tabId: String): TabRuntime = tabs.runtime(tabId).value

    /** Shows [content] in the activity, with this rig's browser host provided. */
    fun show(content: @Composable () -> Unit) = onMain {
        activity.setContent { CompositionLocalProvider(LocalBrowserHost provides host) { content() } }
    }

    /** Shows the current tab's WebView over the whole activity, as the browser screen will. */
    fun showCurrentTab() = show { CurrentTab(tabs) }

    /** Makes [tabId] current, loads [url] in it and waits until it finished loading. */
    fun loadIn(tabId: String, url: String): TabRuntime {
        onMain {
            tabs.select(tabId)
            tabs.load(tabId, url)
        }
        return awaitLoaded(tabId, url)
    }

    /** Waits until [tabId] finished loading [url] (any page when null) and returns its runtime. */
    fun awaitLoaded(tabId: String, url: String? = null, timeoutMs: Long = DEVICE_TIMEOUT_MS): TabRuntime =
        awaitValue(tabs.runtime(tabId), "tab $tabId to finish loading ${url ?: "its page"}", timeoutMs) { runtime ->
            runtime.pageLoaded && (url == null || runtime.committedUrl == url)
        }

    /** The tab's WebView, which the pool makes when the tab has none. */
    fun webViewOf(tabId: String): SkimWebView = onMain {
        pool.obtain(checkNotNull(tab(tabId)) { "No open tab $tabId" }) as SkimWebView
    }

    /** The tabs' WebViews attached to the activity's window, shown or off screen. */
    fun webViewsInWindow(): List<SkimWebView> = onMain { viewsIn<SkimWebView>(activity.window.decorView) }

    /** The one tab WebView in the window, once there is exactly one and it is not [other]. */
    fun shownWebView(other: SkimWebView? = null): SkimWebView {
        waitUntil("one WebView on screen") { webViewsInWindow().singleOrNull()?.let { it !== other } == true }
        return webViewsInWindow().single()
    }

    /** Waits until at least [count] downloads of the test ended and returns how every one ended so far. */
    fun awaitDownloadEvents(count: Int, timeoutMs: Long = DEVICE_TIMEOUT_MS): List<DownloadEvent> {
        waitUntil("$count downloads to end (${downloadEvents.size} so far)", timeoutMs) { downloadEvents.size >= count }
        return downloadEvents.toList()
    }

    /** Runs [script] in [tabId]'s page through the pool and returns its JSON result. */
    fun evaluate(tabId: String, script: String): String? = runBlocking {
        withTimeout(DEVICE_TIMEOUT_MS) { withContext(Dispatchers.Main) { pool.evaluate(tabId, script) } }
    }

    /** Runs [script] in [view] directly and returns its JSON result. */
    fun evaluateIn(view: WebView, script: String): String? = evaluateInWebView(view, script)

    /** The tab's page as Reader view and Skim read it ([TabManager.extractPage]). */
    fun extract(tabId: String): PageContent? = runBlocking { withTimeout(EXTRACT_TIMEOUT_MS) { tabs.extractPage(tabId) } }

    /** [TabManager.highlightBlock], called from the test thread. */
    fun highlight(tabId: String, request: HighlightRequest): Boolean =
        runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { tabs.highlightBlock(tabId, request) } }

    /** Where this test's container keeps its files. */
    val storageDir: File get() = dir

    /**
     * Scrolls [tabId]'s page to [y] and waits until the WebView's own saved state holds the new
     * position: Chromium hands a page's scroll position to its navigation state a moment after the
     * scroll, so a state saved right away would still have the old one.
     */
    fun scrollAndKeep(tabId: String, y: Int) {
        val view = webViewOf(tabId)
        val settled = settledState(view)
        evaluate(tabId, "window.scrollTo(0, $y)")
        waitUntil("the page to scroll to $y") { isScrolledTo(tabId, y) }
        waitUntil("the saved state to hold the scroll position") { !stateBytes(view).contentEquals(settled) }
    }

    /**
     * Whether the page is scrolled to [y] CSS pixels, give or take [SCROLL_TOLERANCE]: device pixels
     * make the offset fractional, and Chromium's own Back puts a page a few pixels off (1600 comes
     * back as 1596.57 at this density, with or without a saved-state round trip).
     */
    fun isScrolledTo(tabId: String, y: Int): Boolean {
        val scrolled = (jsValue(evaluate(tabId, "window.scrollY")) as? Number)?.toDouble() ?: return false
        return abs(scrolled - y) <= SCROLL_TOLERANCE
    }

    /** The WebView's saved state once it stopped changing for a while. */
    private fun settledState(view: WebView): ByteArray {
        var last = stateBytes(view)
        waitUntil("the WebView's state to settle") {
            val stable = holdsFor(STATE_QUIET_MS) { stateBytes(view).contentEquals(last) }
            if (!stable) last = stateBytes(view)
            stable
        }
        return last
    }

    /** What `WebView.saveState` writes for [view] now, flattened to compare. */
    private fun stateBytes(view: WebView): ByteArray = onMain {
        val state = Bundle().also { view.saveState(it) }
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        /** Waiting for the page, then running Readability on it. */
        const val EXTRACT_TIMEOUT_MS = 2 * DEVICE_TIMEOUT_MS
        const val STATE_QUIET_MS = 1_500L
        const val SCROLL_TOLERANCE = 8.0
    }
}

/** Runs [script] in [view] and returns its JSON result, waiting at most [DEVICE_TIMEOUT_MS]. */
internal fun evaluateInWebView(view: WebView, script: String): String? = runBlocking {
    withTimeout(DEVICE_TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                view.evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
            }
        }
    }
}

/** The current tab's WebView, filling its parent; nothing while no tab is current. */
@Composable
fun CurrentTab(tabs: TabManager) {
    val state by tabs.state.collectAsState()
    state.currentTabId?.let { WebViewHost(it, Modifier.fillMaxSize()) }
}

/**
 * An [ActivityHost] that cancels pickers and denies permission prompts at once, and records app
 * launches and the permissions asked for. A launch is only recorded, unless [startExternalWith]
 * hands it on (to `Activity.startActivity`, as the app's own host does, so that a link no app takes
 * fails as it would in the app).
 */
class TestActivityHost : ActivityHost {
    val launched = CopyOnWriteArrayList<Intent>()

    /** The permissions of each prompt asked for, in order; every one was denied. */
    val permissionRequests = CopyOnWriteArrayList<List<String>>()

    @Volatile
    var startExternalWith: ((Intent) -> Unit)? = null

    override fun chooseFiles(intent: Intent, onResult: (resultCode: Int, data: Intent?) -> Unit) {
        onResult(Activity.RESULT_CANCELED, null)
    }

    override fun requestPermissions(permissions: Array<String>, onResult: (granted: Map<String, Boolean>) -> Unit) {
        permissionRequests += permissions.toList()
        onResult(permissions.associateWith { false })
    }

    override fun startExternal(intent: Intent) {
        launched += intent
        startExternalWith?.invoke(intent)
    }
}
