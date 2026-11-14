package com.dantech.skimbrowser.domain.video

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
        path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm") || type?.startsWith("video/") == true ->
            MediaStream.Progressive
        else -> null
    }
}

fun isMediaSegment(url: String, contentType: String?): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val type = contentType?.substringBefore(';')?.trim()?.lowercase()
    return path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".aac") || type == "video/mp2t"
}

/** Page JSON the script already found, carried in [PageVideoHints.mediaUrls] without a new field. */
const val PAGE_DOCUMENT = "skim-page:"

/** The object `evaluateJavascript` returns from the video probe: file URLs and page JSON already on the document. */
fun mediaFromProbe(json: String): List<String> {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return emptyList()
    val urls = (root["urls"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .filter { (it.startsWith("http://") || it.startsWith("https://")) && !it.startsWith("blob:") }
    val docs = (root["docs"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .map { it.take(MAX_PAGE_DOCUMENT) }
        .filter { it.isNotBlank() }
    return (urls + docs.map { PAGE_DOCUMENT + it }).distinct()
}

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
