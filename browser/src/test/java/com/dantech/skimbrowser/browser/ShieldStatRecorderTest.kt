package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.shield.ShieldDelta
import com.dantech.skimbrowser.domain.shield.ShieldSnapshot
import com.dantech.skimbrowser.domain.shield.ShieldStatsStore
import com.dantech.skimbrowser.domain.shield.shieldSnapshot
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShieldStatRecorderTest {

    @Test
    fun aBurstBecomesOneWriteAfterTheDelay() = runTest {
        val store = RecordingStore()
        val recorder = recorder(store)
        repeat(5) { recorder.onRequestBlocked("https://ads.example/a.js", incognito = false) }
        recorder.onPopupBlocked(incognito = false)
        runCurrent()
        assertEquals(0, store.adds)
        advanceTimeBy(ShieldStatRecorder.FLUSH_DELAY_MS)
        runCurrent()
        assertEquals(1, store.adds)
        val day = store.last.single()
        assertEquals(5L, day.requests)
        assertEquals(1L, day.popups)
        assertEquals(5L * 16L * 1024, day.bytes)
        assertEquals(mapOf("ads.example" to 5L), day.hosts)
    }

    @Test
    fun aFullBufferWritesWithoutWaiting() = runTest {
        val store = RecordingStore()
        val recorder = recorder(store)
        repeat(32) { recorder.onRequestBlocked("https://cdn.example/pixel.gif", incognito = false) }
        runCurrent()
        assertEquals(1, store.adds)
        assertEquals(32L, store.last.single().requests)
    }

    @Test
    fun aPrivateTabCountsTheRequestAndDropsTheSite() = runTest {
        val store = RecordingStore()
        val recorder = recorder(store)
        recorder.onRequestBlocked("https://secret.example/a.js", incognito = true)
        recorder.onPopupBlocked(incognito = true)
        recorder.onRedirectBlocked(incognito = true)
        recorder.onCosmeticRules(4, incognito = true)
        advanceTimeBy(ShieldStatRecorder.FLUSH_DELAY_MS)
        runCurrent()
        val day = store.last.single()
        assertEquals(1L, day.requests)
        assertEquals(1L, day.popups)
        assertEquals(1L, day.redirects)
        assertEquals(4L, day.cosmetic)
        assertTrue(day.hosts.isEmpty())
    }

    @Test
    fun resetDropsAPendingBurstAndClearsTheStore() = runTest {
        val store = RecordingStore()
        val recorder = recorder(store)
        recorder.onRequestBlocked("https://ads.example/a.js", incognito = false)
        runCurrent()
        recorder.reset()
        advanceTimeBy(ShieldStatRecorder.FLUSH_DELAY_MS)
        runCurrent()
        assertEquals(0, store.adds)
        assertEquals(1, store.clears)
    }

    @Test
    fun anEventRecordedAfterResetSurvivesTheOldFlush() = runTest {
        val store = RecordingStore()
        lateinit var recorder: ShieldStatRecorder
        var slipped = false
        recorder = ShieldStatRecorder(
            store = store,
            scope = this,
            clock = { 0L },
            zone = { ZoneOffset.UTC },
            io = StandardTestDispatcher(testScheduler),
            flushDelayMs = ShieldStatRecorder.FLUSH_DELAY_MS,
            beforeDrain = {
                if (slipped) return@ShieldStatRecorder
                slipped = true
                recorder.reset()
                recorder.onRequestBlocked("https://later.example/b.js", incognito = false)
            },
        )
        recorder.onRequestBlocked("https://ads.example/a.js", incognito = false)
        runCurrent()
        advanceTimeBy(ShieldStatRecorder.FLUSH_DELAY_MS)
        runCurrent()
        advanceTimeBy(ShieldStatRecorder.FLUSH_DELAY_MS)
        runCurrent()
        assertEquals(1, store.adds)
        val day = store.last.single()
        assertEquals(1L, day.requests)
        assertEquals(mapOf("later.example" to 1L), day.hosts)
    }

    private fun kotlinx.coroutines.test.TestScope.recorder(store: RecordingStore) = ShieldStatRecorder(
        store = store,
        scope = this,
        clock = { 0L },
        zone = { ZoneOffset.UTC },
        io = StandardTestDispatcher(testScheduler),
        flushDelayMs = ShieldStatRecorder.FLUSH_DELAY_MS,
    )

    private class RecordingStore : ShieldStatsStore {
        var adds = 0
        var clears = 0
        var last: List<ShieldDelta> = emptyList()

        override fun observe(todayEpochDay: Long): Flow<ShieldSnapshot> =
            MutableStateFlow(shieldSnapshot(emptyList(), emptyList(), todayEpochDay))

        override suspend fun add(days: List<ShieldDelta>) {
            adds++
            last = days
        }

        override suspend fun clear() {
            clears++
        }
    }
}
