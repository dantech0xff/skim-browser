package com.dantech.skimbrowser.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dantech.skimbrowser.R

// res/font/google_sans_flex.ttf: variable, OFL 1.1. The wght axis needs API 26+.
// variationSettings must be passed explicitly: the Font(resId, weight) overload sends empty
// variation settings, so every weight would render at the font's default instance (400).
val GoogleSansFlex = FontFamily(
    listOf(400, 500, 600).map {
        Font(
            R.font.google_sans_flex,
            FontWeight(it),
            variationSettings = FontVariation.Settings(FontWeight(it), FontStyle.Normal),
        )
    }
)

private val m = Typography()

private fun TextStyle.gsf(w: Int? = null) = copy(
    fontFamily = GoogleSansFlex,
    fontWeight = w?.let(::FontWeight) ?: fontWeight,
    letterSpacing = 0.sp,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val SkimTypography = Typography(
    displaySmall = m.displaySmall.gsf(),
    headlineLarge = m.headlineLarge.gsf(),
    headlineMedium = m.headlineMedium.gsf(),
    headlineSmall = m.headlineSmall.gsf(),
    titleLarge = m.titleLarge.gsf(),
    titleMedium = m.titleMedium.gsf(),
    titleSmall = m.titleSmall.gsf(),
    bodyLarge = m.bodyLarge.gsf(),
    bodyMedium = m.bodyMedium.gsf(),
    bodySmall = m.bodySmall.gsf(),
    labelLarge = m.labelLarge.gsf(),
    labelMedium = m.labelMedium.gsf(),
    labelSmall = m.labelSmall.gsf(),
    displaySmallEmphasized = m.displaySmall.gsf(500),
    headlineLargeEmphasized = m.headlineLarge.gsf(500),
    headlineMediumEmphasized = m.headlineMedium.gsf(500),
    titleLargeEmphasized = m.titleLarge.gsf(500),
    titleMediumEmphasized = m.titleMedium.gsf(600),
    titleSmallEmphasized = m.titleSmall.gsf(600),
    labelLargeEmphasized = m.labelLarge.gsf(600),
    labelMediumEmphasized = m.labelMedium.gsf(600),
)

// Reader body (08): 17.sp on 28.sp, times the
// Text size from 08b (80 to 200 percent).
