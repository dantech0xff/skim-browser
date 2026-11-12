package com.dantech.skimbrowser.domain.download

/**
 * Enough to start a failed download again. It holds the page address, so it is never logged.
 * [userInitiated] is true when the person asked for it (Download image), false when the page started it.
 * [incognito] is the tab's kind when the download was asked for, so a retry still knows it after
 * that tab is closed.
 */
data class DownloadRetry(
    val tabId: String,
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val userInitiated: Boolean,
    val incognito: Boolean,
)
