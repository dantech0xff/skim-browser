package com.dantech.skimbrowser.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Posts one request to the proxy and reads the event stream. Closing the flow cancels the call,
 * which is how Stop ends a reply.
 */
class ClaudeClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val installId: String,
) {
    fun streamEvents(request: SkimRequest): Flow<AiEvent> = callbackFlow {
        val call = http.newCall(httpRequest(request))
        val reader = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    when {
                        response.code == 429 -> throw ProxyLimited()
                        !response.isSuccessful -> throw ProxyHttpException(response.code)
                    }
                    val parser = SseParser()
                    val source = response.body?.source() ?: throw IOException("empty body")
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        for (event in parser.push("$line\n")) {
                            trySend(event)
                        }
                    }
                    for (event in parser.push("\n")) {
                        trySend(event)
                    }
                    close()
                }
            } catch (error: Throwable) {
                close(error)
            }
        }
        awaitClose {
            call.cancel()
            reader.cancel()
        }
    }

    private fun httpRequest(request: SkimRequest): Request =
        Request.Builder()
            .url("$baseUrl/v1/messages")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("X-Skim-Install", installId)
            .post(request.bytes.toRequestBody(JSON))
            .build()

    companion object {
        private val JSON = "application/json".toMediaType()

        fun http(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .retryOnConnectionFailure(false)
            .build()
    }
}
