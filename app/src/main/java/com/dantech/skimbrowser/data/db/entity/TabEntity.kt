package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A normal (not incognito) tab. Incognito tabs live only in memory: this table has no incognito
 * flag on purpose, so nothing can store one.
 *
 * [chatId] has no foreign key and only ever holds the positive id of a chat saved in
 * `skim_chats`; an in-memory chat is stored as null. [closedAt] marks a tab closed while its Undo
 * snackbar is still offered: such a row is never restored after process death.
 */
@Entity(
    tableName = "tabs",
    foreignKeys = [
        ForeignKey(
            entity = TabGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("groupId")],
)
data class TabEntity(
    @PrimaryKey val id: String,
    /** Null while the tab shows the New tab page. */
    val url: String?,
    val title: String,
    /** Null for a tab outside every group ("Other"). */
    val groupId: Long? = null,
    val position: Int,
    val desktopSite: Boolean = false,
    /** Opened by another app's link: Back on the first page closes the tab. */
    val openedByExternalApp: Boolean = false,
    val closeOnBack: Boolean = false,
    /** Started from the New tab page: Back on the first page returns to it. */
    val fromNewTabPage: Boolean = false,
    val chatId: Long? = null,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val closedAt: Long? = null,
)
