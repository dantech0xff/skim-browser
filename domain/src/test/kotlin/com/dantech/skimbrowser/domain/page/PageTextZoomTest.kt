package com.dantech.skimbrowser.domain.page

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTextZoomTest {

    @Test
    fun aNormalFontScaleIsOneHundredPercent() {
        assertEquals(100, PageTextZoom.ofFontScale(1f))
    }

    @Test
    fun aLargerFontScaleGrowsThePageAndStaysInsideTheRange() {
        assertEquals(130, PageTextZoom.ofFontScale(1.3f))
        assertEquals(PageTextZoom.MAX, PageTextZoom.ofFontScale(3f))
        assertEquals(PageTextZoom.MIN, PageTextZoom.ofFontScale(0.1f))
    }

    @Test
    fun aBrokenScaleStaysAtTheNormalSize() {
        assertEquals(PageTextZoom.NORMAL, PageTextZoom.ofFontScale(Float.NaN))
        assertEquals(PageTextZoom.NORMAL, PageTextZoom.ofFontScale(Float.POSITIVE_INFINITY))
        assertEquals(PageTextZoom.NORMAL, PageTextZoom.ofFontScale(0f))
        assertEquals(PageTextZoom.NORMAL, PageTextZoom.ofFontScale(-1f))
    }
}
