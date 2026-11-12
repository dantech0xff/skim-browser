package com.dantech.skimbrowser.browser.downloads

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.AnyThread
import androidx.core.net.toUri
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.download.DownloadRetry
import com.dantech.skimbrowser.domain.download.NoDownloadLog
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
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
        /** How many bytes were written, or -1 when this completion did not count them. */
        val bytes: Long = -1,
    ) : DownloadEvent

    /**
     * The download of [fileName] (null when it never had a name) failed or was refused.
     * [retry] starts the same download again. It is null when nothing was left to try again.
     */
    data class DownloadFailed(
        val fileName: String?,
        override val incognito: Boolean = false,
        val retry: DownloadRetry? = null,
    ) : DownloadEvent
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

    /** Status and byte counts, or null when DownloadManager no longer has [id]. */
    fun snapshot(id: Long): DownloadSnapshot?

    /** Drops [id] from DownloadManager. That also deletes its file. */
    fun remove(id: Long)
}

/** Byte counts DownloadManager reports for one download. [total] is negative when it is unknown. */
data class DownloadSnapshot(val status: Int, val soFar: Long, val total: Long)

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

    override fun snapshot(id: Long): DownloadSnapshot? {
        val downloads = manager ?: return null
        return downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            DownloadSnapshot(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }
    }

    override fun remove(id: Long) {
        manager?.remove(id)
    }

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
@Singleton
class DownloadTracker(
    private val system: SystemDownloads,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val log: DownloadLog = NoDownloadLog,
) {

    @Inject constructor(app: Application, @AppScope scope: CoroutineScope, log: DownloadLog) :
        this(AndroidSystemDownloads(app), scope, log = log)

    private val _events = MutableSharedFlow<DownloadEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    /** DownloadManager ids Skim enqueued and has not reported yet. */
    private val followed = ConcurrentHashMap<Long, Followed>()

    /** In-process saves still writing, so removing a row can stop the file from being published. */
    private val running = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()

    /**
     * Hands [request] to DownloadManager and follows it; false when DownloadManager refused it. It
     * calls into the DownloadManager service, so callers make it off the main thread.
     */
    @AnyThread
    fun enqueue(request: SystemDownloadRequest, retry: DownloadRetry? = null, localId: Long = 0): Boolean {
        val id = try {
            system.enqueue(request)
        } catch (e: RuntimeException) {
            Log.w(TAG, "DownloadManager refused a download: ${e.javaClass.simpleName}")
            return false
        }
        followed[id] = Followed(request.fileName, retry, localId)
        if (localId != 0L) scope.launch(ioDispatcher) { log.noteSystem(localId, id) }
        return true
    }

    /**
     * Asks DownloadManager about a download that outlived this process. The snackbar is not told
     * again. A missing download is recorded as failed.
     */
    fun refreshSystem(localId: Long, systemId: Long) {
        scope.launch(ioDispatcher) {
            val snap = try {
                system.snapshot(systemId)
            } catch (e: RuntimeException) {
                Log.w(TAG, "DownloadManager could not be asked about a download: ${e.javaClass.simpleName}")
                return@launch
            }
            when (snap?.status) {
                null, DownloadManager.STATUS_FAILED -> log.noteFailed(localId)
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val event = completed(systemId, "", null)
                    if (event is DownloadEvent.DownloadCompleted) {
                        log.noteFinished(localId, event.uri.toString(), event.mime, snap.total)
                    } else {
                        log.noteFailed(localId)
                    }
                }
                else -> log.noteProgress(localId, snap.soFar, snap.total)
            }
        }
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
        scope.launch(ioDispatcher) { followed.keys.toList().forEach { askAbout(it) } }
    }

    /** Drops [id] from DownloadManager, which deletes that file. */
    fun removeSystem(id: Long) {
        scope.launch(ioDispatcher) {
            try {
                system.remove(id)
            } catch (e: RuntimeException) {
                Log.w(TAG, "DownloadManager could not remove a download: ${e.javaClass.simpleName}")
            }
        }
    }

    /** Reports how a download that Skim saved itself ended. */
    @AnyThread
    fun report(event: DownloadEvent) {
        _events.tryEmit(event)
    }

    /** [job] is the in-process save of row [localId]. [cancel] stops it before the file is published. */
    fun followJob(localId: Long, job: kotlinx.coroutines.Job) {
        if (localId == 0L) return
        running[localId] = job
        job.invokeOnCompletion { running.remove(localId, job) }
    }

    /** Stops the save of row [localId] when one is still writing. */
    fun cancel(localId: Long) {
        running.remove(localId)?.cancel()
    }

    private suspend fun askAbout(id: Long) {
        val followedDownload = followed[id] ?: return
        publish(followedDownload.localId, id, followedDownload)
    }

    private suspend fun publish(localId: Long, systemId: Long, followedDownload: Followed? = followed[systemId]) {
        val known = followedDownload ?: return
        val name = known.name
        val snap = try {
            system.snapshot(systemId)
        } catch (e: RuntimeException) {
            Log.w(TAG, "DownloadManager could not be asked about a download: ${e.javaClass.simpleName}")
            return
        }
        when (snap?.status) {
            null -> if (followed.remove(systemId) != null && localId != 0L) log.noteFailed(localId)
            DownloadManager.STATUS_SUCCESSFUL -> if (followed.remove(systemId) != null) {
                val event = completed(systemId, name, known.retry)
                report(event)
                if (localId != 0L) {
                    if (event is DownloadEvent.DownloadCompleted) {
                        log.noteFinished(localId, event.uri.toString(), event.mime, snap.total)
                    } else {
                        log.noteFailed(localId)
                    }
                }
            }
            DownloadManager.STATUS_FAILED -> if (followed.remove(systemId) != null) {
                report(DownloadEvent.DownloadFailed(name, incognito = false, retry = known.retry))
                if (localId != 0L) log.noteFailed(localId)
            }
            else -> if (localId != 0L) log.noteProgress(localId, snap.soFar, snap.total)
        }
    }

    private fun completed(id: Long, name: String, retry: DownloadRetry?): DownloadEvent = try {
        val uri = system.uriFor(id)
        if (uri == null) {
            DownloadEvent.DownloadFailed(name, incognito = false, retry = retry)
        } else {
            val mime = system.mimeTypeFor(id)?.takeIf { it.isNotBlank() } ?: OCTET_STREAM
            DownloadEvent.DownloadCompleted(name, uri, mime, incognito = false)
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "A finished download could not be read: ${e.javaClass.simpleName}")
        DownloadEvent.DownloadFailed(name, incognito = false, retry = retry)
    }

    private class Followed(val name: String, val retry: DownloadRetry?, val localId: Long)

    private companion object {
        const val TAG = "SkimDownloads"
        const val EVENT_BUFFER = 16
        const val OCTET_STREAM = "application/octet-stream"
    }
}
