package com.dantech.skimbrowser.domain.video

import com.dantech.skimbrowser.domain.download.DownloadHeader
import com.dantech.skimbrowser.domain.download.DownloadRetry
import com.dantech.skimbrowser.domain.download.VIDEO_AUDIO
import com.dantech.skimbrowser.domain.download.VIDEO_DASH
import com.dantech.skimbrowser.domain.download.VIDEO_HLS
import com.dantech.skimbrowser.domain.download.VIDEO_KIND
import com.dantech.skimbrowser.domain.download.VIDEO_QUALITY
import com.dantech.skimbrowser.domain.download.VIDEO_TITLE
import com.dantech.skimbrowser.domain.download.videoQuality
import com.dantech.skimbrowser.domain.download.videoTitle
import com.dantech.skimbrowser.domain.download.VIDEO_PROGRESSIVE
import com.dantech.skimbrowser.domain.download.VIDEO_SEPARATE
import com.dantech.skimbrowser.domain.download.videoKind
import com.dantech.skimbrowser.domain.download.wireHeaders
import com.dantech.skimbrowser.domain.url.hostOf
import kotlinx.coroutines.CancellationException

/** What saving a variant did. A protected video is not written to the download list. */
sealed interface VideoSaveResult {
    data object Started : VideoSaveResult
    data object Protected : VideoSaveResult
}

/** Qualities a variant expands to, or a refusal when the playlist is protected. */
sealed interface VideoQualities {
    data class Ready(val variants: List<VideoVariant>) : VideoQualities
    data object Protected : VideoQualities
}

/** The browser saves a variant the person picked, and a failed video row the person retries. */
interface VideoCommands {
    suspend fun save(tabId: String, variant: VideoVariant): VideoSaveResult

    /** One row per quality. A master playlist becomes its variants. A protected manifest is refused. */
    suspend fun qualities(tabId: String, variant: VideoVariant): VideoQualities
}

/** Variants a page can save, plus the refusal when every matching extractor declined. */
data class CollectedVideos(
    val variants: List<VideoVariant>,
    val failure: VideoExtractFailure? = null,
)

suspend fun collectVariants(extractors: Iterable<VideoExtractor>, hints: PageVideoHints): CollectedVideos {
    val matched = extractors.filter { it.matches(hints.pageUrl) }.sortedWith(
        compareBy<VideoExtractor> { if (it.id == "generic") 1 else 0 }.thenBy { it.id },
    )
    val found = mutableListOf<VideoVariant>()
    var failure: VideoExtractFailure? = null
    for (extractor in matched) {
        try {
            found += extractor.extract(hints)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: VideoExtractException) {
            if (failure == null) failure = refused.failure
        } catch (_: Exception) {
        }
    }
    val variants = found.distinctBy { it.source.address() }
    return CollectedVideos(variants, failure.takeIf { variants.isEmpty() })
}

/**
 * One row per quality, highest first. A DASH pair replaces the video-only playlist and the
 * video-only rendition files that share its directory. Another clip on the same page stays.
 */
fun listedQualities(variants: List<VideoVariant>): List<VideoVariant> {
    val pairedDirectories = variants.mapNotNull { variant ->
        (variant.source as? VideoSource.SeparateAv)?.let { clipDirectory(it.videoUrl) }
    }.toSet()
    val pairedVideoPaths = variants.mapNotNull { variant ->
        (variant.source as? VideoSource.SeparateAv)?.let { mediaPath(it.videoUrl) }
    }.toSet()
    val kept = variants.filter { variant ->
        val source = variant.source
        if (source is VideoSource.Progressive && mediaPath(source.url) in pairedVideoPaths) return@filter false
        val directory = clipDirectory(source.mediaAddress())
        if (directory !in pairedDirectories) return@filter true
        when (source) {
            is VideoSource.SeparateAv -> true
            is VideoSource.Hls, is VideoSource.Dash -> false
            is VideoSource.Progressive -> !isVideoOnlyRendition(source.url)
        }
    }
    return kept
        .distinctBy { it.source.address() }
        .sortedByDescending { qualityHeight(it.qualityLabel) ?: -1 }
}

/** The directory of a media file, so two files of one clip can be told from another clip. */
internal fun clipDirectory(url: String): String {
    val path = mediaPath(url)
    val slash = path.lastIndexOf('/')
    return if (slash >= 0) path.substring(0, slash) else path
}

