package com.dantech.skimbrowser.data.repo

import android.app.Application
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.domain.download.DownloadDraft
import com.dantech.skimbrowser.domain.download.DownloadItem
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.download.DownloadPhase
import com.dantech.skimbrowser.domain.download.DownloadHeader
import com.dantech.skimbrowser.domain.download.DownloadRetry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The download list in a private file. A row's id is chosen here so the browser can name it
 * before the write returns. The address columns exist only so a failed row can be tried again.
 */
@Singleton
class DownloadRepository(private val file: File) : DownloadLog {

    @Inject constructor(app: Application, storage: AppStorage) : this(storage.downloadsFile(app))

    private val gate = Mutex()
    private val rows = MutableStateFlow<List<Row>?>(null)

    override fun observe(): Flow<List<DownloadItem>> = flow {
        load()
        emitAll(rows.map { list -> list.orEmpty().map { it.toItem() } })
    }

    override suspend fun noteStarted(draft: DownloadDraft): Long = edit { current ->
        val replace = draft.replaceId
        if (replace != null && current.any { it.id == replace }) {
            val updated = current.map { row ->
                if (row.id != replace) {
                    row
                } else {
                    rowFrom(replace, draft)
                }
            }
            return@edit replace to updated
        }
        val id = (current.maxOfOrNull { it.id } ?: 0L) + 1
        val row = rowFrom(id, draft)
        id to (listOf(row) + current)
    }

    private fun rowFrom(id: Long, draft: DownloadDraft) = Row(
        id = id,
        fileName = draft.fileName,
        phase = DownloadPhase.Running,
        bytesSoFar = 0,
        bytesTotal = -1,
        uri = null,
        mime = null,
        incognito = draft.incognito,
        systemId = null,
        tabId = draft.retry?.tabId,
        url = draft.retry?.url,
        userAgent = draft.retry?.userAgent,
        contentDisposition = draft.retry?.contentDisposition,
        requestMime = draft.retry?.mimeType,
        userInitiated = draft.retry?.userInitiated,
        retryIncognito = draft.retry?.incognito,
        headers = draft.retry?.headers.orEmpty().filterNot { it.name.equals("Cookie", ignoreCase = true) },
    )

    override suspend fun noteSystem(localId: Long, systemId: Long) {
        edit { current ->
            0L to current.map { if (it.id == localId) it.copy(systemId = systemId) else it }
        }
    }

    override suspend fun noteProgress(localId: Long, soFar: Long, total: Long) {
        edit { current ->
            0L to current.map { row ->
                if (row.id == localId && row.phase == DownloadPhase.Running) {
                    row.copy(bytesSoFar = soFar.coerceAtLeast(0), bytesTotal = total)
                } else {
                    row
                }
            }
        }
    }

    override suspend fun noteFinished(localId: Long, uri: String?, mime: String?, bytes: Long, fileName: String?) {
        edit { current ->
            0L to current.map { row ->
                if (row.id != localId) {
                    row
                } else {
                    val total = if (bytes >= 0) bytes else row.bytesTotal
                    val name = fileName?.takeIf { it.isNotBlank() && '/' !in it && '\\' !in it } ?: row.fileName
                    row.copy(
                        phase = DownloadPhase.Done,
                        fileName = name,
                        uri = uri,
                        mime = mime,
                        bytesTotal = total,
                        bytesSoFar = if (total >= 0) total else row.bytesSoFar,
                    )
                }
            }
        }
    }

    override suspend fun noteFailed(localId: Long, reason: String?) {
        edit { current ->
            0L to current.map { row ->
                if (row.id == localId) row.copy(phase = DownloadPhase.Failed, failureReason = reason?.take(120)) else row
            }
        }
    }

    override suspend fun remove(id: Long): DownloadItem? {
        var removed: DownloadItem? = null
        edit { current ->
            removed = current.firstOrNull { it.id == id }?.toItem()
            0L to current.filterNot { it.id == id }
        }
        return removed
    }

    override suspend fun clearIncognito() {
        edit { current -> 0L to current.filterNot { it.incognito } }
    }

    override suspend fun runningWithSystemId(): List<DownloadItem> = gate.withLock {
        val current = rows.value ?: read().also { rows.value = it }
        current.filter { it.phase == DownloadPhase.Running && it.systemId != null }.map { it.toItem() }
    }

    private suspend fun load() {
        gate.withLock {
            if (rows.value == null) rows.value = read()
        }
    }

    private suspend fun edit(block: (List<Row>) -> Pair<Long, List<Row>>): Long = gate.withLock {
        val current = rows.value ?: read()
        val (id, next) = block(current)
        write(next)
        id
    }

    private fun read(): List<Row> {
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull { line -> line.takeIf { it.isNotBlank() }?.let(::decode) }
    }

