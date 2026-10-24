package com.dantech.skimbrowser.browser.downloads

import android.Manifest
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.annotation.MainThread
import com.dantech.skimbrowser.browser.ActivityHost
import com.dantech.skimbrowser.browser.BrowserProfile
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.SkimWebView
import com.dantech.skimbrowser.browser.TabManager
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

/**
 * Where the downloads of the tabs go. Lives as long as the Activity; [start] is called on the main
 * thread, by the WebViews' download listener or by a screen.
 *
 * - A page may start a download on its own once per [AUTOMATIC_INTERVAL_MS] per tab, whatever it
 *   loads in between (reloading or moving on to another page gives it no new allowance); more are
 *   dropped and reported as failed. A tap on the page in the last [GESTURE_WINDOW_MS] lets one
 *   more download through (a tap is used up by the download it allows; the start of a scroll is
 *   no tap). Downloads the user asked for outside the page ([start] with `userInitiated`) are
 *   never limited.
 * - At most [MAX_INLINE_DOWNLOADS] `data:` and `blob:` downloads, held in memory while they are
 *   saved, run at a time; one more is refused as failed.
 * - http and https in a normal tab go to Android's DownloadManager, with the WebView's user agent
 *   and DownloadManager's notification. DownloadManager sends the request's `Cookie` header to
 *   every address a redirect leads to, so the default profile's cookies go only with a download
 *   the page started over https that the WebView requested itself, which it hands over with the
 *   response's type (its address is the one after the page's own redirects): never with a download
 *   the user asked for, whose address the page chose (an image behind a login then fails), nor with
 *   a cleartext one, nor with one handed over with no type. An older WebView (Chromium 66) hands a
 *   link with a `download` attribute over before it requests anything: the link's own address, no
 *   redirect followed, and no type.
 * - http and https in an incognito tab are downloaded here ([InProcessDownloader]) with the
 *   incognito profile's cookies, which never leave the process; DownloadManager never sees the
 *   address or the cookies. Without an incognito session there is no download (never the default
 *   profile's cookies).
 * - `data:` (decoded) and `blob:` (read from the page through [BlobBridge]) are saved here, at
 *   most [MAX_INLINE_DOWNLOAD_BYTES].
 * - Any other scheme is refused.
 *
 * The file's name comes from the page ([URLUtil.guessFileName]; a `data:` or `blob:` URL only
 * has one through Content-Disposition) and is made safe by [DownloadNames] for the type read from
 * the file's first bytes ([downloadMimeOf]); a DownloadManager download, whose bytes Skim never
 * sees, uses the server's type. Before scoped storage (API 26–28) Android's storage permission is
 * asked for first. How each download ends reaches [DownloadTracker.events], marked as an incognito
 * tab's when such a tab started it (or when the tab is no longer open, which leaves its kind
 * unknown); downloads run in [scope], so one that started finishes even when the Activity goes
 * away ([destroy]).
 */
