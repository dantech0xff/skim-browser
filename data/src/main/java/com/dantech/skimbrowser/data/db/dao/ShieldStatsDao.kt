package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.dantech.skimbrowser.data.db.entity.ShieldDayEntity
import com.dantech.skimbrowser.data.db.entity.ShieldHostEntity
import kotlinx.coroutines.flow.Flow

data class ShieldHostTotal(val host: String, val count: Long)

@Dao
abstract class ShieldStatsDao {

    @Query("SELECT * FROM shield_days")
    abstract fun observeDays(): Flow<List<ShieldDayEntity>>

    @Query(
        "SELECT host, SUM(count) AS count FROM shield_hosts GROUP BY host " +
            "ORDER BY SUM(count) DESC, host ASC LIMIT :limit",
    )
    abstract fun observeTopHosts(limit: Int): Flow<List<ShieldHostTotal>>

    @Query("SELECT * FROM shield_days WHERE localEpochDay = :localEpochDay")
    abstract suspend fun day(localEpochDay: Long): ShieldDayEntity?

    @Query("SELECT count FROM shield_hosts WHERE localEpochDay = :localEpochDay AND host = :host")
    abstract suspend fun hostCount(localEpochDay: Long, host: String): Long?

    @Insert
    abstract suspend fun insertDay(day: ShieldDayEntity)

    @Insert
    abstract suspend fun insertHost(host: ShieldHostEntity)

    @Query(
        "UPDATE shield_days SET requests = :requests, popups = :popups, redirects = :redirects, " +
            "cosmetic = :cosmetic, bytes = :bytes WHERE localEpochDay = :localEpochDay",
    )
    abstract suspend fun updateDay(localEpochDay: Long, requests: Long, popups: Long, redirects: Long, cosmetic: Long, bytes: Long)

    @Query("UPDATE shield_hosts SET count = :count WHERE localEpochDay = :localEpochDay AND host = :host")
    abstract suspend fun updateHost(localEpochDay: Long, host: String, count: Long)

    @Query("DELETE FROM shield_days")
    abstract suspend fun deleteDays()

    @Query("DELETE FROM shield_hosts")
    abstract suspend fun deleteHosts()

    /**
     * Adds [days] and [hosts] onto the rows already stored. Android 8's SQLite has no upsert,
     * so each row is read and then inserted or updated.
     */
    @Transaction
    open suspend fun add(days: List<ShieldDayEntity>, hosts: List<ShieldHostEntity>) {
        for (day in days) {
            val existing = day(day.localEpochDay)
            if (existing == null) {
                insertDay(day)
            } else {
                updateDay(
                    day.localEpochDay,
                    existing.requests + day.requests,
                    existing.popups + day.popups,
                    existing.redirects + day.redirects,
                    existing.cosmetic + day.cosmetic,
                    existing.bytes + day.bytes,
                )
            }
        }
        for (host in hosts) {
            val existing = hostCount(host.localEpochDay, host.host)
            if (existing == null) insertHost(host) else updateHost(host.localEpochDay, host.host, existing + host.count)
        }
    }

    @Transaction
    open suspend fun clear() {
        deleteDays()
        deleteHosts()
    }
}
