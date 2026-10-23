package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.history.HistoryEntry
import com.dantech.skimbrowser.domain.history.HistoryRepository

class RemoveHistoryEntry(private val history: HistoryRepository) {
    suspend operator fun invoke(id: Long): HistoryEntry? = history.remove(id)
}

class RestoreHistoryEntry(private val history: HistoryRepository) {
    suspend operator fun invoke(entry: HistoryEntry) = history.restore(entry)
}

class MatchHistory(private val history: HistoryRepository) {
    suspend operator fun invoke(text: String, limit: Int): List<HistoryEntry> = history.matches(text, limit)
}