@MainThread
class DownloadRouter(
    private val tabs: TabManager,
    private val incognito: IncognitoSession,
    private val profiles: BrowserProfiles,
    private val tracker: DownloadTracker,
    private val inProcess: InProcessDownloader,
    private val store: MediaStoreDownloads,
    private val blob: BlobBridge,
    activityHost: ActivityHost,
    /** The process's scope: a download outlives the Activity that started it. */
    private val scope: CoroutineScope,
    /** The default profile's `Cookie` header for a URL. */
    private val defaultCookies: (url: String) -> String? = { CookieManager.getInstance().getCookie(it) },
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** `SystemClock.elapsedRealtime()`, the clock the page's taps are timed by too. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {

    private enum class Source { WEB, DATA, BLOB }

    /** When each tab's page last started a download on its own. */
    private val automaticDownloads = HashMap<String, Long>()

    /** The `data:` and `blob:` downloads running now, each held in memory while it is saved. */
    private val inlineDownloads = Semaphore(MAX_INLINE_DOWNLOADS)

    /** Null once the Activity is gone: nothing can ask Android for a permission any more. */
    private var activityHost: ActivityHost? = activityHost

    /**
     * The Activity is being destroyed. Downloads already running go on without it; one that still
     * needs Android's storage permission fails, as nobody can be asked any more.
     */
    fun destroy() {
        activityHost = null
    }

    /**
     * Downloads [url] for tab [tabId], whose page in [webView] asked for it with [userAgent],
     * [contentDisposition] and [mimeType] (as `DownloadListener` reports them).
     * [userInitiated] is true when the user asked for it outside the page (Download image).
     */
    fun start(
        tabId: String,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        webView: WebView,
        userInitiated: Boolean = false,
    ) {
        val declared = mimeEssenceOf(mimeType)
        val source = sourceOf(url)
        val rawName = when (source) {
            Source.DATA, Source.BLOB -> dispositionName(contentDisposition)
            Source.WEB, null -> URLUtil.guessFileName(url, contentDisposition, declared)
        }
        val shownName = DownloadNames.sanitize(rawName, declared)
        val tab = tabs.state.value.tab(tabId)
        // A tab that is no longer open may have been an incognito one: its report says so.
        val incognito = tab?.incognito ?: true
        if (source == null || tab == null) {
            Log.w(TAG, "A download of an unsupported kind was refused")
            fail(shownName, incognito)
            return
        }
        if (!userInitiated && !consumeGesture(webView) && !allowAutomatic(tabId)) {
            Log.w(TAG, "A page started another download on its own; it was dropped")
            fail(shownName, incognito)
            return
        }
        if ((source == Source.DATA || source == Source.BLOB) && !inlineDownloads.tryAcquire()) {
            Log.w(TAG, "Too many downloads held in memory at once; one was dropped")
            fail(shownName, incognito)
            return
        }
        val agent = userAgent?.takeIf { it.isNotBlank() } ?: webView.settings.userAgentString
        when (source) {
            Source.WEB -> if (incognito) {
                startInProcess(url, agent, rawName, declared, shownName)
            } else {
                startSystem(url, agent, rawName, declared, pageStarted = !userInitiated)
            }
            Source.DATA -> launchDownload(shownName, incognito, inlineDownloads::release) { saveData(url, rawName, shownName) }
            Source.BLOB -> launchDownload(shownName, incognito, inlineDownloads::release) {
                saveBlob(webView, url, rawName, declared, shownName)
            }
        }
    }

    /**
     * A normal tab's web download, handed to DownloadManager; the default profile's cookies go
     * with it only when the page started it ([pageStarted]) over https and the WebView requested it
     * before handing it over, which the response's type ([declared]) shows.
     */
    private fun startSystem(url: String, userAgent: String?, rawName: String?, declared: String?, pageStarted: Boolean) {
        val name = DownloadNames.sanitize(rawName, declared)
        val cookie = if (!pageStarted || declared == null || !url.trim().startsWith(HTTPS, ignoreCase = true)) {
            null
        } else {
            try {
                defaultCookies(url)?.takeIf { it.isNotBlank() }
            } catch (e: RuntimeException) {
                Log.w(TAG, "The cookies for a download could not be read: ${e.javaClass.simpleName}")
                null
            }
        }
        launchDownload(name, incognito = false) {
            if (!storagePermitted()) return@launchDownload DownloadEvent.DownloadFailed(name)
            val request = SystemDownloadRequest(url, name, declared, userAgent, cookie)
            val enqueued = withContext(ioDispatcher) { tracker.enqueue(request) }
            // DownloadManager reports the end itself, through the tracker.
            if (enqueued) null else DownloadEvent.DownloadFailed(name)
        }
    }

    /** An incognito tab's web download, made here with the incognito profile's cookies. */
    private fun startInProcess(url: String, userAgent: String?, rawName: String?, declared: String?, shownName: String) {
        val profile = incognitoProfile()
        if (profile == null) {
            Log.w(TAG, "An incognito download has no incognito session")
            fail(shownName, incognito = true)
            return
        }
        launchDownload(shownName, incognito = true) {
            if (!storagePermitted()) return@launchDownload DownloadEvent.DownloadFailed(shownName)
            inProcess.download(
                url = url,
                userAgent = userAgent,
                cookies = { address -> withContext(mainDispatcher) { profile.cookieHeader(address) } },
                rawName = rawName,
                declaredMime = declared,
            )
        }
    }

    private suspend fun saveData(url: String, rawName: String?, shownName: String): DownloadEvent {
        // Malformed, or larger than the limit.
        val data = withContext(ioDispatcher) { parseDataUrl(url) } ?: return DownloadEvent.DownloadFailed(shownName)
        return saveBytes(rawName, data.bytes, data.mimeType)
    }

    private suspend fun saveBlob(webView: WebView, url: String, rawName: String?, declared: String?, shownName: String): DownloadEvent {
        // Read first: the page may let go of its blob soon after it asked for the download.
        val data = blob.fetch(webView, url) ?: return DownloadEvent.DownloadFailed(shownName)
        return saveBytes(rawName, data.bytes, data.mimeType ?: declared)
    }

    private suspend fun saveBytes(rawName: String?, bytes: ByteArray, declared: String?): DownloadEvent {
        val mime = downloadMimeOf(bytes.copyOf(minOf(bytes.size, SNIFF_BYTES)), declared, rawName)
        val name = DownloadNames.sanitize(rawName, mime)
        if (!storagePermitted()) return DownloadEvent.DownloadFailed(name)
        return store.save(name, mime) { out -> out.write(bytes) }
    }

    /**
     * Runs a download in [scope] and reports how it ended, marked [incognito] when an incognito tab
     * started it; an unexpected failure reports [name] failed. [ended] runs once it is over, also
     * when it never ran.
     */
    private fun launchDownload(name: String, incognito: Boolean, ended: () -> Unit = {}, download: suspend () -> DownloadEvent?) {
        scope.launch(mainDispatcher) {
            val event = try {
                download()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "A download failed: ${e.javaClass.simpleName}")
                DownloadEvent.DownloadFailed(name)
            }
            event?.let { tracker.report(it.startedBy(incognito)) }
        }.invokeOnCompletion { ended() }
    }

    private fun fail(name: String?, incognito: Boolean) {
        tracker.report(DownloadEvent.DownloadFailed(name, incognito))
    }

    /** True when Skim may write to the shared Downloads folder, after asking Android when it must. */
    private suspend fun storagePermitted(): Boolean = withContext(mainDispatcher) {
        if (!store.needsStoragePermission) return@withContext true
        val host = activityHost ?: return@withContext false
        suspendCancellableCoroutine { continuation ->
            host.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { granted ->
                if (continuation.isActive) continuation.resume(granted[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true)
            }
        }
    }

    /** True when tab [tabId]'s page may start a download on its own now: once per [AUTOMATIC_INTERVAL_MS]. */
    private fun allowAutomatic(tabId: String): Boolean {
        val now = clock()
        val last = automaticDownloads[tabId]
        if (last != null && now - last < AUTOMATIC_INTERVAL_MS) return false
        automaticDownloads[tabId] = now
        return true
    }

    /** True, using it up, when the user tapped the page just before. */
    private fun consumeGesture(webView: WebView): Boolean = (webView as? SkimWebView)?.consumeGesture(GESTURE_WINDOW_MS) == true

    /** The incognito session's profile, whose cookie store an incognito download uses; null without one. */
    private fun incognitoProfile(): BrowserProfile? {
        val name = when (val session = incognito.state.value) {
            is IncognitoSession.State.Active -> session.profile
            is IncognitoSession.State.Ending -> session.profile
            IncognitoSession.State.None -> return null
        }
        return try {
            profiles.get(name)
        } catch (e: RuntimeException) {
            Log.w(TAG, "The incognito profile could not be opened: ${e.javaClass.simpleName}")
            null
        }
    }

    private companion object {
        const val TAG = "SkimDownloads"

        /** How recent a tap on the page must be for a download to count as the user's. */
        const val GESTURE_WINDOW_MS = 5_000L

        /** How often a page may start a download on its own. */
        const val AUTOMATIC_INTERVAL_MS = 30_000L

        /** `data:` and `blob:` downloads at once: each is held in memory, up to 25 MB. */
        const val MAX_INLINE_DOWNLOADS = 2
        const val HTTPS = "https:"

        fun sourceOf(url: String): Source? = when (url.trim().substringBefore(':').lowercase(Locale.ROOT)) {
            "http", "https" -> Source.WEB
            "data" -> Source.DATA
            "blob" -> Source.BLOB
            else -> null
        }

        /** [this] as the report of a download that an incognito tab started, or a normal one. */
        fun DownloadEvent.startedBy(incognito: Boolean): DownloadEvent = when (this) {
            is DownloadEvent.DownloadCompleted -> copy(incognito = incognito)
            is DownloadEvent.DownloadFailed -> copy(incognito = incognito)
        }

        /** The file name a Content-Disposition header gives, or null when it gives none. */
        fun dispositionName(contentDisposition: String?): String? {
            if (contentDisposition.isNullOrBlank()) return null
            val named = URLUtil.guessFileName(NO_URL, contentDisposition, null)
            // Without a name in the header the platform falls back to a fixed one of its own.
            return named.takeIf { it != URLUtil.guessFileName(NO_URL, null, null) }
        }

        private const val NO_URL = ""
    }
}
