package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import com.dantech.skimbrowser.domain.shield.ShieldChoices
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
    /** Ad and tracker hosts. [ShieldChoices.AllowAll] blocks nothing. */
    private val shield: ShieldChoices = ShieldChoices.AllowAll,
    /** Destroys [view], whose renderer is gone: it can never be used again. */
    private val onRendererGone: (view: WebView, action: RendererGoneAction, didCrash: Boolean) -> Unit,
) : WebViewClient() {

    @Volatile private var detached = false
    /** The main frame currently being loaded. Subresources are often intercepted before [onPageStarted]. */
    @Volatile private var documentUrl: String? = null
    private val blockedCount = AtomicInteger()
    private val documentGeneration = AtomicInteger()
    private val countPosted = AtomicBoolean(false)
    /** True once a main-frame intercept has started a load that [onPageStarted] has not reported yet. */
    private val countForPendingLoad = AtomicBoolean(false)
    private val documentGate = Any()
    private val mainThread = Handler(Looper.getMainLooper())

    /** The WebView is being destroyed: nothing it reports from now on reaches the tab. */
    fun detach() {
        detached = true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        val continued = synchronized(documentGate) {
            val pending = countForPendingLoad.getAndSet(false)
            if (!pending) {
                documentUrl = url
                blockedCount.set(0)
                documentGeneration.incrementAndGet()
            } else if (url != null) {
                // The main frame was intercepted first and may already have been counted.
                documentUrl = url
            }
            pending
        }
        if (detached || url == null) return
        events.onPageStarted(tabId, url)
        val count = blockedCount.get()
        if (continued && count > 0) events.onShieldCount(tabId, count)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (detached) return null
        val url = request.url?.toString() ?: return null
        if (request.isForMainFrame) {
            beginLoad(url)
            return null
        }
        if (!shield.blocks(documentUrl, url, incognito)) return null
        noteBlocked()
        return emptyResponse()
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (detached || url == null) return
        events.onVisitedHistory(tabId, url)
        events.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        if (detached) return
        (view as? SkimWebView)?.applyContentScrollPadding()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (detached || url == null) return
        events.onPageFinished(tabId, url)
        events.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
        (view as? SkimWebView)?.applyContentScrollPadding()
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

    /** A main-frame request is the document for the subresources that follow it, including ones that arrive before [onPageStarted]. */
    private fun beginLoad(url: String) {
        synchronized(documentGate) {
            documentUrl = url
            blockedCount.set(0)
            documentGeneration.incrementAndGet()
            countForPendingLoad.set(true)
        }
    }

    private fun noteBlocked() {
        blockedCount.incrementAndGet()
        val generation = documentGeneration.get()
        if (!countPosted.compareAndSet(false, true)) return
        mainThread.post {
            countPosted.set(false)
            if (detached) return@post
            val count = blockedCount.get()
            // A newer document may have blocked requests while this post waited. The counter was
            // reset with the document, so a non-zero count belongs to the page now showing.
            if (documentGeneration.get() != generation && count == 0) return@post
            events.onShieldCount(tabId, count)
        }
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

        fun emptyResponse(): WebResourceResponse = WebResourceResponse(
            "text/plain",
            "utf-8",
            200,
            "OK",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
    }
}
