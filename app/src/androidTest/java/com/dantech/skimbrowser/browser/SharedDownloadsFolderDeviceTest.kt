package com.dantech.skimbrowser.browser

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.downloads.DownloadEvent
import com.dantech.skimbrowser.browser.downloads.DownloadRouter
import com.dantech.skimbrowser.browser.downloads.InProcessDownloader
import com.dantech.skimbrowser.browser.downloads.MediaStoreDownloads
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the files Skim saves itself land on the device: in the shared Downloads folder, under the
 * name Skim gave them or as "name (1).ext" when that name is taken, listed in MediaStore (its Files
 * collection before Android 10, its Downloads collection from then on) under the content URI Skim
 * reports, through which the file opens. Before Android 10 writing there needs Android's storage
 * permission: Skim asks for it first, and a refusal ends the download as failed, with nothing
 * written. The other tests of the run are given that permission ([DeviceDownloads]).
 */
@RunWith(AndroidJUnit4::class)
class SharedDownloadsFolderDeviceTest {

    @get:Rule(order = 0)
    val downloads = DeviceDownloads()

    @get:Rule(order = 1)
    val rig = BrowserRig()

    @Test
    fun aFileSkimSavesIsInTheSharedDownloadsFolderAndOpensThroughTheContentUriItReports() {
        val view = showDownloadPage(DATA_URL)

        Finger.tap(view, fractionY = LINK_Y)

        val completed = rig.awaitDownloadEvents(1).single() as DownloadEvent.DownloadCompleted
        assertEquals(NAME, completed.fileName)
        assertEquals("text/plain", completed.mime)
        assertTrue("in the shared Downloads folder: ${downloads.downloadFolder()}", NAME in downloads.downloadFolder())
        val row = downloads.savedFiles().single()
        assertEquals("MediaStore lists it under the URI Skim reports", completed.uri, row.uri)
        assertEquals(NAME, row.name)
        assertEquals("text/plain", row.mime)
        assertEquals(DATA_BYTES.size.toLong(), row.size)
        assertEquals("Download/", row.relativePath)
        assertFalse(row.pending)
        assertArrayEquals("it opens through that URI and holds the page's bytes", DATA_BYTES, downloads.read(completed.uri))
    }

    @Test
    fun aSecondFileOfTheSameNameIsSavedAsTheNameWithOneAndBothStay() {
        val view = showDownloadPage(DATA_URL)

        Finger.tap(view, fractionY = LINK_Y)
        rig.awaitDownloadEvents(1)
        Finger.tap(view, fractionY = LINK_Y)

        val saved = rig.awaitDownloadEvents(2).map { it as DownloadEvent.DownloadCompleted }
        assertEquals(listOf(NAME, SECOND_NAME), saved.map { it.fileName })
        assertTrue("both in the folder: ${downloads.downloadFolder()}", downloads.downloadFolder().containsAll(listOf(NAME, SECOND_NAME)))
        val rows = downloads.savedFiles()
        assertEquals(saved.map { it.uri }, rows.map { it.uri })
        assertEquals(listOf(NAME, SECOND_NAME), rows.map { it.name })
        rows.forEach { row -> assertArrayEquals("${row.name} holds its own bytes", DATA_BYTES, downloads.read(row.uri)) }
    }

    /**
     * Android kills an app whose runtime permission is taken back, and the test process with it, so
     * once a test of the run was given the storage permission a refusal cannot come from Android
     * any more. Skim's own check of the permission therefore reads it through a context that says
     * it is not granted, as on a device where the user denied it; the rest is the app's: its
     * download router asks the rig's Activity host, which denies the prompt.
     */
    @Test
    fun aDownloadIsRefusedWithNothingWrittenWhenTheStoragePermissionIsDenied() {
        val store = MediaStoreDownloads(StorageDenied(rig.activity.applicationContext))
        assumeTrue("Skim saves to the shared Downloads folder without any storage permission on this device", store.needsStoragePermission)
        val router = onMain {
            DownloadRouter(
                tabs = rig.tabs,
                incognito = rig.incognito,
                profiles = rig.container.browserProfiles,
                tracker = rig.container.downloadTracker,
                inProcess = InProcessDownloader({ rig.container.httpClient }, store),
                store = store,
                blob = rig.host.blob,
                activityHost = rig.activityHost,
                scope = rig.container.appScope,
            )
        }
        val view = showDownloadPage(rig.pages.url("/report.pdf"), plain = true)
        val id = checkNotNull(rig.tabs.state.value.currentTabId)
        onMain { view.setDownloadListener { url, userAgent, disposition, mime, _ -> router.start(id, url, userAgent, disposition, mime, view) } }
        val folder = downloads.downloadFolder()

        Finger.tap(view, fractionY = LINK_Y)

        assertEquals(DownloadEvent.DownloadFailed(SAFE_REPORT_NAME), rig.awaitDownloadEvents(1).single())
        assertEquals("asked for the storage permission first", listOf(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)), rig.activityHost.permissionRequests)
        assertTrue("DownloadManager was never given the download", downloads.systemDownloads().isEmpty())
        assertEquals("nothing was written", folder, downloads.downloadFolder())
        assertTrue(downloads.savedFiles().isEmpty())
    }

    /**
     * Shows the download page, its link to [href], in a new current tab and returns the tab's
     * WebView. A [plain] link has no download attribute: every WebView requests the file first.
     */
    private fun showDownloadPage(href: String, plain: Boolean = false): SkimWebView {
        rig.showCurrentTab()
        val url = rig.pages.url("/download.html?href=" + Uri.encode(href) + if (plain) "&plain" else "")
        val id = rig.newTab(url)
        rig.awaitLoaded(id, url)
        return rig.shownWebView()
    }

    /** The app's context as Skim reads it on a device where the user denied Android's storage permission. */
    private class StorageDenied(base: Context) : ContextWrapper(base) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE) PackageManager.PERMISSION_DENIED else super.checkPermission(permission, pid, uid)
    }

    private companion object {
        /** Inside the page's download link, the band over its top 45%. */
        const val LINK_Y = 0.2f

        /** What Skim names a text file the page gave no name, and a second one of that name. */
        const val NAME = "download.txt"
        const val SECOND_NAME = "download (1).txt"

        /** [TestPages.REPORT_NAME] as Skim saves it: the folder dropped, the colon replaced. */
        const val SAFE_REPORT_NAME = "Q3_ report.pdf"

        /** Every byte value once, so a byte changed on the way shows. */
        val DATA_BYTES = ByteArray(256) { it.toByte() }
        val DATA_URL = "data:text/plain;base64," + Base64.encodeToString(DATA_BYTES, Base64.NO_WRAP)
    }
}
