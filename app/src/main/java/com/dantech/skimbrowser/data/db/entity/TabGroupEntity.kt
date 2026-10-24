package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A tab group in Tabs (09). [createdBySkim] marks a group made by Skim's grouping, not by the user. */
@Entity(tableName = "tab_groups")
data class TabGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val position: Int,
    val createdBySkim: Boolean = false,
)
