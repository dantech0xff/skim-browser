package com.dantech.skimbrowser.domain.shield

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBlocklistTest {

    @Test
    fun fnv64MatchesThePinnedVector() {
        assertEquals(-2554661609801288243L, HostBlocklist.fnv64("doubleclick.net"))
        assertEquals(-7890142531308090728L, HostBlocklist.fnv64("google-analytics.com"))
    }

    @Test
    fun aParentHostCoversItsSubdomainsAndASpecificAllowWins() {
        val list = HostBlocklist.of(
            blockAll = setOf("tracker.com"),
            allowAll = setOf("good.tracker.com"),
        )

        assertTrue(list.blocks("example.com", "tracker.com"))
        assertTrue(list.blocks("example.com", "ads.tracker.com"))
        assertFalse(list.blocks("example.com", "good.tracker.com"))
        assertFalse(list.blocks("example.com", "cdn.good.tracker.com"))
        assertFalse(list.blocks("example.com", "example.com"))
    }

    @Test
    fun thirdPartyRulesLeaveTheTrackersOwnSiteAlone() {
        val list = HostBlocklist.of(blockThird = setOf("google-analytics.com"))

        assertTrue(list.blocks("example.com", "google-analytics.com"))
        assertTrue(list.blocks("example.com", "ssl.google-analytics.com"))
        assertFalse(list.blocks("google-analytics.com", "google-analytics.com"))
        assertFalse(list.blocks("google-analytics.com", "ssl.google-analytics.com"))
        assertTrue(list.blocks(null, "google-analytics.com"))
    }

    @Test
    fun theAssetHeaderIsIgnoredAndKindsAreRead() {
        val text = """
            # License: GPL-3.0-or-later
            B ads.example
            T metrics.example
            A safe.ads.example
            X partner.metrics.example
        """.trimIndent()
        val list = HostBlocklist.load(StringReader(text).buffered())

        assertTrue(list.blocks("news.example", "ads.example"))
        assertFalse(list.blocks("news.example", "safe.ads.example"))
        assertTrue(list.blocks("news.example", "metrics.example"))
        assertFalse(list.blocks("news.example", "partner.metrics.example"))
        assertFalse(list.blocks("metrics.example", "metrics.example"))
    }

    @Test
    fun lookupsStayUnderAMicrosecondOnALargeList() {
        val hosts = (0 until 20_000).map { "h$it.tracker.example" }.toSet()
        val list = HostBlocklist.of(blockAll = hosts)
        val started = System.nanoTime()
        var blocked = 0
        repeat(100_000) { index ->
            if (list.blocks("example.com", "h${index % 20_000}.tracker.example")) blocked++
        }
        val elapsedNs = System.nanoTime() - started
        assertEquals(100_000, blocked)
        assertTrue("100k lookups took ${elapsedNs / 1_000_000} ms", elapsedNs < 1_000_000_000L)
    }
}
