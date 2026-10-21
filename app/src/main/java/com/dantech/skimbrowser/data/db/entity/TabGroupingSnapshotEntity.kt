package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The layout before Skim grouped the tabs, kept for the Undo on the grouping banner. The table
 * holds at most one row, always with [id] = [SINGLE_ROW_ID].
 */
@Entity(tableName = "tab_grouping_snapshot")
data class TabGroupingSnapshotEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val previousLayoutJson: String,
    /** Identifies the set of tabs that was grouped, so the same set is not grouped again. */
    val tabSetKey: String,
    /** The layout version the grouping produced; Undo is only valid while it is still current. */
    val appliedLayoutVersion: Long,
    val bannerVisible: Boolean,
    val createdAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
