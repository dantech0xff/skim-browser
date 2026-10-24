package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

typealias MessageRole = com.dantech.skimbrowser.domain.chat.MessageRole
typealias MessageKind = com.dantech.skimbrowser.domain.chat.MessageKind
typealias MessageFeedback = com.dantech.skimbrowser.domain.chat.MessageFeedback
typealias MessageStatus = com.dantech.skimbrowser.domain.chat.MessageStatus

/**
 * One message of a saved chat, deleted with its chat. JSON columns stay strings and are parsed in
 * Kotlin, so no query depends on SQLite's JSON functions.
 */
@Entity(
    tableName = "skim_messages",
    foreignKeys = [
        ForeignKey(
            entity = SkimChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chatId")],
)
data class SkimMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val role: MessageRole,
    val kind: MessageKind,
    val text: String,
    /** Cited paragraph indexes, as JSON. */
    val citationsJson: String? = null,
    val feedback: MessageFeedback = MessageFeedback.NONE,
    val status: MessageStatus,
    /** Follow-up questions offered with a tabs overview, as JSON. */
    val suggestedQuestionsJson: String? = null,
    val createdAt: Long,
)
