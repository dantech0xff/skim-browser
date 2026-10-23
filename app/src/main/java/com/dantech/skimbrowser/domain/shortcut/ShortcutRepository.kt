package com.dantech.skimbrowser.domain.shortcut

import kotlinx.coroutines.flow.Flow

/** Home shortcuts, in tile order. */
interface ShortcutRepository {
    fun observe(): Flow<List<Shortcut>>

    suspend fun add(name: String, url: String)

    suspend fun update(id: Long, name: String, url: String)

    /** Deletes shortcut [id] and returns it for Undo. */
    suspend fun remove(id: Long): Shortcut?

    /** The Undo of [remove]: the shortcut returns with its id and position. */
    suspend fun restore(shortcut: Shortcut)
}
