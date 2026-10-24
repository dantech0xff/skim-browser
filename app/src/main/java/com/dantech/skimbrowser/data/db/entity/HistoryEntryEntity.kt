package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per visited http(s) URL (History, 13): a new visit to the same URL updates the row
 * instead of adding one. [host] is the site key from `hostOf`. Incognito visits are never written.
 */
@Entity(
    tableName = "history",
    indices = [Index(value = ["url"], unique = true), Index("visitedAt")],
)
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val host: String,
    val visitedAt: Long,
)
