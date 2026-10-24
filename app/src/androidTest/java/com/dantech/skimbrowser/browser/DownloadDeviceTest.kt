package com.dantech.skimbrowser.browser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.downloads.DownloadEvent
import com.dantech.skimbrowser.browser.downloads.MediaStoreDownloads
import kotlin.math.abs
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Web downloads on the device, from a tap on a page's download link to the file in the shared
 * Downloads folder. A normal tab's download goes to Android's DownloadManager; an incognito tab's
 * is made inside Skim with the session's cookies and saved through MediaStore, and DownloadManager
 * never hears of it. Every download, file and row is removed after each test.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadDeviceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val downloads = DeviceDownloads()

    @get:Rule(order = 2)
    val rig = BrowserRig()

    @Test
    fun aLinkTappedInANormalTabIsDownloadedByDownloadManagerWithTheUserAgentAndNoCookieOverCleartext() {
        rig.showCurrentTab()
        val id = rig.newTab(rig.pages.url("/set-cookie?value=normal"))
        rig.awaitLoaded(id)
        // A link without a download attribute: every WebView requests the file before handing it over.
        rig.loadIn(id, rig.pages.url("/download.html?plain"))
        val file = rig.pages.url("/report.pdf")

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)

        val download = downloads.awaitSystemDownload(file)
        assertEquals(DownloadManager.STATUS_SUCCESSFUL, download.status)
        assertEquals("the page's name, made safe", SAFE_REPORT_NAME, download.title)
        assertEquals(SAFE_REPORT_NAME, Uri.parse(download.localUri).lastPathSegment)
        val (page, system) = pageAndDownloadRequests("/report.pdf")
        assertEquals("the page's own request had the site's cookie", "skim=normal", page.getHeader("Cookie"))
        system.forEach { request ->
            assertNull("no cookie goes to DownloadManager over cleartext", request.getHeader("Cookie"))
            assertEquals("the WebView's user agent", userAgentOf(id), request.getHeader("User-Agent"))
        }

        // DownloadManager's own notice is only heard while MainActivity is started; asked again, the tracker reports the file.
        rig.graph.downloadTracker().refresh()
        val completed = rig.awaitDownloadEvents(1).single() as DownloadEvent.DownloadCompleted
        assertEquals(SAFE_REPORT_NAME, completed.fileName)
        assertEquals("the type DownloadManager keeps for the file", download.mediaType, completed.mime)
        assertEquals("application/pdf", completed.mime)
        assertFalse("a normal tab's", completed.incognito)
        assertArrayEquals("the file opens and holds what the server sent", TestPages.REPORT_BYTES, downloads.read(completed.uri))
    }

    @Test
    fun anIncognitoTabsDownloadIsMadeInSkimWithTheSessionsCookiesAndNeverReachesDownloadManager() {
        WebViewSupport.assumeProfiles()
        rig.showCurrentTab()
        val normal = rig.newTab(rig.pages.url("/set-cookie?value=normal"))
        rig.awaitLoaded(normal)
        val id = rig.newTab(rig.pages.url("/set-cookie?value=incognito"), incognito = true)
        rig.awaitLoaded(id)
        rig.loadIn(id, rig.pages.url("/download.html"))
        val file = rig.pages.url("/report.pdf")
        val saved = downloads.SavedFileChanges()

        val completed = try {
            Finger.tap(rig.shownWebView(), fractionY = LINK_Y)
            rig.awaitDownloadEvents(1).single() as DownloadEvent.DownloadCompleted
        } finally {
            saved.close()
        }

        val (page, download) = pageAndDownloadRequests("/report.pdf")
        assertEquals("skim=incognito", page.getHeader("Cookie"))
        assertEquals("only the session's cookies, never the normal tabs'", listOf("skim=incognito"), download.map { it.getHeader("Cookie") })
        assertEquals(userAgentOf(id), download.single().getHeader("User-Agent"))
        assertTrue("DownloadManager never heard of it", downloads.systemDownloadsOf(file).isEmpty() && downloads.systemDownloads().isEmpty())
        val row = downloads.savedFiles().single()
        assertEquals(completed.uri, row.uri)
        assertFalse("published, no longer pending", row.pending)
        assertEquals(SAFE_REPORT_NAME, row.name)
        assertEquals("application/pdf", row.mime)
        assertEquals("Download/", row.relativePath)
        assertEquals(SAFE_REPORT_NAME, completed.fileName)
        assertEquals("the type MediaStore keeps for the file", row.mime, completed.mime)
        assertTrue("reported as the incognito tab's", completed.incognito)
        assertArrayEquals(TestPages.REPORT_BYTES, downloads.read(completed.uri))
        assertTrue("MediaStore told of the new file", saved.count > 0)
    }

    @Test
    fun anIncognitoDownloadRedirectedToAnotherSiteTakesOnlyThatSitesCookiesThere() {
        WebViewSupport.assumeProfiles()
        rig.showCurrentTab()
        val id = rig.newTab(rig.pages.url("/set-cookie?value=incognito"), incognito = true)
        rig.awaitLoaded(id)
        rig.loadIn(id, rig.pages.url("/set-cookie?value=local", TestPages.LOCALHOST))
        val moved = rig.pages.url("/report.pdf", TestPages.LOCALHOST)
        rig.loadIn(id, rig.pages.url("/download.html?href=" + Uri.encode("/moving.pdf?to=" + Uri.encode(moved))))

        Finger.tap(rig.shownWebView(), fractionY = LINK_Y)

        val completed = rig.awaitDownloadEvents(1).single() as DownloadEvent.DownloadCompleted
        // The page's request found the file; Skim's own request was sent on to the other site.
        val (_, first) = pageAndDownloadRequests("/moving.pdf")
        assertEquals(listOf("skim=incognito"), first.map { it.getHeader("Cookie") })
        val there = rig.pages.requests("/report.pdf").single()
        assertTrue("on the other site: ${there.getHeader("Host")}", there.getHeader("Host").orEmpty().startsWith("${TestPages.LOCALHOST}:"))
        assertEquals("that site's own cookie, none of the first site's", "skim=local", there.getHeader("Cookie"))
        assertArrayEquals(TestPages.REPORT_BYTES, downloads.read(completed.uri))
        assertTrue(downloads.systemDownloads().isEmpty())
    }

    @Test
    fun aServerThatAnnouncesMoreThanSkimDownloadsItselfIsRefusedBeforeAnythingIsWritten() {
        WebViewSupport.assumeProfiles()
        rig.showCurrentTab()
        val id = rig.newTab(rig.pages.url("/download.html?href=/huge.bin"), incognito = true)
        rig.awaitLoaded(id)
        val saved = downloads.SavedFileChanges()

        val tookMs = try {
            val tapped = SystemClock.elapsedRealtime()
            Finger.tap(rig.shownWebView(), fractionY = LINK_Y)
            assertEquals(DownloadEvent.DownloadFailed("huge.bin", incognito = true), rig.awaitDownloadEvents(1).single())
            SystemClock.elapsedRealtime() - tapped
        } finally {
            saved.close()
        }

        assertEquals("the page's request and Skim's", 2, rig.pages.requests("/huge.bin").size)
        // The server sends one byte and then waits: reading the body would take until the read timeout.
        assertTrue("refused from the declared length alone, in $tookMs ms", tookMs < READ_TIMEOUT_MS)
        assertEquals("MediaStore never got a row", 0, saved.count)
        assertTrue(downloads.savedFiles().isEmpty())
        assertTrue(downloads.systemDownloads().isEmpty())
    }

    @Test
    fun theFreeSpaceSkimsOwnDownloadsCheckIsTheFreeSpaceOfTheSharedStorage() {
        val measured = MediaStoreDownloads(rig.activity.applicationContext).freeBytes()

        @Suppress("DEPRECATION") // The volume of the shared Downloads folder, as the device's own tools report it.
        val volume = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
        assertNotEquals("the space could be read", Long.MAX_VALUE, measured)
        assertTrue("$measured bytes against the volume's $volume", abs(measured - volume) <= FREE_SPACE_TOLERANCE_BYTES)
    }

    /** The requests for [path]: the page's own (the WebView's, which found the file is a download) and the download's after it. */
    private fun pageAndDownloadRequests(path: String): Pair<RecordedRequest, List<RecordedRequest>> {
        val requests = rig.pages.requests(path)
        assertTrue("the page's request and at least one of the download's: ${requests.size}", requests.size >= 2)
        return requests.first() to requests.drop(1)
    }

    private fun userAgentOf(tabId: String): String = onMain { rig.webViewOf(tabId).settings.userAgentString }

    private companion object {
        /** Inside the page's download link, the band over its top 45%. */
        const val LINK_Y = 0.2f

        /** [TestPages.REPORT_NAME] as Skim saves it: the folder dropped, the colon replaced. */
        const val SAFE_REPORT_NAME = "Q3_ report.pdf"

        /** The HTTP client's read timeout, which a download that read the body would wait for. */
        const val READ_TIMEOUT_MS = 15_000L

        /** Other writes on the device between the two readings move the free space a little. */
        const val FREE_SPACE_TOLERANCE_BYTES = 64L * 1024 * 1024
    }
}
