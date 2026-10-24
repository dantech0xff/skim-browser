package com.dantech.skimbrowser.browser.downloads

import android.webkit.MimeTypeMap
import java.util.Locale

/** How many first bytes of a download [sniffMime] needs at most. */
const val SNIFF_BYTES = 64

/**
 * The type of a file whose first bytes are [head], when they are those of one of a few common
 * formats (PDF, PNG, JPEG, GIF, WebP, WAV, ZIP, Ogg, FLAC, MP3, and the ISO media family: MP4,
 * M4A, QuickTime, 3GP, HEIC, HEIF, AVIF); null for anything else, text included.
 *
 * A page names and types its downloads as it likes, but the bytes say what the file is. A ZIP
 * archive is only ever `application/zip`: an Android app (APK) is a ZIP archive as well, and no
 * bytes ever make a download an app.
 */
fun sniffMime(head: ByteArray): String? = when {
    head.startsWith(PDF) -> "application/pdf"
    head.startsWith(PNG) -> "image/png"
    head.startsWith(JPEG) -> "image/jpeg"
    head.startsWith(GIF87A) || head.startsWith(GIF89A) -> "image/gif"
    head.isRiff(WEBP) -> "image/webp"
    head.isRiff(WAVE) -> "audio/x-wav"
    ZIP_SIGNATURES.any(head::startsWith) -> ZIP
    head.startsWith(OGG) -> OGG_AUDIO
    head.startsWith(FLAC) -> "audio/flac"
    head.startsWith(ID3) || head.isMp3Frame() -> "audio/mpeg"
    head.startsWithAt(FTYP_OFFSET, FTYP) -> isoMediaType(head)
    else -> null
}

/**
 * The type a download is saved as, given its first bytes [head], the type the server or the page
 * declared ([declared]) and the name it gave ([rawName]).
 *
 * The sniffed type wins over a declared one, which only counts when the bytes are none of the
 * formats [sniffMime] knows; without either the file is `application/octet-stream`. One exception
 * keeps documents usable: a format built on a container the bytes show (a Word, Excel or
 * PowerPoint file, an OpenDocument or an EPUB is a ZIP archive; an M4A is an MP4 file; an Ogg
 * video is an Ogg file) stays that format when the declared type or the name's extension says so.
 * An app is never such a format, so a ZIP archive declared or named as an APK is saved as a ZIP.
 */
fun downloadMimeOf(head: ByteArray, declared: String?, rawName: String?): String {
    val stated = mimeEssenceOf(declared)
    val sniffed = sniffMime(head) ?: return stated ?: OCTET_STREAM
    val named = rawName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    return listOfNotNull(stated, named).firstOrNull { refines(sniffed, it) } ?: sniffed
}

/** The `type/subtype` of [mime] in lowercase, without parameters; null when there is none. */
internal fun mimeEssenceOf(mime: String?): String? =
    mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.contains('/') && !it.startsWith('/') && !it.endsWith('/') }

/**
 * True when [specific] is a format built on the container type [sniffed]. A ZIP-based format
 * must be one Android knows: a made-up type in the family would keep a page's own extension, `.apk`
 * included.
 */
private fun refines(sniffed: String, specific: String): Boolean = when (sniffed) {
    ZIP -> (ZIP_FORMAT_PREFIXES.any(specific::startsWith) || specific in ZIP_FORMATS) &&
        MimeTypeMap.getSingleton().getExtensionFromMimeType(specific) != null
    MP4_VIDEO -> specific in MP4_FORMATS
    OGG_AUDIO -> specific in OGG_FORMATS
    else -> false
}

/** The type of an ISO base media file ("ftyp" box first) by its major brand. */
private fun isoMediaType(head: ByteArray): String {
    if (head.size < BRAND_OFFSET + BRAND_LENGTH) return MP4_VIDEO
    val brand = String(head, BRAND_OFFSET, BRAND_LENGTH, Charsets.ISO_8859_1)
    return when {
        brand == "avif" || brand == "avis" -> "image/avif"
        brand in HEIC_BRANDS -> "image/heic"
        brand == "mif1" || brand == "msf1" -> "image/heif"
        brand == "M4A " || brand == "M4B " || brand == "M4P " -> "audio/mp4"
        brand == "qt  " -> "video/quicktime"
        brand.startsWith("3gp") || brand.startsWith("3g2") -> "video/3gpp"
        else -> MP4_VIDEO
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = startsWithAt(0, prefix)

private fun ByteArray.startsWithAt(offset: Int, prefix: ByteArray): Boolean {
    if (size < offset + prefix.size) return false
    for (index in prefix.indices) {
        if (this[offset + index] != prefix[index]) return false
    }
    return true
}

/** A RIFF file ("RIFF", a 4-byte size, then the form type) of form [form]. */
private fun ByteArray.isRiff(form: ByteArray): Boolean = startsWith(RIFF) && startsWithAt(RIFF_FORM_OFFSET, form)

/** An MPEG-1 or MPEG-2 Layer III frame header without an ID3 tag before it. */
private fun ByteArray.isMp3Frame(): Boolean {
    if (size < 2 || this[0] != 0xFF.toByte()) return false
    val second = this[1].toInt() and 0xFF
    return second == 0xFB || second == 0xF3 || second == 0xF2
}

private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private const val ZIP = "application/zip"
private const val MP4_VIDEO = "video/mp4"
private const val OGG_AUDIO = "audio/ogg"
private const val OCTET_STREAM = "application/octet-stream"
private const val RIFF_FORM_OFFSET = 8
private const val FTYP_OFFSET = 4
private const val BRAND_OFFSET = 8
private const val BRAND_LENGTH = 4

private val PDF = ascii("%PDF-")
private val PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val JPEG = bytes(0xFF, 0xD8, 0xFF)
private val GIF87A = ascii("GIF87a")
private val GIF89A = ascii("GIF89a")
private val RIFF = ascii("RIFF")
private val WEBP = ascii("WEBP")
private val WAVE = ascii("WAVE")
private val ZIP_SIGNATURES = listOf(bytes(0x50, 0x4B, 0x03, 0x04), bytes(0x50, 0x4B, 0x05, 0x06), bytes(0x50, 0x4B, 0x07, 0x08))
private val OGG = ascii("OggS")
private val FLAC = ascii("fLaC")
private val ID3 = ascii("ID3")
private val FTYP = ascii("ftyp")
private val HEIC_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx")

/** Office Open XML and OpenDocument files, which are ZIP archives. */
private val ZIP_FORMAT_PREFIXES = listOf("application/vnd.openxmlformats-officedocument.", "application/vnd.oasis.opendocument.")
private val ZIP_FORMATS = setOf("application/epub+zip")
private val MP4_FORMATS = setOf("audio/mp4", "audio/x-m4a", "audio/m4a", "video/x-m4v", "video/m4v")
private val OGG_FORMATS = setOf("video/ogg", "application/ogg")
