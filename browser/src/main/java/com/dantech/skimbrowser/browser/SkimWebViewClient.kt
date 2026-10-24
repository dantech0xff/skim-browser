package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread

/**
 * The [WebViewClient] of one tab's WebView: reports the main frame's milestones to [events] in the
 * order the WebView calls them, decides where the page may navigate, and hands a WebView whose
 * renderer went away back to its pool.
 *
 * SSL errors, HTTP authentication and Safe Browsing keep the WebView's own handling: an invalid
 * certificate cancels the load, and Safe Browsing shows its interstitial.
 *
 * Once [detach]ed (the pool destroys the WebView), it reports nothing more and blocks navigation.
 */
// It does handle onRenderProcessGone (below). The webkit check reports every call to the
// WebViewClient constructor, and a Kotlin subclass must call it in its header.
@SuppressLint("MissingOnRenderProcessGone")
@MainThread
class SkimWebViewClient(
    private val tabId: String,
    /** True for an incognito tab, which hands fewer links to other apps. */
    private val incognito: Boolean,
    private val events: TabEventSink,
    private val activityHost: ActivityHost,
    /** Loads a URL in a tab through the tab manager, which accepts only http and https. */
    private val loadInTab: (tabId: String, url: String) -> Unit,
    /** Destroys [view], whose renderer is gone: it can never be used again. */
    private val onRendererGone: (view: WebView, action: RendererGoneAction, didCrash: Boolean) -> Unit,
) : WebViewClient() {

    private var detached = false

    /** The WebView is being destroyed: nothing it reports from now on reaches the tab. */
    fun detach() {
        detached = true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (detached || url == null) return
        events.onPageStarted(tabId, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (detached || url == null) return
        events.onVisitedHistory(tabId, url)
        events.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (detached || url == null) return
        events.onPageFinished(tabId, url)
        events.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        // A failed image or frame leaves the page usable; only the page itself failing is an error.
        if (detached || !request.isForMainFrame) return
        events.onLoadError(tabId, error.errorCode, error.description?.toString(), request.url.toString())
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (detached) return true
        // A frame inside the page navigates on its own; only the page's own navigation can leave it.
        if (!request.isForMainFrame) return false
        return when (val navigation = externalNavigation(request.url, request.hasGesture(), incognito, view.context.packageName)) {
            ExternalNav.InWebView -> false
            ExternalNav.Blocked -> true
            is ExternalNav.Launch -> {
                launch(navigation)
                true
            }
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        // Handled either way: returning false would crash (or kill) the whole app with the renderer.
        if (detached) return true
        val didCrash = detail.didCrash()
        val action = events.onRendererGone(tabId, didCrash)
        detached = true
        onRendererGone(view, action, didCrash)
        return true
    }

    private fun launch(navigation: ExternalNav.Launch) {
        try {
            activityHost.startExternal(navigation.intent)
        } catch (_: ActivityNotFoundException) {
            // No app takes it: an intent: URL may name a web page to show instead.
            navigation.fallbackUrl?.let { loadInTab(tabId, it) }
        } catch (_: SecurityException) {
            Log.w(TAG, "An app link was refused by the app it points to")
        }
    }

    private companion object {
        const val TAG = "SkimWebViewClient"
    }
}
