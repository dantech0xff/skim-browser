package com.dantech.skimbrowser.browser.downloads

import android.Manifest
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakeActivityHost
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.SkimWebView
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.browser.TestBrowserParts
import com.dantech.skimbrowser.browser.downloads.DownloadEvent.DownloadCompleted
import com.dantech.skimbrowser.browser.downloads.DownloadEvent.DownloadFailed
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/**
 * The router over the real tab manager, incognito session, tracker, in-process downloader,
 * MediaStore saver and blob bridge; fakes stand only for DownloadManager, MediaStore's provider,
 * the page's message object and the cookie stores. Web downloads of incognito tabs go to a local
 * server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DownloadRouterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity = controller.get()
    private val prompts = FakeActivityHost()
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val fixtures = mutableListOf<TabManagerFixture>()
    private val events = mutableListOf<DownloadEvent>()
    private lateinit var parts: TestBrowserParts
    private lateinit var media: FakeMediaStore
    private val view = SkimWebView(activity)

    @After
    fun tearDown() = runBlocking {
        fixtures.forEach { it.close() }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
        controller.pause().stop().destroy()
        Unit
    }

    // Normal tabs

    @Test
    fun aNormalTabsWebDownloadGoesToDownloadManagerWithTheDefaultProfilesCookies() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        parts.defaultCookies[FILE] = "session=default"
        touch()

        parts.downloads.start(tab, FILE, USER_AGENT, "attachment; filename=\"Invoice\u202Efdp.apk\"", "application/pdf", view)
        runCurrent()

        val request = parts.systemDownloads.enqueued.single()
        assertEquals(FILE, request.url)
        assertEquals("the name cannot disguise itself", "Invoicefdp.pdf", request.fileName)
        assertEquals("application/pdf", request.mimeType)
        assertEquals(USER_AGENT, request.userAgent)
        assertEquals("session=default", request.cookie)
        assertTrue("DownloadManager reports the end itself", events.isEmpty())
        assertTrue("nothing is saved in the process", media.inserts().isEmpty())
    }

    @Test
    fun aWebDownloadIsNamedAfterItsAddressWhenTheServerGivesNoName() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        touch()

        parts.downloads.start(tab, "https://daylightjournal.com/files/annual%20report.pdf?v=2", null, null, "application/pdf", view)
        runCurrent()

        val request = parts.systemDownloads.enqueued.single()
        assertEquals("annual report.pdf", request.fileName)
        assertEquals("the WebView's user agent", view.settings.userAgentString, request.userAgent)
        assertEquals(null, request.cookie)
    }

    @Test
    fun aDownloadDownloadManagerRefusesIsReportedAsFailed() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        parts.systemDownloads.refusal = IllegalStateException("No external storage")
        touch()

        parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertEquals(listOf<DownloadEvent>(DownloadFailed("guide.pdf")), events)
    }

    // Incognito tabs

    @Test
    fun anIncognitoTabsWebDownloadIsMadeHereWithTheIncognitoCookiesAndNeverReachesDownloadManager() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody(Buffer().write(PDF)))
        val url = server.url("/files/guide.pdf").toString()
        incognitoProfile(f).cookies[url] = "session=incognito"
        parts.defaultCookies[url] = "session=default"
        touch()

        parts.downloads.start(tab, url, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "The server got no request" }
        assertEquals("session=incognito", request.getHeader("Cookie"))
        assertEquals(USER_AGENT, request.getHeader("User-Agent"))
        assertTrue("DownloadManager never sees the address or the cookies", parts.systemDownloads.enqueued.isEmpty())
        assertTrue("the default cookies are never read", parts.defaultCookieLookups.isEmpty())
        val row = media.rows().single()
        assertArrayEquals(PDF, row.file.readBytes())
        assertEquals(listOf<DownloadEvent>(DownloadCompleted("guide.pdf", row.uri, "application/pdf", incognito = true)), events)
    }

    @Test
    fun anIncognitoTabsWebDownloadThatFailsIsReportedAsAnIncognitoOne() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)
        server.enqueue(MockResponse().setResponseCode(404))
        touch()

        parts.downloads.start(tab, server.url("/files/guide.pdf").toString(), USER_AGENT, null, "application/pdf", view)
        runCurrent()
        checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "The server got no request" }

        assertEquals(listOf<DownloadEvent>(DownloadFailed("guide.pdf", incognito = true)), events)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun anIncognitoTabWithoutASessionDownloadsNothing() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)
        f.incognito.end(engine = null)
        val url = server.url("/files/guide.pdf").toString()
        parts.defaultCookies[url] = "session=default"
        touch()

        parts.downloads.start(tab, url, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertEquals(listOf<DownloadEvent>(DownloadFailed("guide.pdf", incognito = true)), events)
        assertEquals("no request at all", 0, server.requestCount)
        assertTrue(parts.systemDownloads.enqueued.isEmpty())
        assertTrue("never the default profile's cookies", parts.defaultCookieLookups.isEmpty())
    }

    // data: and blob:

    @Test
    fun aDataUrlIsSavedAsWhatItsBytesAre() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        touch()

        parts.downloads.start(tab, dataUrl(APK, ZIP), null, null, APK, view)
        parts.downloads.start(tab, dataUrl("application/octet-stream", PDF), null, "attachment; filename=\"../../rebate-guide\"", null, view)
        runCurrent()

        val (zip, pdf) = media.rows()
        assertEquals(
            listOf<DownloadEvent>(
                DownloadCompleted("download.zip", zip.uri, "application/zip"),
                DownloadCompleted("rebate-guide.pdf", pdf.uri, "application/pdf"),
            ),
            events,
        )
        assertArrayEquals(ZIP, zip.file.readBytes())
        assertEquals("application/zip", zip.string(MediaStore.Downloads.MIME_TYPE))
        assertTrue(parts.systemDownloads.enqueued.isEmpty())
    }

    @Test
    fun aDataUrlOverTheLimitIsRefused() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        touch()

        parts.downloads.start(tab, "data:text/plain," + "a".repeat(MAX_INLINE_DOWNLOAD_BYTES + 1), null, null, "text/plain", view)
        runCurrent()

        assertEquals(listOf<DownloadEvent>(DownloadFailed("download.txt")), events)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun anIncognitoTabsDataUrlIsSavedHereToo() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)
        touch()

        parts.downloads.start(tab, dataUrl("application/pdf", PDF), null, null, "application/pdf", view)
        runCurrent()

        val row = media.rows().single()
        assertEquals(listOf<DownloadEvent>(DownloadCompleted("download.pdf", row.uri, "application/pdf", incognito = true)), events)
        assertTrue(parts.systemDownloads.enqueued.isEmpty())
        assertTrue(parts.defaultCookieLookups.isEmpty())
    }

    @Test
    fun anIncognitoTabsDataUrlOverTheLimitIsReportedAsAnIncognitoFailure() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)
        touch()

        parts.downloads.start(tab, "data:text/plain," + "a".repeat(MAX_INLINE_DOWNLOAD_BYTES + 1), null, null, "text/plain", view)
        runCurrent()

        assertEquals(listOf<DownloadEvent>(DownloadFailed("download.txt", incognito = true)), events)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun anIncognitoTabsOtherSchemesAreRefused() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)
        touch()

        parts.downloads.start(tab, "ftp://daylightjournal.com/guide.pdf", USER_AGENT, null, "application/pdf", view)
        parts.downloads.start(tab, "file:///sdcard/guide.pdf", USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertEquals(List(2) { DownloadFailed("guide.pdf", incognito = true) }, events)
        assertTrue(parts.systemDownloads.enqueued.isEmpty())
        assertTrue(media.inserts().isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aBlobIsReadFromItsPageUpToTheLimitAndSaved() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)
        parts.blobPage.serve(PNG, "image/png")
        touch()

        parts.downloads.start(tab, BLOB, USER_AGENT, null, "image/png", view)
        runCurrent()

        val row = media.rows().single()
        assertArrayEquals(PNG, row.file.readBytes())
        assertEquals("the incognito tab's", listOf<DownloadEvent>(DownloadCompleted("download.png", row.uri, "image/png", incognito = true)), events)
        val start = parts.blobPage.calls.first()
        assertEquals("start", start.op)
        assertEquals(MAX_INLINE_DOWNLOAD_BYTES.toLong(), start.number("maxBytes"))
        assertEquals(ORIGIN, start.text("origin"))
        assertTrue("nothing is left in the page", parts.blobPage.live.isEmpty())
        assertTrue(parts.systemDownloads.enqueued.isEmpty())
    }

    @Test
    fun aBlobThePageCannotHandOverFails() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        parts.blobPage.onStart = { FakeBlobPage.unavailable(it.nonce) }
        touch()

        parts.downloads.start(tab, BLOB, USER_AGENT, null, "image/png", view)
        runCurrent()

        assertEquals(listOf<DownloadEvent>(DownloadFailed("download.png")), events)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun anIncognitoTabsBlobThePageCannotHandOverFailsAsAnIncognitoOne() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)
        parts.blobPage.onStart = { FakeBlobPage.unavailable(it.nonce) }
        touch()

        parts.downloads.start(tab, BLOB, USER_AGENT, null, "image/png", view)
        runCurrent()

        assertEquals(listOf<DownloadEvent>(DownloadFailed("download.png", incognito = true)), events)
    }

    @Test
    fun otherSchemesAreRefused() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        touch()
        val urls = listOf("ftp://daylightjournal.com/guide.pdf", "file:///sdcard/guide.pdf", "content://media/external/1", "javascript:alert(1)", "about:blank")

        urls.forEach { parts.downloads.start(tab, it, USER_AGENT, null, "application/pdf", view) }
        runCurrent()

        assertEquals(urls.size, events.size)
        assertTrue(events.all { it is DownloadFailed && !it.incognito })
        assertTrue(parts.systemDownloads.enqueued.isEmpty())
        assertTrue(media.inserts().isEmpty())
        assertTrue(prompts.permissionRequests.isEmpty())
    }

    @Test
    fun aTabThatIsNotOpenCannotDownload() = runTest {
        fixture()
        touch()

        parts.downloads.start("tab-404", FILE, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertEquals(
            "a tab that is gone may have been an incognito one",
            listOf<DownloadEvent>(DownloadFailed("guide.pdf", incognito = true)),
            events,
        )
        assertTrue(parts.systemDownloads.enqueued.isEmpty())
    }

    // Downloads a page starts on its own

    @Test
    fun aPageGetsOneDownloadOfItsOwnEvery30SecondsWhateverItLoads() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)

        repeat(3) { parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view) }
        // Reloading, or moving on to another page, gives it no new allowance.
        f.manager.events.onPageStarted(tab, PAGE)
        parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view)
        runCurrent()
        assertEquals(1, parts.systemDownloads.enqueued.size)
        assertEquals("the others are reported as failed", List(3) { DownloadFailed("guide.pdf") }, events)

        ShadowSystemClock.advanceBy(Duration.ofMillis(29_999))
        parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1))
        parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertEquals("one more once 30 s passed", 2, parts.systemDownloads.enqueued.size)
        assertEquals(4, events.size)
    }

    @Test
    fun theDownloadsAnIncognitoPageIsRefusedAreReportedAsIncognitoOnes() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = true, url = PAGE)

        // The page's own allowance (it takes one of the two places for downloads held in memory), one
        // more of its own, a blob the user asked for (the second place) and one blob too many.
        repeat(2) { parts.downloads.start(tab, dataUrl("application/pdf", PDF), null, null, "application/pdf", view) }
        repeat(2) { parts.downloads.start(tab, BLOB, USER_AGENT, null, "image/png", view, userInitiated = true) }
        runCurrent()

        assertEquals(
            listOf(DownloadFailed("download.pdf", incognito = true), DownloadFailed("download.png", incognito = true)),
            events.filterIsInstance<DownloadFailed>(),
        )
        assertEquals(listOf(true), events.filterIsInstance<DownloadCompleted>().map { it.incognito })
    }

    @Test
    fun eachTabHasItsOwnAllowance() = runTest {
        val f = fixture()
        val first = f.manager.newTab(incognito = false, url = PAGE)
        val second = f.manager.newTab(incognito = false, url = PAGE)

        parts.downloads.start(first, FILE, USER_AGENT, null, "application/pdf", view)
        parts.downloads.start(second, FILE, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertEquals(2, parts.systemDownloads.enqueued.size)
        assertTrue(events.isEmpty())
    }

    @Test
    fun aTapAllowsOneDownloadForFiveSeconds() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        // The page's own allowance is used up first.
        parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view)

        touch()
        ShadowSystemClock.advanceBy(Duration.ofMillis(5_000))
        repeat(2) { parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view) }
        touch()
        ShadowSystemClock.advanceBy(Duration.ofMillis(5_001))
        parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertEquals("the page's own, then one per tap in time", 2, parts.systemDownloads.enqueued.size)
        assertEquals(List(2) { DownloadFailed("guide.pdf") }, events)
    }

    @Test
    fun aScrollAllowsNoDownload() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        val slop = ViewConfiguration.get(activity).scaledTouchSlop
        val now = SystemClock.uptimeMillis()
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 400f, 0))
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 10f, 400f - slop - 1, 0))
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 10f, 400f - slop - 1, 0))

        repeat(2) { parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view) }
        runCurrent()

        assertEquals("only the page's own", 1, parts.systemDownloads.enqueued.size)
        assertEquals(listOf<DownloadEvent>(DownloadFailed("guide.pdf")), events)
    }

    @Test
    fun downloadsTheUserAskedForAreNeverLimited() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)

        repeat(4) { parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view, userInitiated = true) }
        parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertEquals("and they leave the page its own allowance", 5, parts.systemDownloads.enqueued.size)
        assertTrue(events.isEmpty())
    }

    @Test
    fun atMostTwoDownloadsHeldInMemoryRunAtOnce() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)

        // The page never hands its blobs over, so the reads wait for their timeout.
        repeat(3) { parts.downloads.start(tab, BLOB, USER_AGENT, null, "image/png", view, userInitiated = true) }
        runCurrent()
        assertEquals("the third is refused at once", listOf<DownloadEvent>(DownloadFailed("download.png")), events)

        advanceTimeBy(2 * BlobBridge.FETCH_TIMEOUT_MS + 1)
        runCurrent()
        parts.downloads.start(tab, dataUrl("application/pdf", PDF), null, null, null, view, userInitiated = true)
        runCurrent()

        assertEquals(List(3) { DownloadFailed("download.png") } + DownloadCompleted("download.pdf", media.rows().single().uri, "application/pdf"), events)
    }

    @Test
    fun aDownloadTheUserAskedForCarriesNoCookies() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        parts.defaultCookies[FILE] = "session=default"

        // The page chose the address; DownloadManager would send the cookies on to wherever it redirects.
        parts.downloads.start(tab, FILE, USER_AGENT, null, "image/png", view, userInitiated = true)
        runCurrent()

        assertNull(parts.systemDownloads.enqueued.single().cookie)
        assertTrue(parts.defaultCookieLookups.isEmpty())
    }

    @Test
    fun aDownloadLinkHandedOverBeforeTheWebViewRequestedItCarriesNoCookies() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        parts.defaultCookies[FILE] = "session=default"
        touch()

        // An older WebView hands a link with a download attribute over at once: the link's own address,
        // with no type and no Content-Disposition. No redirect was followed for it, and DownloadManager
        // would send the cookies on to wherever it redirects.
        parts.downloads.start(tab, FILE, USER_AGENT, "", "", view)
        runCurrent()

        val request = parts.systemDownloads.enqueued.single()
        assertEquals(FILE, request.url)
        assertNull(request.cookie)
        assertTrue(parts.defaultCookieLookups.isEmpty())
    }

    @Test
    fun aCleartextDownloadCarriesNoCookies() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        val cleartext = "http://daylightjournal.com/files/guide.pdf"
        parts.defaultCookies[cleartext] = "session=default"
        touch()

        parts.downloads.start(tab, cleartext, USER_AGENT, null, "application/pdf", view)
        runCurrent()

        assertNull(parts.systemDownloads.enqueued.single().cookie)
        assertTrue(parts.defaultCookieLookups.isEmpty())
    }

    // Before scoped storage

    @Test
    @Config(sdk = [26])
    fun beforeScopedStorageTheStoragePermissionIsAskedForFirst() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        touch()

        parts.downloads.start(tab, FILE, USER_AGENT, null, "application/pdf", view)
        parts.downloads.start(tab, dataUrl("application/pdf", PDF), null, null, null, view)
        runCurrent()
        val (forWeb, forData) = prompts.permissionRequests
        assertEquals(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), forWeb.permissions)
        assertTrue("nothing before Android answers", parts.systemDownloads.enqueued.isEmpty())
        forWeb.onResult(mapOf(Manifest.permission.WRITE_EXTERNAL_STORAGE to true))
        forData.onResult(mapOf(Manifest.permission.WRITE_EXTERNAL_STORAGE to false))
        runCurrent()

        assertEquals(1, parts.systemDownloads.enqueued.size)
        assertEquals(listOf<DownloadEvent>(DownloadFailed("download.pdf")), events)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    @Config(sdk = [26])
    fun onceTheActivityIsGoneNobodyIsAskedForThePermissionAndTheDownloadFails() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        touch()

        parts.downloads.destroy()
        parts.downloads.start(tab, dataUrl("application/pdf", PDF), null, null, null, view)
        runCurrent()

        assertTrue(prompts.permissionRequests.isEmpty())
        assertEquals(listOf<DownloadEvent>(DownloadFailed("download.pdf")), events)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun aDownloadThatNeedsNoPermissionGoesOnWithoutTheActivity() = runTest {
        val f = fixture()
        val tab = f.manager.newTab(incognito = false, url = PAGE)
        touch()

        parts.downloads.start(tab, dataUrl("application/pdf", PDF), null, null, null, view)
        parts.downloads.destroy()
        runCurrent()

        assertEquals(listOf("download.pdf"), events.map { (it as DownloadCompleted).fileName })
    }

    private fun TestScope.fixture(): TabManagerFixture {
        val f = TabManagerFixture(this, folder.root).also { fixtures += it }
        media = FakeMediaStore.install(folder.newFolder("media"))
        parts = TestBrowserParts(f, this, activity, prompts, httpClient = client)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { parts.tracker.events.collect { events += it } }
        return f
    }

    /** A tap on the page, as the user taps a download link. */
    private fun touch() {
        val now = SystemClock.uptimeMillis()
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 10f, 10f, 0))
    }

    private fun incognitoProfile(f: TabManagerFixture) =
        f.profiles.profile((f.incognito.state.value as IncognitoSession.State.Active).profile)

    private fun dataUrl(mime: String, bytes: ByteArray) = "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val PAGE = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val FILE = "https://daylightjournal.com/files/guide.pdf"
        const val ORIGIN = "https://daylightjournal.com"
        const val BLOB = "blob:https://daylightjournal.com/5f0c-41"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) Skim"
        const val APK = "application/vnd.android.package-archive"
        val PDF = "%PDF-1.4\n1 0 obj\n".toByteArray()
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52)
        val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0, 0, 0, 0x08, 0)
    }
}
