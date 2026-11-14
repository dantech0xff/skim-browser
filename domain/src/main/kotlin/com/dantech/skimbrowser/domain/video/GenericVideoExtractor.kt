package com.dantech.skimbrowser.domain.video

/**
 * A direct file, HLS playlist, or DASH manifest already seen on the page, on any site.
 * Used when no site extractor saved a video or refused the page. A playlist replaces a single file in the same folder.
 */
class GenericVideoExtractor : VideoExtractor {
    override val id: String = "generic"

    override fun matches(pageUrl: String): Boolean = pageUrl.startsWith("http://") || pageUrl.startsWith("https://")

    override suspend fun extract(hints: PageVideoHints): List<VideoVariant> = preferManifests(
        hints.mediaUrls.mapNotNull { url -> candidate(hints, url) },
    )

    private fun candidate(hints: PageVideoHints, url: String): VideoVariant? {
        if (url.startsWith(PAGE_DOCUMENT) || isThumbnailUrl(url) || isMediaSegment(url, null)) return null
        val agent = hints.userAgent
        return when (mediaStream(url, null)) {
            MediaStream.Progressive -> offer(hints, qualityFromUrl(url), progressiveMime(url), VideoSource.Progressive(url), agent)
            MediaStream.Hls -> offer(hints, "HLS", "application/vnd.apple.mpegurl", VideoSource.Hls(url), agent)
            MediaStream.Dash -> offer(hints, "DASH", "application/dash+xml", VideoSource.Dash(url), agent)
            null -> if (url.startsWith("http://") || url.startsWith("https://")) {
                offer(hints, "Video", "video/mp4", VideoSource.Progressive(url), agent)
            } else {
                null
            }
        }
    }

    private fun offer(
        hints: PageVideoHints,
        quality: String,
        mime: String,
        source: VideoSource,
        agent: String?,
    ): VideoVariant = variant(hints.pageTitle, quality, mime, source, hints.pageUrl, agent)
}

internal fun progressiveMime(url: String): String {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".webm") -> "video/webm"
        path.endsWith(".mov") -> "video/quicktime"
        else -> "video/mp4"
    }
}

internal fun qualityFromUrl(url: String): String {
    val path = url.substringBefore('?')
    val labeled = Regex("(?:DASH_|_|/)(\\d{3,4})(?:p)?(?:\\.|_|$)").find(path)?.groupValues?.get(1)
    return labeled?.let { "${it}p" } ?: "Video"
}
