package com.dantech.skimbrowser.domain.video

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** TikTok addresses already in the page JSON. `play_addr` and `download_addr` are the files. */
class TikTokVideoExtractor : VideoExtractor {
    override val id: String = "tiktok"

    override fun matches(pageUrl: String): Boolean = hostIs(pageUrl, "tiktok.com")

    override suspend fun extract(hints: PageVideoHints): List<VideoVariant> = hints.pageDocuments
        .flatMap { document ->
            urlsByKey(document, TIKTOK_KEYS)
                .filter { (url, _) -> !isStill(url) }
                .map { (url, quality) -> progressive(hints, url, quality) }
        }
}

/** Facebook and fb.watch files named in the page, including a clear DASH manifest. */
class FacebookVideoExtractor : VideoExtractor {
    override val id: String = "facebook"

    override fun matches(pageUrl: String): Boolean = hostIs(pageUrl, "facebook.com", "fb.com", "fb.watch")

    override suspend fun extract(hints: PageVideoHints): List<VideoVariant> = hints.pageDocuments.flatMap { document ->
        val files = urlsByKey(document, FACEBOOK_FILES)
        val manifests = textByKey(document, setOf("dash_manifest"))
        files.map { (url, quality) -> progressive(hints, url, quality) } + manifests.flatMap { dashVariants(hints, it) }
    }
}

/** Reddit and v.redd.it. A DASH manifest with a separate audio file is muxed when it is saved. */
class RedditVideoExtractor : VideoExtractor {
    override val id: String = "reddit"

    override fun matches(pageUrl: String): Boolean = hostIs(pageUrl, "reddit.com", "redd.it")

    override suspend fun extract(hints: PageVideoHints): List<VideoVariant> = hints.pageDocuments.flatMap { document ->
        val values = urlsByKey(document, REDDIT_KEYS)
        values.map { (url, _) ->
            when (mediaStream(url, null)) {
                MediaStream.Hls -> variant(hints.pageTitle, "HLS", "application/vnd.apple.mpegurl", VideoSource.Hls(url), hints.pageUrl)
                MediaStream.Dash -> variant(hints.pageTitle, "DASH", "application/dash+xml", VideoSource.Dash(url), hints.pageUrl)
                else -> progressive(hints, url, qualityFromUrl(url))
            }
        }
    }
}

private val TIKTOK_KEYS = setOf("play_addr", "playaddr", "download_addr", "downloadaddr")
private val FACEBOOK_FILES = setOf(
    "playable_url_quality_hd",
    "browser_native_hd_url",
    "playable_url",
    "browser_native_sd_url",
)
private val REDDIT_KEYS = setOf("dash_url", "hls_url", "fallback_url")

private fun isStill(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
        path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".mp3") || path.endsWith(".m4a")
}

private fun progressive(hints: PageVideoHints, url: String, quality: String): VideoVariant {
    val mime = if (url.substringBefore('?').endsWith(".webm")) "video/webm" else "video/mp4"
    return variant(hints.pageTitle, quality, mime, VideoSource.Progressive(url), hints.pageUrl)
}

private fun dashVariants(hints: PageVideoHints, manifest: String): List<VideoVariant> {
    if (manifest.trim().startsWith("http://") || manifest.trim().startsWith("https://")) {
        if (dashProtected(manifest)) return emptyList()
        return listOf(variant(hints.pageTitle, "DASH", "application/dash+xml", VideoSource.Dash(manifest.trim()), hints.pageUrl))
    }
    val parsed = parseDash(manifest, hints.pageUrl)
    if (parsed.protected) return emptyList()
    val video = parsed.videos.maxByOrNull { it.height ?: 0 }
    val audio = parsed.audios.firstOrNull()
    if (video != null && audio != null) {
        return listOf(
            variant(
                hints.pageTitle,
                video.height?.let { "${it}p" } ?: "DASH",
                "video/mp4",
                VideoSource.SeparateAv(video.url, audio.url),
                hints.pageUrl,
            ),
        )
    }
    return parsed.videos.map { file ->
        progressive(hints, file.url, file.height?.let { "${it}p" } ?: "DASH")
    }
}

private fun urlsByKey(document: String, keys: Set<String>): List<Pair<String, String>> {
    val root = jsonObject(document) ?: return emptyList()
    val found = mutableListOf<Pair<String, String>>()
    walk(root, null, keys) { url, quality -> found += url to quality }
    return found.distinctBy { it.first }
}

private fun textByKey(document: String, keys: Set<String>): List<String> {
    val root = jsonObject(document) ?: return emptyList()
    val found = mutableListOf<String>()
    walkText(root, keys, found)
    return found
}

private fun jsonObject(document: String): JsonElement? = runCatching {
    val start = document.indexOf('{')
    val end = document.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    Json.parseToJsonElement(document.substring(start, end + 1))
}.getOrNull()

private fun walk(node: JsonElement, gear: String?, keys: Set<String>, emit: (String, String) -> Unit) {
    when (node) {
        is JsonObject -> {
            val nextGear = (node["gear_name"] ?: node["GearName"])?.stringOrNull() ?: gear
            node.forEach { (key, value) ->
                if (key.lowercase() in keys) emitValues(value, qualityLabel(key, nextGear), emit)
                walk(value, nextGear, keys, emit)
            }
        }
        is JsonArray -> node.forEach { walk(it, gear, keys, emit) }
        is JsonPrimitive -> Unit
    }
}

private fun walkText(node: JsonElement, keys: Set<String>, found: MutableList<String>) {
    when (node) {
        is JsonObject -> node.forEach { (key, value) ->
            if (key.lowercase() in keys) value.stringOrNull()?.let { found += it }
            walkText(value, keys, found)
        }
        is JsonArray -> node.forEach { walkText(it, keys, found) }
        is JsonPrimitive -> Unit
    }
}

private fun emitValues(value: JsonElement, quality: String, emit: (String, String) -> Unit) {
    when (value) {
        is JsonPrimitive -> value.stringOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { emit(it, quality) }
        is JsonArray -> value.forEach { emitValues(it, quality, emit) }
        is JsonObject -> value.forEach { (_, child) -> emitValues(child, quality, emit) }
    }
}

private fun qualityLabel(key: String, gear: String?): String = when {
    gear?.contains("720") == true -> "720p"
    gear?.contains("1080") == true -> "1080p"
    key.contains("hd", ignoreCase = true) -> "HD"
    key.contains("sd", ignoreCase = true) || key == "playable_url" -> "SD"
    else -> "Video"
}

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
