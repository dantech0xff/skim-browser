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

/** Lowercases [host] and drops a leading "www.", the form every host-keyed table stores. */
fun normalizeHost(host: String): String {
    val lower = host.trim().lowercase()
    return if (lower.startsWith("www.") && lower.length > "www.".length) lower.substring("www.".length) else lower
}
