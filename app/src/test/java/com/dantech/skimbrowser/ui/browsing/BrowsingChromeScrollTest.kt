package com.dantech.skimbrowser.ui.browsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowsingChromeScrollTest {

    private val scroll = BrowsingChromeScroll(thresholdPx = 32)

    private var now = 1_000L
    private val timed = BrowsingChromeScroll(thresholdPx = 32, uptimeMs = { now })

    @Test
    fun movingDownThePageShrinksThePillOnceTheMoveIsLongEnough() {
        assertNull(scroll.onScrolled(scrollY = 20, dy = 16, collapsed = false))
        assertEquals(true, scroll.onScrolled(scrollY = 40, dy = 20, collapsed = false))
    }

    @Test
    fun movingBackUpRestoresThePill() {
        assertEquals(true, scroll.onScrolled(scrollY = 80, dy = 40, collapsed = false))

        assertNull(scroll.onScrolled(scrollY = 64, dy = -16, collapsed = true))
        assertEquals(false, scroll.onScrolled(scrollY = 40, dy = -24, collapsed = true))
    }

    @Test
    fun aChangeOfDirectionStartsTheCountAgain() {
        assertNull(scroll.onScrolled(scrollY = 20, dy = 20, collapsed = false))
        assertNull(scroll.onScrolled(scrollY = 8, dy = -12, collapsed = false))
        assertNull(scroll.onScrolled(scrollY = 28, dy = 20, collapsed = false))
    }

    @Test
    fun aPillBroughtBackByATapStaysUntilThePageMovesDownThatFarAgain() {
        assertEquals(true, scroll.onScrolled(scrollY = 80, dy = 40, collapsed = false))
        assertNull(scroll.onScrolled(scrollY = 600, dy = 520, collapsed = true))

        // The chip was tapped, so the pill is full again without a move up.
        assertNull(scroll.onScrolled(scrollY = 601, dy = 1, collapsed = false))
        assertNull(scroll.onScrolled(scrollY = 621, dy = 20, collapsed = false))
        assertEquals(true, scroll.onScrolled(scrollY = 633, dy = 12, collapsed = false))
    }

    @Test
    fun aTapOnTheChipHoldsThePillThroughTheFlingStillUnderWay() {
        assertEquals(true, timed.onScrolled(scrollY = 80, dy = 40, collapsed = false))
        now += 16
        assertNull(timed.onScrolled(scrollY = 120, dy = 40, collapsed = true))

        timed.onExpandedByTap()
        // The fling goes on, a frame at a time, far past the threshold.
        repeat(10) { frame ->
            now += 16
            assertNull("frame $frame", timed.onScrolled(scrollY = 160 + frame * 40, dy = 40, collapsed = false))
        }

        // The page came to rest; the next move down is a new one and counts in full.
        now += 400
        assertNull(timed.onScrolled(scrollY = 580, dy = 20, collapsed = false))
        now += 16
        assertEquals(true, timed.onScrolled(scrollY = 592, dy = 12, collapsed = false))
    }

    @Test
    fun aMoveUpEndsTheHoldOfATap() {
        assertEquals(true, timed.onScrolled(scrollY = 80, dy = 40, collapsed = false))
        timed.onExpandedByTap()
        now += 16
        assertNull(timed.onScrolled(scrollY = 76, dy = -4, collapsed = false))

        now += 16
        assertEquals(true, timed.onScrolled(scrollY = 116, dy = 40, collapsed = false))
    }

    @Test
    fun aTapOnAPageAtRestHoldsNothing() {
        assertEquals(true, timed.onScrolled(scrollY = 80, dy = 40, collapsed = false))
        timed.onExpandedByTap()

        now += 2_000
        assertEquals(true, timed.onScrolled(scrollY = 120, dy = 40, collapsed = false))
    }

    @Test
    fun theTopOfThePageRestoresThePill() {
        assertEquals(true, scroll.onScrolled(scrollY = 80, dy = 40, collapsed = false))
        assertEquals(false, scroll.onScrolled(scrollY = 0, dy = -8, collapsed = true))
        assertNull(scroll.onScrolled(scrollY = 0, dy = -4, collapsed = false))
    }
}
