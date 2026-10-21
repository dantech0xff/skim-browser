package com.skim.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class ThemeSetting { System, Light, Dark }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SkimTheme(
    theme: ThemeSetting,         // Theme in 12
    incognito: Boolean = false,  // 09b, 09c
    content: @Composable () -> Unit,
) {
    val dark = incognito || when (theme) {
        ThemeSetting.System -> isSystemInDarkTheme()
        ThemeSetting.Light -> false
        ThemeSetting.Dark -> true
    }
    val ctx = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamic && dark -> dynamicDarkColorScheme(ctx)
        dynamic -> dynamicLightColorScheme(ctx)
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
