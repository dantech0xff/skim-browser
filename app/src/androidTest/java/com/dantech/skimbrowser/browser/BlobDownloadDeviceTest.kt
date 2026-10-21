package com.dantech.skimbrowser.browser

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.downloads.DownloadEvent
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Downloads of a page's `blob:` URLs on the device's WebView: Skim reads the blob from the page
 * itself, one slice at a time, and saves it. A blob too large is refused before any of it is read;
 * a page that moves on, an Activity that goes away or a page whose Content Security Policy keeps
 * it from fetching blobs ends the download with nothing saved. Whatever the end, the page keeps
 * nothing of the read.
 *
 * The WebView hands Skim no name for a blob (no Content-Disposition, whatever the link's download
 * attribute says), so the file is named from its type.
 */
@RunWith(AndroidJUnit4::class)
class BlobDownloadDeviceTest {

    @get:Rule(order = 0)
    val downloads = DeviceDownloads()

    @get:Rule(order = 1)
    val rig = BrowserRig()

    @Test
    fun aBlobLinkIsSavedByteForByteUnderTheNameItsTypeGives() {
        val id = showBlob(SMALL_BYTES)

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)

        val completed = rig.awaitDownloadEvents(1).single() as DownloadEvent.DownloadCompleted
        assertEquals(NAME_OF_TEXT, completed.fileName)
        assertEquals("text/plain", completed.mime)
        assertArrayEquals(blobBytes(SMALL_BYTES), downloads.read(completed.uri))
        assertEquals("one slice", 1, slices(id))
        assertNothingLeftIn(id)
    }

    @Test
    fun aBlobOfManySlicesIsPutTogetherWhole() {
        val id = showBlob(MANY_SLICES_BYTES)

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)

        val completed = rig.awaitDownloadEvents(1, READ_TIMEOUT_MS).single() as DownloadEvent.DownloadCompleted
        assertEquals("read in slices of 384 KiB", MANY_SLICES_BYTES / SLICE_BYTES, slices(id))
        val saved = downloads.read(completed.uri)
        assertEquals(MANY_SLICES_BYTES, saved.size)
        assertArrayEquals("the same SHA-256", sha256(blobBytes(MANY_SLICES_BYTES)), sha256(saved))
        assertNothingLeftIn(id)
    }

    @Test
    fun anEmptyBlobIsSavedAsAnEmptyFile() {
        val id = showBlob(0)

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)

        val completed = rig.awaitDownloadEvents(1).single() as DownloadEvent.DownloadCompleted
        assertEquals(0, downloads.read(completed.uri).size)
        assertEquals(0L, downloads.savedFiles().single().size)
        assertEquals("nothing to read", 0, slices(id))
        assertNothingLeftIn(id)
    }

    @Test
    fun aBlobLargerThanTheLimitIsRefusedBeforeAnyOfItIsRead() {
        val id = showBlob(TOO_LARGE_BYTES)

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)

        assertEquals(DownloadEvent.DownloadFailed(failedName()), rig.awaitDownloadEvents(1, READ_TIMEOUT_MS).single())
        assertEquals("not one slice read", 0, slices(id))
        assertTrue(downloads.savedFiles().isEmpty())
        assertNothingLeftIn(id)
    }

    @Test
    fun aPageThatMovesOnWhileItsBlobIsReadEndsTheDownloadWithNothingSaved() {
        val id = showBlob(MOVING_ON_BYTES)
        rig.evaluate(id, "window.skimLeaveAfter = 2")

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)

        assertEquals(DownloadEvent.DownloadFailed(failedName()), rig.awaitDownloadEvents(1, READ_TIMEOUT_MS).single())
        rig.awaitLoaded(id, rig.pages.url("/plain.html"))
        assertTrue(downloads.savedFiles().isEmpty())
        assertNothingLeftIn(id)
    }

    @Test
    fun aReadStoppedWhenTheActivityGoesAwayLeavesNothingInThePage() {
        val id = showBlob(STOPPED_BYTES)

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)
        waitUntil("the read to start", READ_TIMEOUT_MS) { slices(id) >= 1 }
        onMain { rig.host.blob.destroy() }

        assertEquals(DownloadEvent.DownloadFailed(failedName()), rig.awaitDownloadEvents(1, READ_TIMEOUT_MS).single())
        assertNothingLeftIn(id)
        val read = slices(id)
        assertTrue("$read of ${STOPPED_BYTES / SLICE_BYTES + 1} slices, and no more once stopped", read < STOPPED_BYTES / SLICE_BYTES && holdsFor(SETTLE_MS) { slices(id) == read })
        assertTrue(downloads.savedFiles().isEmpty())
    }

    @Test
    fun aPageWhoseSecurityPolicyForbidsFetchingBlobsCannotHandOneOver() {
        val id = showBlob(SMALL_BYTES, query = "?csp=" + Uri.encode("connect-src 'self'"))

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)

        assertEquals(DownloadEvent.DownloadFailed(failedName()), rig.awaitDownloadEvents(1).single())
        assertEquals(0, slices(id))
        assertTrue(downloads.savedFiles().isEmpty())
        assertNothingLeftIn(id)
    }

    /** Opens the blob page in a new current tab ([query] added) with a text blob of [size] bytes behind its link. */
    private fun showBlob(size: Int, query: String = ""): String {
        rig.showCurrentTab()
        val id = rig.newTab(rig.pages.url("/blob.html$query"))
        // Its only page; Chromium writes the query its own way (a quote becomes %27).
        rig.awaitLoaded(id)
        assertEquals(true, jsValue(rig.evaluate(id, "skimMakeBlob($size, 'text/plain', 'notes.txt')")))
        return id
    }

    /**
     * The name of a blob download that failed before anything was saved: Skim names it from the
     * type the WebView reported for the link ("download.txt" for this text blob), and an older
     * WebView, which hands a `download` link over unread, reports none.
     */
    private fun failedName(): String = if (WebViewSupport.readsDownloadLinksFirst) NAME_OF_TEXT else NAME_WITHOUT_TYPE

    /** How many slices of the blob the page's FileReaders read so far. */
    private fun slices(tabId: String): Int = (jsValue(rig.evaluate(tabId, "window.skimSlices")) as Number).toInt()

    /** The page holds no state of a read: no property of its window, enumerable or not, is named like one. */
    private fun assertNothingLeftIn(tabId: String) {
        waitUntil("the page to drop the read's state") { (jsValue(rig.evaluate(tabId, READ_STATE_COUNT)) as Number).toInt() == 0 }
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private companion object {
        /** Inside the page's link, the band over its top 45%. */
        const val LINK_Y = 0.2f

        /** What Skim names a text file the page gave no name. */
        const val NAME_OF_TEXT = "download.txt"

        /** What Skim names a download of no known type that the page gave no name. */
        const val NAME_WITHOUT_TYPE = "download"

        /** The bytes of one slice: 512 Ki base64 characters. */
        const val SLICE_BYTES = 384 * 1024
        const val SMALL_BYTES = 1_000
        const val MANY_SLICES_BYTES = 3 * 1024 * 1024
        const val MOVING_ON_BYTES = 10 * 1024 * 1024
        const val STOPPED_BYTES = 20 * 1024 * 1024

        /** Over the 25 MB a download held in memory may have. */
        const val TOO_LARGE_BYTES = 26 * 1024 * 1024

        /** A read may take up to Skim's 30 s limit; on a busy emulator a large one takes a while. */
        const val READ_TIMEOUT_MS = 45_000L
        const val SETTLE_MS = 1_000L

        /** How many properties of the page's window hold the state of a blob read. */
        const val READ_STATE_COUNT =
            "Object.getOwnPropertyNames(window).filter(function (name) { return name.indexOf('__skimBlobRead_') === 0; }).length"

        /** The blob page's bytes: byte i is (i * 31 + 7) mod 256. */
        fun blobBytes(size: Int) = ByteArray(size) { ((it * 31 + 7) and 0xFF).toByte() }
    }
}
