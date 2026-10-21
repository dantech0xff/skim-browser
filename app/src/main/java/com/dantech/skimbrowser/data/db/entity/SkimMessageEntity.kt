package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole { USER, ASSISTANT }

enum class MessageKind {
    /** A question typed or picked by the user. */
    QUESTION,

    /** The page summary (06). */
    SUMMARY,

    /** An answer to a question. */
    ANSWER,

    /** The overview of several tabs (06b); [SkimMessageEntity.text] holds its JSON. */
    TABS_OVERVIEW,
}

enum class MessageFeedback { NONE, GOOD, POOR }

enum class MessageStatus {
    COMPLETE,

    /** Interrupted (Stop, navigation or a closed sheet); [SkimMessageEntity.text] is the part received. */
    STOPPED,

    /** The model declined; no text is kept. */
    REFUSED,
    ERROR,
}

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
