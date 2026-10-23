package com.dantech.skimbrowser.domain.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Saved Skim chats as the screens read and undo them. Writing a live transcript stays on the data
 * implementation, which the chat session store calls directly.
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

    /** Deletes chat [id] with its messages and returns it for Undo, or null when there was no such chat. */
    suspend fun delete(id: Long): DeletedChat?

    /** The Undo of [delete]. A chat that is still present is left alone. */
    suspend fun restore(deleted: DeletedChat)
}
