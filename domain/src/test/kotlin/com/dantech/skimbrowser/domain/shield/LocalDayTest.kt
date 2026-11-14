package com.dantech.skimbrowser.domain.shield

import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDayTest {

    @Test
    fun aRunningCollectorMovesToTheNextLocalDay() = runTest {
        var now = 0L
        val days = mutableListOf<Long>()
        val job = launch { localEpochDays({ now }, { ZoneOffset.UTC }).collect { days += it } }
        runCurrent()
        assertEquals(listOf(0L), days.toList())
        now = 24L * 60 * 60 * 1000
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(0L, 1L), days.toList())
        job.cancel()
    }

    @Test
    fun aNewCollectorReadsTheClockAgain() = runTest {
        var now = 0L
        val first = mutableListOf<Long>()
        val job = launch { localEpochDays({ now }, { ZoneOffset.UTC }).collect { first += it } }
        runCurrent()
        job.cancel()
        now = 24L * 60 * 60 * 1000
        val second = mutableListOf<Long>()
        val again = launch { localEpochDays({ now }, { ZoneOffset.UTC }).collect { second += it } }
        runCurrent()
        assertEquals(listOf(1L), second.toList())
        assertTrue(first == listOf(0L))
        again.cancel()
    }

    @Test
    fun midnightIsTheNextLocalDate() {
        assertEquals(24L * 60 * 60 * 1000, millisUntilNextLocalMidnight(0L, ZoneOffset.UTC))
        assertEquals(1L, millisUntilNextLocalMidnight(24L * 60 * 60 * 1000 - 1, ZoneOffset.UTC))
    }
}
