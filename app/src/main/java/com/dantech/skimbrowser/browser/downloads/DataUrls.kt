package com.dantech.skimbrowser.browser.downloads

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale

/** The contents of a `data:` URL: [bytes] of type [mimeType] (the type's essence, lowercase). */
class DataUrl(val mimeType: String, val bytes: ByteArray)

/** The largest file a `data:` or `blob:` download may produce. */
const val MAX_INLINE_DOWNLOAD_BYTES = 25 * 1024 * 1024

/**
 * Decodes a `data:` URL as browsers do (the data URL processor of the Fetch standard): the body is
 * percent-decoded, then base64-decoded when the media type ends in `;base64`; a missing or invalid
 * media type reads as `text/plain`. Returns null for anything but a well-formed `data:` URL (no
 * comma, invalid base64) and for contents larger than [maxBytes]. The body's length is checked
 * before anything is copied or decoded: a base64 body may be at most 4/3 of [maxBytes] long, any
 * other at most 3 times (a percent escape per byte), so a huge URL costs no memory beyond that.
 */
fun parseDataUrl(url: String, maxBytes: Int = MAX_INLINE_DOWNLOAD_BYTES): DataUrl? {
    val trimmed = url.trim()
    if (!trimmed.startsWith(DATA_SCHEME, ignoreCase = true)) return null
    val comma = trimmed.indexOf(',')
    if (comma < 0) return null
    val bodyLength = (trimmed.length - comma - 1).toLong()
    // A percent escape is the longest encoding of a byte, so a longer body is certainly too large.
    if (bodyLength > maxBytes.toLong() * 3 + BODY_SLACK) return null

    var mediaType = percentDecode(trimmed.substring(DATA_SCHEME.length, comma), skipWhitespace = false)
        .toString(Charsets.UTF_8)
        .trim()
    val base64 = BASE64_SUFFIX.containsMatchIn(mediaType)
    if (base64) mediaType = BASE64_SUFFIX.replace(mediaType, "")
    // Every 3 bytes take 4 base64 characters.
    if (base64 && bodyLength > (maxBytes.toLong() + 2) / 3 * 4 + BASE64_SLACK) return null
    val body = trimmed.substring(comma + 1)

    val bytes = if (base64) {
        val encoded = percentDecode(body, skipWhitespace = true)
        // Every 4 characters decode to 3 bytes, less at most 2 of padding.
        if (encoded.size.toLong() / 4 * 3 - 2 > maxBytes) return null
        try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return null
        }
    } else {
        percentDecode(body, skipWhitespace = false)
    }
    if (bytes.size > maxBytes) return null
    return DataUrl(essenceOf(mediaType), bytes)
}

/**
 * The bytes of [text] with every `%XY` escape decoded (a `%` without two hex digits stays as it
 * is), leaving out ASCII white space when [skipWhitespace], as base64 decoding ignores it.
 */
private fun percentDecode(text: String, skipWhitespace: Boolean): ByteArray {
    val out = ByteArrayOutputStream(text.length)
    var index = 0
    while (index < text.length) {
        val high = if (text[index] == '%' && index + 2 < text.length) Character.digit(text[index + 1], 16) else -1
        val low = if (high >= 0) Character.digit(text[index + 2], 16) else -1
        if (low >= 0) {
            val byte = high * 16 + low
            if (!skipWhitespace || !isAsciiWhitespace(byte)) out.write(byte)
            index += 3
            continue
        }
        val codePoint = text.codePointAt(index)
        val chars = Character.charCount(codePoint)
        if (!skipWhitespace || !isAsciiWhitespace(codePoint)) {
            if (codePoint < 0x80) out.write(codePoint) else out.write(text.substring(index, index + chars).toByteArray(Charsets.UTF_8))
        }
        index += chars
    }
    return out.toByteArray()
}

/** `type/subtype` of [mediaType] in lowercase, or `text/plain` when it is not a valid MIME type. */
private fun essenceOf(mediaType: String): String {
    val essence = mediaType.substringBefore(';').trim().lowercase(Locale.ROOT)
    return if (MIME_ESSENCE.matches(essence)) essence else DEFAULT_MIME
}

private fun isAsciiWhitespace(value: Int): Boolean =
    value == ' '.code || value == '\t'.code || value == '\n'.code || value == '\u000C'.code || value == '\r'.code

private const val DATA_SCHEME = "data:"
private const val DEFAULT_MIME = "text/plain"
private const val BODY_SLACK = 64

/** Padding and a line break or two that a base64 body may carry beyond its exact length. */
private const val BASE64_SLACK = 8
private const val MIME_TOKEN = "[!#$%&'*+.^_`|~0-9a-z-]+"
private val BASE64_SUFFIX = Regex(";\\s*base64\\s*$", RegexOption.IGNORE_CASE)
private val MIME_ESSENCE = Regex("$MIME_TOKEN/$MIME_TOKEN")
