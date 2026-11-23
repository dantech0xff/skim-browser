package com.dantech.skimbrowser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

typealias ThemeSetting = com.dantech.skimbrowser.domain.settings.ThemeSetting

/**
 * Whether Skim draws dark. Incognito (09b, 09c) is always dark; otherwise Theme in 12 decides.
 * The app theme and the system bar icons both use this, so they never disagree.
 */
fun ThemeSetting.isDark(systemDark: Boolean, incognito: Boolean): Boolean = incognito || when (this) {
    ThemeSetting.System -> systemDark
    ThemeSetting.Light -> false
    ThemeSetting.Dark -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SkimTheme(
    theme: ThemeSetting,         // Theme in 12
    incognito: Boolean = false,  // 09b, 09c
    dynamicColor: Boolean = false, // Wallpaper colors on API 31+; screenshot tests pass false for the brand scheme
    content: @Composable () -> Unit,
) {
    val dark = theme.isDark(systemDark = isSystemInDarkTheme(), incognito = incognito)
    val ctx = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> SkimDarkColors
        else -> SkimLightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = SkimTypography,
        content = content,
    )
}

/**
 * Whether the colors in use are a dark scheme: incognito (09b, 09c), or Theme Dark (12). Some roles
 * differ between the two drawings, such as the toolbar container and the disabled container.
 */
@Composable
@ReadOnlyComposable
internal fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * The neutral scheme for what floats over a web page: the browsing pill and the bars above it.
 * Inside [SkimTheme] it follows the theme's light or dark, and keeps its type, shapes and motion.
 * Sheets, dialogs and the screens Skim draws itself stay in the Skim scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SkimChromeTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isDarkScheme()) SkimChromeDarkColors else SkimChromeLightColors,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

// Shapes stay at the defaults.
// Motion in components:
// MaterialTheme.motionScheme.defaultSpatialSpec()
