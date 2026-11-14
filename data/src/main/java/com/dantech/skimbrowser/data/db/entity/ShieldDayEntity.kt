package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shield_days")
data class ShieldDayEntity(
    @PrimaryKey val localEpochDay: Long,
    val requests: Long,
    val popups: Long,
    val redirects: Long,
    val cosmetic: Long,
    val bytes: Long,
)
