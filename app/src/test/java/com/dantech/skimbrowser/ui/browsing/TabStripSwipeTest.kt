package com.dantech.skimbrowser.ui.browsing

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.tab.TabNeighbor
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.ProvideHaptics
import com.dantech.skimbrowser.testing.RecordingHapticFeedback
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A horizontal drag on the browsing pill: the haptic at the point where a release switches tab,
 * and the caption, which fades in and out above a pill that stays where it is.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class TabStripSwipeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val haptics = RecordingHapticFeedback()
    private val selected = mutableListOf<String>()

    // Haptics

    @Test
    fun eachTimeTheDragPassesTheSwitchingPointItAsksForOneThresholdHaptic() {
        show()

        drag { down(center); moveBy(Offset(-30.dp.toPx(), 0f)) }
        assertTrue("short of the switching point", haptics.performed.isEmpty())

        drag { moveBy(Offset(-36.dp.toPx(), 0f)) }
        drag { moveBy(Offset(-10.dp.toPx(), 0f)) }
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics.performed)

        // Under the switching point again, then past it again.
        drag { moveBy(Offset(30.dp.toPx(), 0f)) }
        drag { moveBy(Offset(-30.dp.toPx(), 0f)) }
        assertEquals(List(2) { HapticFeedbackType.GestureThresholdActivate }, haptics.performed)

        drag { up() }
        assertEquals("the release asks for nothing more", 2, haptics.performed.size)
        assertEquals(listOf(NEXT.id), selected)
    }

    @Test
    fun aDragTowardAMissingNeighborAsksForNothing() {
        show(previous = null)

        drag { down(center); moveBy(Offset(100.dp.toPx(), 0f)) }
        drag { up() }

        assertTrue(haptics.performed.isEmpty())
        assertTrue(selected.isEmpty())
    }

    @Test
    fun withRemoveAnimationsTheHapticStays() {
        removeAnimations()
        show()

        drag { down(center); moveBy(Offset(-80.dp.toPx(), 0f)) }
        drag { up() }

        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics.performed)
    }

    // The caption

    @Test
    fun theCaptionFadesOutOverSeveralFramesAndThePillStaysPut() {
        show()
        val rest = bar().getUnclippedBoundsInRoot()
        composeRule.mainClock.autoAdvance = false

        drag { down(center); moveBy(Offset(-30.dp.toPx(), 0f)) }
        advanceFrames()
        composeRule.onAllNodesWithText(NEXT.title, useUnmergedTree = true).assertCountEquals(1)
        assertEquals(rest.top, bar().getUnclippedBoundsInRoot().top)

        drag { up() }
        advanceFrames()
        composeRule.onAllNodesWithText(NEXT.title, useUnmergedTree = true).assertCountEquals(1)
        assertEquals("still fading", rest.top, bar().getUnclippedBoundsInRoot().top)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(NEXT.title, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(rest.top, bar().getUnclippedBoundsInRoot().top)
    }

    @Test
    fun withRemoveAnimationsTheCaptionGoesAtOnce() {
        removeAnimations()
        show()
        composeRule.mainClock.autoAdvance = false

        drag { down(center); moveBy(Offset(-30.dp.toPx(), 0f)) }
        advanceFrames()
        composeRule.onAllNodesWithText(NEXT.title, useUnmergedTree = true).assertCountEquals(1)

        drag { up() }
        advanceFrames()
        composeRule.onAllNodesWithText(NEXT.title, useUnmergedTree = true).assertCountEquals(0)
        composeRule.mainClock.autoAdvance = true
    }

    // Helpers

    private fun show(previous: TabNeighbor? = PREVIOUS, next: TabNeighbor? = NEXT) {
        composeRule.setSkimContent {
            ProvideHaptics(haptics) {
                // Anchored at the bottom, as the browsing screen places it.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    TabStripSwipe(
                        previous = previous,
                        next = next,
                        groups = emptyList(),
                        enabled = true,
                        onSelect = { selected += it },
                    ) { modifier ->
                        Box(modifier.fillMaxWidth().height(64.dp).testTag(BAR))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bar(): SemanticsNodeInteraction = composeRule.onNodeWithTag(BAR)

    private fun drag(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        bar().performTouchInput(block)
        composeRule.waitForIdle()
    }

    private fun advanceFrames() {
        composeRule.waitForIdle()
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun removeAnimations() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private companion object {
        const val BAR = "bar"

        /** A few frames: long enough for a snap, far shorter than the expressive springs. */
        const val FRAMES = 4
        val PREVIOUS = TabNeighbor(id = "p", title = "Heat pump sizing calculator", groupId = null, changedGroup = false)
        val NEXT = TabNeighbor(id = "n", title = "Osaka night buses", groupId = null, changedGroup = false)
    }
}
