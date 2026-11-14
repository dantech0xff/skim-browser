package com.dantech.skimbrowser.domain.shield

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * The local epoch day, again at each local midnight and at least once a minute.
 * A collector that stops and starts reads the clock again, so a process that stays up
 * overnight does not keep yesterday's day.
 */
fun localEpochDays(clock: () -> Long, zone: () -> ZoneId): Flow<Long> = flow {
    var previous: Long? = null
    while (currentCoroutineContext().isActive) {
        val day = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().toEpochDay()
        if (day != previous) {
            previous = day
            emit(day)
        }
        val wait = millisUntilNextLocalMidnight(clock(), zone()).coerceIn(1L, DAY_RECHECK_MILLIS)
        delay(wait)
    }
}

/** Milliseconds from [nowMillis] until the next local midnight in [zone]. At least 1. */
fun millisUntilNextLocalMidnight(nowMillis: Long, zone: ZoneId): Long {
    val zoned = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val next = zoned.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return (next - nowMillis).coerceAtLeast(1L)
}

private const val DAY_RECHECK_MILLIS = 60_000L
