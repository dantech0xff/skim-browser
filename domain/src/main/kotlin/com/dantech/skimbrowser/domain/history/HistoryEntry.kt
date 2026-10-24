package com.dantech.skimbrowser.domain.history

/** One visited http(s) URL. A later visit to the same URL replaces this row. */
data class HistoryEntry(
    val id: Long = 0,
    val url: String,
    val title: String,
    val host: String,
    val visitedAt: Long,
)
