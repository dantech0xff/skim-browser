package com.dantech.skimbrowser.data.repo

import com.dantech.skimbrowser.data.db.dao.ShortcutDao
import com.dantech.skimbrowser.data.db.entity.ShortcutEntity
import kotlinx.coroutines.flow.Flow

/** Home shortcuts (04), in tile order. */
class ShortcutRepository(private val dao: ShortcutDao) {

    fun observe(): Flow<List<ShortcutEntity>> = dao.observe()

    /** Adds a shortcut after the last one. */
    suspend fun add(name: String, url: String) {
        require(url.isNotBlank()) { "A shortcut needs a URL" }
        dao.add(name.trim(), url.trim())
    }

    suspend fun update(id: Long, name: String, url: String) {
        require(url.isNotBlank()) { "A shortcut needs a URL" }
        dao.update(id, name.trim(), url.trim())
    }

    /** Deletes shortcut [id] and returns it for Undo. */
    suspend fun remove(id: Long): ShortcutEntity? = dao.remove(id)

    /** The Undo of [remove]: the shortcut returns with its id and position. */
    suspend fun restore(s: ShortcutEntity) = dao.restore(s)
}
