package com.dantech.skimbrowser.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A [HapticFeedback] that records what the UI asks for instead of touching the vibrator, so a test
 * can check that a gesture asks for exactly the haptics it should. Provide it with [ProvideHaptics].
 */
class RecordingHapticFeedback : HapticFeedback {
    private val asked = mutableListOf<HapticFeedbackType>()

    /** Every haptic asked for so far, in order. */
    val performed: List<HapticFeedbackType> get() = asked.toList()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        asked += hapticFeedbackType
    }
}

/** Runs [content] with [haptics] as its [LocalHapticFeedback]. */
@Composable
fun ProvideHaptics(haptics: RecordingHapticFeedback, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHapticFeedback provides haptics, content = content)
}
