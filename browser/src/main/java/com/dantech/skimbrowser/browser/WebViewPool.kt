package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.dantech.skimbrowser.browser.downloads.DownloadRouter
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge
import com.dantech.skimbrowser.data.isWebUrl
import com.dantech.skimbrowser.domain.tab.TabsController
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The live WebViews of the tabs, for the life of one Activity; the [TabEngine] the Activity plugs
 * into the process's [TabEngineAttachment] while it exists. It reads the tabs through
 * [TabsController] and reports to [TabEventSink], and never names the tab manager. Every call is
 * made on the main thread.
 *
 * WebViews are made with the Activity's context when a tab is shown ([obtain]), needs its page
 * off screen ([ensureWebView]) or opens a popup ([obtainForPopup]), and kept by the rules of
 * [WebViewPoolPolicy]: a WebView pushed out has its state saved and is destroyed, and the tab's
 * next WebView restores that state. An incognito tab's WebView gets the incognito session's
 * profile before anything else touches it; without a session none is made, it never falls back to
 * the default profile.
 *
 * A new WebView restores its tab's saved state, read off the main thread, before it loads anything:
 * a load or reload asked for meanwhile waits and runs after the restore, so the back history
 * survives. A WebView's state is saved before it is destroyed, when the Activity stops, on a memory
 * warning, and a second after its page finished loading; it goes to the tab manager
 * ([TabEventSink.onStateSaved]), which keeps it.
 *
 * A host showing a tab's WebView follows [generation]: a new value when the tab's WebView was
 * replaced or destroyed, so the host builds its view again; null while the tab's renderer crashed
 * too often, when the host shows nothing until Try again ([reload]) or a new [load].
 *
 * The pages' downloads go to [downloads] and their permission requests to [sitePermissions]; a
 * WebView's requests still waiting when it is destroyed are denied.
 */
