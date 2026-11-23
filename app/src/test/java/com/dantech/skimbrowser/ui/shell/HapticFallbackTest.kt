package com.dantech.skimbrowser.ui.shell

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/**
 * The haptics Skim asks for still answer on older Android. minSdk is 26, but the gesture types
 * came later: GestureEnd and Confirm in API 30, GestureThresholdActivate in API 34. Compose's own
 * HapticFeedback hands each type to `ViewCompat.performHapticFeedback`, which replaces a type the
 * device does not know with an older one, so Skim provides no wrapper of its own. This test pins
 * that behavior to the pinned Compose and androidx.core versions: if an update drops the
 * fallback, it fails.
 *
 * The device's SDK is set on `Build.VERSION.SDK_INT` rather than by switching Robolectric SDKs.
 */
@RunWith(AndroidJUnit4::class)
class HapticFallbackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val realSdk = Build.VERSION.SDK_INT

    @After
    fun restoreSdk() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", realSdk)
    }

    @Test
    fun api26() = assertConstants(
        sdk = 26,
        threshold = HapticFeedbackConstants.CONTEXT_CLICK,
        gestureEnd = HapticFeedbackConstants.CONTEXT_CLICK,
    )

    @Test
    fun api29() = assertConstants(
        sdk = 29,
        threshold = HapticFeedbackConstants.CONTEXT_CLICK,
        gestureEnd = HapticFeedbackConstants.CONTEXT_CLICK,
    )

    @Test
    fun api30() = assertConstants(
        sdk = 30,
        threshold = HapticFeedbackConstants.CONTEXT_CLICK,
        gestureEnd = HapticFeedbackConstants.GESTURE_END,
    )

    @Test
    fun api33() = assertConstants(
        sdk = 33,
        threshold = HapticFeedbackConstants.CONTEXT_CLICK,
        gestureEnd = HapticFeedbackConstants.GESTURE_END,
    )

    @Test
    fun api34() = assertConstants(
        sdk = 34,
        threshold = HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE,
        gestureEnd = HapticFeedbackConstants.GESTURE_END,
    )

    /** What the window performs for each type Skim asks for, on a device of [sdk]. */
    private fun assertConstants(sdk: Int, threshold: Int, gestureEnd: Int) {
        lateinit var haptics: HapticFeedback
        lateinit var view: View
        composeRule.setContent {
            haptics = LocalHapticFeedback.current
            view = LocalView.current
        }
        composeRule.waitForIdle()
        val window = composeRule.runOnIdle { viewsOf(view.rootView) }
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)

        assertEquals("GestureThresholdActivate on API $sdk", threshold, performed(haptics, window, HapticFeedbackType.GestureThresholdActivate))
        assertEquals("GestureEnd on API $sdk", gestureEnd, performed(haptics, window, HapticFeedbackType.GestureEnd))
        assertEquals("LongPress on API $sdk", HapticFeedbackConstants.LONG_PRESS, performed(haptics, window, HapticFeedbackType.LongPress))
    }

    /**
     * The constant the one view of [window] that performed a haptic received for [type]. Compose
     * performs it on the view that hosts the composition, which need not be `LocalView`.
     */
    private fun performed(haptics: HapticFeedback, window: List<View>, type: HapticFeedbackType): Int {
        window.forEach { ReflectionHelpers.setField(shadowOf(it), "hapticFeedbackPerformed", NONE) }
        composeRule.runOnIdle { haptics.performHapticFeedback(type) }
        val constants = window.map { shadowOf(it).lastHapticFeedbackPerformed() }.filter { it != NONE }
        assertEquals("one view performed $type", 1, constants.size)
        return constants.single()
    }

    private fun viewsOf(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) addAll(viewsOf(root.getChildAt(i)))
    }

    private companion object {
        /** What the shadow reports before any haptic. */
        const val NONE = -1
    }
}
