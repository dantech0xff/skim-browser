package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.shield.ShieldEstimate
import com.dantech.skimbrowser.domain.shield.ShieldSnapshot
import com.dantech.skimbrowser.domain.shield.ShieldStatBuffer
import com.dantech.skimbrowser.domain.shield.ShieldStatSink
import com.dantech.skimbrowser.domain.shield.ShieldStats
import com.dantech.skimbrowser.domain.shield.ShieldStatsStore
import com.dantech.skimbrowser.domain.url.hostOf
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts blocking events in memory and writes them in one batch.
 *
 * A blocked request, popup, redirect, or cosmetic rule updates [ShieldStatBuffer] and returns.
 * The disk write waits [FLUSH_DELAY_MS], or runs on the next loop once the buffer holds
 * [ShieldStatBuffer.FLUSH_AT] events. One coroutine owns that write.
 *
 * Private tabs pass incognito. Their requests, popups, redirects, and cosmetic rules still count.
 * Their sites are not stored. The compiled host list keeps no record of which source list a host
 * came from, so ads and trackers are one request count.
 *
 * [reset] drops the buffer before it clears the store, and a flush that already read the buffer
 * does not write those counts after the reset.
 */
@Singleton
class ShieldStatRecorder internal constructor(
    private val store: ShieldStatsStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val io: CoroutineDispatcher,
    private val flushDelayMs: Long,
) : ShieldStatSink, ShieldStats {

    @Inject constructor(
        store: ShieldStatsStore,
        @AppScope scope: CoroutineScope,
    ) : this(
        store = store,
        scope = scope,
        clock = System::currentTimeMillis,
        zone = ZoneId::systemDefault,
        io = Dispatchers.IO,
        flushDelayMs = FLUSH_DELAY_MS,
    )

    private val buffer = ShieldStatBuffer()
    private val generation = AtomicLong()
    private val scheduled = AtomicBoolean()
    private val gate = Mutex()

    override fun onRequestBlocked(url: String, incognito: Boolean) {
        val host = if (incognito) null else hostOf(url)
        buffer.noteRequest(day(), ShieldEstimate.bytes(ShieldEstimate.resourceOf(url)), host)
        schedule()
    }

    override fun onPopupBlocked(incognito: Boolean) {
        buffer.notePopup(day())
        schedule()
    }

    override fun onRedirectBlocked(incognito: Boolean) {
        buffer.noteRedirect(day())
        schedule()
    }

    override fun onCosmeticRules(rules: Int, incognito: Boolean) {
        buffer.noteCosmetic(day(), rules)
        schedule()
    }

    override fun observe(todayEpochDay: Long): Flow<ShieldSnapshot> = store.observe(todayEpochDay)

    override suspend fun reset() {
        generation.incrementAndGet()
        buffer.clear()
        gate.withLock { store.clear() }
    }

    private fun day(): Long = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().toEpochDay()

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        scope.launch(io) {
            val token = generation.get()
            try {
                val wait = if (buffer.pending() >= ShieldStatBuffer.FLUSH_AT) 0L else flushDelayMs
                if (wait > 0) delay(wait)
                if (token != generation.get()) return@launch
                val batch = buffer.drain()
                if (batch.isEmpty() || token != generation.get()) return@launch
                gate.withLock {
                    if (token != generation.get()) return@withLock
                    store.add(batch)
                }
            } finally {
                scheduled.set(false)
                if (buffer.pending() > 0) schedule()
            }
        }
    }

    companion object {
        const val FLUSH_DELAY_MS = 2_000L
    }
}
