package com.dantech.skimbrowser.data.repo

import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.containsPattern
import com.dantech.skimbrowser.data.db.dao.HistoryDao
import com.dantech.skimbrowser.data.db.entity.HistoryEntryEntity
import com.dantech.skimbrowser.data.hostOf
import com.dantech.skimbrowser.data.isWebUrl
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Browsing history (13 and the History rows of 04b): one row per http(s) URL. Callers must never
 * record incognito visits; this class has no way to tell them apart.
 */
class HistoryRepository(private val dao: HistoryDao, private val db: SkimDatabase) {

    /**
     * Records a visit to [url]; anything but an http(s) URL with a host, or a URL longer than
     * [MAX_URL_LENGTH], is ignored. A title longer than [MAX_TITLE_LENGTH] is cut.
     */
    suspend fun recordVisit(url: String, title: String, at: Long) {
        if (!isWebUrl(url) || url.length > MAX_URL_LENGTH) return
        val host = hostOf(url) ?: return
        dao.upsertKeepingLatest(HistoryEntryEntity(url = url, title = cutTitle(title), host = host, visitedAt = at))
    }

    /** Sets the title of [url]'s row once the page reports one; a blank title keeps the old one. */
    suspend fun updateTitle(url: String, title: String) {
        if (title.isBlank()) return
        dao.updateTitle(url, cutTitle(title))
    }

    /**
     * The newest [LIST_LIMIT] entries, newest first. A non-blank [query] keeps the entries whose
     * title or URL contains it, searched across the whole table.
     */
    fun observe(query: String?): Flow<List<HistoryEntryEntity>> {
        val text = query?.trim().orEmpty()
        return if (text.isEmpty()) dao.observeLatest(LIST_LIMIT) else dao.observeMatching(containsPattern(text), LIST_LIMIT)
    }

    /** Up to [limit] entries whose title or URL contains [text], newest first; none for blank text. */
    suspend fun matches(text: String, limit: Int): List<HistoryEntryEntity> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || limit <= 0) return emptyList()
        return dao.matching(containsPattern(trimmed), limit)
    }

    /** Deletes entry [id] and returns it for Undo. */
    suspend fun remove(id: Long): HistoryEntryEntity? = dao.remove(id)

    /**
     * The Undo of [remove]: the entry returns with its id. If its URL was visited again meanwhile,
     * that newer row is kept instead and takes the later visit time, so Undo never breaks the
     * one-row-per-URL rule.
     */
    suspend fun restore(entry: HistoryEntryEntity) {
        dao.upsertKeepingLatest(entry)
    }

    /** Deletes all history and checkpoints so no deleted row stays in the write-ahead log. */
    suspend fun clearAll() {
        withContext(NonCancellable) {
            dao.deleteAll()
            db.checkpointTruncate()
        }
    }

    companion object {
        /** History (13) lists at most this many entries; search still covers every row. */
        const val LIST_LIMIT = 1000

        /**
         * The longest URL that is recorded. A page can make URLs of megabytes with
         * `history.pushState`, and every distinct URL adds a row that is kept until history is cleared.
         */
        const val MAX_URL_LENGTH = 8_192

        /** The longest title that is stored; a page controls its title and can make it huge. */
        const val MAX_TITLE_LENGTH = 1_024

        /** [title] cut to [MAX_TITLE_LENGTH] characters, never between the two halves of a surrogate pair. */
        private fun cutTitle(title: String): String {
            if (title.length <= MAX_TITLE_LENGTH) return title
            val end = if (title[MAX_TITLE_LENGTH - 1].isHighSurrogate()) MAX_TITLE_LENGTH - 1 else MAX_TITLE_LENGTH
            return title.substring(0, end)
        }
    }
}
