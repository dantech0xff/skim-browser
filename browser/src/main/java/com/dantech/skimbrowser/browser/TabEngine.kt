package com.dantech.skimbrowser.browser

import android.webkit.WebView
import androidx.annotation.MainThread

/**
 * Commands to the tabs' WebViews, implemented by the Activity's WebView pool and plugged into
 * [TabEngineBridge] while the Activity exists; [TabManager] reaches it there. Every call is made on
 * the main thread.
 *
 * A tab without a live WebView keeps [load] and [reload] for the WebView created for it next (it
 * restores the saved state first, so the back history survives); the other commands do nothing.
 */
@MainThread
interface TabEngine {
    fun load(tabId: String, url: String)

    /** Goes back in the tab's history; false when it has no live WebView or nothing to go back to. */
    fun goBack(tabId: String): Boolean

    fun goForward(tabId: String)

    fun reload(tabId: String)

    /** Switches the desktop user agent and reloads. */
    fun setDesktop(tabId: String, on: Boolean)

    /** Highlights every match of [q]; results come back through [TabEventSink.onFindResult]. */
    fun find(tabId: String, q: String)

    fun findNext(tabId: String, forward: Boolean)

    fun clearFind(tabId: String)

    /** Hands the tab's WebView state to [TabEventSink.onStateSaved], then destroys the WebView. */
    fun saveAndDestroy(tabId: String)

    /** Clears the back and forward history of the tab's live WebView. */
    fun clearHistory(tabId: String)

    /**
     * Makes sure the tab has a live WebView. When it has none, one is created and loads the tab's
     * page; with [offscreen] it is attached off screen, so a tab that is not shown can still load.
     */
    fun ensureWebView(tabId: String, offscreen: Boolean)

    /**
     * Runs [script] in the tab's page and returns its result as JSON, or null when the tab has no
     * live WebView. Implementations call `evaluateJavascript` before their first suspension, so
     * checks the caller made on the main thread right before still hold for the page that runs it.
     */
    suspend fun evaluate(tabId: String, script: String): String?

    /**
     * Runs [block] on a WebView of [profile] (null for the default profile) that shows nothing and
     * is destroyed right after; the profile is set before anything else touches the WebView.
     */
    fun withTemporaryWebView(profile: String?, block: (WebView) -> Unit)
}
