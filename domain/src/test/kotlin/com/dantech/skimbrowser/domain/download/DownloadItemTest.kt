package com.dantech.skimbrowser.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadItemTest {

    @Test
    fun aRunningDownloadWithAKnownSizeHasAFraction() {
        val item = sample(phase = DownloadPhase.Running, soFar = 25, total = 100)

        assertEquals(0.25f, item.fraction())
    }

    @Test
    fun aRunningDownloadShowsTheBytesThatHaveArrived() {
        assertEquals(24_000L, sample(phase = DownloadPhase.Running, soFar = 24_000, total = 100_000).runningSize())
        assertEquals(100_000L, sample(phase = DownloadPhase.Done, soFar = 24_000, total = 100_000).runningSize())
        assertEquals(-1L, sample(phase = DownloadPhase.Running, soFar = 0, total = -1).runningSize())
    }

    @Test
    fun anUnknownSizeOrAFinishedDownloadHasNoFraction() {
        assertNull(sample(phase = DownloadPhase.Running, soFar = 10, total = -1).fraction())
        assertNull(sample(phase = DownloadPhase.Done, soFar = 100, total = 100).fraction())
        assertNull(sample(phase = DownloadPhase.Failed, soFar = 0, total = 100).fraction())
    }

    private fun sample(phase: DownloadPhase, soFar: Long, total: Long) = DownloadItem(
        id = 1,
        fileName = "guide.pdf",
        phase = phase,
        bytesSoFar = soFar,
        bytesTotal = total,
        uri = null,
        mime = null,
        incognito = false,
        retry = null,
        systemId = null,
    )
}
