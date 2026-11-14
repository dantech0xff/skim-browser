package com.dantech.skimbrowser.domain.video

data class HlsVariant(val url: String, val bandwidth: Int?, val resolution: String?)

data class HlsPlaylist(
    val protected: Boolean,
    val variants: List<HlsVariant>,
    val segments: List<String>,
)

data class DashFile(val url: String, val height: Int?, val bandwidth: Int?)

data class DashManifest(
    val protected: Boolean,
    val videos: List<DashFile>,
    val audios: List<DashFile>,
)

/** SAMPLE-AES and Widevine playlists cannot be saved. AES-128 is the same refusal: the bytes would not play. */
fun hlsProtected(playlist: String): Boolean = playlist.lineSequence().any { line ->
    if (!line.trim().startsWith("#EXT-X-KEY", ignoreCase = true)) return@any false
    val method = attr(line, "METHOD").orEmpty()
    val format = attr(line, "KEYFORMAT").orEmpty()
    method.equals("NONE", ignoreCase = true).not() && method.isNotEmpty() ||
        format.contains("widevine", ignoreCase = true)
}

fun dashProtected(mpd: String): Boolean = mpd.contains("ContentProtection", ignoreCase = true)

fun parseHls(body: String, playlistUrl: String): HlsPlaylist {
    if (hlsProtected(body)) return HlsPlaylist(protected = true, variants = emptyList(), segments = emptyList())
    val variants = mutableListOf<HlsVariant>()
    val segments = mutableListOf<String>()
    var bandwidth: Int? = null
    var resolution: String? = null
    body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        when {
            line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                bandwidth = attr(line, "BANDWIDTH")?.toIntOrNull()
                resolution = attr(line, "RESOLUTION")
            }
            line.startsWith("#") -> Unit
            bandwidth != null || resolution != null -> {
                variants += HlsVariant(resolveUrl(playlistUrl, line), bandwidth, resolution)
                bandwidth = null
                resolution = null
            }
            else -> segments += resolveUrl(playlistUrl, line)
        }
    }
    return HlsPlaylist(protected = false, variants = variants, segments = segments)
}

fun parseDash(mpd: String, manifestUrl: String): DashManifest {
    if (dashProtected(mpd)) return DashManifest(protected = true, videos = emptyList(), audios = emptyList())
    val videos = mutableListOf<DashFile>()
    val audios = mutableListOf<DashFile>()
    adaptationSets(mpd).forEach { set ->
        val audio = set.contains("contentType=\"audio\"", ignoreCase = true) ||
            set.contains("mimeType=\"audio", ignoreCase = true)
        val target = if (audio) audios else videos
        representations(set).forEach { block ->
            val height = attr(block, "height")?.toIntOrNull()
            val bandwidth = attr(block, "bandwidth")?.toIntOrNull()
            baseUrls(block).forEach { raw ->
                target += DashFile(resolveUrl(manifestUrl, raw), height, bandwidth)
            }
        }
    }
    return DashManifest(protected = false, videos = videos, audios = audios)
}

/** The height in a label such as `720p`, `720p MP4 separate audio`, or `1080p60`. */
fun qualityHeight(label: String): Int? =
    Regex("""(\d+)p""", RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1)?.toIntOrNull()

internal fun resolveUrl(base: String, raw: String): String {
    val ref = raw.trim()
    if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
    val root = base.substringBefore('?').substringBefore('#')
    if (ref.startsWith("/")) {
        val scheme = root.substringBefore("://")
        val host = root.substringAfter("://").substringBefore('/')
        return "$scheme://$host$ref"
    }
    val dir = root.substringBeforeLast('/', root)
    return "$dir/$ref"
}

private fun adaptationSets(mpd: String): List<String> {
    val sets = mutableListOf<String>()
    var from = 0
    while (true) {
        val start = mpd.indexOf("<AdaptationSet", from, ignoreCase = true)
        if (start < 0) break
        val end = mpd.indexOf("</AdaptationSet>", start, ignoreCase = true)
        if (end < 0) break
        sets += mpd.substring(start, end)
        from = end + 1
    }
    return sets
}

private fun representations(set: String): List<String> {
    val blocks = mutableListOf<String>()
    var from = 0
    while (true) {
        val start = set.indexOf("<Representation", from, ignoreCase = true)
        if (start < 0) break
        val end = set.indexOf("</Representation>", start, ignoreCase = true)
        if (end < 0) break
        blocks += set.substring(start, end)
        from = end + 1
    }
    return blocks
}

private fun baseUrls(block: String): List<String> {
    val urls = mutableListOf<String>()
    var from = 0
    while (true) {
        val start = block.indexOf("<BaseURL", from, ignoreCase = true)
        if (start < 0) break
        val open = block.indexOf('>', start)
        val end = block.indexOf("</BaseURL>", open, ignoreCase = true)
        if (open < 0 || end < 0) break
        urls += unescapeXml(block.substring(open + 1, end)).trim()
        from = end + 1
    }
    return urls.filter { it.isNotEmpty() }
}

private fun unescapeXml(text: String): String = text
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")

private fun attr(line: String, name: String): String? {
    val key = "$name="
    val at = line.indexOf(key, ignoreCase = true)
    if (at < 0) return null
    val value = line.substring(at + key.length).trimStart()
    if (value.startsWith("\"")) {
        val end = value.indexOf('"', 1)
        return if (end < 0) null else value.substring(1, end)
    }
    return value.substringBefore(',').substringBefore(' ').trim()
}
