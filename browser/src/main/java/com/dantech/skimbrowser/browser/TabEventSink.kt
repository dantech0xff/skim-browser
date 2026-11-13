package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.certificate.CertificateAction
import com.dantech.skimbrowser.domain.certificate.CertificateWarning
import com.dantech.skimbrowser.domain.phishing.SafetyWarning
import com.dantech.skimbrowser.domain.tab.HitTarget

import android.os.Bundle
import androidx.annotation.MainThread

/**
 * What the WebView layer (the pool and the WebView clients) reports about the tabs, on the main
 * thread and for the main frame only. [TabManager.events] implements it. Reports about a tab that
 * is not open (closed, gone for good or unknown) are ignored, except [onStateSaved] for a tab whose
 * close can still be undone.
 */
@MainThread
interface TabEventSink {
    /** A new document committed (`onPageStarted`); returns the nonce that identifies it. */
    fun onPageStarted(tabId: String, url: String): Long

    /** `onProgressChanged`, from 0 to 100. */
    fun onProgress(tabId: String, percent: Int)

    /** `onReceivedTitle`. */
    fun onTitle(tabId: String, title: String)

    /** `doUpdateVisitedHistory`: a navigation of either kind committed. */
    fun onVisitedHistory(tabId: String, url: String)

    /** `onPageFinished`. */
    fun onPageFinished(tabId: String, url: String)

    /** `onReceivedError` for the main frame, with the error's code and description. */
    fun onLoadError(tabId: String, errorCode: Int, description: String?, url: String)

    /** The WebView's back and forward state after a navigation. */
    fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean)

    /** The find listener's result; [activeMatchIndex] counts from 0. */
    fun onFindResult(tabId: String, activeMatchIndex: Int, matchCount: Int)

    /**
     * `onRenderProcessGone` for the tab's WebView, which the pool destroys either way. The answer
     * says whether to restore the tab from its saved state when it is shown, or to show the error.
     */
    fun onRendererGone(tabId: String, didCrash: Boolean): RendererGoneAction

    /** A long-press on a link or an image. */
    fun onHitTarget(tabId: String, target: HitTarget)

    /**
     * The page of [openerTabId] opened a window after a user gesture. Returns the id of the tab
     * that shows it, or null to refuse the window.
     */
    fun onPopupRequested(openerTabId: String): String?

    /** The page of a popup tab called `window.close()`. */
    fun onPopupClosed(popupTabId: String)

    /**
     * The page tried to open a window without a user gesture, and popup blocking is on for this
     * site. The window is held until [SkimChromeClient.openHeldWindow] or cancelled.
     */
    fun onPopupBlocked(tabId: String, holdId: Long)

    fun onRedirectBlocked(tabId: String, url: String)

    /** The WebView state of the tab was saved, before eviction, on stop or before a close. */
    fun onStateSaved(tabId: String, state: Bundle)

    /** The pool destroyed the tab's WebView; the next one restores the saved state. */
    fun onWebViewDestroyed(tabId: String)

    /**
     * How many requests the shield has answered empty for the current document. Called on the main
     * thread. [onPageStarted] resets the count.
     */
    fun onShieldCount(tabId: String, count: Int)

    /**
     * Whether [tabId] may continue a load whose certificate is [fingerprint] for [host].
     * [CertificateAction.Proceed] only after the person already chose that pair in this session.
     * Incognito tabs answer from their own list.
     */
    fun certificateAction(tabId: String, host: String, fingerprint: String): CertificateAction

    /** A main-frame certificate failure. The load was already cancelled. */
    fun onCertificateError(tabId: String, warning: CertificateWarning)

    /** A scam warning for the tab, or null once the navigation is allowed or abandoned. */
    fun onSafetyWarning(tabId: String, warning: SafetyWarning?)
}

/** What happens to a tab whose renderer went away. */
enum class RendererGoneAction {
    /** Recreate the WebView from the saved state when the tab is shown. */
    RESTORE,

    /** The tab crashed too often: keep it without a WebView and show the error until Try again. */
    SHOW_ERROR,
}
