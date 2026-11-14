package com.dantech.skimbrowser.domain.download

import kotlinx.coroutines.flow.Flow

/** Where a download Skim started is in its life. */
enum class DownloadPhase { Running, Done, Failed }

/**
 * One download Skim started. [bytesTotal] is negative when the size is not known yet.
 * [retry] is null when nothing is left to try again. The address inside [retry] is never logged.
 */
data class DownloadItem(
    val id: Long,
    val fileName: String,
    val phase: DownloadPhase,
    val bytesSoFar: Long,
    val bytesTotal: Long,
    val uri: String?,
    val mime: String?,
    val incognito: Boolean,
    val retry: DownloadRetry?,
    val systemId: Long?,
    /** A short reason the row failed, without an address. Null when the row did not fail, or the cause was not kept. */
    val failureReason: String? = null,
)

/** What is known when a download is first asked for, before any bytes move. */
data class DownloadDraft(
    val fileName: String,
    val incognito: Boolean,
    val retry: DownloadRetry?,
    /** When set, this start continues the failed row instead of adding another. */
    val replaceId: Long? = null,
)

/** The saved list of downloads. The browser writes it. The Downloads screen reads it. */
interface DownloadLog {
    fun observe(): Flow<List<DownloadItem>>

    /** Returns the row id. The row is saved before this returns. */
    suspend fun noteStarted(draft: DownloadDraft): Long

    suspend fun noteSystem(localId: Long, systemId: Long)

    suspend fun noteProgress(localId: Long, soFar: Long, total: Long)

    suspend fun noteFinished(localId: Long, uri: String?, mime: String?, bytes: Long, fileName: String? = null)

    suspend fun noteFailed(localId: Long, reason: String? = null)

    suspend fun remove(id: Long): DownloadItem?

    suspend fun clearIncognito()

    /** Rows still running that DownloadManager can be asked about. */
    suspend fun runningWithSystemId(): List<DownloadItem>
}

/** A fraction of [item] when it is running and the size is known. */
fun DownloadItem.fraction(): Float? {
    if (phase != DownloadPhase.Running || bytesTotal <= 0L) return null
    return (bytesSoFar.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
}

/**
 * The size a running row shows. Once bytes have arrived, that is the cumulative count, so a
 * playlist does not replace it with the latest segment. A finished or failed row shows [bytesTotal].
 */
fun DownloadItem.runningSize(): Long =
    if (phase == DownloadPhase.Running && bytesSoFar > 0L) bytesSoFar else bytesTotal

/** Retry, refresh, and removal for the Downloads screen. The browser owns the real work. */
interface DownloadCommands {
    fun retry(retry: DownloadRetry)

    fun refresh()

    suspend fun remove(id: Long, deleteFile: Boolean)
}
