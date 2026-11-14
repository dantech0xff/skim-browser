package com.dantech.skimbrowser.domain.news

import java.net.URI
import java.net.URISyntaxException

/** https feed addresses a person may add. Anything else is refused before it is stored or fetched. */
object NewsUrls {
    const val MAX_LENGTH = 2048

    fun canonical(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        val uri = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            return null
        }
        if (!uri.isAbsolute || uri.scheme != "https" || uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        if (host.isEmpty() || !host.contains('.')) return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.rawPath ?: ""
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return "https://$host$port$path$query"
    }

    fun hostLabel(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")
        return host.ifBlank { url }
    }

    fun customId(url: String): String = "custom:$url"
}
