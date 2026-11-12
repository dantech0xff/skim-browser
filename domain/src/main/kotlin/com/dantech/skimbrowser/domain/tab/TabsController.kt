package com.dantech.skimbrowser.domain.tab

import com.dantech.skimbrowser.domain.page.HighlightRequest
import com.dantech.skimbrowser.domain.page.PageContent
import kotlinx.coroutines.flow.StateFlow

/**
 * The open tabs. ViewModels call this port. The browser layer implements it and keeps Android
 * types (WebView, Bundle, clocks, logs) on that side.
 *
 * [newTab] and [applyLayout] are extensions so the implementation can keep defaulted commands of
 * the same name. They forward to [openNewTab] and [replaceLayout].
 */
interface TabsController {
    val state: StateFlow<TabsState>

    /** The live state of [tabId]'s page; a tab that is not open reads as idle. */
    fun runtime(tabId: String): StateFlow<TabRuntime>

    fun openNewTab(
        incognito: Boolean,
        url: String?,
        background: Boolean,
        openedByExternalApp: Boolean,
        closeOnBack: Boolean,
        openerTabId: String?,
    ): String

    /** Makes [tabId] the current tab. */
    fun select(tabId: String)

    /** Loads [url] in [tabId]. A URL that is not http or https is ignored. An http URL loads as https. */
    fun load(tabId: String, url: String)

    /** Loads the http page kept after an https upgrade. Does nothing when this load has no fallback. */
    fun loadHttpFallback(tabId: String) {}

    /**
     * Drops the certificate warning on [tabId]. A page that is still showing stays. When the failed
     * page is the first page of a tab another app opened, or of a tab opened for a screen, returns
     * true and leaves that tab open so the screen can close it the way Back does. Any other first
     * page returns to the New tab page, and this returns false.
     */
    fun leaveUnsafeCertificate(tabId: String): Boolean = false

    /** Remembers the warned certificate for this session and loads that page again. */
    fun proceedDespiteCertificate(tabId: String) {}

    /**
     * Leaves a scam warning. Safe Browsing goes back in the WebView. A heuristic warning that
     * never committed returns a tab with no page to the New tab page.
     */
    fun leaveUnsafePage(tabId: String) {}

    /** Opens the warned address anyway. The host stays allowed until the process ends. */
    fun proceedUnsafe(tabId: String) {}

    /** Drops every scam warning. Used when the person turns warnings off. */
    fun clearSafetyWarnings() {}

    /** Back on the first page of a tab started from the New tab page: the tab shows it again. */
    fun returnToNewTabPage(tabId: String)

    fun goBack(tabId: String): Boolean

    fun goForward(tabId: String)

    fun reload(tabId: String)

    fun setDesktopSite(tabId: String, on: Boolean)

    fun find(tabId: String, query: String)

    fun findNext(tabId: String, forward: Boolean)

    fun clearFind(tabId: String)

    /**
     * Closes [tabIds] at once and returns the token that undoes it. Closing the last incognito tab
     * ends that session once the close is final.
     */
    fun close(tabIds: List<String>): CloseToken

    /** Undoes the close of [token]. A token already final or undone does nothing. */
    fun undoClose(token: CloseToken)

    /** Makes the close of [token] final. */
    fun finalizeClose(token: CloseToken)

    /**
     * Applies [layout] to the normal tabs. Returns false and changes nothing when [expectedVersion]
     * is given and the layout version moved on since.
     */
    fun replaceLayout(layout: GroupLayout, expectedVersion: Long?, snapshot: GroupingSnapshot?): Boolean

    /** Reads the page of [tabId] once it is ready. Null when it never gets ready. */
    suspend fun extractPage(tabId: String): PageContent?

    /**
     * Reads the page of a tab that already has a live WebView. Does not create one.
     * Null when the tab has no live page.
     */
    suspend fun extractLivePage(tabId: String): PageContent?

    /**
     * Highlights a cited block in [tabId]'s page. False unless the block id is digits only and the
     * tab still shows the document the citation was read from.
     */
    suspend fun highlightBlock(tabId: String, request: HighlightRequest): Boolean
}

/**
 * Opens a tab and returns its id. It shows [url] (http or https; anything else opens the New tab
 * page) or the New tab page, and becomes the current tab unless [background].
 */
fun TabsController.newTab(
    incognito: Boolean,
    url: String? = null,
    background: Boolean = false,
    openedByExternalApp: Boolean = false,
    closeOnBack: Boolean = false,
    openerTabId: String? = null,
): String = openNewTab(incognito, url, background, openedByExternalApp, closeOnBack, openerTabId)

/** Applies [layout]. See [TabsController.replaceLayout]. */
fun TabsController.applyLayout(
    layout: GroupLayout,
    expectedVersion: Long? = null,
    snapshot: GroupingSnapshot? = null,
): Boolean = replaceLayout(layout, expectedVersion, snapshot)
