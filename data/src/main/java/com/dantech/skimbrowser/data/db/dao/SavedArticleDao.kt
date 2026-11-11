package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dantech.skimbrowser.data.db.entity.SavedArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedArticleDao {
    @Query("SELECT * FROM saved_articles ORDER BY savedAt DESC, id DESC")
    fun observe(): Flow<List<SavedArticleEntity>>

    @Query("SELECT * FROM saved_articles WHERE id = :id")
    suspend fun get(id: Long): SavedArticleEntity?

    @Query("SELECT id FROM saved_articles WHERE url = :url")
    suspend fun idOf(url: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SavedArticleEntity): Long

    @Query("UPDATE saved_articles SET summary = :summary WHERE id = :id")
    suspend fun setSummary(id: Long, summary: String)

    @Query("DELETE FROM saved_articles WHERE id = :id")
    suspend fun delete(id: Long)
}
