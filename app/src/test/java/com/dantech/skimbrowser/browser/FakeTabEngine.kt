package com.dantech.skimbrowser.browser

import android.os.Bundle
import android.webkit.WebView

/**
 * A [TabEngine] without WebViews. It records every command, keeps track of which tabs have a live
 * WebView, and plays to [sink] the reports a real WebView sends, so a test can take a tab through
 * page loads, same-document navigations, errors and renderer crashes.
 */
class FakeTabEngine(
    private val sink: TabEventSink = IgnoredEvents,
    /** Makes the WebView a [withTemporaryWebView] block runs on; without it the block is not run. */
    private val webViewFactory: (() -> WebView)? = null,
) : TabEngine {

    /** A command the engine received. */
    sealed interface Call {
        data class Load(val tabId: String, val url: String) : Call

        data class GoBack(val tabId: String) : Call

        data class GoForward(val tabId: String) : Call

        data class Reload(val tabId: String) : Call

        data class SetDesktop(val tabId: String, val on: Boolean) : Call

        data class Find(val tabId: String, val query: String) : Call

        data class FindNext(val tabId: String, val forward: Boolean) : Call

        data class ClearFind(val tabId: String) : Call

        data class SaveAndDestroy(val tabId: String) : Call

        data class ClearHistory(val tabId: String) : Call

        data class EnsureWebView(val tabId: String, val offscreen: Boolean) : Call

        data class Evaluate(val tabId: String, val script: String) : Call

        data class TemporaryWebView(val profile: String?) : Call
    }

    val calls = mutableListOf<Call>()

    /** The tabs that have a live WebView. */
    val live = linkedSetOf<String>()

    /** The WebViews made for [withTemporaryWebView], each destroyed after its block. */
    val temporaryWebViews = mutableListOf<WebView>()

    /** What [goBack] answers for a live tab. */
    var canGoBack = true

    /** Answers [evaluate] for a tab with a live WebView. */
    var evaluator: suspend (tabId: String, script: String) -> String? = { _, _ -> null }

    /** Runs when [ensureWebView] creates a WebView, standing for the load the new WebView starts. */
    var onWebViewCreated: (tabId: String) -> Unit = {}

    inline fun <reified T : Call> callsOf(): List<T> = calls.filterIsInstance<T>()

    /**
     * A cross-document load of [url] in a live WebView, reported the way the WebView does: page
     * started on commit, the visit, the title when there is one, then finished unless [finish] is
     * false. Returns the load nonce.
     */
    fun openPage(tabId: String, url: String, title: String? = null, finish: Boolean = true): Long {
        live += tabId
        val nonce = sink.onPageStarted(tabId, url)
        sink.onVisitedHistory(tabId, url)
        title?.let { sink.onTitle(tabId, it) }
        if (finish) finishPage(tabId, url)
        return nonce
    }

    fun finishPage(tabId: String, url: String) {
        sink.onProgress(tabId, 100)
        sink.onPageFinished(tabId, url)
    }

    /** `history.pushState` or a fragment change: a visit without a new document. */
    fun navigateInDocument(tabId: String, url: String) {
        sink.onVisitedHistory(tabId, url)
    }

    /** The WebView says whether its history has a page before and after the current one. */
    fun reportHistory(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        sink.onNavigationState(tabId, canGoBack, canGoForward)
    }

    /**
     * A failed main-frame load of [url], in the WebView's order: page started, the error, page
     * finished, then the visit of the error page it commits.
     */
    fun failPage(tabId: String, url: String, errorCode: Int, description: String?): Long {
        live += tabId
        val nonce = sink.onPageStarted(tabId, url)
        sink.onLoadError(tabId, errorCode, description, url)
        sink.onPageFinished(tabId, url)
        sink.onVisitedHistory(tabId, url)
        return nonce
    }

    /** The tab's renderer went away; the WebView is gone whatever the answer. */
    fun loseRenderer(tabId: String, didCrash: Boolean = true): RendererGoneAction {
        live.remove(tabId)
        return sink.onRendererGone(tabId, didCrash)
    }

    /** The pool evicted the tab's WebView after saving its state. */
    fun evict(tabId: String) {
        if (live.remove(tabId)) {
            sink.onStateSaved(tabId, stateOf(tabId))
            sink.onWebViewDestroyed(tabId)
        }
    }

    override fun load(tabId: String, url: String) {
        calls += Call.Load(tabId, url)
    }

    override fun goBack(tabId: String): Boolean {
        calls += Call.GoBack(tabId)
        return tabId in live && canGoBack
    }

    override fun goForward(tabId: String) {
        calls += Call.GoForward(tabId)
    }

    override fun reload(tabId: String) {
        calls += Call.Reload(tabId)
    }

    override fun setDesktop(tabId: String, on: Boolean) {
        calls += Call.SetDesktop(tabId, on)
    }

    override fun find(tabId: String, q: String) {
        calls += Call.Find(tabId, q)
    }

    override fun findNext(tabId: String, forward: Boolean) {
        calls += Call.FindNext(tabId, forward)
    }

    override fun clearFind(tabId: String) {
        calls += Call.ClearFind(tabId)
    }

    override fun saveAndDestroy(tabId: String) {
        calls += Call.SaveAndDestroy(tabId)
        evict(tabId)
    }

    override fun clearHistory(tabId: String) {
        calls += Call.ClearHistory(tabId)
    }

    override fun ensureWebView(tabId: String, offscreen: Boolean) {
        calls += Call.EnsureWebView(tabId, offscreen)
        if (live.add(tabId)) onWebViewCreated(tabId)
    }

    override suspend fun evaluate(tabId: String, script: String): String? {
        calls += Call.Evaluate(tabId, script)
        return if (tabId in live) evaluator(tabId, script) else null
    }

    override fun withTemporaryWebView(profile: String?, block: (WebView) -> Unit) {
        calls += Call.TemporaryWebView(profile)
        val webView = webViewFactory?.invoke() ?: return
        try {
            block(webView)
        } finally {
            webView.destroy()
            temporaryWebViews += webView
        }
    }

    /** A sink for tests that use the engine without a tab manager. */
    object IgnoredEvents : TabEventSink {
        override fun onPageStarted(tabId: String, url: String) = 0L

        override fun onProgress(tabId: String, percent: Int) = Unit

        override fun onTitle(tabId: String, title: String) = Unit

        override fun onVisitedHistory(tabId: String, url: String) = Unit

        override fun onPageFinished(tabId: String, url: String) = Unit

        override fun onLoadError(tabId: String, errorCode: Int, description: String?, url: String) = Unit

        override fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) = Unit

        override fun onFindResult(tabId: String, activeMatchIndex: Int, matchCount: Int) = Unit

        override fun onRendererGone(tabId: String, didCrash: Boolean) = RendererGoneAction.RESTORE

        override fun onHitTarget(tabId: String, target: HitTarget) = Unit

        override fun onPopupRequested(openerTabId: String): String? = null

        override fun onPopupClosed(popupTabId: String) = Unit

        override fun onStateSaved(tabId: String, state: Bundle) = Unit

        override fun onWebViewDestroyed(tabId: String) = Unit
    }

    companion object {
        const val STATE_KEY = "fake-webview-state"

        /** The state the fake hands over for [tabId]. */
        fun stateOf(tabId: String): Bundle = Bundle().apply { putString(STATE_KEY, "history of $tabId") }
    }
}
