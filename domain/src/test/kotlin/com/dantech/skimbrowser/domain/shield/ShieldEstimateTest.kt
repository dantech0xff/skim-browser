package com.dantech.skimbrowser.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Test

class ShieldEstimateTest {

    @Test
    fun eachResourceUsesItsDocumentedAverage() {
        assertEquals(16L * 1024, ShieldEstimate.bytes(BlockedResource.SCRIPT))
        assertEquals(32L * 1024, ShieldEstimate.bytes(BlockedResource.IMAGE))
        assertEquals(1024L, ShieldEstimate.bytes(BlockedResource.XHR))
        assertEquals(128L * 1024, ShieldEstimate.bytes(BlockedResource.MEDIA))
        assertEquals(4L * 1024, ShieldEstimate.bytes(BlockedResource.OTHER))
    }

    @Test
    fun thePathSuffixChoosesTheResource() {
        assertEquals(BlockedResource.SCRIPT, ShieldEstimate.resourceOf("https://ads.example/app.JS?v=1"))
        assertEquals(BlockedResource.IMAGE, ShieldEstimate.resourceOf("https://ads.example/pixel.png"))
        assertEquals(BlockedResource.XHR, ShieldEstimate.resourceOf("https://ads.example/collect.json"))
        assertEquals(BlockedResource.MEDIA, ShieldEstimate.resourceOf("https://ads.example/spot.mp4#t=1"))
        assertEquals(BlockedResource.OTHER, ShieldEstimate.resourceOf("https://ads.example/beacon"))
        assertEquals(BlockedResource.OTHER, ShieldEstimate.resourceOf("https://ads.example/file.css"))
        assertEquals(BlockedResource.OTHER, ShieldEstimate.resourceOf("not a url"))
    }

    @Test
    fun timeSavedIsBandwidthPlusOneRoundTripPerRequest() {
        val bytes = 16_384_000L
        val requests = 1_000L
        val fromBandwidth = bytes / ShieldEstimate.ONE_MEGABIT_BYTES_PER_SECOND * 1_000 +
            bytes % ShieldEstimate.ONE_MEGABIT_BYTES_PER_SECOND * 1_000 / ShieldEstimate.ONE_MEGABIT_BYTES_PER_SECOND
        val expected = fromBandwidth + requests * ShieldEstimate.ROUND_TRIP_MILLIS
        assertEquals(181_072L, expected)
        assertEquals(expected, ShieldEstimate.timeSavedMillis(bytes, requests))
    }

    @Test
    fun timeSavedIgnoresNegativesAndSaturates() {
        assertEquals(0L, ShieldEstimate.timeSavedMillis(bytes = -1, requests = -5))
        assertEquals(Long.MAX_VALUE, ShieldEstimate.timeSavedMillis(Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun savedAmountsUseOneUnit() {
        assertEquals(ScaledBytes(ByteUnit.BYTE, 12, 0), scaleBytes(12))
        assertEquals(ScaledBytes(ByteUnit.KILOBYTE, 16, 0), scaleBytes(16 * 1024))
        assertEquals(ScaledBytes(ByteUnit.MEGABYTE, 1, 5), scaleBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals(ScaledBytes(ByteUnit.GIGABYTE, 2, 0), scaleBytes(2L * 1024 * 1024 * 1024))
        assertEquals(ScaledDuration(DurationUnit.NONE, 0), scaleDuration(999))
        assertEquals(ScaledDuration(DurationUnit.SECOND, 2), scaleDuration(2_100))
        assertEquals(ScaledDuration(DurationUnit.MINUTE, 3), scaleDuration(3 * 60_000L))
        assertEquals(ScaledDuration(DurationUnit.HOUR, 2), scaleDuration(2 * 3_600_000L))
    }

    @Test
    fun cosmeticRuleCountFollowsTheHideMarker() {
        assertEquals(0, CosmeticFilters.displayNoneCount(""))
        assertEquals(2, CosmeticFilters.displayNoneCount(".a{display:none !important}\n.b{display:none !important}\n"))
        assertEquals(1, CosmeticFilters.displayNoneCount("iframe[src*=\"//ads.example/\"],img[src*=\"//ads.example/\"]{display:none !important}\n"))
    }
}
