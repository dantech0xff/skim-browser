package com.dantech.skimbrowser.domain.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Saved Skim chats. Screens read and undo them. The chat session store writes a live transcript
 * through the same interface, and deleting every chat goes through [deleteAll].
 */
interface ChatRepository {
    /** Every chat deletion, after it has committed: the id, or null when all chats were deleted. */
    val removals: SharedFlow<Long?>

    /** The newest [limit] page chats, for the Continue cards on Home. */
    fun observeRecentPageChats(limit: Int): Flow<List<SavedChat>>

    /** Skim history rows, newest first. A non-blank [query] keeps chats whose title or host contains it. */
    fun observeAll(query: String?): Flow<List<ChatSummary>>

    fun observeCount(): Flow<Int>

    suspend fun load(id: Long): SavedChat?

    /**
     * Saves a new chat and returns its id. The store always picks a fresh positive id, so a
     * memory-only id on [chat] is ignored.
     */
    suspend fun createChat(chat: SavedChat): Long

    /** The messages of [chatId] in the order they were written. */
    suspend fun messages(chatId: Long): List<SavedMessage>

    /** Saves a new message of a saved chat and returns its id. The store picks the id. */
    suspend fun addMessage(message: SavedMessage): Long

    suspend fun updateChat(chat: SavedChat)

    suspend fun updateMessage(message: SavedMessage)

    /**
     * Deletes every chat, then signals [removals] with null even when nothing was saved, so chats
     * kept only in memory go too.
     */
    suspend fun deleteAll()

    /** Deletes chat [id] with its messages and returns it for Undo, or null when there was no such chat. */
    suspend fun delete(id: Long): DeletedChat?

    /** The Undo of [delete]. A chat that is still present is left alone. */
    suspend fun restore(deleted: DeletedChat)
}
