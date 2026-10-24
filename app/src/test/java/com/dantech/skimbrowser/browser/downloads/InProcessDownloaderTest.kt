package com.dantech.skimbrowser.browser.downloads

import android.app.Application
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Real HTTP to a local server, saved into a MediaStore that keeps its rows in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class InProcessDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val other = MockWebServer()
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private lateinit var media: FakeMediaStore
    private val cookieLookups = mutableListOf<String>()

    @Before
    fun setUp() {
        media = FakeMediaStore.install(folder.newFolder("media"))
        server.start()
        other.start()
    }

    @After
    fun tearDown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
        other.shutdown()
    }

    @Test
    fun theProfilesCookiesAndTheWebViewsUserAgentAreSent() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody(Buffer().write(PDF)))
        val url = server.url("/files/report.pdf").toString()

        val saved = downloader().download(url, USER_AGENT, cookiesOf(url to "session=incognito"), "report.pdf", null)

        val request = server.received()
        assertEquals("session=incognito", request.getHeader("Cookie"))
        assertEquals(USER_AGENT, request.getHeader("User-Agent"))
        assertEquals(listOf(url), cookieLookups)
        assertEquals("report.pdf", saved.fileName)
        assertEquals("application/pdf", saved.mime)
        assertArrayEquals(PDF, media.rows().single().file.readBytes())
        assertEquals(0, media.rows().single().int(MediaStore.Downloads.IS_PENDING))
    }

    @Test
    fun withoutCookiesNoCookieHeaderIsSent() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(PDF)))

        downloader().download(server.url("/a.pdf").toString(), null, cookiesOf(), "a.pdf", "application/pdf")

        assertNull(server.received().getHeader("Cookie"))
    }

    @Test
    fun theTypeComesFromTheBytesAndTheNameFollowsIt() = runTest {
        // The server says HTML and the page named it like an app; the bytes are a PDF.
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(Buffer().write(PDF)))

        val saved = downloader().download(server.url("/x").toString(), null, cookiesOf(), "Invoice\u202Efdp.apk", APK)

        assertEquals("Invoicefdp.pdf", saved.fileName)
        assertEquals("application/pdf", saved.mime)
        assertEquals("application/pdf", media.inserts().single().second.getAsString(MediaStore.Downloads.MIME_TYPE))
    }

    @Test
    fun aRedirectSendsEachSiteOnlyItsOwnCookies() = runTest {
        val target = other.url("/real/report.pdf").toString()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target))
        other.enqueue(MockResponse().setBody(Buffer().write(PDF)))
        val start = server.url("/go").toString()

        downloader().download(start, USER_AGENT, cookiesOf(start to "first=1", target to "second=2"), "report.pdf", null)

        assertEquals("first=1", server.received().getHeader("Cookie"))
        val redirected = other.received()
        assertEquals("second=2", redirected.getHeader("Cookie"))
        assertEquals(USER_AGENT, redirected.getHeader("User-Agent"))
        assertEquals(listOf(start, target), cookieLookups)
    }

    @Test
    fun aResponseOtherThanSuccessIsAFailureAndSavesNothing() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))

        val failure = runCatching { downloader().download(server.url("/missing.pdf").toString(), null, cookiesOf(), "missing.pdf", null) }
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun anAddressThatIsNotWebIsAFailure() = runTest {
        val failure = runCatching { downloader().download("ftp://example.com/a.pdf", null, cookiesOf(), "a.pdf", null) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun tooManyRedirectsAreAFailure() = runTest {
        repeat(21) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again")) }

        val failure = runCatching { downloader().download(server.url("/again").toString(), null, cookiesOf(), "a.pdf", null) }
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(21, server.requestCount)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun theFileIsWrittenAsItArrivesAndACancelledDownloadLeavesNothing() = runBlocking {
        // 64 KiB, of which the server sends 1 KiB every 2 s: the whole file is far away when saving starts.
        val body = PDF + ByteArray(64 * 1024 - PDF.size) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(body)).throttleBody(1024, 2, TimeUnit.SECONDS))
        val opened = CompletableDeferred<Unit>()
        media.onOpen = { opened.complete(Unit) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val downloader = InProcessDownloader({ client }, MediaStoreDownloads(app, ioDispatcher = Dispatchers.IO), Dispatchers.IO, freeSpace = { Long.MAX_VALUE })
            val download = scope.async {
                downloader.download(server.url("/big.pdf").toString(), null, { null }, "big.pdf", null)
            }

            withTimeout(WAIT_MS) { opened.await() }
            assertEquals("saving started before the file arrived", 1, media.rows().size)
            assertEquals(1, media.rows().single().int(MediaStore.Downloads.IS_PENDING))
            download.cancel()
            val failure = withTimeout(WAIT_MS) { runCatching { download.await() }.exceptionOrNull() }

            assertTrue(failure is CancellationException)
            assertTrue("the half-written row is gone", media.rows().isEmpty())
            assertEquals(1, media.deletes().size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aFileLargerThanTheLimitIsRefusedFromItsLength() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(2_000))))

        val failure = runCatching { downloader(maxBytes = 1_000).download(server.url("/big.bin").toString(), null, cookiesOf(), "big.bin", null) }
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue("refused before anything is written", media.inserts().isEmpty())
    }

    @Test
    fun aFileThatGrowsPastTheLimitIsStoppedAndDeleted() = runTest {
        // No Content-Length: the size is only known as the bytes come.
        server.enqueue(MockResponse().setChunkedBody(Buffer().write(PDF + ByteArray(2_000)), 256))

        val failure = runCatching { downloader(maxBytes = 1_000).download(server.url("/big.pdf").toString(), null, cookiesOf(), "big.pdf", null) }
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(1, media.inserts().size)
        assertTrue("the part written is deleted", media.rows().isEmpty())
    }

    @Test
    fun aDownloadLeavesRoomOnThePhone() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(PDF)))

        val failure = runCatching {
            downloader(freeSpace = { InProcessDownloader.MIN_FREE_BYTES - 1 }).download(server.url("/a.pdf").toString(), null, cookiesOf(), "a.pdf", null)
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun aDownloadStopsWhenTheSpaceRunsOutOnTheWay() = runTest {
        val body = PDF + ByteArray(9 * 1024 * 1024)
        server.enqueue(MockResponse().setBody(Buffer().write(body)))
        var free = 10L * 1024 * 1024 * 1024
        val checks = mutableListOf<Long>()
        val shrinking: () -> Long = {
            checks += free
            free.also { free = InProcessDownloader.MIN_FREE_BYTES / 2 }
        }

        val failure = runCatching { downloader(freeSpace = shrinking).download(server.url("/big.pdf").toString(), null, cookiesOf(), "big.pdf", null) }
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("checked before writing, then after 8 MiB", 2, checks.size)
        assertTrue(media.rows().isEmpty())
    }

    @Test
    fun aServerThatSendsTooSlowlyIsGivenUpOn() = runTest {
        // 10 bytes at a time, with a minute passing between reads.
        server.enqueue(MockResponse().setBody(Buffer().write(PDF + ByteArray(4_096))).throttleBody(10, 20, TimeUnit.MILLISECONDS))
        var now = 0L
        val minutePerRead: () -> Long = { now.also { now += InProcessDownloader.THROUGHPUT_WINDOW_MS + 1 } }

        val failure = runCatching { downloader(clock = minutePerRead).download(server.url("/slow.pdf").toString(), null, cookiesOf(), "slow.pdf", null) }
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(1, media.inserts().size)
        assertTrue(media.rows().isEmpty())
    }

    @Test
    fun downloadsUseConnectionsOfTheirOwnThatAreNeverKept() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(PDF)))
        val downloader = downloader()

        downloader.download(server.url("/a.pdf").toString(), null, cookiesOf(), "a.pdf", null)

        assertNotSame(client.connectionPool, downloader.client.connectionPool)
        assertEquals("no connection is kept for a later request", 0, downloader.client.connectionPool.connectionCount())
        assertEquals(0, client.connectionPool.connectionCount())
    }

    @Test
    fun aDownloadThatFailsHalfwayLeavesNothing() = runTest {
        // The server closes the connection after half the body.
        val body = PDF + ByteArray(8 * 1024)
        server.enqueue(MockResponse().setBody(Buffer().write(body)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        val failure = runCatching { downloader().download(server.url("/cut.pdf").toString(), null, cookiesOf(), "cut.pdf", null) }
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(media.rows().isEmpty())
        assertFalse(media.deletes().isEmpty())
    }

    /** The request the server got; fails at once when none came. */
    private fun MockWebServer.received(): RecordedRequest = checkNotNull(takeRequest(1, TimeUnit.SECONDS)) { "The server got no request" }

    private fun TestScope.downloader(
        maxBytes: Long = MAX_IN_PROCESS_DOWNLOAD_BYTES,
        // Robolectric's file system reports no free space at all.
        freeSpace: () -> Long = { Long.MAX_VALUE },
        clock: () -> Long = { 0 },
    ): InProcessDownloader {
        val io = StandardTestDispatcher(testScheduler)
        return InProcessDownloader({ client }, MediaStoreDownloads(app, ioDispatcher = io), io, maxBytes, freeSpace, clock)
    }

    private fun cookiesOf(vararg cookies: Pair<String, String>): suspend (String) -> String? {
        val byUrl = cookies.toMap()
        return { url ->
            cookieLookups += url
            byUrl[url]
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) Skim"
        const val APK = "application/vnd.android.package-archive"
        const val WAIT_MS = 20_000L
        val PDF = "%PDF-1.4\n1 0 obj\n".toByteArray()
    }
}
