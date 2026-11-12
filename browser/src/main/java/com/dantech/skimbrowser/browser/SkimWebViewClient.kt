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
    /** The page [onPageStarted] reported. A main-frame intercept alone does not change it. */
    @Volatile private var documentUrl: String? = null
    /** A main frame that has been fetched and has not started, failed, or been abandoned. */
    @Volatile private var loadingUrl: String? = null
    private val blockedCount = AtomicInteger()
    private val loadingBlocked = AtomicInteger()
    private val documentGeneration = AtomicInteger()
    private val countPosted = AtomicBoolean(false)
    private val documentGate = Any()
    private val mainThread = Handler(Looper.getMainLooper())

    /** The WebView is being destroyed: nothing it reports from now on reaches the tab. */
    fun detach() {
        detached = true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        val early = synchronized(documentGate) {
            val carried = if (url != null && url == loadingUrl) loadingBlocked.get() else 0
            documentUrl = url
            loadingUrl = null
            loadingBlocked.set(0)
            blockedCount.set(carried)
            documentGeneration.incrementAndGet()
            carried
        }
        if (detached || url == null) return
        events.onPageStarted(tabId, url)
        if (early > 0) events.onShieldCount(tabId, early)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (detached) return null
        val url = request.url?.toString() ?: return null
        if (request.isForMainFrame) {
            noteMainFrame(url)
            return null
        }
        val document = synchronized(documentGate) { loadingUrl ?: documentUrl }
        if (!shield.blocks(document, url, incognito)) return null
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
        if (!request.isForMainFrame) return
        // A failed main frame did not become the page. The reported URL can be a redirect hop,
        // so the in-flight frame is dropped even when the strings differ.
        abandonLoad()
        if (detached) return
        events.onLoadError(tabId, error.errorCode, error.description?.toString(), request.url.toString())
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (detached) return true
        // A frame inside the page navigates on its own; only the page's own navigation can leave it.
        if (!request.isForMainFrame) return false
        return when (val navigation = externalNavigation(request.url, request.hasGesture(), incognito, view.context.packageName)) {
            ExternalNav.InWebView -> false
            ExternalNav.Blocked -> {
                abandonLoad()
                true
            }
            is ExternalNav.Launch -> {
                abandonLoad()
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

    /**
     * [url] was intercepted as a main frame and has not started. A reload intercepts the same URL
     * again, so that request is a new in-flight document. [onPageStarted] carries its blocked
     * count only when the URL matches, and a different page still starts at zero.
     */
    private fun noteMainFrame(url: String) {
        synchronized(documentGate) {
            loadingUrl = url
            loadingBlocked.set(0)
        }
    }

    /**
     * The in-flight main frame did not become the page. A download or error often reports the URL
     * after a redirect, which is not the URL that was intercepted, so this does not compare them.
     * The committed document is left as it is.
     */
    fun abandonLoad() {
        synchronized(documentGate) {
            loadingUrl = null
            loadingBlocked.set(0)
        }
    }

    private fun noteBlocked() {
        val pending = synchronized(documentGate) {
            if (loadingUrl != null) {
                loadingBlocked.incrementAndGet()
                null
            } else {
                blockedCount.incrementAndGet() to documentGeneration.get()
            }
        } ?: return
        val generation = pending.second
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
