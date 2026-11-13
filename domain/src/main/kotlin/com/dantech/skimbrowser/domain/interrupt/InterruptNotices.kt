package com.dantech.skimbrowser.domain.interrupt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A popup or a redirect the browser stopped, so the snackbar can offer to open it. */
data class BlockedInterrupt(
    val tabId: String,
    val kind: InterruptKind,
    /** The site key of the page that started it, or null when that page has no site. */
    val host: String?,
    /** The URL that was stopped. Null for a popup, which has no URL until it is opened. */
    val url: String?,
)

/**
 * Blocked popups and redirects, and the ways back from the snackbar. The browser implements it.
 * [openHeldPopup] finishes a popup that was held. [allowNavigation] loads [url] or hands an app
 * link over. [dropHeldPopup] cancels a popup the person did not open.
 */
interface InterruptNotices {
    val blocked: Flow<BlockedInterrupt>

    fun openHeldPopup(tabId: String)

    fun dropHeldPopup(tabId: String)

    fun allowNavigation(tabId: String, url: String)

    companion object {
        /** Nothing is blocked and the actions do nothing. Screens that never show a page use this. */
        val None: InterruptNotices = object : InterruptNotices {
            override val blocked: Flow<BlockedInterrupt> = emptyFlow()
            override fun openHeldPopup(tabId: String) = Unit
            override fun dropHeldPopup(tabId: String) = Unit
            override fun allowNavigation(tabId: String, url: String) = Unit
        }
    }
}