private fun mediaPath(url: String): String = url.substringBefore('?').substringBefore('#')

/** A page title with a trailing site name removed, including a repeated `| Facebook`. */
fun videoCaption(title: String): String {
    var name = title.trim()
    while (true) {
        val next = SITE_SUFFIX.replace(name, "").trim()
        if (next == name) return name
        name = next
    }
}

/**
 * The name of a video. A page with no title of its own, or whose title is the address, uses the
 * last path segment of [url].
 */
fun videoDisplayTitle(title: String?, url: String): String {
    val caption = videoCaption(title.orEmpty())
    if (caption.isNotBlank() && "://" !in caption) return caption
    val path = url.substringBefore('?').substringBefore('#')
    val segment = path.substringAfterLast('/').trim()
    return segment.ifBlank { caption.ifBlank { "video" } }
}

private fun isVideoOnlyRendition(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.contains("dash_") || path.contains("cmaf_") || path.contains("/hls_")
}

private val SITE_SUFFIX = Regex("""(?:\s+\|\s+|\s+_\s+)(?:TikTok|Facebook|Reddit)\s*$""", RegexOption.IGNORE_CASE)

internal fun hostIs(pageUrl: String, vararg names: String): Boolean {
    val host = hostOf(pageUrl) ?: return false
    return names.any { host == it || host.endsWith(".$it") }
}

internal fun variant(
    title: String?,
    quality: String,
    mime: String?,
    source: VideoSource,
    pageUrl: String,
): VideoVariant {
    val headers = sourceHeaders(source).toMutableMap()
    if (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) {
        headers.putIfAbsent("Referer", pageUrl)
    }
    val withHeaders = when (source) {
        is VideoSource.Progressive -> source.copy(headers = headers)
        is VideoSource.Hls -> source.copy(headers = headers)
        is VideoSource.Dash -> source.copy(headers = headers)
        is VideoSource.SeparateAv -> source.copy(headers = headers)
    }
    return VideoVariant(
        title = videoDisplayTitle(title, source.mediaAddress()),
        qualityLabel = quality,
        mimeType = mime,
        sizeBytes = null,
        source = withHeaders,
    )
}

/** The variant a stored video retry asks for again. Null when the row is an ordinary file. */
fun variantFromRetry(retry: DownloadRetry): VideoVariant? {
    val kind = retry.videoKind() ?: return null
    val headers = retry.wireHeaders()
    val audio = retry.headers.firstOrNull { it.name == VIDEO_AUDIO }?.value
    val source = when (kind) {
        VIDEO_HLS -> VideoSource.Hls(retry.url, headers)
        VIDEO_DASH -> VideoSource.Dash(retry.url, headers)
        VIDEO_SEPARATE -> VideoSource.SeparateAv(retry.url, audio ?: return null, headers)
        else -> VideoSource.Progressive(retry.url, headers)
    }
    val quality = retry.videoQuality()?.takeIf { it.isNotBlank() } ?: "Video"
    val title = videoDisplayTitle(retry.videoTitle(), retry.url)
    return VideoVariant(title, quality, retry.mimeType, null, source)
}

fun retryHeaders(source: VideoSource): List<DownloadHeader> {
    val kind = when (source) {
        is VideoSource.Progressive -> VIDEO_PROGRESSIVE
        is VideoSource.Hls -> VIDEO_HLS
        is VideoSource.Dash -> VIDEO_DASH
        is VideoSource.SeparateAv -> VIDEO_SEPARATE
    }
    val wire = sourceHeaders(source).filterKeys { !it.equals("Cookie", ignoreCase = true) }
        .map { (name, value) -> DownloadHeader(name, value) }
    val marks = listOf(DownloadHeader(VIDEO_KIND, kind)) +
        ((source as? VideoSource.SeparateAv)?.let { listOf(DownloadHeader(VIDEO_AUDIO, it.audioUrl)) } ?: emptyList())
    return wire + marks
}

private fun sourceHeaders(source: VideoSource): Map<String, String> = when (source) {
    is VideoSource.Progressive -> source.headers
    is VideoSource.Hls -> source.headers
    is VideoSource.Dash -> source.headers
    is VideoSource.SeparateAv -> source.headers
}
