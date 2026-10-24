package com.dantech.skimbrowser.data.repo

import androidx.room.withTransaction
import com.dantech.skimbrowser.data.db.SQL_IN_LIMIT
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.TabGroupingSnapshotEntity
import com.dantech.skimbrowser.domain.tab.GroupingSnapshot
import com.dantech.skimbrowser.domain.tab.PersistedTab
import com.dantech.skimbrowser.domain.tab.PersistedTabGroup
import com.dantech.skimbrowser.domain.tab.TabRepository as TabStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

typealias TabPlacement = com.dantech.skimbrowser.domain.tab.TabPlacement

/**
 * Normal tabs, their groups and the grouping snapshot. Incognito tabs never reach this class:
 * [TabEntity] cannot even express one.
 *
 * Closing is soft so it can be undone: [markClosed] hides tabs, [reopen] is the Undo and
 * [deleteClosed] makes the close final. A tab still closed when the process dies is dropped by the
 * next [loadOpen].
 */
@Singleton
class TabRepository @Inject constructor(private val db: SkimDatabase) : TabStore {

    private val dao = db.tabDao()

    /**
     * The open tabs, ordered by group then position, and every group, read in one transaction that
     * first drops tabs closed during an Undo window, chat ids whose chat no longer exists, and
     * groups left without tabs.
     */
    override suspend fun loadOpen(): Pair<List<PersistedTab>, List<PersistedTabGroup>> = db.withTransaction {
        dao.deleteAllClosed()
        dao.clearMissingChatIds()
        dao.deleteGroupsWithoutTabs()
        dao.openTabs().map { it.toDomain() } to dao.groups().map { it.toDomain() }
    }

    /**
     * Inserts or replaces [tab]. Only the positive id of a saved chat is stored (an in-memory chat
     * becomes null), and a group id that no longer exists stores the tab as ungrouped.
     */
    override suspend fun upsert(tab: PersistedTab) {
        require(tab.id.isNotBlank()) { "A tab needs an id" }
        db.withTransaction {
            val stored = tab.toEntity().copy(
                groupId = tab.groupId?.takeIf { dao.groupExists(it) },
                chatId = tab.chatId?.takeIf { it > 0 },
            )
            if (dao.update(stored) == 0) dao.insert(stored)
        }
    }

    override suspend fun markClosed(ids: List<String>, at: Long) = inChunks(ids) { dao.markClosed(it, at) }

    /** The Undo of [markClosed]: the tabs come back with their id, position and group. */
    override suspend fun reopen(ids: List<String>) = inChunks(ids) { dao.reopen(it) }

    /** Deletes the tabs among [ids] that are still closed; a reopened tab is left alone. */
    override suspend fun deleteClosed(ids: List<String>) = inChunks(ids) { dao.deleteClosed(it) }

    /**
     * Writes a whole layout in one transaction: [groups] become the complete set of groups (an id
     * of 0 creates the group, a listed id is updated or created with that id, any group not listed
     * is deleted), each placement moves its tab, and [snapshot], when given, replaces the grouping
     * snapshot. Either all of it is written or none of it.
     *
     * Placements only move tabs that are already stored; they never create a row. Tabs that are
     * not placed keep their position; any that sat in a deleted group become ungrouped.
     *
     * @return the id of each entry of [groups], in order, including the ids of created groups.
     * @throws IllegalArgumentException when a tab is placed twice, a placement points outside
     *   [groups], or a group id is negative or listed twice; nothing is written then.
     */
    suspend fun saveLayout(groups: List<PersistedTabGroup>, placements: List<TabPlacement>): List<Long> =
        saveLayout(groups, placements, snapshot = null)

    override suspend fun saveLayout(
        groups: List<PersistedTabGroup>,
        placements: List<TabPlacement>,
        snapshot: GroupingSnapshot?,
    ): List<Long> {
        require(placements.map { it.tabId }.toSet().size == placements.size) { "A tab is placed more than once" }
        require(placements.all { it.groupIndex == null || it.groupIndex in groups.indices }) {
            "A placement points at a group that is not in the layout"
        }
        val listedIds = groups.map { it.id }.filter { it != 0L }
        require(listedIds.all { it > 0 }) { "A group id is positive, or 0 for a new group" }
        require(listedIds.toSet().size == listedIds.size) { "A group is listed more than once" }

        val entities = groups.map { it.toEntity() }
        val storedSnapshot = snapshot?.toEntity()
        return db.withTransaction {
            val ids = entities.map { group ->
                when {
                    group.id == 0L -> dao.insertGroup(group)
                    dao.updateGroup(group) == 0 -> dao.insertGroup(group)
                    else -> group.id
                }
            }
            val kept = ids.toHashSet()
            dao.groupIds().filterNot { it in kept }.forEach { dao.deleteGroup(it) }
            placements.forEach { dao.place(it.tabId, it.groupIndex?.let(ids::get), it.position) }
            storedSnapshot?.let { dao.putSnapshot(it.copy(id = TabGroupingSnapshotEntity.SINGLE_ROW_ID)) }
            ids
        }
    }

    override suspend fun snapshot(): GroupingSnapshot? = dao.snapshot()?.toDomain()

    override suspend fun setSnapshotBanner(visible: Boolean) = dao.setSnapshotBanner(visible)

    /** Deletes the snapshot (it names tabs and groups) and checkpoints so no copy stays in the log. */
    override suspend fun clearSnapshot() {
        withContext(NonCancellable) {
            dao.clearSnapshot()
            db.checkpointTruncate()
        }
    }

    /** Runs [write] over [ids] in slices small enough for old SQLite, all in one transaction. */
    private suspend fun inChunks(ids: List<String>, write: suspend (List<String>) -> Unit) {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return
        db.withTransaction { distinct.chunked(SQL_IN_LIMIT).forEach { write(it) } }
    }
}
