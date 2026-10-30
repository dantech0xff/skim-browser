package com.dantech.skimbrowser.ui.browsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowsingChromeScrollTest {

    private val scroll = BrowsingChromeScroll(thresholdPx = 32)

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
    fun theTopOfThePageRestoresThePill() {
        assertEquals(true, scroll.onScrolled(scrollY = 80, dy = 40, collapsed = false))
        assertEquals(false, scroll.onScrolled(scrollY = 0, dy = -8, collapsed = true))
        assertNull(scroll.onScrolled(scrollY = 0, dy = -4, collapsed = false))
    }
}
