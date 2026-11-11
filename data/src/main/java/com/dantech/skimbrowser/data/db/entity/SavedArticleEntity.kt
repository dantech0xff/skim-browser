package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One article saved for Reader view. [blocksJson] is the block list. Incognito pages are never stored. */
@Entity(
    tableName = "saved_articles",
    indices = [Index(value = ["url"], unique = true), Index("savedAt")],
)
data class SavedArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val host: String,
    val title: String,
    val byline: String?,
    val blocksJson: String,
    val savedAt: Long,
    val summary: String? = null,
)
