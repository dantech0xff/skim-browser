package com.dantech.skimbrowser.ai

import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** An Anthropic stand-in. It records every request and answers with an SSE fixture. */
class FakeClaudeServer : Closeable {
    val server = MockWebServer()

    init {
        server.start()
    }

    val baseUrl: String = server.url("/").toString().removeSuffix("/")

    val requestCount: Int get() = server.requestCount

    fun enqueue(name: String) {
        server.enqueue(sse(fixture(name)))
    }

    /** An HTTP error with an Anthropic-shaped JSON body, so the client reports a server failure. */
    fun enqueueStatus(code: Int) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"type":"error","error":{"type":"api_error","message":"unavailable"}}"""),
        )
    }

    fun take(): RecordedRequest = server.takeRequest()

    /**
     * Serves [name] slowly on its own port and counts the body bytes that actually went out.
     * [written] stays below the fixture size when the client closes early.
     */
    fun serveSlowly(name: String, pauseAfter: String = "First.", pauseMs: Long = 2_000): SlowBody {
        val text = fixture(name)
        val body = text.toByteArray()
        val markerAt = text.indexOf(pauseAfter)
        val splitAt = if (markerAt < 0) {
            text.length / 2
        } else {
            val eventEnd = text.indexOf("\n\n", markerAt)
            if (eventEnd < 0) text.length else eventEnd + 2
        }
        val split = splitAt
        val socket = ServerSocket(0)
        val written = AtomicInteger()
        val thread = Thread {
            socket.use { listener ->
                val client = listener.accept()
                client.use { connection ->
                    val input = connection.getInputStream().bufferedReader()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val output = connection.getOutputStream()
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                        "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    output.write(header.toByteArray())
                    output.flush()
                    try {
                        output.write(body, 0, split)
                        output.flush()
                        written.addAndGet(split)
                        Thread.sleep(pauseMs)
                        output.write(body, split, body.size - split)
                        output.flush()
                        written.addAndGet(body.size - split)
                    } catch (_: Exception) {
                        // The client closed the stream.
                    }
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        return SlowBody("http://127.0.0.1:${socket.localPort}", body.size, written, thread, socket)
    }

    override fun close() {
        server.shutdown()
    }

    class SlowBody(
        val baseUrl: String,
        val size: Int,
        private val written: AtomicInteger,
        private val thread: Thread,
        private val socket: ServerSocket,
    ) : Closeable {
        fun written(): Int = written.get()

        override fun close() {
            socket.close()
            thread.join(1_000)
        }
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/sse/$name")) { "Missing SSE fixture $name" }.readText()

    private fun sse(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "text/event-stream")
        .setBody(body)
}
