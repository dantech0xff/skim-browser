package com.dantech.skimbrowser.domain.download

/**
 * Enough to start a failed download again. It holds the page address, so it is never logged.
 * [userInitiated] is true when the person asked for it (Download image), false when the page started it.
 * [incognito] is the tab's kind when the download was asked for, so a retry still knows it after
 * that tab is closed.
 */
/** One request header kept so a failed download can be asked for again. A cookie is never one of these. */
data class DownloadHeader(val name: String, val value: String)

data class DownloadRetry(
    val tabId: String,
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val userInitiated: Boolean,
    val incognito: Boolean,
    val headers: List<DownloadHeader> = emptyList(),
)

/** Headers a retry may send. Cookie is looked up again and is not stored. Skim's own marks stay off the wire. */
fun DownloadRetry.wireHeaders(): Map<String, String> = headers.mapNotNull { header ->
    if (header.name.equals("Cookie", ignoreCase = true) || header.name.startsWith("X-Skim-", ignoreCase = true)) {
        null
    } else {
        header.name to header.value
    }
}.toMap()

/** The video kind stored on a retry, or null when the row is an ordinary file download. */
fun DownloadRetry.videoKind(): String? = headers.firstOrNull { it.name == VIDEO_KIND }?.value

const val VIDEO_KIND = "X-Skim-Kind"
const val VIDEO_AUDIO = "X-Skim-Audio"
const val VIDEO_PROGRESSIVE = "progressive"
const val VIDEO_HLS = "hls"
const val VIDEO_DASH = "dash"
const val VIDEO_SEPARATE = "separate"
