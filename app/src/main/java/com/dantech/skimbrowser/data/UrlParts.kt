package com.dantech.skimbrowser.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * True when [url] uses the http or https scheme, in any letter case. Only these URLs are web pages
 * that Skim records, bookmarks from a file, or keys site decisions by.
 */
fun isWebUrl(url: String): Boolean {
    val trimmed = url.trim()
    return trimmed.startsWith("http:", ignoreCase = true) || trimmed.startsWith("https:", ignoreCase = true)
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
    val host = url.trim().toHttpUrlOrNull()?.host ?: return null
    val display = if (':' in host) "[$host]" else host
    return normalizeHost(display).ifEmpty { null }
}

/** [url] without its `#fragment`, which never changes the page a chat is about. */
fun withoutFragment(url: String): String = url.substringBefore('#')

/** Lowercases [host] and drops a leading "www.", the form every host-keyed table stores. */
internal fun normalizeHost(host: String): String {
    val lower = host.trim().lowercase()
    return if (lower.startsWith("www.") && lower.length > "www.".length) lower.substring("www.".length) else lower
}
