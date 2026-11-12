package com.dantech.skimbrowser.domain.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A log that keeps nothing. Tests of the router use it so they do not need a database. */
object NoDownloadLog : DownloadLog {
    override fun observe(): Flow<List<DownloadItem>> = flowOf(emptyList())

    override suspend fun noteStarted(draft: DownloadDraft): Long = 0

    override suspend fun noteSystem(localId: Long, systemId: Long) = Unit

    override suspend fun noteProgress(localId: Long, soFar: Long, total: Long) = Unit

    override suspend fun noteFinished(localId: Long, uri: String?, mime: String?, bytes: Long) = Unit

    override suspend fun noteFailed(localId: Long) = Unit

    override suspend fun remove(id: Long): DownloadItem? = null

    override suspend fun clearIncognito() = Unit

    override suspend fun runningWithSystemId(): List<DownloadItem> = emptyList()
}