@MainThread
class WebViewPool(
    private val activity: Activity,
    private val tabs: TabsController,
    /** Where the WebViews' reports go. */
    private val tabEvents: TabEventSink,
    /** Tells whether this pool is still the one attached, so a replaced pool stops reporting. */
    private val engines: TabEngineSource,
    private val incognito: IncognitoSession,
    private val stateStore: TabStateStore,
    private val thumbnails: ThumbnailStore,
    private val activityHost: ActivityHost,
    private val fullscreen: FullscreenController,
    private val sitePermissions: SitePermissionBridge,
    private val downloads: DownloadRouter,
    isLowRamDevice: Boolean = activity.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    /** The process's JavaScript timers, shared by every pool: WebView pauses them for all WebViews at once. */
    private val timers: WebViewTimers = WebViewTimers.process,
    /** Draws a WebView's thumbnail, or null while it has nothing to show. */
    private val drawThumbnail: (View) -> Bitmap? = ::captureThumbnail,
) : TabEngine {

    private class Entry(
        val tabId: String,
        val view: SkimWebView,
        val client: SkimWebViewClient,
        val chrome: SkimChromeClient,
    ) {
        /** True until the saved state has been read and given to the WebView. */
        var restoring = false
        var destroyed = false
        var restoreJob: Job? = null
        var captureJob: Job? = null
        var saveJob: Job? = null

        /** The hosts showing it: while a screen change composes two, the newer one holds the view. */
        var hosts = 0

        /** `evaluate` calls still waiting for the page's answer. */
        val evaluations = LinkedHashSet<CancellableContinuation<String?>>()

        /** Find in page steps that wait for the WebView to finish counting a new search. */
        val findSteps = FindSteps()
    }

    /** A command that waits for the tab's WebView: there is none yet, or it is still restoring. */
    private sealed interface Pending {
        data class Load(val url: String) : Pending

        data object Reload : Pending

        data object Back : Pending

        data object Forward : Pending
    }

    private class Queued(var navigation: Pending? = null, var clearHistory: Boolean = false)

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val policy = WebViewPoolPolicy(isLowRamDevice)
    private val live = HashMap<String, Entry>()
    private val queued = HashMap<String, Queued>()

    /** Tabs whose renderer crashed too often: no WebView until Try again. */
    private val blocked = HashSet<String>()
    private val generations = HashMap<String, MutableStateFlow<Long?>>()
    private var lastGeneration = 0L
    private val events = PoolEvents()
    private var offscreenHost: FrameLayout? = null
    private var started = false
    private var savedSinceStop = false
    private var destroyed = false

    /**
     * The WebView generation of [tabId] for its host: a new value whenever the tab's WebView was
     * replaced or destroyed; null while the tab must show its crash error instead of a page.
     */
    fun generation(tabId: String): StateFlow<Long?> =
        generations.getOrPut(tabId) { MutableStateFlow(if (tabId in blocked) null else ++lastGeneration) }.asStateFlow()

    /**
     * The WebView of [tab], made when it has none: the profile first for an incognito tab, then
     * the settings and clients, then the saved state is restored (or the tab's http or https page
     * loaded when there is none). It becomes the most recently used, and older WebViews beyond the
     * capacity are evicted.
     *
     * @throws IllegalStateException for an incognito tab while no incognito session runs, or once
     *   the pool is destroyed.
     */
    fun obtain(tab: Tab): WebView {
        check(!destroyed) { "The WebView pool was destroyed with its Activity" }
        live[tab.id]?.let { entry ->
            policy.touch(tab.id)
            return entry.view
        }
        val entry = create(tab)
        restore(entry, tab)
        trim(keep = setOf(tab.id))
        return entry.view
    }

    /**
     * A WebView for the window that a page opened, shown by the new tab [popupTabId]: nothing is
     * loaded or restored into it, the window's content arrives through the opener. It is kept
     * alive with its opener for as long as both live. Null when it cannot be made.
     */
    fun obtainForPopup(popupTabId: String): WebView? {
        if (destroyed) return null
        val tab = tabs.state.value.tab(popupTabId) ?: return null
        val entry = try {
            create(tab)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No WebView for a popup", e)
            return null
        }
        tab.openerTabId?.let { opener -> policy.pinPopup(popupTabId, opener) }
        renew(popupTabId)
        trim(keep = setOfNotNull(popupTabId, tab.openerTabId))
        return entry.view
    }

    /** Saves the state of every live WebView, keeping them all. */
    fun saveAll() {
        live.values.toList().forEach(::saveState)
    }

    /** The Activity started: JavaScript timers run again and the full capacity is back. */
    fun onStart() {
        if (!started) {
            started = true
            timers.poolStarted(live.values.firstOrNull()?.view)
        }
        savedSinceStop = false
        val before = policy.capacity
        policy.onStart()
        if (policy.capacity != before) Log.i(TAG, "Capacity ${policy.capacity} (was $before)")
    }

    /**
     * The Activity stopped: every WebView's state is saved, and JavaScript timers pause once no
     * pool of the process is started any more (another Activity may still show its pages).
     */
    fun onStop() {
        saveAll()
        savedSinceStop = true
        if (started) {
            started = false
            timers.poolStopped(live.values.firstOrNull()?.view)
        }
    }

    /** A memory warning: see [WebViewPoolPolicy.onTrimMemory]. */
    fun onTrimMemory(level: Int) {
        val memory = ActivityManager.MemoryInfo()
        activity.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        val before = policy.capacity
        val response = policy.onTrimMemory(level, memory.lowMemory)
        if (response != WebViewPoolPolicy.TrimResponse.NONE) saveAll()
        if (response == WebViewPoolPolicy.TrimResponse.EVICT) trim(keep = emptySet())
        Log.i(TAG, "Memory warning $level: $response, capacity ${policy.capacity} (was $before), ${live.size} live")
    }

    /**
     * The Activity is being destroyed: the WebViews' state is saved (unless that already happened
     * when it stopped), every WebView destroyed, waiting page scripts answered with null, full
     * screen left and the off-screen host removed. The pool cannot be used again.
     */
    fun destroyAll() {
        if (destroyed) return
        if (!savedSinceStop) saveAll()
        if (started) {
            started = false
            timers.poolStopped(live.values.firstOrNull()?.view)
        }
        live.values.toList().forEach(::destroy)
        queued.clear()
        fullscreen.destroy()
        offscreenHost?.let { (it.parent as? ViewGroup)?.removeView(it) }
        offscreenHost = null
        scope.cancel()
        destroyed = true
    }

    override fun load(tabId: String, url: String) {
        unblock(tabId)
        val entry = readyEntry(tabId)
        if (entry != null) {
            entry.view.loadUrl(url)
        } else {
            queue(tabId).navigation = Pending.Load(url)
        }
    }

    override fun goBack(tabId: String): Boolean {
        val entry = live[tabId] ?: return false
        if (entry.restoring) {
            // The history is not known yet: going back waits for it rather than leave the tab.
            queue(tabId).navigation = Pending.Back
            return true
        }
        if (!entry.view.canGoBack()) return false
        entry.view.goBack()
        return true
    }

    override fun goForward(tabId: String) {
        val entry = live[tabId] ?: return
        if (entry.restoring) {
            queue(tabId).navigation = Pending.Forward
        } else if (entry.view.canGoForward()) {
            entry.view.goForward()
        }
    }

    override fun reload(tabId: String) {
        unblock(tabId)
        val entry = readyEntry(tabId)
        if (entry != null) {
            entry.view.reload()
        } else {
            val waiting = queue(tabId)
            if (waiting.navigation !is Pending.Load) waiting.navigation = Pending.Reload
        }
    }

    override fun setDesktop(tabId: String, on: Boolean) {
        val entry = live[tabId] ?: return
        applyDesktopMode(entry.view, on)
        // A restoring WebView loads its page with the new user agent anyway.
        if (!entry.restoring) entry.view.reload()
    }

    override fun find(tabId: String, q: String) {
        val entry = readyEntry(tabId) ?: return
        entry.findSteps.searchStarted()
        entry.view.findAllAsync(q)
    }

    override fun findNext(tabId: String, forward: Boolean) {
        val entry = readyEntry(tabId) ?: return
        if (entry.findSteps.step(forward)) entry.view.findNext(forward)
    }

    override fun clearFind(tabId: String) {
        val entry = readyEntry(tabId) ?: return
        entry.findSteps.cleared()
        entry.view.clearMatches()
    }

    override fun saveAndDestroy(tabId: String) {
        queued.remove(tabId)
        blocked.remove(tabId)
        live[tabId]?.let { entry ->
            saveState(entry)
            destroy(entry)
        }
        renew(tabId)
    }

    override fun clearHistory(tabId: String) {
        val entry = live[tabId] ?: return
        if (entry.restoring) queue(tabId).clearHistory = true else entry.view.clearHistory()
    }

    override fun ensureWebView(tabId: String, offscreen: Boolean) {
        if (destroyed || tabId in blocked) return
        val existing = live[tabId]
        val view = if (existing != null) {
            policy.touch(tabId)
            existing.view
        } else {
            val tab = tabs.state.value.tab(tabId) ?: return
            try {
                obtain(tab)
            } catch (e: RuntimeException) {
                Log.w(TAG, "No WebView for a tab", e)
                return
            }
        }
        if (offscreen && view.parent == null) offscreenHost()?.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    override suspend fun evaluate(tabId: String, script: String): String? {
        val entry = readyEntry(tabId) ?: return null
        // A page stuck in a loop never answers: the script's result is then null.
        return withTimeoutOrNull(EVALUATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                entry.evaluations += continuation
                continuation.invokeOnCancellation { entry.evaluations.remove(continuation) }
                // Called before the coroutine suspends, so the page that runs it is the one checked just before.
                entry.view.evaluateJavascript(script) { result ->
                    if (entry.evaluations.remove(continuation)) continuation.resume(result)
                }
            }
        }
    }

    override fun withTemporaryWebView(profile: String?, block: (WebView) -> Unit) {
        if (destroyed) {
            Log.w(TAG, "No Activity for a temporary WebView")
            return
        }
        val view = WebView(activity)
        try {
            if (profile != null) setProfile(view, profile)
            block(view)
        } finally {
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
    }

    /** The tab's WebView for its host: shown, so it resumes. Null when it cannot have one. */
    fun showInHost(tabId: String): WebView? {
        if (destroyed || tabId in blocked) return null
        val tab = tabs.state.value.tab(tabId) ?: return null
        val view = try {
            obtain(tab)
        } catch (e: RuntimeException) {
            // No incognito session, or no WebView at all while its package updates: the host shows nothing.
            Log.w(TAG, "No WebView for a tab", e)
            return null
        }
        (view.parent as? ViewGroup)?.removeView(view)
        // A WebView whose layout height is wrap_content gives its page a layout height of 0 (Android
        // WebView's zero-layout-height mode), so a page sized with `height: 100%` (maps, full-height
        // apps) collapses. A host's view gets wrap_content unless told otherwise: the WebView fills it.
        view.layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        live[tabId]?.let { it.hosts++ }
        view.onResume()
        return view
    }

    /**
     * A host stops showing [view]. Once no host shows the tab's live WebView, its thumbnail is
     * taken, it pauses and leaves the window; a host that still shows it keeps it. A WebView that
     * was replaced meanwhile only leaves its old host.
     */
    fun releaseFromHost(tabId: String, view: View) {
        val entry = live[tabId]?.takeIf { it.view === view && !it.destroyed }
        if (entry == null) {
            (view.parent as? ViewGroup)?.removeView(view)
            return
        }
        entry.hosts = (entry.hosts - 1).coerceAtLeast(0)
        if (entry.hosts > 0) return
        capture(entry)
        entry.view.onPause()
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun create(tab: Tab): Entry {
        // Known before any WebView exists: an incognito tab without its session's profile gets none.
        val profile = if (tab.incognito) incognitoProfile() else null
        val view = SkimWebView(activity, incognito = tab.incognito)
        val entry = try {
            if (profile != null) setProfile(view, profile)
            configure(view, tab)
        } catch (e: RuntimeException) {
            view.destroy()
            throw e
        }
        live[tab.id] = entry
        policy.touch(tab.id)
        timers.webViewCreated(view)
        return entry
    }

    /** The profile of the running incognito session; there is no other one an incognito tab may use. */
    private fun incognitoProfile(): String = when (val session = incognito.state.value) {
        is IncognitoSession.State.Active -> session.profile
        is IncognitoSession.State.Ending -> session.profile
        IncognitoSession.State.None -> {
            Log.w(TAG, "An incognito tab has no incognito session")
            throw IllegalStateException("An incognito tab has no incognito session")
        }
    }

    /** Sets [profile] on [view], which nothing has touched yet. */
    private fun setProfile(view: WebView, profile: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.setProfile(view, profile)
        } else {
            throw IllegalStateException("WebView profiles are not supported")
        }
    }

    // A browser runs the pages' JavaScript; no Java object is ever exposed to it.
    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(view: SkimWebView, tab: Tab): Entry {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(true)
        }
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            events.onFindResult(tab.id, activeMatchOrdinal, numberOfMatches)
            live[tab.id]?.takeIf { it.view === view }?.findSteps?.reported(isDoneCounting)?.forEach(view::findNext)
        }
        view.onHitTarget = { target -> events.onHitTarget(tab.id, target) }
        view.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloads.start(tab.id, url, userAgent, contentDisposition, mimeType, view)
        }
        val client = SkimWebViewClient(tab.id, tab.incognito, events, activityHost, tabs::load) { gone, action, didCrash ->
            onRendererGone(tab.id, gone, action, didCrash)
        }
        val chrome = SkimChromeClient(
            tabId = tab.id,
            incognito = tab.incognito,
            events = events,
            activityHost = activityHost,
            fullscreen = fullscreen,
            sitePermissions = sitePermissions,
            obtainForPopup = ::obtainForPopup,
            isShown = { isShown(tab.id, view) },
            documentId = { tabs.runtime(tab.id).value.loadNonce },
        )
        view.webViewClient = client
        view.webChromeClient = chrome
        applyDesktopMode(view, tab.desktopSite)
        return Entry(tab.id, view, client, chrome)
    }

    /** Reads the tab's saved state off the main thread, then restores it before anything loads. */
    private fun restore(entry: Entry, tab: Tab) {
        entry.restoring = true
        entry.restoreJob = scope.launch {
            val state = try {
                stateStore.load(tab.id, tab.incognito)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Tab state not read", e)
                null
            }
            if (!entry.destroyed) finishRestore(entry, state)
        }
    }

    private fun finishRestore(entry: Entry, state: Bundle?) {
        entry.restoring = false
        val restored = state != null && try {
            entry.view.restoreState(state) != null
        } catch (e: Throwable) {
            // State written by another WebView build can fail in any way; it must not crash the tab.
            Log.w(TAG, "Tab state not restored", e)
            false
        }
        if (state != null && !restored) stateStore.delete(entry.tabId)
        val waiting = queued.remove(entry.tabId)
        val navigation = waiting?.navigation
        var loadedFresh = false
        if (!restored && navigation !is Pending.Load) {
            // Only a web page loads by itself; a tab on the New tab page shows nothing until asked.
            val url = tabs.state.value.tab(entry.tabId)?.url
            if (url != null && isWebUrl(url)) {
                entry.view.loadUrl(url)
                loadedFresh = true
            }
        }
        when (navigation) {
            is Pending.Load -> entry.view.loadUrl(navigation.url)
            Pending.Reload -> if (restored && !loadedFresh) entry.view.reload()
            Pending.Back -> if (entry.view.canGoBack()) entry.view.goBack()
            Pending.Forward -> if (entry.view.canGoForward()) entry.view.goForward()
            null -> Unit
        }
        if (waiting?.clearHistory == true) entry.view.clearHistory()
    }

    /** Evicts the least recently used WebViews beyond the capacity, never the current tab's or [keep]. */
    private fun trim(keep: Set<String>) {
        val current = tabs.state.value.currentTabId
        val evicted = policy.evictions(if (current == null) keep else keep + current)
        if (evicted.isEmpty()) return
        evicted.forEach { tabId ->
            live[tabId]?.let { entry ->
                capture(entry)
                saveState(entry)
                destroy(entry)
                renew(tabId)
            }
        }
        Log.i(TAG, "Evicted ${evicted.size}, ${live.size} live, capacity ${policy.capacity}")
    }

    /** Hands [entry]'s state to the tab manager; a WebView with no page yet has none to hand over. */
    private fun saveState(entry: Entry) {
        if (entry.destroyed || entry.restoring) return
        val view = entry.view
        if (view.copyBackForwardList().size == 0) return
        val state = Bundle()
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAVE_STATE)) {
                WebViewCompat.saveState(view, state, MAX_STATE_BYTES, true)
            } else if (view.saveState(state) == null) {
                return
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Tab state not saved", e)
            return
        }
        if (!state.isEmpty) events.onStateSaved(entry.tabId, state)
    }

    /** Destroys [entry]'s WebView; page scripts still waiting on it are answered with null. */
    private fun destroy(entry: Entry) {
        if (entry.destroyed) return
        entry.destroyed = true
        live.remove(entry.tabId)
        policy.remove(entry.tabId)
        entry.restoreJob?.cancel()
        entry.captureJob?.cancel()
        entry.saveJob?.cancel()
        val waiting = entry.evaluations.toList()
        entry.evaluations.clear()
        waiting.forEach { it.resume(null) }
        fullscreen.exit(entry.tabId)
        val view = entry.view
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        entry.client.detach()
        entry.chrome.detach()
        view.webChromeClient = null
        view.setFindListener(null)
        view.setDownloadListener(null)
        view.onHitTarget = null
        view.destroy()
        // A pool another Activity replaced no longer speaks for the tabs: the new pool shows them now.
        if (engines.isAttached(this)) tabEvents.onWebViewDestroyed(entry.tabId)
    }

    /**
     * [view]'s renderer is gone: the WebView is destroyed and never used again. [RendererGoneAction.RESTORE]
     * makes a new one from the saved state once the tab is shown; [RendererGoneAction.SHOW_ERROR]
     * makes none until Try again.
     */
    private fun onRendererGone(tabId: String, view: WebView, action: RendererGoneAction, didCrash: Boolean) {
        live[tabId]?.takeIf { it.view === view }?.let(::destroy)
        if (action == RendererGoneAction.SHOW_ERROR) blocked += tabId
        if (!didCrash) {
            val before = policy.capacity
            policy.onRendererKilled()
            if (policy.capacity != before) Log.i(TAG, "Capacity ${policy.capacity} (was $before) after a renderer was killed")
        }
        renew(tabId)
    }

    /** Takes [entry]'s thumbnail when its page finished loading and it is laid out in a window. */
    private fun capture(entry: Entry) {
        if (entry.destroyed || entry.restoring) return
        val tab = tabs.state.value.tab(entry.tabId) ?: return
        if (tab.url == null) return
        val runtime = tabs.runtime(entry.tabId).value
        if (!runtime.pageLoaded || runtime.loadError != null) return
        drawThumbnail(entry.view)?.let { thumbnails.put(entry.tabId, it, tab.incognito) }
    }

    /** After a page finished loading: its thumbnail shortly, its state a moment later. */
    private fun scheduleAfterLoad(entry: Entry) {
        entry.captureJob?.cancel()
        entry.captureJob = scope.launch {
            delay(CAPTURE_DELAY_MS)
            capture(entry)
        }
        entry.saveJob?.cancel()
        entry.saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            if (tabs.runtime(entry.tabId).value.pageLoaded) saveState(entry)
        }
    }

    private fun readyEntry(tabId: String): Entry? = live[tabId]?.takeIf { !it.restoring && !it.destroyed }

    private fun queue(tabId: String): Queued = queued.getOrPut(tabId) { Queued() }

    private fun unblock(tabId: String) {
        if (blocked.remove(tabId)) renew(tabId)
    }

    private fun renew(tabId: String) {
        generations[tabId]?.value = if (tabId in blocked) null else ++lastGeneration
    }

    /** True when [view] is the current tab's WebView and a host shows it in the window. */
    private fun isShown(tabId: String, view: WebView): Boolean {
        val entry = live[tabId]?.takeIf { it.view === view && !it.destroyed } ?: return false
        return entry.hosts > 0 && view.isAttachedToWindow && tabs.state.value.currentTabId == tabId
    }

    /**
     * A frame in the Activity's window, the size of the screen but drawn outside it, where a tab
     * that is not shown loads its page and runs its scripts. Never focused, never read out.
     */
    private fun offscreenHost(): FrameLayout? {
        offscreenHost?.let { return it }
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val host = FrameLayout(activity).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            translationX = resources.displayMetrics.widthPixels * OFFSCREEN_SHIFT
        }
        // Below the app's content, so even a wrong offset could not put a page over it.
        content.addView(host, 0, ViewGroup.LayoutParams(MATCH, MATCH))
        offscreenHost = host
        return host
    }

    /** What the WebViews report, on its way to the tab manager; the pool schedules work on it. */
    private inner class PoolEvents : TabEventSink by tabEvents {
        override fun onPageStarted(tabId: String, url: String): Long {
            live[tabId]?.let { entry ->
                entry.captureJob?.cancel()
                entry.saveJob?.cancel()
            }
            return tabEvents.onPageStarted(tabId, url)
        }

        override fun onProgress(tabId: String, percent: Int) {
            tabEvents.onProgress(tabId, percent)
            if (percent >= FULL_PROGRESS) live[tabId]?.let(::scheduleAfterLoad)
        }
    }

    private companion object {
        const val TAG = "SkimWebViewPool"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        /** WebView keeps the state near this size, dropping the oldest history entries first. */
        const val MAX_STATE_BYTES = 256 * 1024
        const val CAPTURE_DELAY_MS = 500L
        const val SAVE_DELAY_MS = 1_000L
        const val FULL_PROGRESS = 100

        /** How long a page script may run before its result counts as none. */
        const val EVALUATE_TIMEOUT_MS = 10_000L

        /** Screen widths the off-screen host is moved by. */
        const val OFFSCREEN_SHIFT = 2f
    }
}

