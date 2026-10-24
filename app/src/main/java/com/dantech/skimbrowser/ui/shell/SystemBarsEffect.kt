package com.dantech.skimbrowser.ui.shell

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Keeps the system bar icons readable on what Skim draws: light icons when the app is [dark],
 * dark icons otherwise. `SystemBarStyle.auto` alone follows the system's dark mode, not the app's
 * (Theme in Settings, incognito), so the styles are set again whenever [dark] changes.
 *
 * A configuration change makes the Activity apply the styles of its first edge-to-edge call again
 * (those follow the system), so they are also set again after each one.
 */
@Composable
fun SystemBarsEffect(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val configuration = LocalConfiguration.current
    DisposableEffect(activity, dark, configuration) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        onDispose { }
    }
}

/** The navigation bar scrims Android draws behind three-button navigation (the androidx defaults). */
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
