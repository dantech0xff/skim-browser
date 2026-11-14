package com.dantech.skimbrowser.extractor.youtube

import java.net.URI

internal object YoutubeUrls {
    fun matches(pageUrl: String): Boolean {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        return when (host) {
            "youtu.be" -> pathSegment(uri, 0) != null && pathSegment(uri, 1) == null
            "youtube.com", "m.youtube.com", "music.youtube.com" -> isVideoPage(uri)
            else -> false
        }
    }

    private fun isVideoPage(uri: URI): Boolean {
        val head = pathSegment(uri, 0) ?: return false
        return when (head) {
            "watch" -> queryParam(uri.query, "v") != null
            "shorts", "embed", "live", "v" -> pathSegment(uri, 1) != null
            else -> false
        }
    }

    private fun pathSegment(uri: URI, index: Int): String? =
        uri.path.orEmpty().split('/').filter { it.isNotEmpty() }.getOrNull(index)?.takeIf { it.isNotBlank() }

    private fun queryParam(query: String?, name: String): String? {
        if (query.isNullOrBlank()) return null
        return query.split('&').firstNotNullOfOrNull { part ->
            val pieces = part.split('=', limit = 2)
            val key = pieces[0]
            val value = pieces.getOrElse(1) { "" }
            value.takeIf { key == name && it.isNotBlank() }
        }
    }
}
