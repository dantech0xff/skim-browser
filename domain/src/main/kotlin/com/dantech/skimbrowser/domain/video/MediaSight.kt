package com.dantech.skimbrowser.domain.video

/**
 * One subresource the page asked for. The sniffer sees the address and the request headers only.
 * It does not read the body and it does not send the request again.
 */
data class MediaLook(
    val url: String,
    val range: String? = null,
    val accept: String? = null,
)

/**
 * What to remember from one look. A segment is never a download row. It points at a playlist
 * already seen for the same clip, or at nothing when that playlist has not been seen yet.
 */
sealed interface MediaSight {
    data class File(val url: String, val stream: MediaStream) : MediaSight
    data class Segment(val manifestUrl: String?) : MediaSight
}

/**
 * Classifies one observed request. [tracker] is true when the host is already on the ad and
 * tracker list. [knownManifests] are playlist addresses already seen for this navigation.
 */
fun sightMedia(look: MediaLook, tracker: Boolean, knownManifests: List<String> = emptyList()): MediaSight? {
    if (tracker) return null
    val url = look.url
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    if (isThumbnailUrl(url)) return null
    if (isMediaSegment(url, null)) return MediaSight.Segment(manifestForSegment(url, knownManifests))
    val stream = mediaStream(url, null)
    if (stream != null) return MediaSight.File(url, stream)
    if (obviousNonMedia(url) || !looksLikeMedia(look)) return null
    return MediaSight.File(url, MediaStream.Progressive)
}

/** The playlist this fragment belongs to, when one was already seen in the same directory or a parent. */
fun manifestForSegment(segmentUrl: String, knownManifests: List<String>): String? {
    val segmentDir = clipDirectory(segmentUrl)
    var best: String? = null
    var bestLength = -1
    for (manifest in knownManifests) {
        val kind = mediaStream(manifest, null)
        if (kind != MediaStream.Hls && kind != MediaStream.Dash) continue
        val dir = clipDirectory(manifest)
        val matches = segmentDir == dir || segmentDir.startsWith("$dir/")
        if (matches && dir.length > bestLength) {
            best = manifest
            bestLength = dir.length
        }
    }
    return best
}

/** A poster, a sprite, or an image. A file named `thumb.mp4` is a preview, not the video. */
fun isThumbnailUrl(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val file = path.substringAfterLast('/')
    val ext = file.substringAfterLast('.', "")
    if (ext in IMAGE_EXTENSIONS) return true
    val segments = path.substringAfter("://", path).substringAfter('/', "").split('/')
    if (segments.any { it in THUMB_SEGMENTS }) return true
    return file.startsWith("thumb.") || file.startsWith("thumbnail.") || file.startsWith("poster.")
}

/**
 * A sample the page probe reported. A short clip that is also small is a thumbnail. A long video
 * stays even when the picture is small. Unknown duration counts as a thumbnail only at thumbnail size.
 */
fun isThumbnailSample(durationSeconds: Double, width: Int, height: Int): Boolean {
    val tiny = width in 1..THUMB_EDGE && height in 1..THUMB_EDGE
    val compact = width in 1..THUMB_WIDE && height in 1..THUMB_SHORT
    val short = durationSeconds > 0.0 && durationSeconds < THUMB_SECONDS
    if (tiny && (short || durationSeconds <= 0.0)) return true
    return short && compact
}

/** Drops a single file that lives in the same folder as a playlist. The playlist names the qualities. */
fun preferManifests(variants: List<VideoVariant>): List<VideoVariant> {
    val roots = variants.mapNotNull { variant ->
        when (val source = variant.source) {
            is VideoSource.Hls -> clipDirectory(source.playlistUrl)
            is VideoSource.Dash -> clipDirectory(source.manifestUrl)
            else -> null
        }
    }
    if (roots.isEmpty()) return variants
    return variants.filter { variant ->
        val source = variant.source
        if (source !is VideoSource.Progressive) return@filter true
        val dir = clipDirectory(source.url)
        roots.none { root -> dir == root || dir.startsWith("$root/") }
    }
}

private fun looksLikeMedia(look: MediaLook): Boolean {
    val range = look.range?.trim().orEmpty()
    if (range.startsWith("bytes", ignoreCase = true)) return true
    val accept = look.accept?.lowercase().orEmpty()
    return accept.split(',').any { part ->
        val type = part.trim().substringBefore(';').trim()
        type == "video" || type.startsWith("video/")
    }
}

private fun obviousNonMedia(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val file = path.substringAfterLast('/')
    val ext = file.substringAfterLast('.', "")
    if (ext.isEmpty() || ext == file) return false
    return ext in NON_MEDIA_EXTENSIONS
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "svg", "ico")

private val THUMB_SEGMENTS = setOf("thumb", "thumbs", "thumbnail", "thumbnails", "poster", "posters", "sprite", "sprites")

private val NON_MEDIA_EXTENSIONS = setOf(
    "js", "mjs", "css", "html", "htm", "json", "xml", "txt", "map", "pdf",
    "woff", "woff2", "ttf", "otf", "wasm", "vtt", "srt",
)

private const val THUMB_EDGE = 160
private const val THUMB_WIDE = 320
private const val THUMB_SHORT = 180
private const val THUMB_SECONDS = 2.0
