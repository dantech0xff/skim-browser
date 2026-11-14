package com.dantech.skimbrowser.domain.interrupt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A popup or a redirect the browser stopped, so the snackbar can offer to open it. */
data class BlockedInterrupt(
    val tabId: String,
    val kind: InterruptKind,
    val host: String?,
    /** The URL that was stopped. Null for a popup, which has no URL until it is opened. */
    val url: String?,
    /** Identifies one held popup. A later popup on the same tab gets a new id. Zero for a redirect. */
    val holdId: Long = 0,
)

interface InterruptNotices {
    val blocked: Flow<BlockedInterrupt>

    fun openHeldPopup(tabId: String, holdId: Long)

    fun dropHeldPopup(tabId: String, holdId: Long)

    fun allowNavigation(tabId: String, url: String)

    companion object {
        val None: InterruptNotices = object : InterruptNotices {
            override val blocked: Flow<BlockedInterrupt> = emptyFlow()
            override fun openHeldPopup(tabId: String, holdId: Long) = Unit
            override fun dropHeldPopup(tabId: String, holdId: Long) = Unit
            override fun allowNavigation(tabId: String, url: String) = Unit
        }
    }
}
