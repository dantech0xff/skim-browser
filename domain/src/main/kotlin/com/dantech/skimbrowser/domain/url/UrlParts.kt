package com.dantech.skimbrowser.domain.url

import java.net.IDN

/**
 * True when [url] uses the http or https scheme, in any letter case. Only these URLs are web pages
 * that Skim records, bookmarks from a file, or keys site decisions by.
 */
fun isWebUrl(url: String): Boolean {
    val trimmed = url.trim()
    return trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
}

/**
 * The origin of an http(s) URL: scheme, host, and a port only when it is not the scheme's default.
 * A leading `www` stays, because that host is a different origin. Null when [url] has no web host.
 */
fun webOrigin(url: String): String? {
    if (!isWebUrl(url)) return null
    val trimmed = url.trim()
    val schemeEnd = trimmed.indexOf("://")
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    val rest = trimmed.substring(schemeEnd + 3).replace('\\', '/')
    val authority = rest.substringBefore('#').substringBefore('?').substringBefore('/')
    if (authority.isEmpty()) return null
    val hostPort = if ('@' in authority) authority.substringAfterLast('@') else authority
    if (hostPort.isEmpty() || ' ' in hostPort) return null
    val host: String
    val port: Int?
    if (hostPort.startsWith("[")) {
        val end = hostPort.indexOf(']')
        if (end < 1) return null
        host = hostPort.substring(0, end + 1).lowercase()
        val portText = hostPort.substring(end + 1)
        port = when {
            portText.isEmpty() -> null
            portText.startsWith(":") -> portText.substring(1).toIntOrNull() ?: return null
            else -> return null
        }
    } else {
        val colon = hostPort.lastIndexOf(':')
        if (colon >= 0) {
            host = hostPort.substring(0, colon)
            port = hostPort.substring(colon + 1).toIntOrNull() ?: return null
        } else {
            host = hostPort
            port = null
        }
        if (host.isEmpty()) return null
    }
    val ascii = if (host.startsWith("[")) {
        host
    } else {
        try {
            IDN.toASCII(host).lowercase()
        } catch (_: IllegalArgumentException) {
            return null
        }
    }
    if (ascii.isEmpty()) return null
    val defaultPort = if (scheme == "https") 443 else 80
    val shownPort = if (port == null || port == defaultPort) "" else ":$port"
    return "$scheme://$ascii$shownPort"
}

/** True when [pageUrl] and [sourceOrigin] are the same http(s) origin. */
fun sameWebOrigin(pageUrl: String, sourceOrigin: String): Boolean {
    val page = webOrigin(pageUrl) ?: return false
    return page == webOrigin(sourceOrigin)
}

/**
 * The site key of [url]: its host in lowercase without a leading "www.", as the address bar and
 * site permissions use it. Null when [url] is not an http(s) URL with a valid host, so
 * `javascript:`, `data:`, `about:blank`, `file:` and `view-source:` URLs have no site.
 *
 * Parsing follows the URL standard browsers use: user info and port are dropped, a backslash ends
 * the host like a slash, and a Unicode host becomes its punycode form, so the key matches the page
 * the browser actually loads. An IPv6 host keeps its brackets.
 */
fun hostOf(url: String): String? {
    if (!isWebUrl(url)) return null
    val rest = url.trim().substringAfter("://").replace('\\', '/')
    val authority = rest.substringBefore('#').substringBefore('?').substringBefore('/')
    if (authority.isEmpty()) return null
    val hostPort = if ('@' in authority) authority.substringAfterLast('@') else authority
    if (hostPort.isEmpty() || ' ' in hostPort) return null
    val host = if (hostPort.startsWith("[")) {
        val end = hostPort.indexOf(']')
        if (end < 1) return null
        hostPort.substring(0, end + 1).lowercase()
    } else {
        val name = hostPort.substringBefore(':')
        if (name.isEmpty() || ' ' in name) return null
        try {
            IDN.toASCII(name)
        } catch (_: IllegalArgumentException) {
            return null
        }
    }
    return normalizeHost(host).ifEmpty { null }
}

/** [url] without its `#fragment`, which never changes the page a chat is about. */
fun withoutFragment(url: String): String = url.substringBefore('#')

/**
 * The https form of an http URL. Null when [url] is not http, or when its host is loopback:
 * a local page is left on http because nothing there is listening on https.
 */
fun httpsUpgrade(url: String): String? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true)) return null
    val host = hostOf(trimmed) ?: return null
    if (host == "localhost" || host == "127.0.0.1" || host == "[::1]") return null
    val rest = trimmed.substring(trimmed.indexOf("://") + 3)
    return "https://" + withoutHttpDefaultPort(rest)
}

/** Drops an explicit port 80. https on port 80 is not the service that answered http. */
private fun withoutHttpDefaultPort(rest: String): String {
    val split = rest.indexOfAny(charArrayOf('/', '?', '#'))
    val authorityEnd = if (split < 0) rest.length else split
    val authority = rest.substring(0, authorityEnd)
    val tail = rest.substring(authorityEnd)
    val at = authority.lastIndexOf('@')
    val userInfo = if (at < 0) "" else authority.substring(0, at + 1)
    val hostPort = if (at < 0) authority else authority.substring(at + 1)
    val hostOnly = if (hostPort.startsWith("[")) {
        val bracket = hostPort.indexOf(']')
        if (bracket >= 0 && hostPort.substring(bracket + 1) == ":80") hostPort.substring(0, bracket + 1) else hostPort
    } else if (hostPort.endsWith(":80")) {
        hostPort.removeSuffix(":80")
    } else {
        hostPort
    }
    return userInfo + hostOnly + tail
}

/**
 * True when [a] and [b] are the same navigation. The host and scheme are compared without letter
 * case, a fragment is ignored, and one trailing slash does not make them different. The path and
 * query keep their case, which is how a WebView reports the page it tried to load.
 */
fun sameNavigation(a: String, b: String): Boolean = navigationKey(a) == navigationKey(b)

private fun navigationKey(url: String): String {
    val noHash = url.trim().substringBefore('#').trimEnd('/')
    val marker = "://"
    val split = noHash.indexOf(marker)
    if (split < 0) return noHash.lowercase()
    val scheme = noHash.substring(0, split).lowercase()
    val rest = noHash.substring(split + marker.length)
    val slash = rest.indexOf('/')
    val authority = (if (slash < 0) rest else rest.substring(0, slash)).lowercase()
    val pathAndQuery = if (slash < 0) "" else rest.substring(slash)
    return "$scheme://$authority$pathAndQuery"
}

/** Lowercases [host] and drops a leading "www.", the form every host-keyed table stores. */
fun normalizeHost(host: String): String {
    val lower = host.trim().lowercase()
    return if (lower.startsWith("www.") && lower.length > "www.".length) lower.substring("www.".length) else lower
}
