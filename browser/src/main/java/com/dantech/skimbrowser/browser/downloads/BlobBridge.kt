package com.dantech.skimbrowser.browser.downloads

import android.content.res.AssetManager
import android.util.Log
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.core.net.toUri
import com.dantech.skimbrowser.browser.page.escapeForScript
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** The bytes of a `blob:` URL and the type the page gave the blob (null when it gave none). */
class BlobData(val mimeType: String?, val bytes: ByteArray)

/**
 * What the blob bridge needs of a WebView: running a script in its page, behind an interface so
 * the bridge can be tested without Chromium.
 */
internal interface ScriptRunner {
    /** Runs [script] in [webView]'s page and returns its result as JSON; null when there is none. */
    suspend fun evaluate(webView: WebView, script: String): String?

    /** Runs [script] in [webView]'s page without waiting for it, so a cancelled caller can still run it. */
    fun run(webView: WebView, script: String)
}

/** [ScriptRunner] over `WebView.evaluateJavascript`. */
internal object WebViewScriptRunner : ScriptRunner {

    override suspend fun evaluate(webView: WebView, script: String): String? = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
    }

    override fun run(webView: WebView, script: String) {
        webView.evaluateJavascript(script, null)
    }
}

/**
 * Reads the bytes of a page's `blob:` URL for a download, from the page itself: a blob only exists
 * inside the page that made it. Lives as long as the Activity; every call is made on the main
 * thread.
 *
 * Skim runs `skim-blob-fetch.js` in the page once to start a read and then again to take each
 * step, a slice of the blob at a time: no Java object and no message channel is ever given to the
 * page. While a read runs the page holds its state in one non-enumerable property of `window`
 * named after the read's random nonce, dropped afterwards whatever happens (done, failed, timed
 * out, cancelled, or the Activity going away). The script gets its arguments as one JSON value,
 * never spliced into its code, and only starts when the page is of the blob's own origin.
 *
 * What the page answers is the page's to make up, so all of it is checked here: the origin the
 * page reports, the nonce of every answer, the size against [MAX_INLINE_DOWNLOAD_BYTES] before any
 * byte and again while the bytes add up, the order and form of the chunks, and an end that matches
 * them. A read waits at most [FETCH_TIMEOUT_MS] in all. Reads run one at a time.
 *
 * A page whose Content Security Policy does not let it fetch its own `blob:` URLs cannot hand one
 * over; the read fails.
 */
