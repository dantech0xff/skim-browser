package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SkimBottomSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var state: SkimSheetState
    private lateinit var scope: CoroutineScope
    private var dismissals by mutableIntStateOf(0)

    @Test
    fun theSheetIsAnnouncedByItsPaneTitle() {
        setSheet(skipPartiallyExpanded = true)

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, PANE_TITLE)).assertExists()
        // material3's own title would otherwise be read: the sheet has one pane title, this one.
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).assertCountEquals(1)
    }

    @Test
    fun aFullHeightSheetOpensAtFullHeight() {
        setSheet(skipPartiallyExpanded = true)

        assertEquals(FULL_HEIGHT_CONTENT_TOP, contentTop(), 0.5f)
    }

    @Test
    fun theContentStarts36BelowTheTopOfTheSheet() {
        setSheet(skipPartiallyExpanded = true)

        val handle = composeRule.onNodeWithContentDescription(DRAG_HANDLE).getUnclippedBoundsInRoot()
        // The handle row spans the top 36 dp of the sheet, its bar in the middle.
        assertEquals(36f, (handle.bottom - handle.top).value, 0.5f)
        assertEquals(handle.bottom.value, contentTop(), 0.5f)
    }

    @Test
    fun aHalfHeightSheetOpensAtHalfHeightAndExpands() {
        setSheet(skipPartiallyExpanded = false)
        assertEquals(HALF_HEIGHT_CONTENT_TOP, contentTop(), 0.5f)

        composeRule.runOnIdle { scope.launch { state.expand() } }

        assertEquals(FULL_HEIGHT_CONTENT_TOP, contentTop(), 0.5f)
    }

    @Test
    fun partialExpandLowersAnExpandedSheetToHalfHeight() {
        setSheet(skipPartiallyExpanded = false)
        composeRule.runOnIdle { scope.launch { state.expand() } }
        assertEquals(FULL_HEIGHT_CONTENT_TOP, contentTop(), 0.5f)

        composeRule.runOnIdle { scope.launch { state.partialExpand() } }

        assertEquals(HALF_HEIGHT_CONTENT_TOP, contentTop(), 0.5f)
        assertEquals(0, dismissals)
    }

    @Test
    fun partialExpandLeavesAFullHeightSheetWhereItIs() {
        setSheet(skipPartiallyExpanded = true)

        composeRule.runOnIdle { scope.launch { state.partialExpand() } }

        assertEquals(FULL_HEIGHT_CONTENT_TOP, contentTop(), 0.5f)
        assertEquals(0, dismissals)
    }

    @Test
    fun tappingTheScrimDismissesTheSheetOnce() {
        setSheet(skipPartiallyExpanded = true)

        tapAboveTheSheet()

        assertEquals(1, dismissals)
    }

    @Test
    fun theDragHandleOffersTalkBackTheSheetActions() {
        setSheet(skipPartiallyExpanded = false)

        // At half height the handle expands the sheet; then it lowers it, or dismisses it.
        composeRule.onNodeWithContentDescription(DRAG_HANDLE).performSemanticsAction(SemanticsActions.Expand)
        composeRule.waitForIdle()
        assertEquals(FULL_HEIGHT_CONTENT_TOP, contentTop(), 0.5f)

        composeRule.onNodeWithContentDescription(DRAG_HANDLE).performSemanticsAction(SemanticsActions.Dismiss)
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
    }

    @Test
    fun theSheetLeavesWithItsCaller() {
        setSheet(skipPartiallyExpanded = true, shown = { dismissals == 0 })

        tapAboveTheSheet()

        composeRule.onAllNodesWithTag(CONTENT).assertCountEquals(0)
    }

    private fun setSheet(skipPartiallyExpanded: Boolean, shown: () -> Boolean = { true }) {
        composeRule.setSkimContent {
            state = rememberSkimSheetState(skipPartiallyExpanded)
            scope = rememberCoroutineScope()
            if (shown()) {
                SkimBottomSheet(onDismiss = { dismissals++ }, paneTitle = PANE_TITLE, state = state) {
                    Box(Modifier.fillMaxWidth().height(CONTENT_HEIGHT.dp).testTag(CONTENT))
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Taps the scrim where the screen shows above the sheet. */
    private fun tapAboveTheSheet() {
        composeRule.onNodeWithContentDescription(CLOSE_SHEET).performTouchInput { click(Offset(centerX, 100f)) }
        composeRule.waitForIdle()
    }

    /** The top of the sheet's content, in dp from the top of the screen. */
    private fun contentTop(): Float {
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().top.value
    }

    private companion object {
        const val CONTENT = "content"
        const val PANE_TITLE = "Ask about this page"
        const val DRAG_HANDLE = "Drag handle"
        const val CLOSE_SHEET = "Close sheet"

        /** With the 36 dp handle row the sheet is 736 dp high, as on 06. */
        const val CONTENT_HEIGHT = 700

        /** 915 - 736, plus the handle row. */
        const val FULL_HEIGHT_CONTENT_TOP = 215f

        /** Half of the 915 dp screen, plus the handle row. */
        const val HALF_HEIGHT_CONTENT_TOP = 493.5f
    }
}
