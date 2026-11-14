package com.dantech.skimbrowser.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldStatBufferTest {

    private val buffer = ShieldStatBuffer()

    @Test
    fun oneDrainMergesABurstAndTheNextDrainIsEmpty() {
        buffer.noteRequest(localEpochDay = 4, bytes = 100, host = "ads.example")
        buffer.noteRequest(localEpochDay = 4, bytes = 50, host = "ads.example")
        buffer.noteRequest(localEpochDay = 4, bytes = 10, host = null)
        buffer.notePopup(4)
        buffer.noteRedirect(4)
        buffer.noteCosmetic(4, rules = 3)
        buffer.noteCosmetic(4, rules = 0)
        assertEquals(6, buffer.pending())

        val drained = buffer.drain().single()
        assertEquals(4L, drained.localEpochDay)
        assertEquals(3L, drained.requests)
        assertEquals(160L, drained.bytes)
        assertEquals(1L, drained.popups)
        assertEquals(1L, drained.redirects)
        assertEquals(3L, drained.cosmetic)
        assertEquals(mapOf("ads.example" to 2L), drained.hosts)
        assertTrue(buffer.drain().isEmpty())
        assertEquals(0, buffer.pending())
    }

    @Test
    fun aNullHostIsACountWithoutASite() {
        buffer.noteRequest(localEpochDay = 1, bytes = 4, host = null)
        buffer.noteRequest(localEpochDay = 1, bytes = 4, host = "")
        assertTrue(buffer.drain().single().hosts.isEmpty())
    }

    @Test
    fun daysStaySeparate() {
        buffer.noteRequest(localEpochDay = 1, bytes = 1, host = "a.example")
        buffer.notePopup(2)
        val days = buffer.drain().associateBy { it.localEpochDay }
        assertEquals(1L, days.getValue(1).requests)
        assertEquals(0L, days.getValue(1).popups)
        assertEquals(1L, days.getValue(2).popups)
        assertEquals(0L, days.getValue(2).requests)
    }

    @Test
    fun hostsBeyondTheCapStillCountAsRequests() {
        repeat(ShieldStatBuffer.MAX_HOSTS + 5) { index ->
            buffer.noteRequest(localEpochDay = 1, bytes = 1, host = "h$index.example")
        }
        val drained = buffer.drain().single()
        assertEquals((ShieldStatBuffer.MAX_HOSTS + 5).toLong(), drained.requests)
        assertEquals(ShieldStatBuffer.MAX_HOSTS, drained.hosts.size)
    }

    @Test
    fun clearDropsAPendingBurst() {
        buffer.noteRequest(localEpochDay = 1, bytes = 1, host = "ads.example")
        buffer.clear()
        assertTrue(buffer.drain().isEmpty())
        assertEquals(0, buffer.pending())
    }
}
