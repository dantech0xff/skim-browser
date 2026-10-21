package com.dantech.skimbrowser.data.repo

import androidx.room.withTransaction
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.containsPattern
import com.dantech.skimbrowser.data.db.dao.ChatListItem
import com.dantech.skimbrowser.data.db.dao.SkimChatDao
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.SkimMessageEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/** A chat removed from Skim history, kept whole so Undo can put it back with the same ids. */
data class DeletedChat(val chat: SkimChatEntity, val messages: List<SkimMessageEntity>)

/**
 * Saved Skim chats (04c, 06, 06b). Only chats of normal tabs are stored, and only while Keep chat
 * history is on; the caller enforces both.
 */
class ChatRepository(private val dao: SkimChatDao, private val db: SkimDatabase) {

    private val removalSignals = MutableSharedFlow<Long?>(extraBufferCapacity = REMOVAL_BUFFER)

    /**
     * Every chat deletion, after it has committed: the deleted chat's id, or null when all chats
     * were deleted. This is the only deletion signal, so whatever holds chats in memory listens
     * here. Nothing is replayed to late collectors, and emitting never waits when nobody listens.
     */
    val removals: SharedFlow<Long?> = removalSignals.asSharedFlow()

    /** The newest [limit] page chats, for the Continue cards on Home. */
    fun observeRecentPageChats(limit: Int): Flow<List<SkimChatEntity>> =
        dao.observeRecent(ChatKind.PAGE, limit.coerceAtLeast(0))

    /** Skim history rows, newest first; a non-blank [query] keeps chats whose title or host contains it. */
    fun observeAll(query: String?): Flow<List<ChatListItem>> {
        val text = query?.trim().orEmpty()
        return dao.observeListItems(if (text.isEmpty()) null else containsPattern(text))
    }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun chat(id: Long): SkimChatEntity? = dao.chat(id)

    /** The messages of [chatId] in the order they were written. */
    suspend fun messages(chatId: Long): List<SkimMessageEntity> = dao.messages(chatId)

    /** Saves a new chat and returns its id; the database always picks a fresh positive id. */
    suspend fun createChat(c: SkimChatEntity): Long = dao.insertChat(c.copy(id = 0))

    suspend fun updateChat(c: SkimChatEntity) = dao.updateChat(c)

    /** Saves a new message of a saved chat and returns its id; the database picks the id. */
    suspend fun addMessage(m: SkimMessageEntity): Long {
        require(m.chatId > 0) { "A message belongs to a saved chat" }
        return dao.insertMessage(m.copy(id = 0))
    }

    suspend fun updateMessage(m: SkimMessageEntity) = dao.updateMessage(m)

    /**
     * Deletes chat [id] with its messages, then emits [id] on [removals]. Returns what was deleted,
     * for Undo, or null when there was no such chat. Finishes even if the caller is cancelled, so a
     * deleted chat is never left behind in memory.
     */
    suspend fun delete(id: Long): DeletedChat? = withContext(NonCancellable) {
        val deleted = db.withTransaction {
            dao.chat(id)?.let { chat ->
                DeletedChat(chat, dao.messages(id)).also { dao.deleteChat(id) }
            }
        }
        if (deleted != null) removalSignals.emit(id)
        deleted
    }

    /** The Undo of [delete]: the chat and its messages return with their ids; a chat still present is left alone. */
    suspend fun restore(d: DeletedChat) {
        require(d.chat.id > 0) { "Only a deleted saved chat can be restored" }
        db.withTransaction {
            if (dao.chat(d.chat.id) == null) {
                dao.insertChat(d.chat)
                dao.insertMessages(d.messages.map { it.copy(chatId = d.chat.id) })
            }
        }
    }

    /**
     * Deletes every chat, checkpoints so no deleted text stays in the write-ahead log, then emits
     * null on [removals] even when nothing was saved, so chats kept only in memory go too.
     */
    suspend fun deleteAll() {
        withContext(NonCancellable) {
            dao.deleteAllChats()
            db.checkpointTruncate()
            removalSignals.emit(null)
        }
    }

    private companion object {
        /** Signals buffered for a slow collector before a deletion waits for it. */
        const val REMOVAL_BUFFER = 64
    }
}
