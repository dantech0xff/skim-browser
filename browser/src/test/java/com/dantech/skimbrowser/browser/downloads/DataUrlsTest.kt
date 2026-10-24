package com.dantech.skimbrowser.browser.downloads

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DataUrlsTest {

    @Test
    fun aPercentEncodedBodyIsDecodedToItsBytes() {
        val data = assertParsed("data:text/plain,hello%20world%21")

        assertEquals("text/plain", data.mimeType)
        assertEquals("hello world!", data.bytes.decodeToString())
    }

    @Test
    fun aBase64BodyIsDecodedWithItsType() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val data = assertParsed("data:image/png;base64," + Base64.getEncoder().encodeToString(png))

        assertEquals("image/png", data.mimeType)
        assertArrayEquals(png, data.bytes)
    }

    @Test
    fun theTypeAndTheBase64MarkerAreCaseInsensitiveAndParametersAreDropped() {
        val data = assertParsed("DATA:Application/PDF;Name=Report.pdf; BASE64,JVBERi0=")

        assertEquals("application/pdf", data.mimeType)
        assertEquals("%PDF-", data.bytes.decodeToString())
    }

    @Test
    fun aMissingOrInvalidTypeReadsAsPlainText() {
        assertEquals("text/plain", assertParsed("data:,hi").mimeType)
        assertEquals("text/plain", assertParsed("data:;base64,aGk=").mimeType)
        assertEquals("text/plain", assertParsed("data:not a type,hi").mimeType)
    }

    @Test
    fun base64IgnoresWhiteSpaceMissingPaddingAndPercentEscapes() {
        assertEquals("Hello", assertParsed("data:text/plain;base64,SGVs bG8=").bytes.decodeToString())
        assertEquals("Hello", assertParsed("data:text/plain;base64,SGVsbG8").bytes.decodeToString())
        assertEquals("Hello", assertParsed("data:text/plain;base64,SGVsbG8%3D").bytes.decodeToString())
        assertEquals("Hello", assertParsed("data:text/plain;base64,SGVs%0AbG8=").bytes.decodeToString())
    }

    @Test
    fun aBrokenPercentEscapeStaysAsItIs() {
        assertEquals("100%zz", assertParsed("data:,100%zz").bytes.decodeToString())
        assertEquals("50%", assertParsed("data:,50%").bytes.decodeToString())
    }

    @Test
    fun nonAsciiTextIsKeptAsUtf8() {
        assertEquals("Đà Nẵng", assertParsed("data:text/plain;charset=utf-8,Đà Nẵng").bytes.decodeToString())
    }

    @Test
    fun malformedUrlsAreRefused() {
        assertNull(parseDataUrl("data:text/plain;base64"))
        assertNull(parseDataUrl("data:text/plain;base64,SGVs*G8="))
        assertNull(parseDataUrl("data:text/plain;base64,SGVsbG8-_w=="))
        assertNull(parseDataUrl("https://example.com/file.pdf"))
        assertNull(parseDataUrl("blob:https://example.com/0f5c"))
    }

    @Test
    fun contentsOverTheCapAreRefused() {
        assertNotNull(parseDataUrl("data:,0123456789", maxBytes = 10))
        assertNull(parseDataUrl("data:,0123456789A", maxBytes = 10))

        val tenBytes = Base64.getEncoder().encodeToString(ByteArray(10))
        val elevenBytes = Base64.getEncoder().encodeToString(ByteArray(11))
        assertNotNull(parseDataUrl("data:application/octet-stream;base64,$tenBytes", maxBytes = 10))
        assertNull(parseDataUrl("data:application/octet-stream;base64,$elevenBytes", maxBytes = 10))
    }

    @Test
    fun aBodyFarOverTheCapIsRefusedBeforeDecoding() {
        val huge = "data:application/octet-stream;base64," + "A".repeat(1_000)

        assertNull(parseDataUrl(huge, maxBytes = 100))
    }

    @Test
    fun aBase64BodyIsMeasuredBeforeItIsCopied() {
        val hundred = Base64.getEncoder().encodeToString(ByteArray(100))
        assertEquals(136, hundred.length)
        fun padded(spaces: Int) = "data:application/octet-stream;base64," + hundred.substring(0, 68) + " ".repeat(spaces) + hundred.substring(68)

        // White space that decoding skips still counts: the length alone bounds the work.
        assertEquals(100, parseDataUrl(padded(8), maxBytes = 100)?.bytes?.size)
        assertNull(parseDataUrl(padded(9), maxBytes = 100))
        assertNull("far under the bound for a body of percent escapes", parseDataUrl(padded(150), maxBytes = 100))
        assertEquals("a body of percent escapes may be three times as long", 100, parseDataUrl("data:," + "%41".repeat(100), maxBytes = 100)?.bytes?.size)
    }

    @Test
    fun theDefaultCapIs25Megabytes() {
        assertEquals(25 * 1024 * 1024, MAX_INLINE_DOWNLOAD_BYTES)
    }

    private fun assertParsed(url: String): DataUrl = parseDataUrl(url) ?: throw AssertionError("$url did not parse")
}
