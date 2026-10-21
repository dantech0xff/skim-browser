package com.dantech.skimbrowser.browser

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/**
 * The pages of the device tests, served over cleartext http from 127.0.0.1 by a server in the test
 * process: the HTML files in the test APK's assets, and a few endpoints of their own. The same
 * server answers as `localhost` ([url] with [LOCALHOST]), which browsers treat as another site with
 * cookies of its own.
 *
 * - `/set-cookie?value=V` answers with the cookie `skim=V`; `/cookie-check` sets nothing, its
 *   request shows which cookies the browser sent.
 * - `/image.png` is a small red PNG; `/cacheable.css` and `/cacheable.js` may be cached for a day;
 *   `/sw.js` is a service worker that does nothing.
 * - `/report.pdf` is a small PDF ([REPORT_BYTES]) sent as an attachment named [REPORT_NAME], or
 *   `?name=` when given; `/redirect?to=U` redirects (302) to U; `/moving.pdf` is the same PDF the
 *   first time it is asked for and then redirects to `?to=`, as a file server that moved the file
 *   between two requests; `/huge.bin` declares a length beyond what Skim downloads itself
 *   ([HUGE_LENGTH]) and sends the first [HUGE_SENT_BYTES] of it, then nothing.
 * - An asset asked for with `?csp=P` comes with the Content Security Policy P.
 * - Any other path that names no asset gets a short page, so links and popups have somewhere to go.
 *
 * Every request is kept, so a test can read the headers a page was loaded with. [holdAfterFirst]
 * keeps a path's later answers back until [release], so a test can act while a download waits.
 */
class TestPages : Closeable {

    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<RecordedRequest>()
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val image: ByteArray by lazy { redSquarePng() }
    private val held = ConcurrentHashMap<String, CountDownLatch>()

    fun start(): TestPages {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received += request
                return respond(request)
            }
        }
        server.start(InetAddress.getByName(HOST), 0)
        return this
    }

    /** The address of [path] (starting with a slash, query allowed) on this server, under [host]. */
    fun url(path: String, host: String = HOST): String = "http://$host:${server.port}$path"

    /** The requests for [path] (query aside) received so far, oldest first. */
    fun requests(path: String): List<RecordedRequest> = received.filter { it.requestUrl?.encodedPath == path }

    /**
     * Holds back the answer to every request for [path] but the first until [release]: the first
     * is the WebView's own, which tells it the response is a download, and a later one is the
     * download itself.
     */
    fun holdAfterFirst(path: String) {
        held[path] = CountDownLatch(1)
    }

    /** Lets the answers [holdAfterFirst] holds for [path] go. */
    fun release(path: String) {
        held.remove(path)?.countDown()
    }

    override fun close() {
        held.values.forEach { it.countDown() }
        held.clear()
        try {
            server.shutdown()
        } catch (e: IOException) {
            Log.w(TAG, "The test server did not shut down cleanly", e)
        }
    }

    private fun respond(request: RecordedRequest): MockResponse {
        val url = request.requestUrl ?: return MockResponse().setResponseCode(BAD_REQUEST)
        val path = url.encodedPath
        held[path]?.let { gate -> if (requests(path).size > 1) gate.await(HOLD_LIMIT_MS, TimeUnit.MILLISECONDS) }
        return when (path) {
            "/set-cookie" -> page("Cookie set").addHeader("Set-Cookie", "skim=${url.queryParameter("value")}; Path=/; Max-Age=3600")
            "/cookie-check" -> page("Cookies")
            "/image.png" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(image))
            "/cacheable.css" -> cacheable("text/css", "body { color: #222; }")
            "/cacheable.js" -> cacheable("text/javascript", "window.skimCached = true;")
            "/sw.js" -> MockResponse().setHeader("Content-Type", "text/javascript").setBody("self.addEventListener('fetch', function () {});")
            "/favicon.ico" -> MockResponse().setResponseCode(NOT_FOUND)
            "/report.pdf" -> report(url.queryParameter("name") ?: REPORT_NAME)
            "/redirect" -> redirect(url.queryParameter("to"))
            "/moving.pdf" -> if (requests(path).size == 1) report(REPORT_NAME) else redirect(url.queryParameter("to"))
            "/huge.bin" -> huge()
            else -> asset(path.removePrefix("/"))?.apply { url.queryParameter("csp")?.let { setHeader("Content-Security-Policy", it) } }
                ?: page(path)
        }
    }

    private fun asset(name: String): MockResponse? {
        if (!name.endsWith(".html") || '/' in name) return null
        val text = try {
            assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            return null
        }
        return MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(text)
    }

    private fun page(title: String) = MockResponse()
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody("<!doctype html><html><head><meta charset=\"utf-8\"><title>$title</title></head><body><p>$title</p></body></html>")

    private fun cacheable(type: String, body: String) = MockResponse()
        .setHeader("Content-Type", type)
        .setHeader("Cache-Control", "public, max-age=86400")
        .setBody(body)

    private fun report(name: String) = MockResponse()
        .setHeader("Content-Type", "application/pdf")
        .setHeader("Content-Disposition", "attachment; filename=\"$name\"")
        .setBody(Buffer().write(REPORT_BYTES))

    private fun redirect(to: String?) =
        if (to == null) MockResponse().setResponseCode(BAD_REQUEST) else MockResponse().setResponseCode(FOUND).setHeader("Location", to)

    /**
     * Headers that announce [HUGE_LENGTH] bytes, then [HUGE_SENT_BYTES] and nothing more while the
     * connection stays open: a client that reads the body waits until its read timeout. The WebView
     * only hands a download over once it has the first bytes of the body.
     */
    private fun huge() = MockResponse()
        .setHeader("Content-Type", "application/octet-stream")
        .setHeader("Content-Disposition", "attachment; filename=\"huge.bin\"")
        .setBody(Buffer().write(ByteArray(HUGE_SENT_BYTES)))
        .setHeader("Content-Length", HUGE_LENGTH)

    companion object {
        private const val TAG = "SkimTestPages"
        private const val HOST = "127.0.0.1"

        /** This server under another site's name: cookies of [HOST] never go to it. */
        const val LOCALHOST = "localhost"

        /** The name `/report.pdf` gives its file: a folder and a colon that a file name cannot keep. */
        const val REPORT_NAME = "reports/Q3: report.pdf"

        /** What `/huge.bin` says it holds: 3 GiB. */
        const val HUGE_LENGTH = 3L * 1024 * 1024 * 1024

        /** What `/huge.bin` sends of it. */
        const val HUGE_SENT_BYTES = 64 * 1024

        /** The bytes of `/report.pdf`: a one-page PDF. */
        val REPORT_BYTES: ByteArray = (
            "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n"
            ).toByteArray(Charsets.US_ASCII)

        private const val BAD_REQUEST = 400
        private const val FOUND = 302
        private const val NOT_FOUND = 404
        private const val IMAGE_SIZE = 64

        /** No test holds an answer longer; past it the answer goes anyway, so the server never hangs. */
        private const val HOLD_LIMIT_MS = 60_000L

        private fun redSquarePng(): ByteArray {
            val bitmap = createBitmap(IMAGE_SIZE, IMAGE_SIZE).apply { eraseColor(Color.RED) }
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }
    }
}
