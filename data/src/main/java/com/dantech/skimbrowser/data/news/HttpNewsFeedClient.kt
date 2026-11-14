package com.dantech.skimbrowser.data.news

import com.dantech.skimbrowser.domain.news.NewsFeedClient
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches one feed with the platform HTTP stack. Publishers answered a browser user agent and
 * returned an empty channel, or nothing, without one. No cookies are sent.
 */
class HttpNewsFeedClient @Inject constructor() : NewsFeedClient {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) { read(url) }

    internal fun read(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", ACCEPT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Feed answered HTTP $code")
            connection.inputStream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) error("Feed is larger than $MAX_BYTES bytes")
                    buffer.write(chunk, 0, read)
                }
                return buffer.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        const val MAX_BYTES = 1_500_000
        const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
