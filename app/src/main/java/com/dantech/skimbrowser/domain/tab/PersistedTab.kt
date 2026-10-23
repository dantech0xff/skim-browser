package com.dantech.skimbrowser.domain.tab

/**
 * A normal tab as it is stored. Incognito tabs are never persisted: this type has no incognito flag.
 * [chatId] holds only the positive id of a saved chat.
 */
data class PersistedTab(
    val id: String,
    val url: String?,
    val title: String,
    val groupId: Long? = null,
    val position: Int,
    val desktopSite: Boolean = false,
    val openedByExternalApp: Boolean = false,
    val closeOnBack: Boolean = false,
    val fromNewTabPage: Boolean = false,
    val chatId: Long? = null,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val closedAt: Long? = null,
)

/** A tab group. [createdBySkim] marks a group made by Skim's grouping, not by the person. */
data class PersistedTabGroup(
    val id: Long = 0,
    val title: String,
    val position: Int,
    val createdBySkim: Boolean = false,
)

/**
 * Where an open tab sits in a layout: inside `groups[groupIndex]` of that same call, or outside
 * every group when [groupIndex] is null, at [position].
 */
data class TabPlacement(val tabId: String, val groupIndex: Int?, val position: Int)

/**
 * The layout before Skim grouped the tabs, kept for the Undo on the grouping banner. At most one
 * snapshot is stored.
 */
data class GroupingSnapshot(
    val id: Int = SINGLE_ROW_ID,
    val previousLayoutJson: String,
    val tabSetKey: String,
    val appliedLayoutVersion: Long,
    val bannerVisible: Boolean,
    val createdAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
