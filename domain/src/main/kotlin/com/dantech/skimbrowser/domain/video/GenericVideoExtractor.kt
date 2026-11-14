package com.dantech.skimbrowser.domain.video

/** A direct, HLS, or DASH address already seen on the page, on any site. */
class GenericVideoExtractor : VideoExtractor {
    override val id: String = "generic"

    override fun matches(pageUrl: String): Boolean = pageUrl.startsWith("http://") || pageUrl.startsWith("https://")

    override suspend fun extract(hints: PageVideoHints): List<VideoVariant> = hints.mediaUrls.mapNotNull { url ->
        if (url.startsWith(PAGE_DOCUMENT)) return@mapNotNull null
        when (mediaStream(url, null)) {
            MediaStream.Progressive -> variant(hints.pageTitle, qualityFromUrl(url), "video/mp4", VideoSource.Progressive(url), hints.pageUrl)
            MediaStream.Hls -> variant(hints.pageTitle, "HLS", "application/vnd.apple.mpegurl", VideoSource.Hls(url), hints.pageUrl)
            MediaStream.Dash -> variant(hints.pageTitle, "DASH", "application/dash+xml", VideoSource.Dash(url), hints.pageUrl)
            null -> null
        }
    }
}

internal fun qualityFromUrl(url: String): String {
    val path = url.substringBefore('?')
    val labeled = Regex("(?:DASH_|_|/)(\\d{3,4})(?:p)?(?:\\.|_|$)").find(path)?.groupValues?.get(1)
    return labeled?.let { "${it}p" } ?: "Video"
}
