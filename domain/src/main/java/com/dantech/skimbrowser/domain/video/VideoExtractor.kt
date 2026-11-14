package com.dantech.skimbrowser.domain.video

data class PageVideoHints(val pageUrl: String, val pageTitle: String?, val mediaUrls: List<String>, val userAgent: String?, val cookieHeader: String?)

sealed interface VideoSource {
    data class Progressive(val url: String, val headers: Map<String, String> = emptyMap()) : VideoSource
    data class Hls(val playlistUrl: String, val headers: Map<String, String> = emptyMap()) : VideoSource
    data class Dash(val manifestUrl: String, val headers: Map<String, String> = emptyMap()) : VideoSource
    data class SeparateAv(val videoUrl: String, val audioUrl: String, val headers: Map<String, String> = emptyMap()) : VideoSource
}

data class VideoVariant(val title: String, val qualityLabel: String, val mimeType: String?, val sizeBytes: Long?, val source: VideoSource)

interface VideoExtractor {
    val id: String
    fun matches(pageUrl: String): Boolean
    suspend fun extract(hints: PageVideoHints): List<VideoVariant>
}
