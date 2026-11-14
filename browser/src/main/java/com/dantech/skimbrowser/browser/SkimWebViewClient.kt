package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dantech.skimbrowser.domain.certificate.CertificateAction
import com.dantech.skimbrowser.domain.certificate.CertificateWarning
import com.dantech.skimbrowser.domain.certificate.certificateFault
import com.dantech.skimbrowser.domain.interrupt.InterruptKind
import com.dantech.skimbrowser.domain.interrupt.InterruptPolicy
import com.dantech.skimbrowser.domain.interrupt.InterruptRequest
import com.dantech.skimbrowser.domain.interrupt.InterruptVerdict
import com.dantech.skimbrowser.domain.interrupt.decideInterrupt
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.sameNavigation
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.webkit.SafeBrowsingResponseCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import com.dantech.skimbrowser.domain.shield.CosmeticFilters
import com.dantech.skimbrowser.domain.shield.CosmeticScript
import com.dantech.skimbrowser.domain.shield.ShieldChoices
import java.io.ByteArrayInputStream
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The [WebViewClient] of one tab's WebView: reports the main frame's milestones to [events] in the
 * order the WebView calls them, decides where the page may navigate, and hands a WebView whose
 * renderer went away back to its pool.
 *
 * An invalid certificate is shown by Skim instead of the WebView interstitial. Safe Browsing is
 * enabled on the WebView, and a hit is shown by Skim as well. HTTP authentication keeps the
 * WebView's own handling.
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
    private val safety: SafetyGate = SafetyGate.Off,
    private val navigations: TabNavigations = TabNavigations(),
    private val policy: () -> InterruptPolicy = { InterruptPolicy() },
    /** Monotonic clock for how long this document has gone without a touch. */
    private val now: () -> Long = SystemClock::elapsedRealtime,
    /** Destroys [view], whose renderer is gone: it can never be used again. */
    private val onRendererGone: (view: WebView, action: RendererGoneAction, didCrash: Boolean) -> Unit,
) : WebViewClientCompat() {

    @Volatile private var detached = false
    /** The load was stopped because Skim is showing its own warning. The error from stopping is ignored. */
    private var stoppedForWarning = false
    /** onPageStarted calls. A step back from a warning re-enters this method. */
    private var pageStartDepth = 0
    /** True while [history.pushState] is discarding a flagged forward entry. */
    private var droppingForward = false
    /** The next page start is the step back off the duplicate entry [droppingForward] added. */
    private var undoForwardDrop = false
    /** Main-frame Safe Browsing hits the person has not chosen to open. Forward must not return to them. */
    private val forwardThreats = LinkedHashSet<String>()
    /** The threat whose abort is still the warning on screen. Its visit and error are not a page. */
    private var showingThreat: String? = null
    /** The page [onPageStarted] reported. A main-frame intercept alone does not change it. */
    @Volatile private var documentUrl: String? = null
    /** A main frame that has been fetched and has not started, failed, or been abandoned. */
    @Volatile private var loadingUrl: String? = null
    /** A page certificate already cancelled. Other URLs stay subresources until a document starts. */
    private var refusedPageUrl: String? = null
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
    private var cookieScript: ScriptHandler? = null
    /** True after this document received a cookie stylesheet, so turning hiding off can remove it. */
    private var cookieStyleInjected = false
    private var documentStartedAt = 0L
    private var lastInteractionAt = NO_INTERACTION

    /** The WebView is being destroyed: nothing it reports from now on reaches the tab. */
    fun detach() {
        detached = true
        removeHideScript()
        removeCookieScript()
        safety.cancelSafeBrowsing(tabId)
    }

    /** A finger went down on this page. The odd-redirect check treats that as the person being here. */
    fun noteInteraction() {
        lastInteractionAt = now()
    }

    /**
     * Loads [url] because the person allowed a redirect or an app link that had been blocked.
     * The gesture is treated as present, so the checks that require one let it through.
     */
    fun openPermitted(view: WebView, url: String) {
        if (detached) return
        when (val navigation = externalNavigation(url.toUri(), hasGesture = true, incognito, view.context.packageName)) {
            ExternalNav.InWebView -> view.loadUrl(url)
            ExternalNav.Blocked -> Unit
            is ExternalNav.Launch -> launch(navigation)
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        pageStartDepth++
        try {
            onPageStartedInner(view, url)
        } finally {
            pageStartDepth--
        }
    }

    private fun onPageStartedInner(view: WebView, url: String?) {
        if (undoForwardDrop && url != null && safety.heuristic(url, incognito) == null && !isForwardThreat(url)) {
            undoForwardDrop = false
            return
        }
        // A start for some other navigation is ignored. Stopping here would abort the load
        // the person just asked for, not only the refused document.
        if (url != null && !navigations.acceptStart(tabId, url)) return
        if (!detached && url != null) {
            val hit = safety.heuristic(url, incognito)
            if (hit != null) {
                stoppedForWarning = true
                view.stopLoading()
                events.onSafetyWarning(tabId, hit)
                // Step back now so the stopped document is not left on screen. The warning stays
                // up over the previous page. With no history, the tab records this document and
                // Back to safety leaves it.
                if (view.canGoBack()) view.goBack() else events.onPageStarted(tabId, url)
                return
            }
        }
        // A nested start is the page goBack just restored. Keep dropping the flagged visit.
        if (pageStartDepth == 1) stoppedForWarning = false
        documentStartedAt = now()
        lastInteractionAt = NO_INTERACTION
        val early = synchronized(documentGate) {
            val sameDocument = url != null && url == loadingUrl
            val carried = if (sameDocument) loadingBlocked.get() else 0
            val carriedHosts = if (carriesCollapse(url, loadingUrl)) LinkedHashSet(loadingCollapsed) else LinkedHashSet()
            documentUrl = url
            loadingUrl = null
            refusedPageUrl = null
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
        cookieStyleInjected = false
        injectHide(view, url)
        injectCookie(view, url)
        early.second?.let { inject(view, CosmeticScript.collapse(it)) }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (detached) return null
        val url = request.url?.toString() ?: return null
        if (request.isForMainFrame) {
            if (request.isRedirect) navigations.follow(tabId, url)
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
        if ((stoppedForWarning && safety.heuristic(url, incognito) != null) || isShowingThreat(url) || !navigations.acceptVisit(tabId, url)) {
            reportNavigation(view)
            return
        }
        events.onVisitedHistory(tabId, url)
        reportNavigation(view)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        if (detached) return
        (view as? SkimWebView)?.applyContentScrollPadding()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        // Stopping the flagged document reports a finish. The page behind the warning still finishes.
        if (url != null && ((stoppedForWarning && safety.heuristic(url, incognito) != null) || isShowingThreat(url) || !navigations.matches(tabId, url))) return
        if (detached || url == null) return
        dropFlaggedForwardEntry(view)
        events.onPageFinished(tabId, url)
        reportNavigation(view)
        (view as? SkimWebView)?.applyContentScrollPadding()
        injectHide(view, url)
        injectCookie(view, url)
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
        if (!isPageCertificate(url)) return
        val repeat = synchronized(documentGate) {
            val repeat = refusedPageUrl?.let { sameNavigation(it, url) } == true
            refusedPageUrl = url
            repeat
        }
        // Cancel often never reaches onReceivedError, so the in-flight frame is dropped here.
        abandonLoad()
        if (repeat) return
        events.onCertificateError(
            tabId,
            CertificateWarning(url, host, fingerprint, certificateFault(error.primaryError)),
        )
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceErrorCompat) {
        // A failed image or frame leaves the page usable; only the page itself failing is an error.
        if (!request.isForMainFrame) return
        // Safe Browsing reports this when a resource is refused. The page warning is separate,
        // and a refused image or frame must not become a failed page.
        if (error.errorCode == WebViewClient.ERROR_UNSAFE_RESOURCE) return
        val failedUrl = request.url?.toString()
        // stopLoading reports an error for the flagged document. A different page is a real failure.
        if (failedUrl != null && (safety.heuristic(failedUrl, incognito) != null || isShowingThreat(failedUrl))) return
        if (stoppedForWarning && failedUrl == null) return
        if (failedUrl != null && !navigations.matches(tabId, failedUrl)) {
            // A stale abort of an address already left must not drop the document now loading.
            // The in-flight frame still has to be dropped, including when its URL is that frame.
            if (loadingUrl != null && (failedUrl == loadingUrl || sameNavigation(loadingUrl!!, failedUrl))) abandonLoad()
            return
        }
        // A failed main frame did not become the page. The reported URL can be a redirect hop,
        // so the in-flight frame is dropped even when the strings differ.
        abandonLoad()
        if (detached) return
        // The compat type says the description is present. A WebView can still pass null.
        val description = error.description as CharSequence?
        events.onLoadError(tabId, error.errorCode, description?.toString(), request.url.toString())
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (detached) return true
        // A frame inside the page navigates on its own; only the page's own navigation can leave it.
        if (!request.isForMainFrame) return false
        val url = request.url?.toString()
        if (url != null && request.isRedirect) navigations.follow(tabId, url)
        if (url != null) {
            val hit = safety.heuristic(url, incognito)
            if (hit != null) {
                abandonLoad()
                events.onSafetyWarning(tabId, hit)
                return true
            }
            // isRedirect is a server hop. Those stay on the navigation tracker and are not odd redirects.
            if (!request.isRedirect &&
                decideInterrupt(interruptRequest(url, request.hasGesture()), policy()) == InterruptVerdict.BLOCK
            ) {
                abandonLoad()
                events.onRedirectBlocked(tabId, url)
                return true
            }
        }
        return when (val navigation = externalNavigation(request.url, request.hasGesture(), incognito, view.context.packageName)) {
            ExternalNav.InWebView -> {
                if (url != null && !request.isRedirect) onNavigate(url)
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

    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponseCompat,
    ) {
        if (detached) {
            callback.backToSafety(false)
            return
        }
        if (!request.isForMainFrame) {
            // Cancel the image, script, or frame. This is not the interstitial button, which
            // leaves the page: the callback only aborts this resource, and Skim shows no warning.
            callback.backToSafety(false)
            return
        }
        val url = request.url?.toString().orEmpty()
        val host = hostOf(url)
        if (host == null || !navigations.matches(tabId, url)) {
            callback.backToSafety(false)
            return
        }
        val warning = safety.offerSafeBrowsing(
            tabId = tabId,
            url = url,
            host = host,
            threat = safeBrowsingThreat(threatType),
            incognito = incognito,
        ) { proceed ->
            // false: do not send a separate "the person proceeded" report. The lookup already
            // happened inside Android System WebView.
            if (proceed) {
                forwardThreats.removeAll { sameNavigation(it, url) }
                if (isShowingThreat(url)) showingThreat = null
                callback.proceed(false)
            } else {
                showingThreat = null
                callback.backToSafety(false)
            }
        }
        if (warning != null) {
            forwardThreats.add(url)
            showingThreat = url
            events.onSafetyWarning(tabId, warning)
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
     * True when [url] is the page being loaded, the page already refused, or the page showing
     * when nothing else is in flight. Before any of those, the first error is the page. A
     * different URL is a subresource and stays off the interstitial.
     */
    private fun isPageCertificate(url: String): Boolean {
        val (pending, shown, refused) = synchronized(documentGate) { Triple(loadingUrl, documentUrl, refusedPageUrl) }
        if (pending != null) return sameNavigation(pending, url)
        if (refused != null) return sameNavigation(refused, url)
        if (shown == null) return true
        return sameNavigation(shown, url)
    }

    /**
     * [url] was intercepted as a main frame and has not started. A reload intercepts the same URL
     * again, so that request is a new in-flight document. [onPageStarted] carries its blocked
     * count only when the URL matches, and a different page still starts at zero.
     *
     * A new main-frame fetch is a new attempt, including a retry of a URL whose certificate was
     * just refused. Extra SSL callbacks for that same attempt do not intercept the main frame again.
     */
    /** Reports back/forward, hiding Forward when it would open a flagged page. */
    private fun reportNavigation(view: WebView) {
        dropFlaggedForwardEntry(view)
        events.onNavigationState(tabId, view.canGoBack(), view.canGoForward() && flaggedForwardUrl(view) == null)
    }

    /**
     * goBack leaves the flagged document as the forward entry, and a later Forward can paint it
     * before [onPageStarted] runs. pushState discards forward entries. It also adds a copy of the
     * current page, and the callback steps back onto the original entry.
     */
    private fun dropFlaggedForwardEntry(view: WebView) {
        if (droppingForward || detached || flaggedForwardUrl(view) == null) return
        val current = view.url
        droppingForward = true
        try {
            view.evaluateJavascript(DROP_FORWARD_HISTORY) { value ->
                droppingForward = false
                if (value?.contains("ok") != true) return@evaluateJavascript
                if (detached || view.url != current || !view.canGoBack()) return@evaluateJavascript
                undoForwardDrop = true
                view.goBack()
            }
        } catch (e: RuntimeException) {
            droppingForward = false
            Log.w(TAG, "The flagged forward entry stayed in history", e)
        }
    }

    private fun flaggedForwardUrl(view: WebView): String? {
        val list = view.copyBackForwardList()
        val next = list.currentIndex + 1
        if (next < 0 || next >= list.size) return null
        val url = list.getItemAtIndex(next)?.url ?: return null
        return url.takeIf { safety.heuristic(it, incognito) != null || isForwardThreat(it) }
    }

    /** [url] is the navigation the person or the app just started. */
    fun onNavigate(url: String) {
        navigations.prepare(tabId, url)
        showingThreat = null
        forwardThreats.removeAll { sameNavigation(it, url) }
    }

    /** Warnings are off. A threat that was paused loads; every other tab keeps its page. */
    fun onWarningsDisabled() {
        val threat = showingThreat
        showingThreat = null
        forwardThreats.clear()
        if (threat != null) navigations.begin(tabId, threat) else navigations.bump(tabId)
    }

    private fun isShowingThreat(url: String): Boolean = showingThreat?.let { sameNavigation(it, url) } == true

    private fun isForwardThreat(url: String): Boolean = forwardThreats.any { sameNavigation(it, url) }

    private fun noteMainFrame(url: String) {
        synchronized(documentGate) {
            loadingUrl = url
            loadingBlocked.set(0)
            loadingCollapsed.clear()
            refusedPageUrl = null
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
            shield.whenCosmeticReady {
                val apply = Runnable {
                    if (detached || !stillTheArmedPage(url)) return@Runnable
                    installDocumentStart(view, url, shield.cosmeticCss(url, incognito))
                }
                if (Looper.myLooper() == Looper.getMainLooper()) apply.run() else mainThread.post(apply)
            }
        } else {
            installDocumentStart(view, url, css)
        }
        val cookie = shield.cookieCss(url, incognito)
        if (cookie == null) {
            shield.whenCookieReady {
                val apply = Runnable {
                    if (detached || !stillTheArmedPage(url)) return@Runnable
                    installCookieDocumentStart(view, url, shield.cookieCss(url, incognito))
                }
                if (Looper.myLooper() == Looper.getMainLooper()) apply.run() else mainThread.post(apply)
            }
        } else {
            installCookieDocumentStart(view, url, cookie)
        }
    }

    /** Applies the current cookie choice to the document this client is showing. */
    fun refreshCookies(view: WebView) {
        if (detached) return
        val url = synchronized(documentGate) { documentUrl ?: loadingUrl } ?: return
        injectCookie(view, url)
        val css = shield.cookieCss(url, incognito)
        if (css != null) installCookieDocumentStart(view, url, css)
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

    private fun injectCookie(view: WebView, url: String, wait: Boolean = true) {
        val css = shield.cookieCss(url, incognito)
        if (css == null) {
            if (wait) {
                shield.whenCookieReady {
                    val apply = Runnable {
                        if (!detached) injectCookie(view, url, wait = false)
                    }
                    if (Looper.myLooper() == Looper.getMainLooper()) apply.run() else mainThread.post(apply)
                }
            }
            return
        }
        val host = hostOf(url) ?: return
        if (css.isEmpty()) {
            removeCookieScript()
            if (cookieStyleInjected) {
                cookieStyleInjected = false
                inject(view, CosmeticScript.cookie(host, ""))
            }
            return
        }
        cookieStyleInjected = true
        inject(view, CosmeticScript.cookie(host, css))
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
                    installCookieDocumentStart(view, url, shield.cookieCss(url, incognito))
                }
                val showing = documentUrl
                val injectUrl = if (showing != null && hostOf(showing) != null) showing else url
                if (hostOf(injectUrl) != null) {
                    injectHide(view, injectUrl, wait = false)
                    injectCookie(view, injectUrl, wait = false)
                }
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

    private fun installCookieDocumentStart(view: WebView, url: String, css: String?) {
        if (!documentStartSupported || detached || css == null) return
        val host = hostOf(url) ?: return
        removeCookieScript()
        if (css.isEmpty()) return
        cookieStyleInjected = true
        try {
            cookieScript = WebViewCompat.addDocumentStartJavaScript(view, CosmeticScript.cookie(host, css), ALL_ORIGINS)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cookie banner rules were not installed at document start", e)
        }
    }

    private fun removeCookieScript() {
        val script = cookieScript ?: return
        cookieScript = null
        try {
            script.remove()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cookie document-start script was not removed", e)
        }
    }

    private fun interruptRequest(target: String, hasGesture: Boolean): InterruptRequest {
        val source = synchronized(documentGate) { documentUrl }
        val since = if (lastInteractionAt == NO_INTERACTION) null else (now() - lastInteractionAt).coerceAtLeast(0)
        return InterruptRequest(
            kind = InterruptKind.TOP_FRAME,
            sourceUrl = source,
            targetUrl = target,
            hasGesture = hasGesture,
            isMainFrame = true,
            millisSinceInteraction = since,
        )
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
        const val NO_INTERACTION = -1L

        /** Discards forward history. The callback then steps back off the copy this adds. */
        const val DROP_FORWARD_HISTORY =
            "(function(){try{history.pushState(history.state,'',location.href);return 'ok'}catch(e){return 'no'}})()"

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
