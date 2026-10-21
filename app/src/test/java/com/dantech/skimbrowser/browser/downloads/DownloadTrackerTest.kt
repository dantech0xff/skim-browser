package com.dantech.skimbrowser.browser.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.downloads.DownloadEvent.DownloadCompleted
import com.dantech.skimbrowser.browser.downloads.DownloadEvent.DownloadFailed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DownloadTrackerTest {

    private val system = FakeSystemDownloads()
    private val events = mutableListOf<DownloadEvent>()

    @Test
    fun aFinishedDownloadIsReportedOnceWithTheUriAndTypeTheSystemKeeps() = runTest {
        val tracker = tracker()
        assertTrue(tracker.enqueue(request("report.pdf", declaredMime = "application/octet-stream")))
        val id = system.lastEnqueuedId()
        system.statuses[id] = DownloadManager.STATUS_SUCCESSFUL
        system.uris[id] = FILE
        system.mimeTypes[id] = "application/pdf"

        tracker.onDownloadComplete(id)
        runCurrent()
        tracker.onDownloadComplete(id)
        tracker.refresh()
        runCurrent()

        assertEquals("the type to open it with is the system's, not the page's", listOf(DownloadCompleted("report.pdf", FILE, "application/pdf")), events)
    }

    @Test
    fun anotherAppsDownloadIsIgnored() = runTest {
        val tracker = tracker()
        system.statuses[42] = DownloadManager.STATUS_SUCCESSFUL
        system.uris[42] = FILE

        tracker.onDownloadComplete(42)
        tracker.refresh()
        runCurrent()

        assertTrue(events.isEmpty())
        assertTrue("the system is not even asked", system.statusQueries.isEmpty())
    }

    @Test
    fun aFailedDownloadIsReportedAsFailedUnderItsName() = runTest {
        val tracker = tracker()
        tracker.enqueue(request("big.zip"))
        val id = system.lastEnqueuedId()
        system.statuses[id] = DownloadManager.STATUS_FAILED

        tracker.onDownloadComplete(id)
        runCurrent()

        assertEquals(listOf<DownloadEvent>(DownloadFailed("big.zip")), events)
    }

    @Test
    fun refreshFindsWhatFinishedWhileNobodyListened() = runTest {
        val tracker = tracker()
        tracker.enqueue(request("one.pdf"))
        val first = system.lastEnqueuedId()
        tracker.enqueue(request("two.pdf"))
        val second = system.lastEnqueuedId()
        system.statuses[first] = DownloadManager.STATUS_RUNNING
        system.statuses[second] = DownloadManager.STATUS_PAUSED

        tracker.refresh()
        runCurrent()
        assertTrue("nothing finished yet", events.isEmpty())

        system.statuses[first] = DownloadManager.STATUS_SUCCESSFUL
        system.uris[first] = FILE
        system.mimeTypes[first] = "application/pdf"
        system.statuses[second] = DownloadManager.STATUS_FAILED
        tracker.refresh()
        runCurrent()
        tracker.refresh()
        runCurrent()

        assertEquals(listOf(DownloadCompleted("one.pdf", FILE, "application/pdf"), DownloadFailed("two.pdf")), events)
    }

    @Test
    fun aDownloadRemovedFromTheSystemIsDroppedWithoutAWord() = runTest {
        val tracker = tracker()
        tracker.enqueue(request("gone.pdf"))
        val id = system.lastEnqueuedId()

        tracker.onDownloadComplete(id)
        runCurrent()
        tracker.refresh()
        runCurrent()

        assertTrue(events.isEmpty())
        assertEquals("not asked about again", 1, system.statusQueries[id])
    }

    @Test
    fun aSuccessWithoutAFileIsAFailureAndAMissingTypeOpensAsBytes() = runTest {
        val tracker = tracker()
        tracker.enqueue(request("lost.pdf"))
        val lost = system.lastEnqueuedId()
        tracker.enqueue(request("untyped"))
        val untyped = system.lastEnqueuedId()
        system.statuses[lost] = DownloadManager.STATUS_SUCCESSFUL
        system.statuses[untyped] = DownloadManager.STATUS_SUCCESSFUL
        system.uris[untyped] = FILE

        tracker.refresh()
        runCurrent()

        assertEquals(listOf(DownloadFailed("lost.pdf"), DownloadCompleted("untyped", FILE, "application/octet-stream")), events)
    }

    @Test
    fun aRefusedDownloadIsNotFollowed() = runTest {
        val tracker = tracker()
        system.refusal = SecurityException("No permission to write")

        assertFalse(tracker.enqueue(request("report.pdf")))
        tracker.refresh()
        runCurrent()

        assertTrue(system.statusQueries.isEmpty())
    }

    @Test
    fun downloadsSkimSavesItselfAreReportedOnTheSameStream() = runTest {
        val tracker = tracker()

        tracker.report(DownloadCompleted("photo.jpg", FILE, "image/jpeg"))
        tracker.report(DownloadFailed(null))

        assertEquals(listOf(DownloadCompleted("photo.jpg", FILE, "image/jpeg"), DownloadFailed(null)), events)
    }

    @Test
    fun theSystemRequestCarriesTheCookiesTheUserAgentAndANotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloads = AndroidSystemDownloads(context)

        val id = downloads.enqueue(SystemDownloadRequest(PAGE, "report.pdf", "application/pdf", USER_AGENT, "session=abc; theme=dark"))

        val manager = shadowOf(context.getSystemService(DownloadManager::class.java))
        val request = shadowOf(manager.getRequest(id))
        assertEquals(Uri.parse(PAGE), request.uri)
        assertEquals(
            listOf("User-Agent" to USER_AGENT, "Cookie" to "session=abc; theme=dark"),
            request.requestHeaders.map { it.first to it.second },
        )
        assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED, request.notificationVisibility)
        assertEquals("application/pdf", request.mimeType)
        assertEquals("report.pdf", request.title)
        assertTrue(request.destination.toString(), request.destination.toString().endsWith("/Download/report.pdf"))
    }

    @Test
    fun theSystemRequestLeavesOutWhatItDoesNotHave() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloads = AndroidSystemDownloads(context)

        val id = downloads.enqueue(SystemDownloadRequest(PAGE, "report.pdf", null, null, null))

        val request = shadowOf(shadowOf(context.getSystemService(DownloadManager::class.java)).getRequest(id))
        assertTrue(request.requestHeaders.isEmpty())
        assertNull(request.mimeType)
    }

    @Test
    fun theSystemsStatusIsReadBackByIdAndAnUnknownIdHasNone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloads = AndroidSystemDownloads(context)
        val id = downloads.enqueue(SystemDownloadRequest(PAGE, "report.pdf", null, null, null))
        shadowOf(shadowOf(context.getSystemService(DownloadManager::class.java)).getRequest(id)).status = DownloadManager.STATUS_SUCCESSFUL

        assertEquals(DownloadManager.STATUS_SUCCESSFUL, downloads.status(id))
        assertNull(downloads.status(id + 1_000))
    }

    private fun TestScope.tracker(): DownloadTracker {
        val tracker = DownloadTracker(system, backgroundScope, StandardTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tracker.events.collect { events += it } }
        return tracker
    }

    private fun request(name: String, declaredMime: String? = null) = SystemDownloadRequest(PAGE, name, declaredMime, USER_AGENT, null)

    private fun FakeSystemDownloads.lastEnqueuedId(): Long = 100L + enqueued.size

    private companion object {
        const val PAGE = "https://daylightjournal.com/files/report.pdf"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) Skim"
        val FILE: Uri = Uri.parse("content://downloads/all_downloads/101")
    }
}
