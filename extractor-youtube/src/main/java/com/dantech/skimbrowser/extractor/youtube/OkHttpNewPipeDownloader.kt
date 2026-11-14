package com.dantech.skimbrowser.extractor.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

internal class OkHttpNewPipeDownloader(
    private val client: OkHttpClient,
) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod().uppercase()
        val body = when (method) {
            "GET", "HEAD" -> null
            else -> (request.dataToSend() ?: ByteArray(0)).toRequestBody()
        }
        val call = okhttp3.Request.Builder()
            .url(request.url())
            .method(method, body)
        request.headers().forEach { (name, values) ->
            values.forEach { value -> call.addHeader(name, value) }
        }
        client.newCall(call.build()).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
