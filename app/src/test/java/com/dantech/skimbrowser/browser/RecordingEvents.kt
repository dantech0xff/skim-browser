package com.dantech.skimbrowser.browser

import android.os.Bundle

/** A [TabEventSink] that records every report in order and answers as a test sets it up. */
class RecordingEvents : TabEventSink {

    /** One report, as the WebView layer made it. */
    sealed interface Event {
        data class PageStarted(val tabId: String, val url: String) : Event

        data class Progress(val tabId: String, val percent: Int) : Event

        data class Title(val tabId: String, val title: String) : Event

        data class VisitedHistory(val tabId: String, val url: String) : Event

        data class PageFinished(val tabId: String, val url: String) : Event

        data class LoadError(val tabId: String, val errorCode: Int, val description: String?, val url: String) : Event

        data class NavigationState(val tabId: String, val canGoBack: Boolean, val canGoForward: Boolean) : Event

        data class FindResult(val tabId: String, val activeMatchIndex: Int, val matchCount: Int) : Event

        data class RendererGone(val tabId: String, val didCrash: Boolean) : Event

        data class Hit(val tabId: String, val target: HitTarget) : Event

        data class PopupRequested(val openerTabId: String) : Event

        data class PopupClosed(val popupTabId: String) : Event

        data class StateSaved(val tabId: String, val state: Bundle) : Event

        data class WebViewDestroyed(val tabId: String) : Event
    }

    val events = mutableListOf<Event>()

    /** What [onRendererGone] answers. */
    var rendererGoneAction = RendererGoneAction.RESTORE

    /** What [onPopupRequested] answers: the popup tab's id, or null to refuse the window. */
    var popupTabId: String? = "popup-tab"

    inline fun <reified T : Event> eventsOf(): List<T> = events.filterIsInstance<T>()

    override fun onPageStarted(tabId: String, url: String): Long {
        events += Event.PageStarted(tabId, url)
        return events.size.toLong()
    }

    override fun onProgress(tabId: String, percent: Int) {
        events += Event.Progress(tabId, percent)
    }

    override fun onTitle(tabId: String, title: String) {
        events += Event.Title(tabId, title)
    }

    override fun onVisitedHistory(tabId: String, url: String) {
        events += Event.VisitedHistory(tabId, url)
    }

    override fun onPageFinished(tabId: String, url: String) {
        events += Event.PageFinished(tabId, url)
    }

    override fun onLoadError(tabId: String, errorCode: Int, description: String?, url: String) {
        events += Event.LoadError(tabId, errorCode, description, url)
    }

    override fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        events += Event.NavigationState(tabId, canGoBack, canGoForward)
    }

    override fun onFindResult(tabId: String, activeMatchIndex: Int, matchCount: Int) {
        events += Event.FindResult(tabId, activeMatchIndex, matchCount)
    }

    override fun onRendererGone(tabId: String, didCrash: Boolean): RendererGoneAction {
        events += Event.RendererGone(tabId, didCrash)
        return rendererGoneAction
    }

    override fun onHitTarget(tabId: String, target: HitTarget) {
        events += Event.Hit(tabId, target)
    }

    override fun onPopupRequested(openerTabId: String): String? {
        events += Event.PopupRequested(openerTabId)
        return popupTabId
    }

    override fun onPopupClosed(popupTabId: String) {
        events += Event.PopupClosed(popupTabId)
    }

    override fun onStateSaved(tabId: String, state: Bundle) {
        events += Event.StateSaved(tabId, state)
    }

    override fun onWebViewDestroyed(tabId: String) {
        events += Event.WebViewDestroyed(tabId)
    }
}
