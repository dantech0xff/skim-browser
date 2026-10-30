package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.dantech.skimbrowser.data.db.entity.ShortcutEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShortcutDao {

    @Query("SELECT * FROM shortcuts ORDER BY position, id")
    abstract fun observe(): Flow<List<ShortcutEntity>>

    @Query("SELECT * FROM shortcuts WHERE id = :id")
    abstract suspend fun byId(id: Long): ShortcutEntity?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM shortcuts")
    abstract suspend fun nextPosition(): Int

    @Query("SELECT COUNT(*) FROM shortcuts")
    abstract suspend fun count(): Int

    @Insert
    abstract suspend fun insert(shortcut: ShortcutEntity): Long

    @Query("UPDATE shortcuts SET name = :name, url = :url WHERE id = :id")
    abstract suspend fun update(id: Long, name: String, url: String)

    @Query("DELETE FROM shortcuts WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    /** Appends a shortcut after the last one and returns its id. */
    @Transaction
    open suspend fun add(name: String, url: String): Long =
        insert(ShortcutEntity(name = name, url = url, position = nextPosition()))

    /**
     * Writes [presets] at positions 0, 1, … when the table is empty. Returns false when a
     * shortcut is already stored, and does not move it.
     */
    @Transaction
    open suspend fun insertPresetsIfEmpty(presets: List<ShortcutEntity>): Boolean {
        if (count() > 0) return false
        presets.forEachIndexed { index, preset ->
            insert(preset.copy(id = 0, position = index))
        }
        return true
    }

    /** Deletes shortcut [id] and returns it, or returns null when it does not exist. */
    @Transaction
    open suspend fun remove(id: Long): ShortcutEntity? {
        val shortcut = byId(id) ?: return null
        deleteById(id)
        return shortcut
    }

    /** Puts a removed [shortcut] back with its id and position; nothing changes if it is there. */
    @Transaction
    open suspend fun restore(shortcut: ShortcutEntity) {
        if (byId(shortcut.id) == null) insert(shortcut)
    }
}
