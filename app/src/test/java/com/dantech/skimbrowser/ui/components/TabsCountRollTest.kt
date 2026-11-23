package com.dantech.skimbrowser.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tab count rolls to a new number: in from below as it grows, from above as it shrinks. The
 * numbers are hidden from accessibility, so they are found in the unmerged tree.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class TabsCountRollTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var count by mutableIntStateOf(7)

    @Test
    fun theFirstNumberShowsWithoutMotion() {
        composeRule.mainClock.autoAdvance = false
        show()
        advanceFrames()
        val first = top("7")

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(top("7"), first)
    }

    @Test
    fun aGrowingCountRollsUp() {
        show()
        val rest = top("7")
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { count = 8 }
        advanceFrames()
        assertTrue("the new number comes from below", top("8") > rest)
        assertTrue("the old number leaves upward", top("7") < rest)

        settle()
        texts("7").assertCountEquals(0)
        assertEquals(rest, top("8"))
    }

    @Test
    fun aShrinkingCountRollsDown() {
        count = 8
        show()
        val rest = top("8")
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { count = 7 }
        advanceFrames()
        assertTrue("the new number comes from above", top("7") < rest)
        assertTrue("the old number leaves downward", top("8") > rest)

        settle()
        texts("8").assertCountEquals(0)
        assertEquals(rest, top("7"))
    }

    @Test
    fun withRemoveAnimationsTheNumberChangesAtOnce() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()
        val rest = top("7")
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { count = 8 }
        advanceFrames()
        texts("7").assertCountEquals(0)
        assertEquals(rest, top("8"))
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun ninetyNinePlusStillWidensTheSquareAndDoesNotRollBetweenHundreds() {
        count = 99
        show()
        val narrow = width()

        count = 120
        composeRule.waitForIdle()
        assertTrue(width() > narrow)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { count = 121 }
        advanceFrames()
        texts("99+").assertCountEquals(1)
        composeRule.mainClock.autoAdvance = true
    }

    private fun show() {
        composeRule.setSkimContent { TabsCountIcon(count, Modifier.testTag(ICON)) }
        composeRule.waitForIdle()
    }

    private fun texts(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true)

    private fun top(text: String): Dp = composeRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    private fun width(): Dp = composeRule.onNodeWithTag(ICON).getUnclippedBoundsInRoot().let { it.right - it.left }

    private fun advanceFrames() {
        composeRule.waitForIdle()
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    private companion object {
        const val ICON = "count"
        const val FRAMES = 3
    }
}