    private fun write(items: List<Row>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(items.joinToString("\n") { it.encode() })
        if (!tmp.renameTo(file)) {
            file.writeBytes(tmp.readBytes())
            tmp.delete()
        }
        rows.value = items
    }
}

private const val SEP = "\u001f"

private data class Row(
    val id: Long,
    val fileName: String,
    val phase: DownloadPhase,
    val bytesSoFar: Long,
    val bytesTotal: Long,
    val uri: String?,
    val mime: String?,
    val incognito: Boolean,
    val systemId: Long?,
    val tabId: String?,
    val url: String?,
    val userAgent: String?,
    val contentDisposition: String?,
    val requestMime: String?,
    val userInitiated: Boolean?,
    val retryIncognito: Boolean?,
    val headers: List<DownloadHeader> = emptyList(),
    val failureReason: String? = null,
) {
    fun toItem(): DownloadItem {
        val retry = if (url.isNullOrBlank() || tabId.isNullOrBlank() || userInitiated == null || retryIncognito == null) {
            null
        } else {
            DownloadRetry(tabId, url, userAgent, contentDisposition, requestMime, userInitiated, retryIncognito, headers)
        }
        return DownloadItem(id, fileName, phase, bytesSoFar, bytesTotal, uri, mime, incognito, retry, systemId, failureReason)
    }

    fun encode(): String = listOf(
        id.toString(),
        esc(fileName),
        phase.name,
        bytesSoFar.toString(),
        bytesTotal.toString(),
        esc(uri),
        esc(mime),
        if (incognito) "1" else "0",
        systemId?.toString().orEmpty(),
        esc(tabId),
        esc(url),
        esc(userAgent),
        esc(contentDisposition),
        esc(requestMime),
        userInitiated?.let { if (it) "1" else "0" }.orEmpty(),
        retryIncognito?.let { if (it) "1" else "0" }.orEmpty(),
        encodeHeaders(headers),
        esc(failureReason),
    ).joinToString(SEP)
}

private fun decode(line: String): Row? {
    val parts = line.split(SEP)
    if (parts.size !in 16..18) return null
    val phase = enumValues<DownloadPhase>().firstOrNull { it.name == parts[2] } ?: return null
    return Row(
        id = parts[0].toLongOrNull() ?: return null,
        fileName = unesc(parts[1]).ifEmpty { return null },
        phase = phase,
        bytesSoFar = parts[3].toLongOrNull() ?: return null,
        bytesTotal = parts[4].toLongOrNull() ?: return null,
        uri = unesc(parts[5]).ifEmpty { null },
        mime = unesc(parts[6]).ifEmpty { null },
        incognito = parts[7] == "1",
        systemId = parts[8].toLongOrNull(),
        tabId = unesc(parts[9]).ifEmpty { null },
        url = unesc(parts[10]).ifEmpty { null },
        userAgent = unesc(parts[11]).ifEmpty { null },
        contentDisposition = unesc(parts[12]).ifEmpty { null },
        requestMime = unesc(parts[13]).ifEmpty { null },
        userInitiated = when (parts[14]) {
            "1" -> true
            "0" -> false
            else -> null
        },
        retryIncognito = when (parts[15]) {
            "1" -> true
            "0" -> false
            else -> null
        },
        headers = if (parts.size >= 17) decodeHeaders(parts[16]) else emptyList(),
        failureReason = if (parts.size >= 18) unesc(parts[17]).ifEmpty { null } else null,
    )
}

private const val HEADER_PAIR = "\u001e"
private const val HEADER_NAME = "\u001d"

private fun encodeHeaders(headers: List<DownloadHeader>): String = headers.joinToString(HEADER_PAIR) { header ->
    esc(header.name) + HEADER_NAME + esc(header.value)
}

private fun decodeHeaders(field: String): List<DownloadHeader> {
    if (field.isEmpty()) return emptyList()
    return field.split(HEADER_PAIR).mapNotNull { pair ->
        val name = pair.substringBefore(HEADER_NAME, "")
        val value = pair.substringAfter(HEADER_NAME, missingDelimiterValue = "")
        if (name.isEmpty() || !pair.contains(HEADER_NAME)) null else DownloadHeader(unesc(name), unesc(value))
    }
}

private fun esc(value: String?): String = value.orEmpty()
    .replace("\\", "\\\\")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace(SEP, "\\s")

private fun unesc(value: String): String = buildString(value.length) {
    var i = 0
    while (i < value.length) {
        val char = value[i]
        if (char == '\\' && i + 1 < value.length) {
            when (value[i + 1]) {
                '\\' -> append('\\')
                'n' -> append('\n')
                'r' -> append('\r')
                's' -> append(SEP)
                else -> append(value[i + 1])
            }
            i += 2
        } else {
            append(char)
            i++
        }
    }
}
