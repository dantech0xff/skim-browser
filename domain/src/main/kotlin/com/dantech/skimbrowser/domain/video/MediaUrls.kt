package com.dantech.skimbrowser.domain.video

import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.isWebUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** How a URL the page already asked for can be saved. */
enum class MediaStream { Progressive, Hls, Dash }

/**
 * A playlist or a whole video file. Segment URLs (`.ts`, `.m4s`, `video/mp2t`) are not a file
 * the person can save on their own.
 */
fun mediaStream(url: String, contentType: String?): MediaStream? {
    if (isMediaSegment(url, contentType)) return null
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val type = contentType?.substringBefore(';')?.trim()?.lowercase()
    return when {
        path.endsWith(".m3u8") || type == "application/vnd.apple.mpegurl" || type == "application/x-mpegurl" ->
            MediaStream.Hls
        path.endsWith(".mpd") || type == "application/dash+xml" -> MediaStream.Dash
        path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm") || path.endsWith(".mov") ||
            type?.startsWith("video/") == true ->
            MediaStream.Progressive
        else -> null
    }
}

fun isMediaSegment(url: String, contentType: String?): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val type = contentType?.substringBefore(';')?.trim()?.lowercase()
    return path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".aac") || type == "video/mp2t"
}

/** Prefix on a probe result that is page JSON, not a file address. The tab stores it in its own slot. */
const val PAGE_DOCUMENT = "skim-page:"

/** The object `evaluateJavascript` returns from the video probe: file URLs and page JSON already on the document. */
fun mediaFromProbe(json: String): List<String> {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return emptyList()
    val urls = (root["urls"] as? JsonArray).orEmpty().mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.takeIf(::playableAddress)
            is JsonObject -> probeSample(element)
            else -> null
        }
    }
    val docs = (root["docs"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .map { it.take(MAX_PAGE_DOCUMENT) }
        .filter { it.isNotBlank() }
    return (urls + docs.map { PAGE_DOCUMENT + it }).distinct()
}

/** The page a playing-event report names, when the payload is the probe object plus `page`. */
fun playingPage(json: String): String? {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
    return (root["page"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

/** The longest address a playing-message report may carry. Matches the history URL cap. */
const val MAX_BRIDGE_URL_LENGTH = 8_192

/** The longest raw playing-message body that is parsed. One file address fits, a pasted page does not. */
const val MAX_PLAYING_REPORT_CHARS = 16 * 1024

/**
 * [url] when it is an http(s) address with a host and at most [MAX_BRIDGE_URL_LENGTH] characters.
 * The playing-message port is the page's word, so anything else is dropped.
 */
fun bridgeUrl(url: String): String? =
    url.takeIf { it.length <= MAX_BRIDGE_URL_LENGTH && isWebUrl(it) && hostOf(it) != null }

/**
 * File addresses from a playing-message body. The page address has to be an acceptable [bridgeUrl],
 * and page JSON carried for site extractors is not taken from this port.
 */
fun mediaFromPlayingBridge(json: String): List<String> {
    if (json.length > MAX_PLAYING_REPORT_CHARS) return emptyList()
    if (bridgeUrl(playingPage(json).orEmpty()) == null) return emptyList()
    return mediaFromProbe(json).mapNotNull(::bridgeUrl)
}

/** How many file addresses one document keeps. The newest stays, and the oldest is the one that goes. */
const val MAX_REMEMBERED_MEDIA = 32

/**
 * One playing clip. [durationSeconds] is 0 when the element has not reported a duration yet.
 */
data class PlayedClip(val src: String, val durationSeconds: Double, val width: Int, val height: Int)

/**
 * [newestFirst] plus [clip], newest at the front, or null when [clip] is not a video worth keeping.
 * A thumbnail is refused before the cap is applied, so preview clips cannot fill the list.
 * A clip already kept moves to the front and is still returned, so the latest play is always offered.
 * The caller passes an empty list for a new document.
 */
fun rememberPlayed(newestFirst: List<PlayedClip>, clip: PlayedClip, limit: Int = MAX_REMEMBERED_MEDIA): List<PlayedClip>? {
    if (!playableAddress(clip.src) || isThumbnailUrl(clip.src) || isThumbnailSample(clip.durationSeconds, clip.width, clip.height)) {
        return null
    }
    val older = newestFirst.filterNot { it.src == clip.src }
    return (listOf(clip) + older).take(limit.coerceAtLeast(0))
}

/** How many page-JSON documents one page keeps. Newest first. A file address never takes one of these slots. */
const val MAX_PAGE_DOCUMENTS = 4

/** [newestFirst] plus a file [url], newest at the front, dropping the oldest file past [limit]. */
fun rememberMediaUrl(newestFirst: List<String>, url: String, limit: Int = MAX_REMEMBERED_MEDIA): List<String> {
    if (url.isBlank() || url.startsWith(PAGE_DOCUMENT) || limit <= 0) return newestFirst
    val older = newestFirst.filterNot { it == url || it.startsWith(PAGE_DOCUMENT) }
    return (listOf(url) + older).take(limit)
}

/** [newestFirst] plus the JSON in [document], newest first, within [limit]. The `skim-page:` prefix is not stored. */
fun rememberPageDocument(newestFirst: List<String>, document: String, limit: Int = MAX_PAGE_DOCUMENTS): List<String> {
    val text = document.removePrefix(PAGE_DOCUMENT).trim()
    if (text.isEmpty() || limit <= 0) return newestFirst
    val older = newestFirst.filterNot { it == text }
    return (listOf(text) + older).take(limit)
}

private fun probeSample(sample: JsonObject): String? {
    val src = (sample["src"] as? JsonPrimitive)?.contentOrNull ?: return null
    if (!playableAddress(src)) return null
    val duration = (sample["duration"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
    val width = (sample["width"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    val height = (sample["height"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    if (isThumbnailSample(duration, width, height) || isThumbnailUrl(src)) return null
    return src
}

private fun playableAddress(url: String): Boolean =
    (url.startsWith("http://") || url.startsWith("https://")) &&
        !url.startsWith("blob:") &&
        !url.startsWith("mediasource:")

private const val MAX_PAGE_DOCUMENT = 200_000

fun pageDocuments(mediaUrls: List<String>): List<String> = mediaUrls.mapNotNull { url ->
    url.takeIf { it.startsWith(PAGE_DOCUMENT) }?.removePrefix(PAGE_DOCUMENT)?.takeIf { it.isNotBlank() }
}

fun VideoSource.address(): String = when (this) {
    is VideoSource.Progressive -> url
    is VideoSource.Hls -> playlistUrl
    is VideoSource.Dash -> manifestUrl
    is VideoSource.SeparateAv -> "$videoUrl|$audioUrl"
}

/** The file a title falls back to when the page has no name of its own. */
fun VideoSource.mediaAddress(): String = when (this) {
    is VideoSource.Progressive -> url
    is VideoSource.Hls -> playlistUrl
    is VideoSource.Dash -> manifestUrl
    is VideoSource.SeparateAv -> videoUrl
}
