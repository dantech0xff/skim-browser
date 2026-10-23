package com.dantech.skimbrowser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
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
    dynamicColor: Boolean = true, // Wallpaper colors on API 31+; screenshot tests pass false for the brand scheme
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

// Shapes stay at the defaults.
// Motion in components:
// MaterialTheme.motionScheme.defaultSpatialSpec()
