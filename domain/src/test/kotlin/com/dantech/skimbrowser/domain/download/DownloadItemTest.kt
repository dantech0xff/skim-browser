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
