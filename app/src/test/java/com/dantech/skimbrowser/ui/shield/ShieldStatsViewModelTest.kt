package com.dantech.skimbrowser.ui.shield

import com.dantech.skimbrowser.domain.shield.ShieldDay
import com.dantech.skimbrowser.domain.shield.ShieldSnapshot
import com.dantech.skimbrowser.domain.shield.ShieldStats
import com.dantech.skimbrowser.domain.shield.shieldSnapshot
import com.dantech.skimbrowser.testing.MainDispatcherRule
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShieldStatsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun resetClearsOnTheAppScope() = runTest {
        val stats = FakeStats()
        val model = ShieldStatsViewModel(
            stats = stats,
            appScope = this,
            clock = { 0L },
            zone = { ZoneOffset.UTC },
        )
        model.reset()
        advanceUntilIdle()
        assertEquals(1, stats.resets)
        assertEquals(0L, model.state.value.allTime.requests)
    }

    @Test
    fun theOpenScreenMovesToTheNextLocalDay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        val stats = DayStats()
        val model = ShieldStatsViewModel(stats, this, { now }, { ZoneOffset.UTC })
        val job = launch { model.state.collect {} }
        runCurrent()
        assertEquals(10L, model.state.value.today.requests)
        now = DAY
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(11L, model.state.value.today.requests)
        job.cancel()
    }

    @Test
    fun aNewSubscriberReadsTheClockAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        val stats = DayStats()
        val model = ShieldStatsViewModel(stats, this, { now }, { ZoneOffset.UTC })
        val first = launch { model.state.collect {} }
        runCurrent()
        assertEquals(10L, model.state.value.today.requests)
        first.cancel()
        advanceTimeBy(6_000)
        now = DAY
        val second = launch { model.state.collect {} }
        runCurrent()
        assertEquals(11L, model.state.value.today.requests)
        second.cancel()
    }

    private class DayStats : ShieldStats {
        override fun observe(todayEpochDay: Long): Flow<ShieldSnapshot> = flowOf(
            shieldSnapshot(
                listOf(ShieldDay(epochDay = todayEpochDay, requests = todayEpochDay + 10)),
                emptyList(),
                todayEpochDay,
            ),
        )

        override suspend fun reset() = Unit
    }

    private class FakeStats : ShieldStats {
        var resets = 0
        private val snapshot = MutableStateFlow(shieldSnapshot(emptyList(), emptyList(), 0))

        override fun observe(todayEpochDay: Long): Flow<ShieldSnapshot> = snapshot

        override suspend fun reset() {
            resets++
        }
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
