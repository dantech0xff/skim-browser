package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ChatKind {
    /** About one page (06). */
    PAGE,

    /** About several open tabs (06b). */
    TABS,
}

/**
 * A saved Skim chat (Skim history, 04c). Only chats of normal tabs are stored, and only while
 * Keep chat history is on. [url] is the page URL without its fragment; [tabCount] is the number of
 * tabs a [ChatKind.TABS] chat covers.
 */
@Entity(tableName = "skim_chats", indices = [Index("updatedAt")])
data class SkimChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ChatKind,
    val url: String?,
    val host: String?,
    val title: String,
    val tabCount: Int = 0,
    /** A short summary line for the Continue card on Home. */
    val snippet: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
