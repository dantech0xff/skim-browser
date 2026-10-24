package com.dantech.skimbrowser.browser.downloads

import android.os.SystemClock
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource

/** A download that could not be made; the message never holds an address or a cookie. */
class DownloadException(message: String) : IOException(message)

/** The largest file Skim downloads itself (from an incognito tab). */
const val MAX_IN_PROCESS_DOWNLOAD_BYTES = 2L * 1024 * 1024 * 1024

/**
 * Downloads a file over HTTP inside Skim's process, for incognito tabs: their cookies are sent
 * from here and never handed to DownloadManager or any other process. The response is streamed
 * into [MediaStoreDownloads] as it arrives, never held whole in memory.
 *
 * Redirects are followed here, up to [MAX_REDIRECTS], so each request carries the cookies of its
 * own address: the cookies of one site never follow a redirect to another. Its connections are its
 * own and never kept once idle, so no later request of a normal tab reuses one a download made.
 *
 * A server cannot fill the phone: a file larger than [maxBytes] is refused (from its
 * Content-Length, or once that many bytes came), the download stops while less than
 * [MIN_FREE_BYTES] would be left free (checked before writing and every [FREE_SPACE_CHECK_BYTES]),
 * and a server that sends less than [MIN_WINDOW_BYTES] in [THROUGHPUT_WINDOW_MS] is given up on.
 * What was written so far is deleted.
 */
class InProcessDownloader(
    /** The app's shared HTTP client, made on first use. */
    httpClient: () -> OkHttpClient,
    private val store: MediaStoreDownloads,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = MAX_IN_PROCESS_DOWNLOAD_BYTES,
    /** Bytes free where downloads are saved. */
    private val freeSpace: () -> Long = store::freeBytes,
    /** `SystemClock.elapsedRealtime()`, for the server's pace. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {

    /** The app client's settings and threads, with connections of its own that are never kept idle. */
    val client: OkHttpClient by lazy {
        httpClient().newBuilder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * Downloads [url] (http or https) and saves it. [cookies] gives the `Cookie` header for each
     * address the download goes through, the first one and every redirect. The file's type is
     * read from its first bytes ([downloadMimeOf]), else the response's or [declaredMime]; its
     * name is [rawName] made safe for that type.
     *
     * @throws IOException when the download fails, including a response other than 2xx; a file
     *   being written is deleted, as it is when the coroutine is cancelled.
     */
    suspend fun download(
        url: String,
        userAgent: String?,
        cookies: suspend (url: String) -> String?,
        rawName: String?,
        declaredMime: String?,
    ): DownloadEvent.DownloadCompleted {
        var address = url.toHttpUrlOrNull() ?: throw DownloadException("Not an http or https address")
        repeat(MAX_REDIRECTS + 1) {
            val request = Request.Builder().url(address).get().apply {
                userAgent?.takeIf { it.isNotBlank() }?.let { header(USER_AGENT, it) }
                cookies(address.toString())?.takeIf { it.isNotBlank() }?.let { header(COOKIE, it) }
            }.build()
            val step = withContext(ioDispatcher) {
                client.newCall(request).executeCancellable { response ->
                    when {
                        response.isRedirect -> Step.Redirect(redirectTarget(address, response))
                        !response.isSuccessful -> throw DownloadException("The server answered ${response.code}")
                        else -> Step.Saved(save(response, rawName, declaredMime))
                    }
                }
            }
            when (step) {
                is Step.Saved -> return step.file
                is Step.Redirect -> address = step.to
            }
        }
        throw DownloadException("Too many redirects")
    }

    /** What one request of a download led to. */
    private sealed interface Step {
        class Redirect(val to: HttpUrl) : Step

        class Saved(val file: DownloadEvent.DownloadCompleted) : Step
    }

    private fun redirectTarget(from: HttpUrl, response: Response): HttpUrl {
        val location = response.header(LOCATION) ?: throw DownloadException("A redirect without a location")
        return from.resolve(location) ?: throw DownloadException("A redirect to an address that is not http or https")
    }

    private suspend fun save(response: Response, rawName: String?, declaredMime: String?): DownloadEvent.DownloadCompleted {
        val body = response.body ?: throw DownloadException("The response has no body")
        if (body.contentLength() > maxBytes) throw DownloadException("The file is larger than the limit")
        if (freeSpace() < MIN_FREE_BYTES) throw DownloadException("Too little free space")
        val source = body.source()
        val head = firstBytes(source)
        val mime = downloadMimeOf(head, body.contentType()?.toString() ?: declaredMime, rawName)
        val name = DownloadNames.sanitize(rawName, mime)
        return store.save(name, mime) { out ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var total = 0L
            var sinceSpaceCheck = 0L
            var windowStart = clock()
            var windowBytes = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) throw DownloadException("The file is larger than the limit")
                sinceSpaceCheck += read
                if (sinceSpaceCheck >= FREE_SPACE_CHECK_BYTES) {
                    sinceSpaceCheck = 0
                    if (freeSpace() < MIN_FREE_BYTES) throw DownloadException("Too little free space")
                }
                windowBytes += read
                val now = clock()
                if (now - windowStart >= THROUGHPUT_WINDOW_MS) {
                    if (windowBytes < MIN_WINDOW_BYTES) throw DownloadException("The server sends too slowly")
                    windowStart = now
                    windowBytes = 0
                }
                out.write(buffer, 0, read)
            }
        }
    }

    /** Up to [SNIFF_BYTES] first bytes of [source], left in it to be read again. */
    private fun firstBytes(source: BufferedSource): ByteArray {
        source.request(SNIFF_BYTES.toLong())
        val available = minOf(source.buffer.size, SNIFF_BYTES.toLong()).toInt()
        return source.buffer.snapshot(available).toByteArray()
    }

    companion object {
        /** Free space a download must leave on the phone. */
        const val MIN_FREE_BYTES = 256L * 1024 * 1024
        const val FREE_SPACE_CHECK_BYTES = 8L * 1024 * 1024
        const val THROUGHPUT_WINDOW_MS = 60_000L
        const val MIN_WINDOW_BYTES = 16L * 1024

        /** As many as OkHttp itself follows. */
        private const val MAX_REDIRECTS = 20
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val USER_AGENT = "User-Agent"
        private const val COOKIE = "Cookie"
        private const val LOCATION = "Location"
    }
}

/**
 * Runs this call and hands its response to [block], then closes the response. Cancelling the
 * coroutine cancels the call, which also ends a read the server keeps waiting; the coroutine then
 * ends as cancelled, not with the call's failure.
 */
private suspend fun <T> Call.executeCancellable(block: suspend (Response) -> T): T = coroutineScope {
    val call = this@executeCancellable
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }
    try {
        call.execute().use { response -> block(response) }
    } catch (e: IOException) {
        currentCoroutineContext().ensureActive()
        throw e
    } finally {
        watcher.cancel()
    }
}
