package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dantech.skimbrowser.domain.certificate.CertificateAction
import com.dantech.skimbrowser.domain.certificate.CertificateWarning
import com.dantech.skimbrowser.domain.certificate.certificateFault
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.sameNavigation
import androidx.annotation.MainThread
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import com.dantech.skimbrowser.domain.shield.CosmeticFilters
import com.dantech.skimbrowser.domain.shield.CosmeticScript
import com.dantech.skimbrowser.domain.shield.ShieldChoices
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.sameNavigation
import java.io.ByteArrayInputStream
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The [WebViewClient] of one tab's WebView: reports the main frame's milestones to [events] in the
 * order the WebView calls them, decides where the page may navigate, and hands a WebView whose
 * renderer went away back to its pool.
 *
 * An invalid certificate cancels the load. `proceed` runs only when this session already allowed
 * that host and certificate. HTTP authentication and Safe Browsing keep the WebView's own handling,
 * and Safe Browsing shows its interstitial.
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
    /** True when this WebView supports [WebViewCompat.addDocumentStartJavaScript]. */
    private val documentStartSupported: Boolean = false,
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
    private val collapsePosted = AtomicBoolean(false)
    private val documentGate = Any()
    private val mainThread = Handler(Looper.getMainLooper())
    private val loadingCollapsed = LinkedHashSet<String>()
    private val collapsed = LinkedHashSet<String>()
    private var hideScript: ScriptHandler? = null

    /** The WebView is being destroyed: nothing it reports from now on reaches the tab. */
    fun detach() {
        detached = true
        removeHideScript()
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        val early = synchronized(documentGate) {
            val sameDocument = url != null && url == loadingUrl
            val carried = if (sameDocument) loadingBlocked.get() else 0
            val carriedHosts = if (carriesCollapse(url, loadingUrl)) LinkedHashSet(loadingCollapsed) else LinkedHashSet()
            documentUrl = url
            loadingUrl = null
            loadingBlocked.set(0)
            loadingCollapsed.clear()
            blockedCount.set(carried)
            collapsed.clear()
            collapsed.addAll(carriedHosts)
            documentGeneration.incrementAndGet()
            val collapseCss = if (carriedHosts.isEmpty()) null else CosmeticFilters.collapseBlockedCss(carriedHosts)
            carried to collapseCss
        }
        if (detached || url == null) return
        events.onPageStarted(tabId, url)
        if (early.first > 0) events.onShieldCount(tabId, early.first)
        injectHide(view, url)
        early.second?.let { inject(view, CosmeticScript.collapse(it)) }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (detached) return null
        val url = request.url?.toString() ?: return null
        if (request.isForMainFrame) {
            noteMainFrame(url)
            shield.warmCosmetic()
            return null
        }
        val document = synchronized(documentGate) { loadingUrl ?: documentUrl }
        if (!shield.blocks(document, url, incognito)) return null
        noteBlocked(view, url)
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
        injectHide(view, url)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        if (detached) {
            handler.cancel()
            return
        }
        val url = error.url?.takeIf { it.isNotBlank() }
        val certificate = error.certificate
        val host = url?.let { hostOf(it) }
        if (url == null || certificate == null || host == null) {
            handler.cancel()
            return
        }
        val fingerprint = certificateFingerprintOf(certificate)
        if (events.certificateAction(tabId, host, fingerprint) == CertificateAction.Proceed) {
            handler.proceed()
            return
        }
        handler.cancel()
        if (isPageCertificate(url)) {
            events.onCertificateError(
                tabId,
                CertificateWarning(url, host, fingerprint, certificateFault(error.primaryError)),
            )
        }
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
            ExternalNav.InWebView -> {
                armCosmetic(view, request.url.toString())
                false
            }
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
     * True when [url] is the page being loaded, or the page already showing when nothing else is
     * in flight. A request for another URL is a subresource and stays off the interstitial.
     */
    private fun isPageCertificate(url: String): Boolean {
        val (pending, shown) = synchronized(documentGate) { loadingUrl to documentUrl }
        if (pending != null) return sameNavigation(pending, url)
        if (shown == null) return true
        return sameNavigation(shown, url)
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
            loadingCollapsed.clear()
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
            loadingCollapsed.clear()
        }
    }

    private fun noteBlocked(view: WebView, requestUrl: String) {
        val host = hostOf(requestUrl)
        val pending = synchronized(documentGate) {
            if (host != null) rememberCollapsed(host)
            if (loadingUrl != null) {
                loadingBlocked.incrementAndGet()
                null
            } else {
                blockedCount.incrementAndGet() to documentGeneration.get()
            }
        } ?: return
        if (host != null) scheduleCollapse(view)
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

    /** Installs the document-start hide sheet for [url] when this WebView supports it. */
    fun armCosmetic(view: WebView, url: String) {
        if (!documentStartSupported || detached) return
        val css = shield.cosmeticCss(url, incognito)
        if (css == null) {
            applyWhenReady(view, url, arm = true)
            return
        }
        installDocumentStart(view, url, css)
    }

    private fun removeHideScript() {
        val script = hideScript ?: return
        hideScript = null
        try {
            script.remove()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cosmetic document-start script was not removed", e)
        }
    }

    private fun injectHide(view: WebView, url: String, wait: Boolean = true) {
        val css = shield.cosmeticCss(url, incognito)
        if (css == null) {
            if (wait) applyWhenReady(view, url, arm = false)
            return
        }
        val host = hostOf(url) ?: return
        if (css.isEmpty()) return
        inject(view, CosmeticScript.hide(host, css))
    }

    /**
     * The rules are still loading. [arm] installs the document-start sheet for [url] itself.
     * The sheet for the page on screen is separate, so a blank document cannot clear [url].
     */
    private fun applyWhenReady(view: WebView, url: String, arm: Boolean) {
        shield.whenCosmeticReady {
            val apply = Runnable {
                if (detached) return@Runnable
                if (arm && stillTheArmedPage(url)) {
                    installDocumentStart(view, url, shield.cosmeticCss(url, incognito))
                }
                val showing = documentUrl
                val injectUrl = if (showing != null && hostOf(showing) != null) showing else url
                if (hostOf(injectUrl) != null) injectHide(view, injectUrl, wait = false)
            }
            if (Looper.myLooper() == Looper.getMainLooper()) apply.run() else mainThread.post(apply)
        }
    }

    /**
     * The hide sheet is per host. A redirect that only changes the path still gets the sheet
     * that was armed for that host. A different host does not.
     */
    private fun stillTheArmedPage(url: String): Boolean = synchronized(documentGate) {
        val armedHost = hostOf(url) ?: return@synchronized false
        val live = loadingUrl ?: documentUrl
        val liveHost = live?.let(::hostOf) ?: return@synchronized true
        liveHost == armedHost
    }

    private fun installDocumentStart(view: WebView, url: String, css: String?) {
        if (!documentStartSupported || detached || css == null) return
        val host = hostOf(url) ?: return
        removeHideScript()
        if (css.isEmpty()) return
        try {
            hideScript = WebViewCompat.addDocumentStartJavaScript(view, CosmeticScript.hide(host, css), ALL_ORIGINS)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cosmetic rules were not installed at document start", e)
        }
    }

    /**
     * True when [started] is the in-flight main frame even if WebView spells the URL differently.
     * A fragment, a trailing slash, or a `www` prefix is the same page. Another path is not.
     */
    private fun carriesCollapse(started: String?, loading: String?): Boolean {
        if (started == null || loading == null) return false
        if (started == loading || sameNavigation(started, loading)) return true
        val startedHost = hostOf(started)
        val loadingHost = hostOf(loading)
        if (startedHost == null || startedHost != loadingHost) return false
        return pagePath(started) == pagePath(loading)
    }

    private fun pagePath(url: String): String {
        val noHash = url.substringBefore('#').trimEnd('/')
        val rest = noHash.substringAfter("://", noHash)
        val slash = rest.indexOf('/')
        return if (slash < 0) "" else rest.substring(slash)
    }

    /** [host] is recorded for the in-flight document or the one on screen. Callers hold [documentGate]. */
    private fun rememberCollapsed(host: String) {
        val target = if (loadingUrl != null) loadingCollapsed else collapsed
        if (target.size < CosmeticFilters.MAX_COLLAPSE_HOSTS || host in target) target.add(host)
    }

    private fun scheduleCollapse(view: WebView) {
        if (!collapsePosted.compareAndSet(false, true)) return
        mainThread.post {
            collapsePosted.set(false)
            if (detached) return@post
            val css = synchronized(documentGate) { CosmeticFilters.collapseBlockedCss(collapsed) }
            if (css.isNotEmpty()) inject(view, CosmeticScript.collapse(css))
        }
    }

    private fun inject(view: WebView, script: String) {
        if (detached || script.isEmpty()) return
        view.evaluateJavascript(script, null)
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
        private val ALL_ORIGINS = setOf("*")

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
