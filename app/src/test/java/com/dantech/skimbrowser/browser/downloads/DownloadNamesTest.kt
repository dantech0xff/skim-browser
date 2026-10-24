package com.dantech.skimbrowser.browser.downloads

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadNamesTest {

    @Test
    fun onlyTheLastPathSegmentIsKept() {
        assertEquals("passwd.txt", DownloadNames.sanitize("../../etc/passwd", "text/plain"))
        assertEquals("report.pdf", DownloadNames.sanitize("..\\..\\Windows\\report.pdf", "application/pdf"))
        assertEquals("report.pdf", DownloadNames.sanitize("/sdcard/Download/../../data/report.pdf", "application/pdf"))
    }

    @Test
    fun aNameThatIsOnlyDotsBecomesTheFallback() {
        assertEquals("download", DownloadNames.sanitize("..", null))
        assertEquals("download.pdf", DownloadNames.sanitize("../..", "application/pdf"))
        assertEquals("download.pdf", DownloadNames.sanitize(null, "application/pdf"))
        assertEquals("download", DownloadNames.sanitize("   ", "application/octet-stream"))
    }

    @Test
    fun aRightToLeftOverrideCannotDisguiseAnApp() {
        val disguised = "Invoice\u202Efdp.apk"

        assertEquals("Invoicefdp.apk", DownloadNames.sanitize(disguised, "application/vnd.android.package-archive"))
        assertEquals("Invoicefdp.pdf", DownloadNames.sanitize(disguised, "application/pdf"))
    }

    @Test
    fun bidiAndZeroWidthCharactersAreRemoved() {
        assertEquals(
            "report.pdf",
            DownloadNames.sanitize("\u2066re\u200Bport\u200D\uFEFF\u2069\u200E.pdf", "application/pdf"),
        )
    }

    @Test
    fun controlCharactersAndReservedCharactersCannotSurvive() {
        val name = DownloadNames.sanitize("a\u0000b\nc:d*e?f|g\"h<i>.pdf", "application/pdf")

        assertEquals("a b c_d_e_f_g_h_i_.pdf", name)
        assertTrue(name.none { it.isISOControl() })
    }

    @Test
    fun leadingDotsCannotHideTheFile() {
        assertEquals("hidden.pdf", DownloadNames.sanitize(".hidden.pdf", "application/pdf"))
    }

    @Test
    fun theExtensionFollowsTheType() {
        assertEquals("photo.jpg", DownloadNames.sanitize("photo", "image/jpeg"))
        assertEquals("photo.png", DownloadNames.sanitize("photo.jpg", "image/png"))
        assertEquals("report.pdf", DownloadNames.sanitize("report.exe", "application/pdf; charset=binary"))
    }

    @Test
    fun anExtensionThatFitsTheTypeIsKeptAsWritten() {
        assertEquals("photo.jpeg", DownloadNames.sanitize("photo.jpeg", "image/jpeg"))
        assertEquals("REPORT.PDF", DownloadNames.sanitize("REPORT.PDF", "application/pdf"))
    }

    @Test
    fun aGenericTypeKeepsTheNamesOwnExtension() {
        assertEquals("backup.tar.zst", DownloadNames.sanitize("backup.tar.zst", "application/octet-stream"))
        assertEquals("data.csv", DownloadNames.sanitize("data.csv", "text/plain"))
        assertEquals("notes.txt", DownloadNames.sanitize("notes", "text/plain"))
        assertEquals("archive", DownloadNames.sanitize("archive", null))
    }

    @Test
    fun aTypeWithoutAnExtensionDropsOneThatNamesAnotherType() {
        assertEquals("update", DownloadNames.sanitize("update.apk", "application/x-skim-unknown"))
        assertEquals("update", DownloadNames.sanitize("update.pdf", "application/x-skim-unknown"))
        assertEquals("one that names no type stays", "save.skim1", DownloadNames.sanitize("save.skim1", "application/x-skim-unknown"))
        assertEquals("save", DownloadNames.sanitize("save", "application/x-skim-unknown"))
    }

    @Test
    fun aLongNameIsCutAndKeepsItsExtension() {
        val name = DownloadNames.sanitize("a".repeat(500) + ".pdf", "application/pdf")

        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 200)
        assertEquals(200, name.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun aLongNonAsciiNameIsCutByBytesBetweenCharacters() {
        val name = DownloadNames.sanitize("Đà Nẵng ".repeat(40) + "😀".repeat(20) + ".pdf", "application/pdf")

        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 200)
        assertTrue(name.startsWith("Đà Nẵng Đà Nẵng"))
        // A cut through a surrogate pair would not survive the round trip through UTF-8.
        assertEquals(name, String(name.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }
}
