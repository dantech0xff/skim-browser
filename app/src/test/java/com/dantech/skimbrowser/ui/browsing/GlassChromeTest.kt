package com.dantech.skimbrowser.ui.browsing

import org.junit.Assert.assertEquals
import org.junit.Test

/** The browsing pill stays readable when the page behind it is bright, dark, or busy. */
class GlassChromeTest {

    @Test
    fun theFrostedTintStaysStrongAndHighContrastMakesItNearlySolid() {
        assertEquals(0.84f, glassChromeAlpha(dark = false, highContrast = false))
        assertEquals(0.80f, glassChromeAlpha(dark = true, highContrast = false))
        assertEquals(0.96f, glassChromeAlpha(dark = false, highContrast = true))
        assertEquals(0.94f, glassChromeAlpha(dark = true, highContrast = true))
    }
}
