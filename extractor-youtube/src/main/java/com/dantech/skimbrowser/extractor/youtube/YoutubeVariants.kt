@file:Suppress("DEPRECATION")

package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoExtractException
import com.dantech.skimbrowser.domain.video.VideoExtractFailure
import com.dantech.skimbrowser.domain.video.VideoSource
import com.dantech.skimbrowser.domain.video.VideoVariant
import java.net.URI
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

internal object YoutubeVariants {
    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun fromInfo(info: StreamInfo, hints: PageVideoHints): List<VideoVariant> {
        if (info.ageLimit >= 18) throw VideoExtractException(VideoExtractFailure.AGE_RESTRICTED)
        val title = info.name?.takeIf { it.isNotBlank() } ?: hints.pageTitle?.takeIf { it.isNotBlank() } ?: "Video"
        val drafts = fileDrafts(info, hints)
        val labeled = disambiguate(drafts)
        if (labeled.isNotEmpty()) return labeled.map { it.toVariant(title) }
        val hls = info.hlsUrl.orEmpty()
        if (hls.startsWith("http://") || hls.startsWith("https://")) {
            return listOf(
                VideoVariant(
                    title,
                    "HLS",
                    "application/vnd.apple.mpegurl",
                    null,
                    VideoSource.Hls(hls, headersFor(hints, listOf(hls))),
                ),
            )
        }
        val dash = info.dashMpdUrl.orEmpty()
        if (dash.startsWith("http://") || dash.startsWith("https://")) {
            return listOf(
                VideoVariant(
                    title,
                    "DASH",
                    "application/dash+xml",
                    null,
                    VideoSource.Dash(dash, headersFor(hints, listOf(dash))),
                ),
            )
        }
        throw VideoExtractException(VideoExtractFailure.UNAVAILABLE)
    }

    private fun fileDrafts(info: StreamInfo, hints: PageVideoHints): List<Draft> {
        val progressive = info.videoStreams.orEmpty().mapNotNull { stream ->
            if (stream.isVideoOnly) return@mapNotNull null
            val url = directUrl(stream) ?: return@mapNotNull null
            Draft(
                height = heightOf(stream),
                label = labelOf(stream),
                mime = stream.format?.mimeType,
                size = sizeOf(stream.itagItem),
                separate = false,
                source = VideoSource.Progressive(url, headersFor(hints, listOf(url))),
            )
        }
        val separate = info.videoOnlyStreams.orEmpty().mapNotNull { stream ->
            if (!stream.isVideoOnly) return@mapNotNull null
            val videoUrl = directUrl(stream) ?: return@mapNotNull null
            val audio = pickAudio(stream, info.audioStreams.orEmpty()) ?: return@mapNotNull null
            val audioUrl = directUrl(audio) ?: return@mapNotNull null
            Draft(
                height = heightOf(stream),
                label = labelOf(stream),
                mime = stream.format?.mimeType,
                size = sumSizes(sizeOf(stream.itagItem), sizeOf(audio.itagItem)),
                separate = true,
                source = VideoSource.SeparateAv(videoUrl, audioUrl, headersFor(hints, listOf(videoUrl, audioUrl))),
            )
        }
        return (progressive + separate).sortedWith(compareByDescending<Draft> { it.height }.thenBy { it.separate })
    }

    private fun pickAudio(video: VideoStream, audio: List<AudioStream>): AudioStream? {
        val family = familyOf(video.format?.mimeType)
        val same = audio.filter { directUrl(it) != null && familyOf(it.format?.mimeType) == family }
        return same.maxByOrNull { it.averageBitrate }
    }

    private fun directUrl(stream: Stream): String? {
        if (!stream.isUrl) return null
        val content = stream.content
        if (!content.startsWith("http://") && !content.startsWith("https://")) return null
        return content
    }

    private fun familyOf(mime: String?): String = when {
        mime == null -> "other"
        mime.contains("webm") || mime.contains("opus") -> "webm"
        mime.contains("mp4") || mime.contains("3gpp") -> "mp4"
        else -> mime
    }

    private fun heightOf(stream: VideoStream): Int {
        if (stream.height > 0) return stream.height
        val resolution = stream.resolution
        Regex("""(\d+)p""").find(resolution)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return Regex("""\d+x(\d+)""").find(resolution)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun labelOf(stream: VideoStream): String {
        if (stream.resolution.isNotBlank()) return stream.resolution
        return if (stream.height > 0) "${stream.height}p" else "Video"
    }

    private fun sizeOf(item: ItagItem?): Long? {
        val length = item?.contentLength ?: return null
        return length.takeIf { it > 0 }
    }

    private fun sumSizes(video: Long?, audio: Long?): Long? =
        if (video != null && audio != null) video + audio else null

    private fun disambiguate(drafts: List<Draft>): List<Draft> {
        fun uniquify(items: List<Draft>, suffix: (Draft) -> String): List<Draft> {
            val counts = items.groupingBy { it.label }.eachCount()
            return items.map { draft ->
                if ((counts[draft.label] ?: 0) <= 1) draft
                else draft.copy(label = "${draft.label} ${suffix(draft)}")
            }
        }
        return uniquify(uniquify(drafts) { containerTag(it.mime) }) {
            if (it.separate) "separate audio" else "muxed"
        }
    }

    private fun containerTag(mime: String?): String = when {
        mime == null -> "stream"
        mime.contains("webm") -> "WebM"
        mime.contains("mp4") -> "MP4"
        mime.contains("3gpp") -> "3GPP"
        else -> mime.substringAfter('/')
    }

    fun headersFor(hints: PageVideoHints, mediaUrls: List<String>): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["User-Agent"] = hints.userAgent?.takeIf { it.isNotBlank() } ?: FALLBACK_USER_AGENT
        headers["Referer"] = hints.pageUrl
        val cookie = hints.cookieHeader?.takeIf { it.isNotBlank() }
        if (cookie != null && mediaUrls.all { cookieAllowed(hints.pageUrl, it) }) {
            headers["Cookie"] = cookie
        }
        return headers
    }

    /** Page cookies stay on https URLs for that page's host. Other hosts never receive them. */
    internal fun cookieAllowed(pageUrl: String, mediaUrl: String): Boolean {
        val media = runCatching { URI(mediaUrl) }.getOrNull() ?: return false
        if (!media.scheme.equals("https", ignoreCase = true)) return false
        val mediaHost = media.host?.lowercase()?.removePrefix("www.") ?: return false
        val pageHost = runCatching { URI(pageUrl).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return false
        return mediaHost == pageHost || mediaHost.endsWith(".$pageHost")
    }

    private data class Draft(
        val height: Int,
        val label: String,
        val mime: String?,
        val size: Long?,
        val separate: Boolean,
        val source: VideoSource,
    ) {
        fun toVariant(title: String) = VideoVariant(title, label, mime, size, source)
    }
}
