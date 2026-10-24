package com.dantech.skimbrowser.browser.downloads

import android.webkit.MimeTypeMap
import com.dantech.skimbrowser.browser.toVisibleText
import java.util.Locale

/** File names for downloads, which pages control and may craft to mislead or to escape a folder. */
object DownloadNames {

    /** The name used when nothing usable is left of the page's name. */
    const val FALLBACK_NAME = "download"

    /**
     * A safe file name for a download that the page called [raw] (from Content-Disposition, a
     * `download` attribute or the URL) and whose type is [mime] (sniffed from the first bytes when
     * known, else the server's).
     *
     * Only the last path segment is kept, so `../` and backslashes cannot leave the Downloads
     * folder; control, bidi and zero-width characters are removed, so "Invoice" + U+202E + "fdp.apk" cannot
     * pass for a PDF; characters storage rejects become `_`; leading and trailing dots go. The
     * extension follows [mime]: one that names another type is replaced, a missing one is added,
     * and only a generic type (`application/octet-stream`, `text/plain`) leaves the name's own. A
     * type Android has no extension for drops an extension that names another type. The result
     * fits in [MAX_NAME_BYTES] bytes of UTF-8 with its extension intact.
     */
    fun sanitize(raw: String?, mime: String?): String {
        val type = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.ifEmpty { null }
        val cleaned = raw.orEmpty()
            .replace('\\', '/')
            .substringAfterLast('/')
            .toVisibleText()
            .map { if (it in RESERVED) '_' else it }
            .joinToString("")
            .trim { it == '.' || it.isWhitespace() }

        val dot = cleaned.lastIndexOf('.')
        val ownExtension = cleaned.substring(dot + 1).takeIf { dot > 0 && isExtension(it) }
        val base = (if (ownExtension != null) cleaned.substring(0, dot) else cleaned)
            .trim { it == '.' || it.isWhitespace() }
            .ifEmpty { FALLBACK_NAME }
        return fit(base, extensionFor(ownExtension, type))
    }

    private fun extensionFor(own: String?, type: String?): String? {
        if (type == null || type in GENERIC_TYPES) {
            return own ?: if (type == TEXT_PLAIN) "txt" else null
        }
        val map = MimeTypeMap.getSingleton()
        val ownType = own?.let { map.getMimeTypeFromExtension(it.lowercase(Locale.ROOT)) }
        // A type Android has no extension for keeps the name's own only when that names no other type.
        val expected = map.getExtensionFromMimeType(type) ?: return own?.takeIf { ownType == null || ownType == type }
        if (own != null && ownType == type) return own
        return expected
    }

    /** [base] cut by whole code points so that [base] plus [extension] fit in [MAX_NAME_BYTES]. */
    private fun fit(base: String, extension: String?): String {
        val suffix = extension?.let { ".$it" }.orEmpty()
        val budget = MAX_NAME_BYTES - suffix.utf8Length()
        val kept = StringBuilder()
        var used = 0
        var index = 0
        while (index < base.length) {
            val codePoint = base.codePointAt(index)
            val size = utf8Length(codePoint)
            if (used + size > budget) break
            kept.appendCodePoint(codePoint)
            used += size
            index += Character.charCount(codePoint)
        }
        val trimmedBase = kept.toString().trim { it == '.' || it.isWhitespace() }.ifEmpty { FALLBACK_NAME }
        return trimmedBase + suffix
    }

    private fun isExtension(text: String) = text.length in 1..MAX_EXTENSION_LENGTH && text.all { it.isLetterOrDigit() }

    private fun String.utf8Length(): Int {
        var total = 0
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            total += utf8Length(codePoint)
            index += Character.charCount(codePoint)
        }
        return total
    }

    private fun utf8Length(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    /** File systems allow 255 bytes; the rest leaves room for the " (1)" MediaStore adds to repeats. */
    private const val MAX_NAME_BYTES = 200
    private const val MAX_EXTENSION_LENGTH = 16
    private const val TEXT_PLAIN = "text/plain"

    private val RESERVED = setOf('"', '*', ':', '<', '>', '?', '|', '/')

    /** Types that say nothing about the file, so the page's own extension stays. */
    private val GENERIC_TYPES = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/download",
        "application/force-download",
        "application/x-download",
        "application/unknown",
        TEXT_PLAIN,
    )
}
