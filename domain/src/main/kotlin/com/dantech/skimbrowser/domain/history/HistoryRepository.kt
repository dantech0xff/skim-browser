package com.dantech.skimbrowser.domain.history

import kotlinx.coroutines.flow.Flow

/**
 * Browsing history: one row per http(s) URL. Callers must never record incognito visits.
 */
interface HistoryRepository {
    /**
     * Records a visit to [url]. Anything but an http(s) URL with a host, or a URL longer than
     * [MAX_URL_LENGTH], is ignored. A title longer than [MAX_TITLE_LENGTH] is cut.
     */
    suspend fun recordVisit(url: String, title: String, at: Long)

    /** Sets the title of [url]'s row once the page reports one. A blank title keeps the old one. */
    suspend fun updateTitle(url: String, title: String)

    /**
     * The newest [LIST_LIMIT] entries, newest first. A non-blank [query] keeps the entries whose
     * title or URL contains it, searched across the whole table.
     */
    fun observe(query: String?): Flow<List<HistoryEntry>>

    /** Up to [limit] entries whose title or URL contains [text], newest first. None for blank text. */
    suspend fun matches(text: String, limit: Int): List<HistoryEntry>

    /** Deletes entry [id] and returns it for Undo. */
    suspend fun remove(id: Long): HistoryEntry?

    /**
     * The Undo of [remove]. The entry returns with its id. If its URL was visited again meanwhile,
     * that newer row is kept, so Undo never breaks the one-row-per-URL rule.
     */
    suspend fun restore(entry: HistoryEntry)

    /** Deletes all history. */
    suspend fun clearAll()

    companion object {
        /** History (13) lists at most this many entries. Search still covers every row. */
        const val LIST_LIMIT = 1000

        const val MAX_URL_LENGTH = 8_192

        const val MAX_TITLE_LENGTH = 1_024
    }
}
