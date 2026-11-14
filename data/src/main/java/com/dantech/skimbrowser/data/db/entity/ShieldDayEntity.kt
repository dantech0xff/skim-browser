package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One local day of blocking counts. The epoch day is the device's date, not UTC. */
@Entity(tableName = "shield_days")
data class ShieldDayEntity(
    @PrimaryKey val epochDay: Long,
    val requests: Long,
    val popups: Long,
    val redirects: Long,
    val cosmetic: Long,
    val bytes: Long,
)
