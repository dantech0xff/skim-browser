package com.dantech.skimbrowser.browser.downloads

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MimeSnifferTest {

    @Test
    fun documentsAndImagesAreKnownByTheirFirstBytes() {
        assertEquals("application/pdf", sniffMime(ascii("%PDF-1.7\n%\u00e2\u00e3")))
        assertEquals("image/png", sniffMime(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)))
        assertEquals("image/jpeg", sniffMime(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))
        assertEquals("image/gif", sniffMime(ascii("GIF89a\u0001\u0000")))
        assertEquals("image/gif", sniffMime(ascii("GIF87a\u0001\u0000")))
        assertEquals("image/webp", sniffMime(ascii("RIFF\u0010\u0000\u0000\u0000WEBPVP8 ")))
    }

    @Test
    fun audioIsKnownByItsFirstBytes() {
        assertEquals("audio/x-wav", sniffMime(ascii("RIFF\u0010\u0000\u0000\u0000WAVEfmt ")))
        assertEquals("audio/ogg", sniffMime(ascii("OggS\u0000\u0002")))
        assertEquals("audio/flac", sniffMime(ascii("fLaC\u0000\u0000")))
        assertEquals("audio/mpeg", sniffMime(ascii("ID3\u0004\u0000")))
        assertEquals("audio/mpeg", sniffMime(bytes(0xFF, 0xFB, 0x90, 0x64)))
    }

    @Test
    fun isoMediaFilesAreTypedByTheirBrand() {
        assertEquals("video/mp4", sniffMime(ftyp("isom")))
        assertEquals("video/mp4", sniffMime(ftyp("mp42")))
        assertEquals("audio/mp4", sniffMime(ftyp("M4A ")))
        assertEquals("video/quicktime", sniffMime(ftyp("qt  ")))
        assertEquals("video/3gpp", sniffMime(ftyp("3gp5")))
        assertEquals("image/heic", sniffMime(ftyp("heic")))
        assertEquals("image/heif", sniffMime(ftyp("mif1")))
        assertEquals("image/avif", sniffMime(ftyp("avif")))
    }

    @Test
    fun aZipArchiveIsOnlyEverAZip() {
        val zip = bytes(0x50, 0x4B, 0x03, 0x04, 0x14, 0, 0, 0)

        assertEquals("application/zip", sniffMime(zip))
        assertEquals("application/zip", sniffMime(bytes(0x50, 0x4B, 0x05, 0x06)))
        assertEquals("an app is a ZIP too, and is never called one", "application/zip", downloadMimeOf(zip, APK, "Invoice.apk"))
        assertEquals("application/zip", downloadMimeOf(zip, null, "app.apk"))
    }

    @Test
    fun unknownBytesAndTextAreNotSniffed() {
        assertNull(sniffMime(ascii("<!doctype html><html>")))
        assertNull(sniffMime(ascii("hello, world")))
        assertNull(sniffMime(ByteArray(0)))
        assertNull("too short to be a WebP", sniffMime(ascii("RIFF\u0010\u0000")))
        assertNull("RIFF of another form", sniffMime(ascii("RIFF\u0010\u0000\u0000\u0000AVI LIST")))
    }

    @Test
    fun theBytesWinOverWhatThePageDeclared() {
        val pdf = ascii("%PDF-1.4")

        assertEquals("application/pdf", downloadMimeOf(pdf, APK, "Invoice.apk"))
        assertEquals("application/pdf", downloadMimeOf(pdf, "text/html; charset=utf-8", "page.html"))
        assertEquals("application/pdf", downloadMimeOf(pdf, null, null))
    }

    @Test
    fun withoutKnownBytesTheDeclaredTypeCounts() {
        val unknown = ascii("plain words")

        assertEquals("text/csv", downloadMimeOf(unknown, "Text/CSV; charset=utf-8", "data.csv"))
        assertEquals(APK, downloadMimeOf(unknown, APK, "app.apk"))
        assertEquals("application/octet-stream", downloadMimeOf(unknown, null, "notes"))
        assertEquals("a value that is no type is ignored", "application/octet-stream", downloadMimeOf(unknown, "garbage", null))
    }

    @Test
    fun documentsBuiltOnAZipKeepTheirOwnType() {
        val zip = bytes(0x50, 0x4B, 0x03, 0x04)

        assertEquals(DOCX, downloadMimeOf(zip, DOCX, "report.docx"))
        assertEquals("the name says so when the server does not", DOCX, downloadMimeOf(zip, "application/octet-stream", "report.docx"))
        assertEquals("application/vnd.oasis.opendocument.spreadsheet", downloadMimeOf(zip, null, "sheet.ods"))
        assertEquals("application/epub+zip", downloadMimeOf(zip, "application/epub+zip", "book"))
        assertEquals("a PDF type cannot ride on a ZIP", "application/zip", downloadMimeOf(zip, "application/pdf", "report.pdf"))
    }

    @Test
    fun aMadeUpTypeOfADocumentFamilyOnAZipIsAZip() {
        val zip = bytes(0x50, 0x4B, 0x03, 0x04)

        listOf("application/vnd.oasis.opendocument.update", "application/vnd.openxmlformats-officedocument.installer").forEach { madeUp ->
            assertEquals(madeUp, "application/zip", downloadMimeOf(zip, madeUp, "update.apk"))
            assertEquals("update.zip", DownloadNames.sanitize("update.apk", downloadMimeOf(zip, madeUp, "update.apk")))
        }
    }

    @Test
    fun mediaBuiltOnTheirContainerKeepTheirOwnType() {
        assertEquals("audio/mp4", downloadMimeOf(ftyp("isom"), "audio/mp4", "song.m4a"))
        assertEquals("video/ogg", downloadMimeOf(ascii("OggS\u0000\u0002"), "video/ogg", "clip.ogv"))
        assertEquals("video/mp4", downloadMimeOf(ftyp("isom"), APK, "clip.apk"))
    }

    private fun ftyp(brand: String): ByteArray = bytes(0, 0, 0, 0x20) + ascii("ftyp$brand") + bytes(0, 0, 0, 0)

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private companion object {
        const val APK = "application/vnd.android.package-archive"
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
