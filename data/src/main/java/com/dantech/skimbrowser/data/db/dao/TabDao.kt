package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dantech.skimbrowser.data.db.entity.TabEntity
import com.dantech.skimbrowser.data.db.entity.TabGroupEntity
import com.dantech.skimbrowser.data.db.entity.TabGroupingSnapshotEntity

/** Single statements on tabs and groups; `TabRepository` composes them into transactions. */
@Dao
interface TabDao {

    @Query("SELECT * FROM tabs WHERE closedAt IS NULL ORDER BY groupId, position, createdAt, id")
    suspend fun openTabs(): List<TabEntity>

    @Query("SELECT * FROM tab_groups ORDER BY position, id")
    suspend fun groups(): List<TabGroupEntity>

    @Query("SELECT id FROM tab_groups")
    suspend fun groupIds(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM tab_groups WHERE id = :id)")
    suspend fun groupExists(id: Long): Boolean

    /** Tabs closed while their Undo was offered; after process death they are gone for good. */
    @Query("DELETE FROM tabs WHERE closedAt IS NOT NULL")
    suspend fun deleteAllClosed()

    @Query("UPDATE tabs SET chatId = NULL WHERE chatId IS NOT NULL AND chatId NOT IN (SELECT id FROM skim_chats)")
    suspend fun clearMissingChatIds()

    // The subquery skips NULL group ids: a NULL inside NOT IN would make the condition match nothing.
    @Query("DELETE FROM tab_groups WHERE id NOT IN (SELECT groupId FROM tabs WHERE groupId IS NOT NULL)")
    suspend fun deleteGroupsWithoutTabs()

    @Insert
    suspend fun insert(tab: TabEntity)

    @Update
    suspend fun update(tab: TabEntity): Int

    @Query("UPDATE tabs SET closedAt = :at WHERE id IN (:ids)")
    suspend fun markClosed(ids: List<String>, at: Long)

    @Query("UPDATE tabs SET closedAt = NULL WHERE id IN (:ids)")
    suspend fun reopen(ids: List<String>)

    @Query("DELETE FROM tabs WHERE closedAt IS NOT NULL AND id IN (:ids)")
    suspend fun deleteClosed(ids: List<String>)

    /** Moves an existing tab; never creates a row. */
    @Query("UPDATE tabs SET groupId = :groupId, position = :position WHERE id = :id")
    suspend fun place(id: String, groupId: Long?, position: Int)

    @Insert
    suspend fun insertGroup(group: TabGroupEntity): Long

    @Update
    suspend fun updateGroup(group: TabGroupEntity): Int

    @Query("DELETE FROM tab_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("SELECT * FROM tab_grouping_snapshot LIMIT 1")
    suspend fun snapshot(): TabGroupingSnapshotEntity?

    /** The snapshot table has one fixed row id, so replacing it keeps a single row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSnapshot(snapshot: TabGroupingSnapshotEntity)

    @Query("UPDATE tab_grouping_snapshot SET bannerVisible = :visible")
    suspend fun setSnapshotBanner(visible: Boolean)

    @Query("DELETE FROM tab_grouping_snapshot")
    suspend fun clearSnapshot()
}
