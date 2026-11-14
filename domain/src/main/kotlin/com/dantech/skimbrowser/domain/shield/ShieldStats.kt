package com.dantech.skimbrowser.domain.shield

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * One local day of blocking counts. [bytes] counts blocked requests only, using [ShieldEstimate].
 * Cosmetic rules and popups do not add bytes.
 */
data class ShieldDay(
    val epochDay: Long,
    val requests: Long = 0,
    val popups: Long = 0,
    val redirects: Long = 0,
    val cosmetic: Long = 0,
    val bytes: Long = 0,
)

/** A site whose requests were blocked. Private tabs never produce one of these. */
data class BlockedHost(val host: String, val count: Long)

/** Counts for one span. [timeSavedMillis] is [ShieldEstimate.timeSavedMillis] of [bytes] and [requests]. */
data class ShieldTotals(
    val requests: Long = 0,
    val popups: Long = 0,
    val redirects: Long = 0,
    val cosmetic: Long = 0,
    val bytes: Long = 0,
) {
    val timeSavedMillis: Long get() = ShieldEstimate.timeSavedMillis(bytes, requests)
}

/**
 * What the stats screen shows. [chart] is blocked requests for the seven days ending on the day
 * the snapshot was built, oldest first. [topHosts] is at most five sites, most blocked first.
 */
data class ShieldSnapshot(
    val today: ShieldTotals = ShieldTotals(),
    val week: ShieldTotals = ShieldTotals(),
    val allTime: ShieldTotals = ShieldTotals(),
    val chart: List<Long> = List(CHART_DAYS) { 0L },
    val topHosts: List<BlockedHost> = emptyList(),
)

/** One flush of the in-memory counter. [hosts] omits private-tab requests. */
data class ShieldDelta(
    val epochDay: Long,
    val requests: Long = 0,
    val popups: Long = 0,
    val redirects: Long = 0,
    val cosmetic: Long = 0,
    val bytes: Long = 0,
    val hosts: Map<String, Long> = emptyMap(),
)

const val CHART_DAYS = 7
const val TOP_HOSTS = 5

/**
 * Folds stored days into today, the last seven days, and all time.
 * [todayEpochDay] is the device's local date as an epoch day. A day outside that week still
 * counts in [ShieldSnapshot.allTime]. [topHosts] is already ordered. This keeps the first
 * [TOP_HOSTS] with a positive count.
 */
fun shieldSnapshot(days: List<ShieldDay>, topHosts: List<BlockedHost>, todayEpochDay: Long): ShieldSnapshot {
    val today = totals(days) { it.epochDay == todayEpochDay }
    val weekStart = todayEpochDay - (CHART_DAYS - 1)
    val week = totals(days) { it.epochDay in weekStart..todayEpochDay }
    val allTime = totals(days) { true }
    val chart = LongArray(CHART_DAYS)
    for (day in days) {
        val index = (day.epochDay - weekStart).toInt()
        if (index in chart.indices) chart[index] = saturatingAdd(chart[index], day.requests.coerceAtLeast(0))
    }
    return ShieldSnapshot(
        today = today,
        week = week,
        allTime = allTime,
        chart = chart.toList(),
        topHosts = topHosts.filter { it.host.isNotEmpty() && it.count > 0 }.take(TOP_HOSTS),
    )
}

private fun totals(days: List<ShieldDay>, include: (ShieldDay) -> Boolean): ShieldTotals {
    var requests = 0L
    var popups = 0L
    var redirects = 0L
    var cosmetic = 0L
    var bytes = 0L
    for (day in days) {
        if (!include(day)) continue
        requests = saturatingAdd(requests, day.requests)
        popups = saturatingAdd(popups, day.popups)
        redirects = saturatingAdd(redirects, day.redirects)
        cosmetic = saturatingAdd(cosmetic, day.cosmetic)
        bytes = saturatingAdd(bytes, day.bytes)
    }
    return ShieldTotals(requests, popups, redirects, cosmetic, bytes)
}

/** The on-disk daily totals. The recorder is the only caller of [add]. */
interface ShieldStatsStore {
    fun observe(todayEpochDay: Long): Flow<ShieldSnapshot>
    suspend fun add(days: List<ShieldDelta>)
    suspend fun clear()
}

/**
 * What the page-load path calls. Each method returns without waiting on disk.
 * Private tabs pass [incognito] true, and the recorder then keeps the count and drops the site.
 */
interface ShieldStatSink {
    fun onRequestBlocked(url: String, incognito: Boolean) {}
    fun onPopupBlocked(incognito: Boolean) {}
    fun onRedirectBlocked(incognito: Boolean) {}
    fun onCosmeticRules(rules: Int, incognito: Boolean) {}

    companion object {
        val Ignore: ShieldStatSink = object : ShieldStatSink {}
    }
}

/** The stats screen and the New tab counter. [reset] drops pending counts as well as stored ones. */
interface ShieldStats {
    fun observe(todayEpochDay: Long): Flow<ShieldSnapshot>
    suspend fun reset()

    companion object {
        val Empty: ShieldStats = object : ShieldStats {
            override fun observe(todayEpochDay: Long): Flow<ShieldSnapshot> =
                flowOf(shieldSnapshot(emptyList(), emptyList(), todayEpochDay))

            override suspend fun reset() = Unit
        }
    }
}
