package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.SkimMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * A row of Skim history (04c): the chat with the counts its subtitle needs. [questionCount] counts
 * the user's questions; [hasSummary] and [answerCount] count only assistant messages that kept
 * text (complete or stopped, not refused or failed). [answerCount] covers answers and tab overviews.
 */
data class ChatListItem(
    @Embedded val chat: SkimChatEntity,
    val questionCount: Int,
    val hasSummary: Boolean,
    val answerCount: Int,
)

/** Single statements on chats and messages; `ChatRepository` composes them into transactions. */
@Dao
interface SkimChatDao {

    @Query("SELECT * FROM skim_chats WHERE kind = :kind ORDER BY updatedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(kind: ChatKind, limit: Int): Flow<List<SkimChatEntity>>

    /** Every chat, newest first; a non-null [pattern] (from containsPattern) filters by title or host. */
    @Query(
        "SELECT c.*, " +
            "(SELECT COUNT(*) FROM skim_messages m WHERE m.chatId = c.id AND m.kind = 'QUESTION') AS questionCount, " +
            "EXISTS(SELECT 1 FROM skim_messages m WHERE m.chatId = c.id AND m.kind = 'SUMMARY' " +
            "AND m.status IN ('COMPLETE', 'STOPPED')) AS hasSummary, " +
            "(SELECT COUNT(*) FROM skim_messages m WHERE m.chatId = c.id AND m.role = 'ASSISTANT' " +
            "AND m.kind IN ('ANSWER', 'TABS_OVERVIEW') AND m.status IN ('COMPLETE', 'STOPPED')) AS answerCount " +
            "FROM skim_chats c " +
            "WHERE :pattern IS NULL OR c.title LIKE :pattern ESCAPE '\\' OR c.host LIKE :pattern ESCAPE '\\' " +
            "ORDER BY c.updatedAt DESC, c.id DESC",
    )
    fun observeListItems(pattern: String?): Flow<List<ChatListItem>>

    @Query("SELECT COUNT(*) FROM skim_chats")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM skim_chats WHERE id = :id")
    suspend fun chat(id: Long): SkimChatEntity?

    @Query("SELECT * FROM skim_messages WHERE chatId = :chatId ORDER BY createdAt, id")
    suspend fun messages(chatId: Long): List<SkimMessageEntity>

    @Insert
    suspend fun insertChat(chat: SkimChatEntity): Long

    @Update
    suspend fun updateChat(chat: SkimChatEntity)

    @Insert
    suspend fun insertMessage(message: SkimMessageEntity): Long

    @Insert
    suspend fun insertMessages(messages: List<SkimMessageEntity>)

    @Update
    suspend fun updateMessage(message: SkimMessageEntity)

    /** Deletes a chat; its messages go with it through the foreign key. */
    @Query("DELETE FROM skim_chats WHERE id = :id")
    suspend fun deleteChat(id: Long)

    @Query("DELETE FROM skim_chats")
    suspend fun deleteAllChats()
}
