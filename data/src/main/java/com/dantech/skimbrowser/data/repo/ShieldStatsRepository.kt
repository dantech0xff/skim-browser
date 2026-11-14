package com.dantech.skimbrowser.data.repo

import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.dao.ShieldStatsDao
import com.dantech.skimbrowser.data.db.entity.ShieldDayEntity
import com.dantech.skimbrowser.data.db.entity.ShieldHostEntity
import com.dantech.skimbrowser.domain.shield.BlockedHost
import com.dantech.skimbrowser.domain.shield.ShieldDay
import com.dantech.skimbrowser.domain.shield.ShieldDelta
import com.dantech.skimbrowser.domain.shield.ShieldSnapshot
import com.dantech.skimbrowser.domain.shield.ShieldStatsStore
import com.dantech.skimbrowser.domain.shield.TOP_HOSTS
import com.dantech.skimbrowser.domain.shield.shieldSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Daily blocking totals in `skim.db`. Nothing here leaves the device. */
@Singleton
class ShieldStatsRepository(private val dao: ShieldStatsDao) : ShieldStatsStore {

    @Inject constructor(database: SkimDatabase) : this(database.shieldStatsDao())

    override fun observe(todayEpochDay: Long): Flow<ShieldSnapshot> = combine(
        dao.observeDays(),
        dao.observeTopHosts(TOP_HOSTS),
    ) { days, hosts ->
        shieldSnapshot(
            days.map { it.toDay() },
            hosts.map { BlockedHost(it.host, it.count) },
            todayEpochDay,
        )
    }

    override suspend fun add(days: List<ShieldDelta>) {
        if (days.isEmpty()) return
        dao.add(
            days.map { it.toEntity() },
            days.flatMap { day ->
                day.hosts.map { (host, count) -> ShieldHostEntity(day.epochDay, host, count) }
            },
        )
    }

    override suspend fun clear() {
        dao.clear()
    }
}

private fun ShieldDayEntity.toDay() = ShieldDay(epochDay, requests, popups, redirects, cosmetic, bytes)

private fun ShieldDelta.toEntity() = ShieldDayEntity(epochDay, requests, popups, redirects, cosmetic, bytes)
