package com.dantech.skimbrowser.browser.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.AnyThread
import androidx.core.net.toUri
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** How a download ended, for the snackbar that reports it. */
sealed interface DownloadEvent {
    /**
     * True when an incognito tab started the download, or when its tab is unknown: the snackbar that
     * names the file must not show up in a screenshot of a screen that is not incognito.
     */
    val incognito: Boolean

    /**
     * The file [fileName] is in the shared Downloads folder at [uri]. [mime] is the type Android
     * keeps for the file (MediaStore's or DownloadManager's), the one to open it with.
     */
    data class DownloadCompleted(
        val fileName: String,
        val uri: Uri,
        val mime: String,
        override val incognito: Boolean = false,
    ) : DownloadEvent

    /** The download of [fileName] (null when it never had a name) failed or was refused. */
    data class DownloadFailed(val fileName: String?, override val incognito: Boolean = false) : DownloadEvent
}

/**
 * A download for Android's DownloadManager. Never logged or printed: it holds the page's address
 * and the cookies sent with it.
 */
class SystemDownloadRequest(
    val url: String,
    /** The file's name in the Downloads folder, already made safe. */
    val fileName: String,
    /** The type the server declared, or null to leave it to the response. */
    val mimeType: String?,
    val userAgent: String?,
    /** The `Cookie` header for [url], or null without cookies. */
    val cookie: String?,
)

/** What the tracker needs of Android's DownloadManager, behind an interface so it can be tested. */
interface SystemDownloads {
    /**
     * Hands [request] to DownloadManager and returns its download id.
     *
     * @throws RuntimeException when DownloadManager refuses it.
     */
    fun enqueue(request: SystemDownloadRequest): Long

    /** The download's `DownloadManager.STATUS_*`, or null when there is no such download any more. */
    fun status(id: Long): Int?

    /** `DownloadManager.getUriForDownloadedFile`. */
    fun uriFor(id: Long): Uri?

    /** `DownloadManager.getMimeTypeForDownloadedFile`: the type DownloadManager keeps for the file. */
    fun mimeTypeFor(id: Long): String?
}

/**
 * [SystemDownloads] over DownloadManager: the file goes to the shared Downloads folder under its
 * name, with a notification while it downloads and once it is done.
 */
class AndroidSystemDownloads(context: Context) : SystemDownloads {

    private val manager: DownloadManager? by lazy { context.getSystemService(DownloadManager::class.java) }

    override fun enqueue(request: SystemDownloadRequest): Long {
        val downloads = checkNotNull(manager) { "DownloadManager is not available" }
        val system = DownloadManager.Request(request.url.toUri())
            .setTitle(request.fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, request.fileName)
        request.mimeType?.let(system::setMimeType)
        request.userAgent?.takeIf { it.isNotBlank() }?.let { system.addRequestHeader(USER_AGENT, it) }
        request.cookie?.takeIf { it.isNotBlank() }?.let { system.addRequestHeader(COOKIE, it) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Before scoped storage a file DownloadManager writes is only listed once it is scanned.
            @Suppress("DEPRECATION")
            system.allowScanningByMediaScanner()
        }
        return downloads.enqueue(system)
    }

    override fun status(id: Long): Int? {
        val downloads = manager ?: return null
        return downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
        }
    }

    override fun uriFor(id: Long): Uri? = manager?.getUriForDownloadedFile(id)

    override fun mimeTypeFor(id: Long): String? = manager?.getMimeTypeForDownloadedFile(id)

    private companion object {
        const val USER_AGENT = "User-Agent"
        const val COOKIE = "Cookie"
    }
}

/**
 * The downloads of the process and how they end, for the snackbar that reports them (one stream
 * for every screen and Activity).
 *
 * DownloadManager downloads are followed by id: only ids Skim enqueued count, and each one is
 * reported once, when DownloadManager says it succeeded (with the URI and type DownloadManager
 * gives) or failed. A download removed meanwhile (cancelled from its notification) is dropped
 * without a word. The completion broadcast only arrives while the Activity listens, so [refresh]
 * asks again for every download still followed. DownloadManager only ever gets the downloads of
 * normal tabs (an incognito tab's never leave the process), so its reports are never incognito
 * ones. Downloads Skim saves itself are reported through [report], as their router marks them.
 *
 * Events are not replayed: they go to whoever collects [events] when they happen.
 */
class DownloadTracker(
    private val system: SystemDownloads,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _events = MutableSharedFlow<DownloadEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    /** DownloadManager ids Skim enqueued and has not reported yet, with their file names. */
    private val followed = ConcurrentHashMap<Long, String>()

    /**
     * Hands [request] to DownloadManager and follows it; false when DownloadManager refused it. It
     * calls into the DownloadManager service, so callers make it off the main thread.
     */
    @AnyThread
    fun enqueue(request: SystemDownloadRequest): Boolean {
        val id = try {
            system.enqueue(request)
        } catch (e: RuntimeException) {
            Log.w(TAG, "DownloadManager refused a download: ${e.javaClass.simpleName}")
            return false
        }
        followed[id] = request.fileName
        return true
    }

    /** DownloadManager finished download [id], of Skim's or another app's. */
    @AnyThread
    fun onDownloadComplete(id: Long) {
        if (!followed.containsKey(id)) return
        scope.launch(ioDispatcher) { askAbout(id) }
    }

    /** Asks DownloadManager again about every download still followed. */
    @AnyThread
    fun refresh() {
        if (followed.isEmpty()) return
        scope.launch(ioDispatcher) { followed.keys.toList().forEach(::askAbout) }
    }

    /** Reports how a download that Skim saved itself ended. */
    @AnyThread
    fun report(event: DownloadEvent) {
        _events.tryEmit(event)
    }

    private fun askAbout(id: Long) {
        val name = followed[id] ?: return
        val status = try {
            system.status(id)
        } catch (e: RuntimeException) {
            Log.w(TAG, "DownloadManager could not be asked about a download: ${e.javaClass.simpleName}")
            return
        }
        when (status) {
            null -> followed.remove(id)
            DownloadManager.STATUS_SUCCESSFUL -> if (followed.remove(id) != null) report(completed(id, name))
            DownloadManager.STATUS_FAILED -> if (followed.remove(id) != null) report(DownloadEvent.DownloadFailed(name, incognito = false))
            // Pending, running or paused: still followed.
            else -> Unit
        }
    }

    private fun completed(id: Long, name: String): DownloadEvent = try {
        val uri = system.uriFor(id)
        if (uri == null) {
            DownloadEvent.DownloadFailed(name, incognito = false)
        } else {
            val mime = system.mimeTypeFor(id)?.takeIf { it.isNotBlank() } ?: OCTET_STREAM
            DownloadEvent.DownloadCompleted(name, uri, mime, incognito = false)
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "A finished download could not be read: ${e.javaClass.simpleName}")
        DownloadEvent.DownloadFailed(name, incognito = false)
    }

    private companion object {
        const val TAG = "SkimDownloads"
        const val EVENT_BUFFER = 16
        const val OCTET_STREAM = "application/octet-stream"
    }
}
