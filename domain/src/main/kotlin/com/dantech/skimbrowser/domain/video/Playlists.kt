package com.dantech.skimbrowser.domain.video

data class HlsVariant(
    val url: String,
    val bandwidth: Int?,
    val resolution: String?,
    val audioGroup: String? = null,
)

data class HlsByteRange(val length: Long, val offset: Long)

data class HlsSegment(val url: String, val range: HlsByteRange? = null)

data class HlsAudio(val groupId: String, val name: String, val url: String, val default: Boolean)

data class HlsPlaylist(
    val protected: Boolean,
    val variants: List<HlsVariant>,
    val segments: List<HlsSegment>,
    val map: HlsSegment? = null,
    val audios: List<HlsAudio> = emptyList(),
)

/** One read of a media file. A null start is the whole file. Adjacent byte ranges are already joined. */
data class MediaSpan(val url: String, val start: Long?, val length: Long?)

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
    if (hlsProtected(body)) {
        return HlsPlaylist(protected = true, variants = emptyList(), segments = emptyList())
    }
    val variants = mutableListOf<HlsVariant>()
    val segments = mutableListOf<HlsSegment>()
    val audios = mutableListOf<HlsAudio>()
    var bandwidth: Int? = null
    var resolution: String? = null
    var audioGroup: String? = null
    var pendingRange: String? = null
    var map: HlsSegment? = null
    val nextOffset = mutableMapOf<String, Long>()
    body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        when {
            line.startsWith("#EXT-X-MEDIA:", ignoreCase = true) -> {
                if (attr(line, "TYPE").equals("AUDIO", ignoreCase = true)) {
                    val uri = attr(line, "URI")
                    val group = attr(line, "GROUP-ID")
                    if (!uri.isNullOrBlank() && !group.isNullOrBlank()) {
                        audios += HlsAudio(
                            groupId = group,
                            name = attr(line, "NAME").orEmpty(),
                            url = resolveUrl(playlistUrl, uri),
                            default = attr(line, "DEFAULT").equals("YES", ignoreCase = true),
                        )
                    }
                }
            }
            line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                val uri = attr(line, "URI")
                if (!uri.isNullOrBlank()) {
                    val url = resolveUrl(playlistUrl, uri)
                    val piece = HlsSegment(url, byteRange(attr(line, "BYTERANGE"), url, nextOffset))
                    if (map == null) map = piece
                    segments += piece
                }
            }
            line.startsWith("#EXT-X-BYTERANGE:", ignoreCase = true) -> pendingRange = line.substringAfter(':').trim()
            line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                bandwidth = attr(line, "BANDWIDTH")?.toIntOrNull()
                resolution = attr(line, "RESOLUTION")
                audioGroup = attr(line, "AUDIO")
            }
            line.startsWith("#") -> Unit
            bandwidth != null || resolution != null -> {
                variants += HlsVariant(resolveUrl(playlistUrl, line), bandwidth, resolution, audioGroup)
                bandwidth = null
                resolution = null
                audioGroup = null
                pendingRange = null
            }
            else -> {
                val url = resolveUrl(playlistUrl, line)
                segments += HlsSegment(url, byteRange(pendingRange, url, nextOffset))
                pendingRange = null
            }
        }
    }
    return HlsPlaylist(protected = false, variants = variants, segments = segments, map = map, audios = audios)
}

/** The audio rendition for [groupId]. A variant with no audio group keeps its own audio. */
fun preferredHlsAudio(audios: List<HlsAudio>, groupId: String?): HlsAudio? {
    if (groupId == null) return null
    val group = audios.filter { it.groupId == groupId }
    return group.maxByOrNull { audioRate(it.url) }
}

/** Initialization maps and media ranges, in playlist order. Contiguous ranges of one file are joined. */
fun mediaSpans(playlist: HlsPlaylist): List<MediaSpan> {
    val pieces = playlist.segments
    if (pieces.isEmpty()) return emptyList()
    if (pieces.any { it.range == null }) {
        return pieces.map { piece -> MediaSpan(piece.url, piece.range?.offset, piece.range?.length) }
    }
    val spans = mutableListOf<MediaSpan>()
    for (piece in pieces) {
        val range = piece.range ?: continue
        val last = spans.lastOrNull()
        if (last != null && last.url == piece.url && last.start != null && last.length != null &&
            last.start + last.length == range.offset
        ) {
            spans[spans.lastIndex] = last.copy(length = last.length + range.length)
        } else {
            spans += MediaSpan(piece.url, range.offset, range.length)
        }
    }
    return spans
}

private fun byteRange(raw: String?, url: String, nextOffset: MutableMap<String, Long>): HlsByteRange? {
    if (raw.isNullOrBlank()) return null
    val length = raw.substringBefore('@').trim().toLongOrNull() ?: return null
    if (length <= 0) return null
    val offset = raw.substringAfter('@', "").trim().toLongOrNull() ?: nextOffset[url] ?: 0L
    nextOffset[url] = offset + length
    return HlsByteRange(length, offset)
}

private fun audioRate(url: String): Int =
    Regex("""(\d{2,4})""").findAll(url).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0

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
