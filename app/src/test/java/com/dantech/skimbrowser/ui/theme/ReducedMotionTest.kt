package com.dantech.skimbrowser.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReducedMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var reduced: Boolean? = null

    @After
    fun restoreAnimations() = setAnimatorScale(1f)

    @Test
    fun animationsAreOnByDefault() {
        composeRule.setContent { reduced = rememberReducedMotion() }
        composeRule.waitForIdle()

        assertFalse(reduced!!)
    }

    @Test
    fun aZeroAnimatorDurationScaleIsReducedMotion() {
        setAnimatorScale(0f)

        composeRule.setContent { reduced = rememberReducedMotion() }
        composeRule.waitForIdle()

        assertTrue(reduced!!)
    }

    @Test
    fun aSlowerAnimatorScaleIsNotReducedMotion() {
        setAnimatorScale(0.5f)

        composeRule.setContent { reduced = rememberReducedMotion() }
        composeRule.waitForIdle()

        assertFalse(reduced!!)
    }

    @Test
    fun turningRemoveAnimationsOnWhileShownUpdatesTheValue() {
        composeRule.setContent { reduced = rememberReducedMotion() }
        composeRule.waitForIdle()
        assertFalse(reduced!!)

        setAnimatorScale(0f)
        // The system notifies observers of the setting; Robolectric needs the notification sent.
        context.contentResolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        composeRule.waitForIdle()

        assertTrue(reduced!!)
    }

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }
}
