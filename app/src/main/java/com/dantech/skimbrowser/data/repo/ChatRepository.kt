package com.dantech.skimbrowser.data.repo

import androidx.room.withTransaction
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.containsPattern
import com.dantech.skimbrowser.data.db.dao.SkimChatDao
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.domain.chat.ChatRepository as ChatStore
import com.dantech.skimbrowser.domain.chat.ChatSummary
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.chat.SavedMessage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

typealias DeletedChat = com.dantech.skimbrowser.domain.chat.DeletedChat

/**
 * Saved Skim chats (04c, 06, 06b). Only chats of normal tabs are stored, and only while Keep chat
 * history is on; the caller enforces both.
 */
class ChatRepository(private val dao: SkimChatDao, private val db: SkimDatabase) : ChatStore {

    private val removalSignals = MutableSharedFlow<Long?>(extraBufferCapacity = REMOVAL_BUFFER)

    /**
     * Every chat deletion, after it has committed: the deleted chat's id, or null when all chats
     * were deleted. This is the only deletion signal, so whatever holds chats in memory listens
     * here. Nothing is replayed to late collectors, and emitting never waits when nobody listens.
     */
    override val removals: SharedFlow<Long?> = removalSignals.asSharedFlow()

    /** The newest [limit] page chats, for the Continue cards on Home. */
    override fun observeRecentPageChats(limit: Int): Flow<List<SavedChat>> =
        dao.observeRecent(ChatKind.PAGE, limit.coerceAtLeast(0)).map { rows -> rows.map { it.toDomain() } }

    /** Skim history rows, newest first; a non-blank [query] keeps chats whose title or host contains it. */
    override fun observeAll(query: String?): Flow<List<ChatSummary>> {
        val text = query?.trim().orEmpty()
        return dao.observeListItems(if (text.isEmpty()) null else containsPattern(text)).map { items -> items.map { it.toDomain() } }
    }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun load(id: Long): SavedChat? = dao.chat(id)?.toDomain()

    /** Saves a new chat and returns its id; the database always picks a fresh positive id. */
    override suspend fun createChat(chat: SavedChat): Long = dao.insertChat(chat.toEntity().copy(id = 0))

    /** The messages of [chatId] in the order they were written. */
    override suspend fun messages(chatId: Long): List<SavedMessage> = dao.messages(chatId).map { it.toDomain() }

    override suspend fun updateChat(chat: SavedChat) = dao.updateChat(chat.toEntity())

    /** Saves a new message of a saved chat and returns its id; the database picks the id. */
    override suspend fun addMessage(message: SavedMessage): Long {
        require(message.chatId > 0) { "A message belongs to a saved chat" }
        return dao.insertMessage(message.toEntity().copy(id = 0))
    }

    override suspend fun updateMessage(message: SavedMessage) = dao.updateMessage(message.toEntity())

    /**
     * Deletes chat [id] with its messages, then emits [id] on [removals]. Returns what was deleted,
     * for Undo, or null when there was no such chat. Finishes even if the caller is cancelled, so a
     * deleted chat is never left behind in memory.
     */
    override suspend fun delete(id: Long): DeletedChat? = withContext(NonCancellable) {
        val deleted = db.withTransaction {
            dao.chat(id)?.let { chat ->
                deletedChat(chat, dao.messages(id)).also { dao.deleteChat(id) }
            }
        }
        if (deleted != null) removalSignals.emit(id)
        deleted
    }

    /** The Undo of [delete]: the chat and its messages return with their ids; a chat still present is left alone. */
    override suspend fun restore(d: DeletedChat) {
        require(d.chat.id > 0) { "Only a deleted saved chat can be restored" }
        val chat = d.chat.toEntity()
        db.withTransaction {
            if (dao.chat(chat.id) == null) {
                dao.insertChat(chat)
                dao.insertMessages(d.messages.map { it.toEntity().copy(chatId = chat.id) })
            }
        }
    }

    /**
     * Deletes every chat, checkpoints so no deleted text stays in the write-ahead log, then emits
     * null on [removals] even when nothing was saved, so chats kept only in memory go too.
     */
    override suspend fun deleteAll() {
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
