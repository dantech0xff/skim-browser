package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity

/**
 * Blocked requests for one site on one day. The primary key is the day and the site.
 * Private tabs do not write a row.
 */
@Entity(tableName = "shield_hosts", primaryKeys = ["epochDay", "host"])
data class ShieldHostEntity(
    val epochDay: Long,
    val host: String,
    val count: Long,
)