@MainThread
class BlobBridge internal constructor(
    private val runner: ScriptRunner,
    /** The script, `assets/js/skim-blob-fetch.js`: one function expression. */
    private val readScript: () -> String,
    private val newNonce: () -> String = { UUID.randomUUID().toString() },
    private val timeoutMs: Long = FETCH_TIMEOUT_MS,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {

    constructor(assets: AssetManager) : this(
        runner = WebViewScriptRunner,
        readScript = { assets.open(SCRIPT).bufferedReader(Charsets.UTF_8).use { it.readText() } },
    )

    /** One read: the page's state for it lives from its start until it is dropped. */
    private class Read(val webView: WebView, val nonce: String, private val script: String) {
        var reading: Job? = null
        var dropped = false

        fun call(args: BlobScriptArgs): String =
            "($script)(${escapeForScript(Json.encodeToString(BlobScriptArgs.serializer(), args))})"
    }

    private val script by lazy { readScript().trimEnd().removeSuffix(";") }
    private val oneAtATime = Mutex()
    private var current: Read? = null
    private var destroyed = false

    /**
     * The bytes of [blobUrl] from [webView]'s page, at most [maxBytes] of them; null when the blob
     * cannot be read: an origin that is not http or https (an opaque `blob:null/…` one included),
     * a page of another origin, a blob too large, an answer that does not check out, no end within
     * the timeout, or the Activity went away.
     */
    suspend fun fetch(webView: WebView, blobUrl: String, maxBytes: Int = MAX_INLINE_DOWNLOAD_BYTES): BlobData? =
        withContext(mainDispatcher) {
            val origin = blobOriginOf(blobUrl)
            if (destroyed || origin == null) return@withContext null
            oneAtATime.withLock {
                if (destroyed) null else read(webView, blobUrl, origin, maxBytes)
            }
        }

    /** The Activity is being destroyed: a read still running ends with null and its state in the page is dropped now. */
    fun destroy() {
        destroyed = true
        current?.let { read ->
            drop(read)
            read.reading?.cancel()
        }
    }

    private suspend fun read(webView: WebView, blobUrl: String, origin: String, maxBytes: Int): BlobData? {
        val source = try {
            script
        } catch (e: IOException) {
            Log.w(TAG, "The blob script could not be read: ${e.javaClass.simpleName}")
            return null
        }
        val read = Read(webView, newNonce(), source)
        current = read
        try {
            return withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    val reading = async { transfer(read, blobUrl, origin, maxBytes) }
                    read.reading = reading
                    try {
                        reading.await()
                    } catch (e: CancellationException) {
                        // destroy() stopped the read, which ends with nothing; a cancelled caller stays cancelled.
                        ensureActive()
                        null
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.w(TAG, "A blob could not be read: ${e.javaClass.simpleName}")
            return null
        } finally {
            drop(read)
            if (current === read) current = null
        }
    }

    /** Starts the read in the page, then takes its steps until the blob is whole or the read fails. */
    private suspend fun transfer(read: Read, blobUrl: String, origin: String, maxBytes: Int): BlobData? {
        val start = BlobScriptArgs(op = START, nonce = read.nonce, url = blobUrl, origin = origin, maxBytes = maxBytes, chunkChars = CHUNK_CHARS)
        if (!startedFor(runner.evaluate(read.webView, read.call(start)), read.nonce, origin)) {
            Log.w(TAG, "The page cannot hand over its blob")
            return null
        }
        val take = read.call(BlobScriptArgs(op = TAKE, nonce = read.nonce))
        val assembly = BlobAssembly(read.nonce, maxBytes)
        while (true) {
            when (val step = assembly.accept(runner.evaluate(read.webView, take))) {
                BlobAssembly.Step.More -> Unit
                BlobAssembly.Step.Waiting -> delay(POLL_INTERVAL_MS)
                BlobAssembly.Step.Failed -> return null
                is BlobAssembly.Step.Done -> return step.blob
            }
        }
    }

    /** Deletes the read's state in the page, once, without waiting: it also runs for a cancelled read. */
    private fun drop(read: Read) {
        if (read.dropped) return
        read.dropped = true
        try {
            runner.run(read.webView, read.call(BlobScriptArgs(op = DROP, nonce = read.nonce)))
        } catch (e: RuntimeException) {
            Log.w(TAG, "A blob read could not be dropped: ${e.javaClass.simpleName}")
        }
    }

    internal companion object {
        const val SCRIPT = "js/skim-blob-fetch.js"
        const val FETCH_TIMEOUT_MS = 30_000L

        /** How long to wait before asking again while the page reads its next slice. */
        const val POLL_INTERVAL_MS = 25L

        /** Base64 characters per chunk (384 KiB of the blob). */
        const val CHUNK_CHARS = 512 * 1024
        const val START = "start"
        const val TAKE = "take"
        const val DROP = "drop"
        private const val TAG = "SkimBlobBridge"
    }
}

/** The blob script's one argument; what an operation does not use is left out. */
@Serializable
internal data class BlobScriptArgs(
    val op: String,
    val nonce: String,
    val url: String? = null,
    val origin: String? = null,
    val maxBytes: Int? = null,
    val chunkChars: Int? = null,
)

/**
 * The origin of [blobUrl] as a page reports it in `location.origin`: `blob:https://host:8443/uuid`
 * gives `https://host:8443` (lowercase, without a default port). Null for anything but a `blob:`
 * URL of an http or https origin, so an opaque `blob:null/…` blob (a sandboxed frame, a `data:`
 * page) is never read.
 */
internal fun blobOriginOf(blobUrl: String): String? {
    val trimmed = blobUrl.trim()
    if (!trimmed.startsWith(BLOB_SCHEME, ignoreCase = true)) return null
    return webOriginOf(trimmed.substring(BLOB_SCHEME.length))
}

/** The serialized origin of an http or https [url] (`scheme://host[:port]`), or null. */
internal fun webOriginOf(url: String): String? {
    val parsed = url.trim().toUri()
    val scheme = parsed.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    // The host as Chromium serializes it: brackets kept around an IPv6 address.
    val host = parsed.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    val bracketed = if (':' in host && !host.startsWith('[')) "[$host]" else host
    val defaultPort = if (scheme == "https") 443 else 80
    val port = parsed.port.takeIf { it != -1 && it != defaultPort }
    return "$scheme://$bracketed" + (port?.let { ":$it" } ?: "")
}

/** True when [reply] is the page's answer to a start: started, for [nonce], by a page of [origin]. */
internal fun startedFor(reply: String?, nonce: String, origin: String): Boolean {
    val message = jsonObjectOf(reply) ?: return false
    return message.text("status") == "started" && message.text("nonce") == nonce && message.text("origin") == origin
}

/**
 * Puts a blob together from the page's answers to take, one answer at a time: [Step.Waiting]
 * while the page reads, [Step.More] when an answer was taken in, then [Step.Done] or [Step.Failed]
 * once, and [Step.Failed] for anything after that.
 *
 * Any answer that does not check out fails the read: not a JSON object, another nonce, an error
 * or a read the page no longer holds, a size above [maxBytes] or bytes beyond the size announced,
 * chunks out of order, too long or not base64, or an end that does not match what came.
 */
internal class BlobAssembly(private val nonce: String, private val maxBytes: Int) {

    sealed interface Step {
        data object Waiting : Step

        data object More : Step

        data object Failed : Step

        class Done(val blob: BlobData) : Step
    }

    private var finished = false
    private var size = -1L
    private var mime: String? = null
    private var nextIndex = 0
    private var bytes: ByteArrayOutputStream? = null

    fun accept(reply: String?): Step {
        if (finished) return Step.Failed
        val message = jsonObjectOf(reply)
        val step = when {
            message == null || message.text("nonce") != nonce -> Step.Failed
            else -> when (message.text("status")) {
                "waiting" -> Step.Waiting
                "start" -> start(message)
                "chunk" -> chunk(message)
                "end" -> end(message)
                else -> Step.Failed
            }
        }
        if (step == Step.Failed || step is Step.Done) {
            finished = true
            bytes = null
        }
        return step
    }

    private fun start(message: JsonObject): Step {
        val announced = message.whole("size")
        if (size >= 0 || announced == null || announced < 0 || announced > maxBytes) return Step.Failed
        size = announced
        mime = message.text("mime")?.trim()?.ifEmpty { null }
        bytes = ByteArrayOutputStream(announced.coerceAtMost(INITIAL_BUFFER_BYTES.toLong()).toInt())
        return Step.More
    }

    private fun chunk(message: JsonObject): Step {
        val out = bytes ?: return Step.Failed
        val index = message.whole("index")
        val text = message.text("data")
        if (index != nextIndex.toLong() || text == null || text.length > MAX_CHUNK_CHARS) return Step.Failed
        val decoded = try {
            Base64.getDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            return Step.Failed
        }
        if (out.size().toLong() + decoded.size > size) return Step.Failed
        out.write(decoded)
        nextIndex++
        return Step.More
    }

    private fun end(message: JsonObject): Step {
        val out = bytes ?: return Step.Failed
        if (message.whole("chunks") != nextIndex.toLong() || out.size().toLong() != size) return Step.Failed
        return Step.Done(BlobData(mime, out.toByteArray()))
    }

    private companion object {
        /** A chunk may hold a little more than the script sends. */
        const val MAX_CHUNK_CHARS = BlobBridge.CHUNK_CHARS + 1024
        const val INITIAL_BUFFER_BYTES = 1024 * 1024
    }
}

/** [text] as a JSON object, or null for anything else (null, malformed JSON, another kind of value). */
private fun jsonObjectOf(text: String?): JsonObject? {
    if (text == null) return null
    return try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The whole number at [key], however the WebView wrote it (`4`, `4.0`, `4e0`); null for anything else. */
private fun JsonObject.whole(key: String): Long? {
    val value = (this[key] as? JsonPrimitive)?.takeIf { !it.isString } ?: return null
    value.longOrNull?.let { return it }
    val number = value.doubleOrNull ?: return null
    return if (number.isFinite() && number == floor(number) && abs(number) <= MAX_WHOLE) number.toLong() else null
}

/** The largest whole number a double holds exactly. */
private const val MAX_WHOLE = 9_007_199_254_740_992.0

private const val BLOB_SCHEME = "blob:"
