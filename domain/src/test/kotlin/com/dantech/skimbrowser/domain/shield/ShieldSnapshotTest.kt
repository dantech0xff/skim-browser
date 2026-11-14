package com.dantech.skimbrowser.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Test

class ShieldSnapshotTest {

    @Test
    fun todayWeekAndAllTimeUseTheLocalDay() {
        val days = listOf(
            day(epochDay = 10, requests = 2, bytes = 1_000),
            day(epochDay = 9, requests = 3, popups = 1),
            day(epochDay = 4, requests = 7, redirects = 2),
            day(epochDay = 3, requests = 11, cosmetic = 5),
        )
        val snapshot = shieldSnapshot(days, topHosts = emptyList(), todayEpochDay = 10)
        assertEquals(2L, snapshot.today.requests)
        assertEquals(1_000L, snapshot.today.bytes)
        assertEquals(ShieldEstimate.timeSavedMillis(1_000, 2), snapshot.today.timeSavedMillis)
        assertEquals(2L + 3L + 7L, snapshot.week.requests)
        assertEquals(1L, snapshot.week.popups)
        assertEquals(2L, snapshot.week.redirects)
        assertEquals(0L, snapshot.week.cosmetic)
        assertEquals(2L + 3L + 7L + 11L, snapshot.allTime.requests)
        assertEquals(5L, snapshot.allTime.cosmetic)
        assertEquals(listOf(7L, 0L, 0L, 0L, 0L, 3L, 2L), snapshot.chart)
    }

    @Test
    fun topHostsKeepFivePositiveCountsInTheGivenOrder() {
        val hosts = (1..6).map { BlockedHost("h$it.example", it.toLong()) } + BlockedHost("", 9) + BlockedHost("zero.example", 0)
        val snapshot = shieldSnapshot(emptyList(), hosts, todayEpochDay = 1)
        assertEquals(listOf("h1.example", "h2.example", "h3.example", "h4.example", "h5.example"), snapshot.topHosts.map { it.host })
    }

    private fun day(
        epochDay: Long,
        requests: Long = 0,
        popups: Long = 0,
        redirects: Long = 0,
        cosmetic: Long = 0,
        bytes: Long = 0,
    ) = ShieldDay(epochDay, requests, popups, redirects, cosmetic, bytes)
}
