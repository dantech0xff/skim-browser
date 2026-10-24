package com.dantech.skimbrowser.domain.text

/** [this] cut to at most [max] UTF-16 units, never between the two halves of a surrogate pair. */
fun String.cutTo(max: Int): String {
    if (length <= max) return this
    if (max <= 0) return ""
    val end = if (this[max - 1].isHighSurrogate()) max - 1 else max
    return substring(0, end)
}

/**
 * [this] as plain visible text: control characters and line or paragraph separators become
 * spaces, characters that are invisible or reorder text are dropped (bidi embeddings, overrides and
 * isolates, zero-width characters, soft hyphens, Unicode tag characters), runs of white space
 * collapse to one space, and the ends are trimmed.
 *
 * Pages use those characters to hide text from the reader while a model still reads it, or to
 * make a file name read backwards: "Invoice", U+202E, "fdp.apk" displays as "Invoiceapk.pdf".
 */
fun String.toVisibleText(): String {
    val out = StringBuilder(length)
    var pendingSpace = false
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        index += Character.charCount(codePoint)
        when {
            isSpacing(codePoint) -> pendingSpace = out.isNotEmpty()
            isInvisible(codePoint) -> Unit
            else -> {
                if (pendingSpace) out.append(' ')
                pendingSpace = false
                out.appendCodePoint(codePoint)
            }
        }
    }
    return out.toString()
}

private fun isSpacing(codePoint: Int): Boolean =
    Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) ||
        codePoint in 0x00..0x1F || codePoint in 0x7F..0x9F

private fun isInvisible(codePoint: Int): Boolean = when (codePoint) {
    0x00AD, // soft hyphen
    0x061C, // Arabic letter mark
    0x180E, // Mongolian vowel separator
    0x200B, 0x200C, 0x200D, // zero-width space, non-joiner, joiner
    0x200E, 0x200F, // left-to-right and right-to-left marks
    0xFEFF, // zero-width no-break space
    -> true
    in 0x202A..0x202E -> true // bidi embeddings and overrides
    in 0x2060..0x2064 -> true // word joiner, invisible operators
    in 0x2066..0x2069 -> true // bidi isolates
    in 0xFFF9..0xFFFB -> true // interlinear annotation
    in 0xE0000..0xE007F -> true // tag characters, which can spell out hidden text
    else -> false
}
