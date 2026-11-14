package com.dantech.skimbrowser.ui.shield

import com.dantech.skimbrowser.domain.shield.ShieldSnapshot
import com.dantech.skimbrowser.domain.shield.ShieldStats
import com.dantech.skimbrowser.domain.shield.shieldSnapshot
import com.dantech.skimbrowser.testing.MainDispatcherRule
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        assertEquals(0L, model.state.value.snapshot.allTime.requests)
    }

    private class FakeStats : ShieldStats {
        var resets = 0
        private val snapshot = MutableStateFlow(shieldSnapshot(emptyList(), emptyList(), 0))

        override fun observe(todayEpochDay: Long): Flow<ShieldSnapshot> = snapshot

        override suspend fun reset() {
            resets++
        }
    }
}