/**
 * The JavaScript timers of every WebView in the process: `pauseTimers` and `resumeTimers` act on
 * all WebViews at once, whichever WebView they are called on. Pools count as started from their
 * Activity's start to its stop; the timers pause when the last started pool stops and resume when
 * a pool starts again, so an Activity that stops while a newer one already shows pages never
 * freezes the newer one's pages.
 */
@MainThread
class WebViewTimers {
    private var startedPools = 0

    /** True while the timers are paused. */
    var paused = false
        private set

    /** A pool started; the timers resume through [anyView], one of its WebViews, when it has one. */
    fun poolStarted(anyView: WebView?) {
        startedPools++
        if (paused && anyView != null) resume(anyView)
    }

    /** A pool stopped; when it was the last started one the timers pause through [anyView]. */
    fun poolStopped(anyView: WebView?) {
        startedPools = (startedPools - 1).coerceAtLeast(0)
        if (startedPools == 0 && !paused && anyView != null) {
            anyView.pauseTimers()
            paused = true
        }
    }

    /** A pool made [view]: paused timers resume while some pool is started. */
    fun webViewCreated(view: WebView) {
        if (paused && startedPools > 0) resume(view)
    }

    private fun resume(view: WebView) {
        view.resumeTimers()
        paused = false
    }

    companion object {
        /** The one set of timers of the process. */
        val process = WebViewTimers()
    }
}
