package com.dantech.skimbrowser.search

import com.dantech.skimbrowser.data.isWebUrl
import com.dantech.skimbrowser.data.settings.SearchEngine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val LOCALHOST = "localhost"
private const val MAX_HOST_LENGTH = 253
private const val MAX_LABEL_LENGTH = 63
private const val MAX_PORT = 65_535

/**
 * What Enter in the search field (04b) opens for [input], its ends trimmed: nothing (null) for blank
 * input, the web address it names (see [omniboxAddress]), or else [engine]'s results for the text.
 */
fun resolveOmnibox(input: String, engine: SearchEngine): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    return omniboxAddress(text) ?: engine.searchUrl(text)
}

/**
 * The web address [input] names, its ends trimmed, or null when it is text to search for (or
 * blank). Only http and https are addresses:
 * - an `http:` or `https:` URL that parses stays as typed, in any letter case;
 * - a host without a scheme opens over https: a domain (a dot between valid labels, a top-level
 *   label that is not all digits), `localhost`, an IPv4 address or a bracketed IPv6 address, with an
 *   optional port from 1 to 65535 and an optional path, query or fragment;
 * - every other scheme (`javascript:`, `file:`, `content:`, `intent:`, `data:`, `about:`...) is text.
 *
 * Text with white space is never an address, nor is text with control, bidi, zero-width or other
 * invisible characters: a URL loader drops some of them and others make an address read differently
 * from what it opens.
 */
internal fun omniboxAddress(input: String): String? {
    val text = input.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() } || text.hasInvisibleCharacters()) return null
    if (isHost(text)) return "https://$text".takeIf { it.toHttpUrlOrNull() != null }
    if (isWebUrl(text)) return text.takeIf { it.toHttpUrlOrNull() != null }
    return null
}

/** Control and format characters (bidi controls, zero-width characters, tags) and lone surrogates. */
private fun String.hasInvisibleCharacters(): Boolean = codePoints().anyMatch { codePoint ->
    when (Character.getType(codePoint).toByte()) {
        Character.CONTROL, Character.FORMAT, Character.SURROGATE -> true
        else -> false
    }
}

/** True when [text] is a host with an optional port, then nothing or a path, query or fragment. */
private fun isHost(text: String): Boolean {
    val authority = text.substring(0, text.indexOfAny(charArrayOf('/', '?', '#')).takeIf { it >= 0 } ?: text.length)
    val port: String?
    if (authority.startsWith('[')) {
        val close = authority.indexOf(']')
        if (close < 0 || !isIpv6(authority.substring(1, close))) return false
        val rest = authority.substring(close + 1)
        port = when {
            rest.isEmpty() -> null
            rest.startsWith(':') -> rest.substring(1)
            else -> return false
        }
    } else {
        val colon = authority.indexOf(':')
        val host = if (colon < 0) authority else authority.substring(0, colon)
        port = if (colon < 0) null else authority.substring(colon + 1)
        if (!host.equals(LOCALHOST, ignoreCase = true) && !isIpv4(host) && !isDomain(host)) return false
    }
    return port == null || isPort(port)
}

/** Hex digits and colons (with a dotted IPv4 tail allowed); the URL parser checks the rest. */
private fun isIpv6(host: String): Boolean =
    ':' in host && host.all { it == ':' || it == '.' || it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun isIpv4(host: String): Boolean {
    val parts = host.split('.')
    return parts.size == 4 && parts.all { part -> part.length in 1..3 && part.all { it in '0'..'9' } && part.toInt() <= 255 }
}

/**
 * Two or more labels of letters, digits and inner hyphens. The last one has a letter, so numbers
 * such as "1.5" or "3.14" are searched for, not opened.
 */
private fun isDomain(host: String): Boolean {
    if (host.length > MAX_HOST_LENGTH) return false
    val labels = host.split('.')
    if (labels.size < 2 || !labels.all(::isLabel)) return false
    return labels.last().codePoints().anyMatch(Character::isLetter)
}

private fun isLabel(label: String): Boolean =
    label.length in 1..MAX_LABEL_LENGTH && !label.startsWith('-') && !label.endsWith('-') &&
        label.codePoints().allMatch { it == '-'.code || Character.isLetterOrDigit(it) }

private fun isPort(port: String): Boolean =
    port.length in 1..5 && port.all { it in '0'..'9' } && port.toInt() in 1..MAX_PORT
