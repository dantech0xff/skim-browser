package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.dantech.skimbrowser.data.db.entity.HistoryEntryEntity
import kotlinx.coroutines.flow.Flow

// Both LIKE operands share one pattern built by containsPattern, which escapes with a backslash.
private const val MATCHING =
    "SELECT * FROM history WHERE title LIKE :pattern ESCAPE '\\' OR url LIKE :pattern ESCAPE '\\' " +
        "ORDER BY visitedAt DESC, id DESC LIMIT :limit"

@Dao
abstract class HistoryDao {

    @Query("SELECT * FROM history WHERE url = :url")
    abstract suspend fun byUrl(url: String): HistoryEntryEntity?

    @Query("SELECT * FROM history WHERE id = :id")
    abstract suspend fun byId(id: Long): HistoryEntryEntity?

    @Insert
    abstract suspend fun insert(entry: HistoryEntryEntity): Long

    @Query("UPDATE history SET title = :title, visitedAt = :visitedAt WHERE id = :id")
    abstract suspend fun updateVisit(id: Long, title: String, visitedAt: Long)

    @Query("UPDATE history SET title = :title WHERE url = :url")
    abstract suspend fun updateTitle(url: String, title: String)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC, id DESC LIMIT :limit")
    abstract fun observeLatest(limit: Int): Flow<List<HistoryEntryEntity>>

    @Query(MATCHING)
    abstract fun observeMatching(pattern: String, limit: Int): Flow<List<HistoryEntryEntity>>

    @Query(MATCHING)
    abstract suspend fun matching(pattern: String, limit: Int): List<HistoryEntryEntity>

    @Query("DELETE FROM history WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    abstract suspend fun deleteAll()

    /**
     * Writes [entry] as the one row of its URL. A new URL is inserted with [entry]'s id (0 lets the
     * database pick one, an Undo passes the old id). An existing URL keeps its row and id, its
     * visit time becomes the later of the two, and its title follows the later visit unless that
     * title is blank. Reading and writing in one transaction replaces SQL upsert syntax, which the
     * SQLite of Android 8 to 10 does not have.
     *
     * @return the id of the row now holding the URL.
     */
    @Transaction
    open suspend fun upsertKeepingLatest(entry: HistoryEntryEntity): Long {
        val existing = byUrl(entry.url) ?: return insert(entry)
        val incomingIsLatest = entry.visitedAt >= existing.visitedAt
        val title = if (incomingIsLatest && entry.title.isNotBlank()) entry.title else existing.title
        updateVisit(existing.id, title, maxOf(existing.visitedAt, entry.visitedAt))
        return existing.id
    }

    /** Deletes the entry [id] and returns it, or returns null when it does not exist. */
    @Transaction
    open suspend fun remove(id: Long): HistoryEntryEntity? {
        val entry = byId(id) ?: return null
        deleteById(id)
        return entry
    }
}
