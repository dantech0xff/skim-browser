package com.dantech.skimbrowser.ui.theme

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import com.dantech.skimbrowser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SkimTypographyTest {

    @Test
    fun everyGoogleSansFlexWeightSetsTheWghtAxis() {
        val fonts = (GoogleSansFlex as FontListFontFamily).fonts

        assertEquals(listOf(400, 500, 600), fonts.map { it.weight.weight })
        fonts.forEach { font ->
            val resourceFont = font as ResourceFont
            assertEquals(R.font.google_sans_flex, resourceFont.resId)
            // Without an explicit wght setting the variable font renders at its default
            // instance, so 500 and 600 would look exactly like 400.
            val wght = resourceFont.variationSettings.settings.singleOrNull { it.axisName == "wght" }
            assertNotNull("weight ${font.weight.weight} does not set the wght axis", wght)
            assertEquals(font.weight.weight.toFloat(), wght!!.toVariationValue(null), 0f)
        }
    }
}
