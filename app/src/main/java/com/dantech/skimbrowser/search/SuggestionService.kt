package com.dantech.skimbrowser.search

import android.util.Log
import com.dantech.skimbrowser.data.settings.SearchEngine
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The search engine's suggestions for what is typed in the search field (04b), from its OpenSearch
 * suggestions endpoint. A failure of any kind gives no suggestions, so the field then shows only
 * history and bookmarks.
 *
 * Only what the user types is sent, and only to the engine's own https address: requests carry no
 * cookie, credential or referrer, are never cached (incognito text must not reach a disk cache) and
 * never follow a redirect. Nothing about a query or its address is logged.
 *
 * @param client the app's HTTP client, whose connections and threads are shared.
 * @param callTimeoutMs the longest a request may take, from connecting to the last byte.
 */
class SuggestionService(client: OkHttpClient, callTimeoutMs: Long = CALL_TIMEOUT_MS) {

    internal val client: OkHttpClient = client.newBuilder()
        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .cache(null)
        .build()

    /**
     * [engine]'s suggestions for [query], at most [MAX_SUGGESTIONS]. Nothing is sent, and the list
     * is empty, when the query is blank, longer than [MAX_QUERY_LENGTH] or opens as an address
     * ([omniboxAddress]): an address being typed or edited, such as the page's own from the address
     * bar, would tell the engine where the user goes, path and query included, and the engine only
     * suggests searches. Any failure (no connection, the timeout, an HTTP error or redirect, a body
     * over [MAX_BODY_BYTES] or one that is not suggestions) also gives an empty list; nothing is
     * thrown except the cancellation of the caller, which cancels the request.
     */
    suspend fun suggestions(engine: SearchEngine, query: String): List<String> {
        if (query.isBlank() || query.length > MAX_QUERY_LENGTH || omniboxAddress(query) != null) return emptyList()
        val url = suggestionAddress(engine, query) ?: return emptyList()
        return try {
            client.newCall(Request.Builder().url(url).build()).await(::readSuggestions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            emptyList()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Suggestions failed: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    internal companion object {
        const val CALL_TIMEOUT_MS = 3_000L

        /** A response larger than this is not suggestions; it is dropped unread past this size. */
        const val MAX_BODY_BYTES = 64L * 1024

        /** Longer text is not sent: it is pasted rather than typed, and no engine suggests for it. */
        const val MAX_QUERY_LENGTH = 100

        private const val TAG = "SuggestionService"

        /** [engine]'s suggestion address for [query], or null unless it is https on the engine's own host. */
        fun suggestionAddress(engine: SearchEngine, query: String): HttpUrl? {
            val url = engine.suggestUrl(query).toHttpUrlOrNull() ?: return null
            val host = engine.suggestTemplate.substringAfter("://").substringBefore('/')
            return url.takeIf { it.isHttps && it.host == host }
        }

        /** The suggestions in a successful response read up to [MAX_BODY_BYTES], in the charset its header names (UTF-8 by default). */
        private fun readSuggestions(response: Response): List<String> {
            val body = response.body
            if (!response.isSuccessful || body == null || body.contentLength() > MAX_BODY_BYTES) return emptyList()
            val source = body.source()
            if (source.request(MAX_BODY_BYTES + 1)) return emptyList()
            val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            return parseOpenSearchSuggestions(source.buffer.readString(charset))
        }
    }
}

/**
 * Runs this call on the client's threads and returns what [read] makes of its response, which is
 * closed after. Cancelling the coroutine cancels the call; a response or failure that comes after
 * that is dropped.
 */
private suspend fun <T> Call.await(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching { response.use(read) })
            }
        },
    )
}
