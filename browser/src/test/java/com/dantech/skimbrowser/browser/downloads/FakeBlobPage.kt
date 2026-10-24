package com.dantech.skimbrowser.browser.downloads

import android.webkit.WebView
import java.util.Base64
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * A page for the blob bridge without Chromium: a [ScriptRunner] that answers each run of the blob
 * script ([script]) the way a page would, and records every run. A read's state exists in the page
 * from its start until it is dropped ([live]), as the script's window property does; take answers
 * from [answers] in order, then waiting, and gone once the read is not live. Like
 * `evaluateJavascript`, it answers after a dispatch, never within the call.
 */
internal class FakeBlobPage(private val script: String) : ScriptRunner {

    /** One run of the script: its argument, and whether the bridge waited for its result. */
    class Call(val args: JsonObject, val awaited: Boolean) {
        val op: String get() = text("op").orEmpty()
        val nonce: String get() = text("nonce").orEmpty()

        fun text(key: String): String? = (args[key] as? JsonPrimitive)?.contentOrNull

        fun number(key: String): Long? = (args[key] as? JsonPrimitive)?.longOrNull
    }

    val calls = mutableListOf<Call>()

    /** The reads whose state the page holds. */
    val live = linkedSetOf<String>()

    /** The page's own origin, as `location.origin`. */
    var origin = "https://daylightjournal.com"

    /** How the page answers a start: by default as the script does for a page of [origin]. */
    var onStart: (Call) -> String? = { call ->
        if (call.text("origin") == origin) started(call.nonce, origin) else unavailable(call.nonce)
    }

    /** The page's next answers to take, in order. */
    val answers = ArrayDeque<(nonce: String) -> String?>()

    /** When true the WebView never answers a script it was asked to evaluate, like one that went away. */
    var silent = false

    /** The ops run, in order. */
    val ops: List<String> get() = calls.map { it.op }

    private var takes = 0

    override suspend fun evaluate(webView: WebView, script: String): String? {
        val call = parse(script, awaited = true)
        // A read that never stops asking is a failing test, not one that runs for ever.
        if (call.op == "take" && ++takes > MAX_TAKES) throw AssertionError("The page was asked more than $MAX_TAKES times")
        calls += call
        if (call.op == "start") live += call.nonce
        yield()
        if (silent) awaitCancellation()
        return when (call.op) {
            "start" -> onStart(call)
            "take" -> when {
                call.nonce !in live -> gone(call.nonce)
                answers.isEmpty() -> waiting(call.nonce)
                else -> answers.removeFirst().invoke(call.nonce)
            }
            else -> null
        }
    }

    override fun run(webView: WebView, script: String) {
        val call = parse(script, awaited = false)
        calls += call
        if (call.op == "drop") live -= call.nonce
    }

    /** Answers like an honest page holding [bytes] of type [mime], [sliceBytes] per chunk. */
    fun serve(bytes: ByteArray, mime: String?, sliceBytes: Int = BlobBridge.CHUNK_CHARS / 4 * 3) {
        answers += { nonce -> start(nonce, bytes.size.toLong(), mime) }
        var offset = 0
        var index = 0
        while (offset < bytes.size) {
            val end = minOf(offset + sliceBytes, bytes.size)
            val part = bytes.copyOfRange(offset, end)
            val number = index
            answers += { nonce -> chunk(nonce, number, part) }
            offset = end
            index++
        }
        val chunks = index
        answers += { nonce -> end(nonce, chunks) }
    }

    private fun parse(run: String, awaited: Boolean): Call {
        val prefix = "($script)("
        check(run.startsWith(prefix) && run.endsWith(")")) { "Not a run of the blob script" }
        return Call(Json.parseToJsonElement(run.substring(prefix.length, run.length - 1)).jsonObject, awaited)
    }

    companion object {
        /** Far more than a read that times out after 30 s asks (one take every 25 ms). */
        const val MAX_TAKES = 5_000

        fun started(nonce: String, origin: String) = """{"status":"started","origin":"$origin","nonce":"$nonce"}"""

        fun unavailable(nonce: String) = """{"status":"unavailable","nonce":"$nonce"}"""

        fun waiting(nonce: String) = """{"status":"waiting","nonce":"$nonce"}"""

        fun gone(nonce: String) = """{"status":"gone","nonce":"$nonce"}"""

        fun start(nonce: String, size: Long, mime: String?) = """{"status":"start","size":$size,"mime":"${mime.orEmpty()}","nonce":"$nonce"}"""

        fun chunk(nonce: String, index: Int, bytes: ByteArray) =
            """{"status":"chunk","index":$index,"data":"${Base64.getEncoder().encodeToString(bytes)}","nonce":"$nonce"}"""

        fun end(nonce: String, chunks: Int) = """{"status":"end","chunks":$chunks,"nonce":"$nonce"}"""

        fun error(nonce: String, reason: String) = """{"status":"error","reason":"$reason","nonce":"$nonce"}"""
    }
}
