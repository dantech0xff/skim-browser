package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity

@Entity(tableName = "shield_hosts", primaryKeys = ["localEpochDay", "host"])
data class ShieldHostEntity(
    val localEpochDay: Long,
    val host: String,
    val count: Long,
)
