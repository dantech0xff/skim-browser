package com.dantech.skimbrowser.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ReaderSizeSliderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reported = mutableListOf<Int>()

    @Test
    fun valuesMapToTheNearestStepOfTen() {
        assertEquals(80, readerSizePercent(80f))
        assertEquals(100, readerSizePercent(104.9f))
        assertEquals(110, readerSizePercent(105f))
        assertEquals(140, readerSizePercent(143.7f))
        assertEquals(200, readerSizePercent(200f))
    }

    @Test
    fun valuesOutsideTheRangeStopAtTheEnds() {
        assertEquals(80, readerSizePercent(10f))
        assertEquals(200, readerSizePercent(260f))
    }

    @Test
    fun theSliderIsLabelledSizeWithThePercentageAsItsState() {
        setSlider(percent = 110)

        composeRule.onNodeWithContentDescription(SIZE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "110%"))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(current = 110f, range = 80f..200f, steps = 11),
                ),
            )
    }

    @Test
    fun settingAProgressReportsTheNearestSize() {
        setSlider(percent = 110)

        composeRule.onNodeWithContentDescription(SIZE).performSemanticsAction(SemanticsActions.SetProgress) { it(143f) }

        assertEquals(listOf(140), reported)
    }

    @Test
    fun tappingTheEndOfTheTrackReportsTheLargestSize() {
        setSlider(percent = 110)

        // Near the end of the track: the node's bounds reach a little past the track at both ends.
        composeRule.onNodeWithContentDescription(SIZE).performTouchInput { click(percentOffset(0.95f, 0.5f)) }

        assertEquals(200, reported.last())
    }

    @Test
    fun theSliderShowsTheSizeTheCallerKeeps() {
        composeRule.setSkimContent {
            var percent by remember { mutableIntStateOf(100) }
            ReaderSizeSlider(percent = percent, onPercent = { percent = it })
        }

        composeRule.onNodeWithContentDescription(SIZE).performSemanticsAction(SemanticsActions.SetProgress) { it(170f) }

        composeRule.onNodeWithContentDescription(SIZE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "170%"))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(current = 170f, range = 80f..200f, steps = 11),
                ),
            )
    }

    @Test
    fun aSizeTheCallerIgnoresDoesNotMoveTheSlider() {
        setSlider(percent = 100)

        composeRule.onNodeWithContentDescription(SIZE).performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }

        assertEquals(listOf(150), reported)
        composeRule.onNodeWithContentDescription(SIZE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100%"))
    }

    private fun setSlider(percent: Int) {
        composeRule.setSkimContent {
            ReaderSizeSlider(percent = percent, onPercent = { reported += it })
        }
    }

    private companion object {
        const val SIZE = "Size"
    }
}
