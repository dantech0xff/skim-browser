package com.dantech.skimbrowser.domain.video

data class PageVideoHints(
    val pageUrl: String,
    val pageTitle: String?,
    val mediaUrls: List<String>,
    val userAgent: String?,
    val cookieHeader: String?,
    /** Page JSON the probe found. Kept apart from [mediaUrls], which is only sniffed files. */
    val pageDocuments: List<String> = emptyList(),
)

sealed interface VideoSource {
    data class Progressive(val url: String, val headers: Map<String, String> = emptyMap()) : VideoSource
    data class Hls(
        val playlistUrl: String,
        val headers: Map<String, String> = emptyMap(),
        val audioPlaylistUrl: String? = null,
    ) : VideoSource
    data class Dash(val manifestUrl: String, val headers: Map<String, String> = emptyMap()) : VideoSource
    data class SeparateAv(val videoUrl: String, val audioUrl: String, val headers: Map<String, String> = emptyMap()) : VideoSource
}

data class VideoVariant(val title: String, val qualityLabel: String, val mimeType: String?, val sizeBytes: Long?, val source: VideoSource)

interface VideoExtractor {
    val id: String
    fun matches(pageUrl: String): Boolean

    /**
     * True when [matches] is enough to offer a download before the page has revealed a media
     * address. A file or a playlist still found on the page is offered either way.
     */
    val pageUrlSufficient: Boolean get() = false

    suspend fun extract(hints: PageVideoHints): List<VideoVariant>
}

/** A download can be offered for this page: a media address was seen, or an extractor knows the page. */
fun videoCanBeSaved(
    pageUrl: String?,
    mediaUrls: List<String>,
    extractors: Iterable<VideoExtractor>,
    pageDocuments: List<String> = emptyList(),
): Boolean {
    if (mediaUrls.isNotEmpty() || pageDocuments.isNotEmpty()) return true
    val page = pageUrl ?: return false
    return extractors.any { extractor -> extractor.pageUrlSufficient && extractor.matches(page) }
}
