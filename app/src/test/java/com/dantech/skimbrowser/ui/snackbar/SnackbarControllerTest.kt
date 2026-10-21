package com.dantech.skimbrowser.ui.snackbar

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.SkimSnackbarHost
import com.dantech.skimbrowser.ui.components.snackbarDuration
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnackbarControllerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calls = mutableListOf<String>()

    // Unconfined runs undo and finalize blocks as soon as the registry launches them.
    private val registry = UndoRegistry(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    private val controller = SnackbarController(registry)
    private val actions = mutableListOf<SnackbarAction>()

    private val tabClosed = UiText.Res(R.string.snackbar_tab_closed)

    @Test
    fun showingAgainKeepsOnlyTheNewMessageAndFinalizesTheReplacedUndo() {
        controller.show(tabClosed, SnackbarAction.Undo(undoToken("first")))
        controller.show(UiText.Res(R.string.snackbar_bookmark_added), SnackbarAction.EditBookmark(7))

        assertEquals(UiText.Res(R.string.snackbar_bookmark_added), controller.current.value?.text)
        assertEquals(listOf("finalize first"), calls)
    }

    @Test
    fun tappingUndoRunsTheUndoAndNeverTheFinalize() {
        controller.show(tabClosed, SnackbarAction.Undo(undoToken("tab")))
        val shown = controller.current.value!!

        controller.onActionClicked(shown.id)
        controller.onDismissed(shown.id)

        assertNull(controller.current.value)
        assertEquals(listOf("undo tab"), calls)
    }

    @Test
    fun aDismissedUndoIsFinalizedAndStaleIdsAreIgnored() {
        controller.show(tabClosed, SnackbarAction.Undo(undoToken("old")))
        val old = controller.current.value!!
        controller.show(tabClosed, SnackbarAction.Undo(undoToken("new")))
        val new = controller.current.value!!

        controller.onDismissed(old.id)
        controller.onActionClicked(old.id)
        assertEquals("a stale id leaves the message on screen", new, controller.current.value)

        controller.onDismissed(new.id)
        assertNull(controller.current.value)
        assertEquals(listOf("finalize old", "finalize new"), calls)
    }

    @Test
    fun dismissForClosesTheUndoOfThatKindOnly() {
        controller.show(UiText.Res(R.string.snackbar_removed_from_history), SnackbarAction.Undo(undoToken("visit", UndoKind.HISTORY)))

        controller.dismissFor(UndoKind.BOOKMARK)
        assertNotNull("another kind leaves it on screen", controller.current.value)
        assertEquals(emptyList<String>(), calls)

        controller.dismissFor(UndoKind.HISTORY)
        assertNull(controller.current.value)
        assertEquals(listOf("finalize visit"), calls)
    }

    @Test
    fun dismissForStillClosesAfterTheRegistryCancelledThatKind() {
        controller.show(UiText.Res(R.string.snackbar_bookmark_deleted), SnackbarAction.Undo(undoToken("bookmark", UndoKind.BOOKMARK)))

        registry.cancelAll(UndoKind.BOOKMARK)
        controller.dismissFor(UndoKind.BOOKMARK)

        assertNull(controller.current.value)
        assertEquals(listOf("finalize bookmark"), calls)
    }

    @Test
    fun dismissForLeavesMessagesWithoutUndo() {
        controller.show(UiText.Res(R.string.snackbar_opened_in_new_tab), SnackbarAction.SwitchToTab("t2"))

        UndoKind.entries.forEach(controller::dismissFor)

        assertEquals(SnackbarAction.SwitchToTab("t2"), controller.current.value?.action)
    }

    @Test
    fun anActionMakesTheSnackbarLongAndNoActionShort() {
        assertEquals(SnackbarDuration.Long, snackbarDuration(hasAction = true))
        assertEquals(SnackbarDuration.Short, snackbarDuration(hasAction = false))
    }

    @Test
    fun hostKeepsAMessageWithAnActionForTenSeconds() {
        showHost()
        composeRule.mainClock.autoAdvance = false
        controller.show(tabClosed, SnackbarAction.Undo(undoToken("tab")))

        composeRule.mainClock.advanceTimeBy(9_500)
        composeRule.onNodeWithText(string(R.string.snackbar_tab_closed)).assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)

        composeRule.mainClock.advanceTimeBy(1_000)
        assertNull(controller.current.value)
        assertEquals(listOf("finalize tab"), calls)
    }

    @Test
    fun hostKeepsAMessageWithoutActionForFourSeconds() {
        showHost()
        composeRule.mainClock.autoAdvance = false
        controller.show(UiText.Res(R.string.snackbar_copied))

        composeRule.mainClock.advanceTimeBy(3_500)
        composeRule.onNodeWithText(string(R.string.snackbar_copied)).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(1_000)
        assertNull(controller.current.value)
    }

    @Test
    fun hostLeavingTheCompositionWhileShowingFinalizesTheUndo() {
        var hostVisible by mutableStateOf(true)
        composeRule.setSkimContent {
            if (hostVisible) SkimSnackbarHost(controller, SnackbarPlacement.AboveToolbar, onAction = { actions += it })
        }
        controller.show(tabClosed, SnackbarAction.Undo(undoToken("tab")))
        composeRule.onNodeWithText(string(R.string.snackbar_tab_closed)).assertIsDisplayed()

        hostVisible = false
        composeRule.waitForIdle()

        assertNull(controller.current.value)
        assertEquals(listOf("finalize tab"), calls)
    }

    @Test
    fun hostRunsUndoThroughTheRegistryWithoutCallingOnAction() {
        showHost()
        controller.show(tabClosed, SnackbarAction.Undo(undoToken("tab")))

        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("undo tab"), calls)
        assertEquals(emptyList<SnackbarAction>(), actions)
        assertNull(controller.current.value)
    }

    @Test
    fun hostHandsOtherActionsToOnAction() {
        showHost()
        controller.show(UiText.Res(R.string.snackbar_opened_in_new_tab), SnackbarAction.SwitchToTab("t2"))

        composeRule.onNodeWithText(string(R.string.action_switch)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<SnackbarAction>(SnackbarAction.SwitchToTab("t2")), actions)
        assertNull(controller.current.value)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun hostShowsOnlyTheNewestMessage() {
        showHost()
        controller.show(tabClosed, SnackbarAction.Undo(undoToken("tab")))
        composeRule.onNodeWithText(string(R.string.snackbar_tab_closed)).assertIsDisplayed()

        controller.show(UiText.Res(R.string.snackbar_bookmark_added), SnackbarAction.EditBookmark(3))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.snackbar_bookmark_added)).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.snackbar_tab_closed)).assertCountEquals(0)
        assertEquals(listOf("finalize tab"), calls)
    }

    @Test
    fun anErrorIsAnAssertiveLiveRegion() {
        showHost()
        controller.show(UiText.Res(R.string.snackbar_import_failed), isError = true)
        composeRule.waitForIdle()

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assert(hasAnyDescendant(hasText(string(R.string.snackbar_import_failed))))
    }

    @Test
    fun aMessageThatIsNotAnErrorIsNotAssertive() {
        showHost()
        controller.show(UiText.Res(R.string.snackbar_copied))
        composeRule.onNodeWithText(string(R.string.snackbar_copied)).assertIsDisplayed()

        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assertCountEquals(0)
    }

    private fun showHost() {
        composeRule.setSkimContent {
            SkimSnackbarHost(controller, SnackbarPlacement.AboveToolbar, onAction = { actions += it })
        }
    }

    private fun undoToken(name: String, kind: UndoKind = UndoKind.TAB): UndoToken =
        registry.register(kind, undo = { calls += "undo $name" }, finalize = { calls += "finalize $name" })

    private fun string(id: Int): String = context.getString(id)
}
