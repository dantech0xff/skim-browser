package com.dantech.skimbrowser.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user turned on Remove animations (Accessibility settings), which sets the animator
 * duration scale to 0. Skim then shows a still frame of the loading indicator and flat wavy
 * progress instead of moving ones.
 *
 * The value is read again when the configuration changes and whenever the setting itself changes,
 * so returning from Settings with the option flipped updates the screen.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var settingChanges by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                settingChanges++
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return remember(context, configuration, settingChanges) {
        animationsRemoved(context.contentResolver)
    }
}

/** True when the animator duration scale is 0, the value Remove animations writes. */
internal fun animationsRemoved(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
