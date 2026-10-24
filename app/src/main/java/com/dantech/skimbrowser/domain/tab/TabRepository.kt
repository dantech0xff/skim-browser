package com.dantech.skimbrowser.domain.tab

/** Normal tabs, their groups and the grouping snapshot. Incognito tabs never reach this store. */
interface TabRepository {
    /** The open tabs, ordered by group then position, and every group. */
    suspend fun loadOpen(): Pair<List<PersistedTab>, List<PersistedTabGroup>>

    /**
     * Inserts or replaces [tab]. Only a positive chat id is stored, and a group id that no longer
     * exists stores the tab as ungrouped.
     */
    suspend fun upsert(tab: PersistedTab)

    suspend fun markClosed(ids: List<String>, at: Long)

    suspend fun reopen(ids: List<String>)

    suspend fun deleteClosed(ids: List<String>)

    /**
     * Writes a whole layout in one transaction. Returns the id of each entry of [groups], in order.
     * Throws [IllegalArgumentException] when the layout is invalid, and writes nothing then.
     */
    suspend fun saveLayout(
        groups: List<PersistedTabGroup>,
        placements: List<TabPlacement>,
        snapshot: GroupingSnapshot? = null,
    ): List<Long>

    suspend fun snapshot(): GroupingSnapshot?

    suspend fun setSnapshotBanner(visible: Boolean)

    suspend fun clearSnapshot()
}
