package com.dantech.skimbrowser.system

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.downloads.BlobBridge
import com.dantech.skimbrowser.browser.downloads.BlobData
import com.dantech.skimbrowser.browser.downloads.DownloadNames
import com.dantech.skimbrowser.browser.downloads.MAX_INLINE_DOWNLOAD_BYTES
import com.dantech.skimbrowser.browser.downloads.downloadMimeOf
import com.dantech.skimbrowser.browser.downloads.parseDataUrl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.io.DEFAULT_BUFFER_SIZE
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

/**
 * Prepares an image for the share sheet: http(s) fetched with the tab's own cookies, a `data:`
 * URL decoded in place, or a `blob:` read from the page. The bytes are written under
 * `cacheDir/shared/` (or `shared/incognito/` for an incognito tab) and returned as a file-provider
 * URI. Nothing over [MAX_INLINE_DOWNLOAD_BYTES] is written. A failure returns null and names no URL.
 */
@Singleton
class ShareImage(
    private val httpClient: OkHttpClient,
    private val cacheDir: File,
    private val incognito: IncognitoSession,
    private val profiles: BrowserProfiles,
    private val defaultCookies: (String) -> String? = { url ->
        android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
    },
) {

    @Inject constructor(
        httpClient: OkHttpClient,
        app: Application,
        incognito: IncognitoSession,
        profiles: BrowserProfiles,
    ) : this(httpClient, app.cacheDir, incognito, profiles)

    /**
     * The file to share, or null when the image cannot be read or is too large. [webView] is the
     * page that owns a `blob:` URL; other URLs ignore it.
     */
    suspend fun prepare(
        context: Context,
        tab: Tab,
        src: String,
        blob: BlobBridge,
        webView: android.webkit.WebView?,
    ): SharedImage? {
        val trimmed = src.trim()
        return try {
            val loaded = load(tab, trimmed, blob, webView) ?: return null
            withContext(Dispatchers.IO) {
                val rawName = fileNameOf(trimmed)
                val mime = downloadMimeOf(loaded.bytes, loaded.mimeType, rawName)
                val name = uniqueName(DownloadNames.sanitize(rawName, mime))
                val dir = File(cacheDir, if (tab.incognito) INCOGNITO_DIR else SHARED_DIR)
                if (!dir.isDirectory && !dir.mkdirs()) return@withContext null
                val file = File(dir, name)
                file.writeBytes(loaded.bytes)
                val uri = FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, file)
                SharedImage(uri, mime)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not prepare an image to share: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not prepare an image to share: ${e.javaClass.simpleName}")
            null
        }
    }

    private suspend fun load(tab: Tab, src: String, blob: BlobBridge, webView: android.webkit.WebView?): Loaded? {
        parseDataUrl(src)?.let { return Loaded(it.mimeType, it.bytes) }
        if (src.startsWith(BLOB, ignoreCase = true)) {
            val data = withContext(Dispatchers.Main.immediate) {
                val view = webView ?: return@withContext null
                blob.fetch(view, src)
            } ?: return null
            return loaded(data)
        }
        if (!src.startsWith("http:", ignoreCase = true) && !src.startsWith("https:", ignoreCase = true)) {
            return null
        }
        // Profile stores are main-thread only. The download itself is not.
        val cookie = withContext(Dispatchers.Main.immediate) { cookieFor(tab, src) }
        return withContext(Dispatchers.IO) { fetchHttp(src, cookie) }
    }

    private fun fetchHttp(src: String, cookie: String?): Loaded? {
        val request = Request.Builder().url(src).apply { cookie?.let { header("Cookie", it) } }.build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val length = body.contentLength()
            if (length > MAX_INLINE_DOWNLOAD_BYTES) return null
            val bytes = body.readAtMost(MAX_INLINE_DOWNLOAD_BYTES) ?: return null
            val declared = response.header("Content-Type")?.substringBefore(';')?.trim()
            return Loaded(declared, bytes)
        }
    }

    private fun cookieFor(tab: Tab, url: String): String? {
        if (!tab.incognito) return defaultCookies(url)
        val name = when (val state = incognito.state.value) {
            is IncognitoSession.State.Active -> state.profile
            is IncognitoSession.State.Ending -> state.profile
            IncognitoSession.State.None -> return null
        }
        return profiles.get(name)?.cookieHeader(url)
    }

    private fun loaded(data: BlobData): Loaded? =
        if (data.bytes.size > MAX_INLINE_DOWNLOAD_BYTES) null else Loaded(data.mimeType, data.bytes)

    private companion object {
        const val TAG = "ShareImage"
        const val SHARED_DIR = "shared"
        const val INCOGNITO_DIR = "shared/incognito"
        const val PROVIDER_SUFFIX = ".fileprovider"
        const val BLOB = "blob:"
    }
}

/** An image written for the share sheet: its file-provider [uri] and the type to send it as. */
data class SharedImage(val uri: android.net.Uri, val mime: String)

private class Loaded(val mimeType: String?, val bytes: ByteArray)

/** Reads at most [max] bytes; null when the body is longer, so a huge response is not kept. */
internal fun ResponseBody.readAtMost(max: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    byteStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > max) return null
            out.write(buffer, 0, read)
        }
    }
    return out.toByteArray()
}

/** A `data:` URL has no file name; the type supplies the extension. Other URLs use their last segment. */
private fun fileNameOf(src: String): String {
    if (src.startsWith("data:", ignoreCase = true)) return "image"
    return src.substringBefore('?').substringBefore('#').substringAfterLast('/')
}

private fun uniqueName(safe: String): String {
    val stamp = UUID.randomUUID().toString().substring(0, 8)
    val dot = safe.lastIndexOf('.')
    return if (dot > 0) safe.substring(0, dot) + "-" + stamp + safe.substring(dot) else "$safe-$stamp"
}

/**
 * Deletes every file shared from an incognito tab, and shared files from normal tabs that are
 * older than a day. Run when the process starts.
 */
internal fun purgeOldShares(cacheDir: File, now: Long = System.currentTimeMillis()) {
    val shared = File(cacheDir, "shared")
    File(shared, "incognito").deleteRecursively()
    val cutoff = now - TimeUnit.DAYS.toMillis(1)
    shared.listFiles()?.forEach { file ->
        if (file.isFile && file.lastModified() < cutoff) file.delete()
    }
}
