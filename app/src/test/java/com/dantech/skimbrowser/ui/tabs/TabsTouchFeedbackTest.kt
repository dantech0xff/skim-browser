package com.dantech.skimbrowser.ui.tabs

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.ProvideHaptics
import com.dantech.skimbrowser.testing.RecordingHapticFeedback
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureComponent
import com.dantech.skimbrowser.testing.setSkimContent
import kotlin.math.abs
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What touching a tab card gives back: the haptics of swipe to close and of reorder, the close mark
 * a swiped card uncovers, and the lift of a card picked up for reorder.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class TabsTouchFeedbackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val haptics = RecordingHapticFeedback()
    private val calls = mutableListOf<String>()
    private var errorContainer = Color.Unspecified
    private var onErrorContainer = Color.Unspecified

    // Swipe to close

    @Test
    fun eachTimeASwipePassesTheClosingPointItAsksForOneThresholdHaptic() {
        show()
        val card = card(FIRST)

        card.performTouchInput {
            down(center)
            moveBy(Offset(-40.dp.toPx(), 0f))
        }
        composeRule.waitForIdle()
        assertEquals("short of the closing point", emptyList<HapticFeedbackType>(), haptics.performed)

        card.performTouchInput { moveBy(Offset(-50.dp.toPx(), 0f)) }
        composeRule.waitForIdle()
        card.performTouchInput { moveBy(Offset(-10.dp.toPx(), 0f)) }
        composeRule.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics.performed)

        // Back under the closing point, then past it again.
        card.performTouchInput { moveBy(Offset(40.dp.toPx(), 0f)) }
        composeRule.waitForIdle()
        card.performTouchInput { moveBy(Offset(-40.dp.toPx(), 0f)) }
        composeRule.waitForIdle()
        assertEquals(List(2) { HapticFeedbackType.GestureThresholdActivate }, haptics.performed)

        card.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("the release asks for nothing more", 2, haptics.performed.size)
        // Material confirms the dismissal more than once while this static grid keeps the card.
        assertTrue(calls.isNotEmpty() && calls.all { it == "close:a" })
    }

    @Test
    fun closeTabIsAPlainTapWithoutAHaptic() {
        show()

        composeRule.onAllNodesWithContentDescription("Close tab")[0].performClick()

        assertEquals(listOf("close:a"), calls)
        assertTrue(haptics.performed.isEmpty())
    }

    @Test
    fun aSwipedCardUncoversTheCloseMarkOnTheSideItLeaves() {
        show()
        val box = composeRule.onAllNodesWithTag("tab-card")[0]
        assertFalse("nothing behind a card at rest", box.captureToImage().hasPixel(errorContainer))

        card(FIRST).performTouchInput {
            down(center)
            moveBy(Offset(-64.dp.toPx(), 0f))
        }
        composeRule.waitForIdle()
        val leftward = box.captureToImage()
        assertTrue(leftward.hasPixel(errorContainer))
        assertTrue("the mark is at the end", leftward.hasPixel(onErrorContainer, fromX = 0.5f))
        assertFalse(leftward.hasPixel(onErrorContainer, toX = 0.5f))

        card(FIRST).performTouchInput { moveBy(Offset(128.dp.toPx(), 0f)) }
        composeRule.waitForIdle()
        val rightward = box.captureToImage()
        assertTrue("the mark is at the start", rightward.hasPixel(onErrorContainer, toX = 0.5f))
        assertFalse(rightward.hasPixel(onErrorContainer, fromX = 0.5f))
    }

    @Test
    fun theCloseMarkStaysWhileTheCardSpringsBackThenGoes() {
        show()
        val box = composeRule.onAllNodesWithTag("tab-card")[0]
        card(FIRST).performTouchInput {
            down(center)
            moveBy(Offset(-48.dp.toPx(), 0f))
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        card(FIRST).performTouchInput { up() }
        advanceFrames()
        assertTrue("still uncovered while the card returns", box.captureToImage().hasPixel(errorContainer))

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertFalse(box.captureToImage().hasPixel(errorContainer))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun aCardMidSwipeShowsTheCloseMark() {
        show()
        midSwipe()
        composeRule.onAllNodesWithTag("tab-card")[1].captureComponent("tab-card-swipe-close")
        composeRule.assertA11y()
    }

    @Test
    fun aCardMidSwipeShowsTheCloseMarkInTheDark() {
        show(dark = true)
        midSwipe()
        composeRule.onAllNodesWithTag("tab-card")[1].captureComponent("tab-card-swipe-close-dark")
        composeRule.assertA11y()
    }

    // Reorder

    @Test
    fun pickingACardUpAsksForALongPressAndDroppingItForAGestureEnd() {
        show()

        card(FIRST).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 4.dp.toPx()))
        }
        composeRule.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)

        card(FIRST).performTouchInput { moveBy(Offset(8.dp.toPx(), 0f)) }
        card(FIRST).performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.LongPress, HapticFeedbackType.GestureEnd), haptics.performed)
    }

    @Test
    fun aCancelledDragAsksForNothingAtTheEnd() {
        show()

        card(FIRST).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 4.dp.toPx()))
        }
        card(FIRST).performTouchInput { cancel() }
        composeRule.waitForIdle()

        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun whereReorderIsOffALongPressAsksForNothing() {
        show(incognito = true)

        card(FIRST).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 4.dp.toPx()))
            up()
        }
        composeRule.waitForIdle()

        assertTrue(haptics.performed.isEmpty())
    }

    @Test
    fun aPickedUpCardGrowsOverSeveralFramesAndSettlesBackWhenDropped() {
        show()
        val rest = width(card(FIRST))
        composeRule.mainClock.autoAdvance = false

        pickUp(FIRST)
        advanceFrames()
        val rising = width(card(FIRST))
        assertTrue("growing: $rising of $rest", rising > rest + 0.5f && rising < rest * LiftScale - 0.5f)

        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(rest * LiftScale, width(card(FIRST)), 0.5f)

        card(FIRST).performTouchInput { up() }
        advanceFrames()
        val falling = width(card(FIRST))
        assertTrue("settling: $falling of $rest", falling > rest + 0.5f)

        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(rest, width(card(FIRST)), 0.5f)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun withRemoveAnimationsAPickedUpCardLiftsAndSettlesAtOnceAndTheHapticsStay() {
        removeAnimations()
        show()
        val rest = width(card(FIRST))
        composeRule.mainClock.autoAdvance = false

        pickUp(FIRST)
        advanceFrames()
        assertEquals(rest * LiftScale, width(card(FIRST)), 0.5f)

        card(FIRST).performTouchInput { cancel() }
        advanceFrames()
        assertEquals(rest, width(card(FIRST)), 0.5f)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
        composeRule.mainClock.autoAdvance = true
    }

    // Helpers

    private fun show(incognito: Boolean = false, dark: Boolean = false) {
        composeRule.setSkimContent(dark = dark, incognito = incognito) {
            errorContainer = MaterialTheme.colorScheme.errorContainer
            onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
            ProvideHaptics(haptics) {
                TabsScreen(state = board(incognito), searchState = null, actions = actions(incognito))
            }
        }
        composeRule.waitForIdle()
    }

    private fun card(title: String): SemanticsNodeInteraction = composeRule.onNode(hasText(title) and hasClickAction())

    private fun width(node: SemanticsNodeInteraction): Float = node.getBoundsInRoot().let { (it.right - it.left).value }

    /** Presses [title]'s card for longer than a long press, with the clock paused. */
    private fun pickUp(title: String) {
        val timeout = composeRule.onRoot().fetchSemanticsNode().layoutInfo.viewConfiguration.longPressTimeoutMillis
        card(title).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(timeout + 16)
    }

    /** The second card held [SWIPE] toward the start, short of the closing point. */
    private fun midSwipe() {
        card(SECOND).performTouchInput {
            down(center)
            moveBy(Offset(-SWIPE.toPx(), 0f))
        }
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

    private fun board(incognito: Boolean) = TabsUiState(
        incognito = incognito,
        count = 2,
        sections = persistentListOf(
            TabSection(
                group = null,
                showHeader = false,
                tabs = persistentListOf(
                    TabCardState("a", FIRST, "D", isCurrent = true, hasSkimChat = false),
                    TabCardState("b", SECOND, "K", isCurrent = false, hasSkimChat = false),
                ),
            ),
        ),
    )

    private fun actions(incognito: Boolean) = TabsActions(
        onSearch = {},
        onCloseSearch = {},
        onSelectMode = {},
        onOpen = { calls += "open:$it" },
        onClose = { calls += "close:$it" },
        onMove = { id, index -> calls += "move:$id:$index" },
        onRename = {},
        onUngroup = {},
        onCloseGroup = {},
        onUndoGrouping = {},
        onAskSkim = {},
        onNewIncognitoTab = if (incognito) null else ({}),
        onCloseAll = {},
        onNewTab = {},
    )

    private companion object {
        const val FIRST = "Why heat pumps are winning in cold climates"
        const val SECOND = "Kotlin 2.2 release notes"

        /** A few frames: long enough for a snap, far shorter than the expressive springs. */
        const val FRAMES = 4
        const val SETTLE_MILLIS = 2_000L
        val SWIPE: Dp = 64.dp
    }
}

/** Per channel, on a 0..1 scale: 4 of 255, for rounding in the raster. */
private const val TOLERANCE = 4f / 255f

/** Whether a pixel between [fromX] and [toX] (shares of the width) has [color]. */
private fun ImageBitmap.hasPixel(color: Color, fromX: Float = 0f, toX: Float = 1f): Boolean {
    val pixels = toPixelMap()
    for (y in 0 until height) {
        for (x in (width * fromX).toInt() until (width * toX).toInt()) {
            val pixel = pixels[x, y]
            if (abs(pixel.red - color.red) < TOLERANCE &&
                abs(pixel.green - color.green) < TOLERANCE &&
                abs(pixel.blue - color.blue) < TOLERANCE
            ) {
                return true
            }
        }
    }
    return false
}
