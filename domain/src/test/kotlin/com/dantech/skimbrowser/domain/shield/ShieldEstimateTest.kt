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
        val fromBandwidth = bytes / ShieldEstimate.BANDWIDTH_BYTES_PER_SECOND * 1_000 +
            bytes % ShieldEstimate.BANDWIDTH_BYTES_PER_SECOND * 1_000 / ShieldEstimate.BANDWIDTH_BYTES_PER_SECOND
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
    fun cosmeticRuleCountFollowsTheHideMarker() {
        assertEquals(0, CosmeticFilters.ruleCount(""))
        assertEquals(2, CosmeticFilters.ruleCount(".a{display:none !important}\n.b{display:none !important}\n"))
        assertEquals(1, CosmeticFilters.ruleCount("iframe[src*=\"//ads.example/\"],img[src*=\"//ads.example/\"]{display:none !important}\n"))
    }
}
