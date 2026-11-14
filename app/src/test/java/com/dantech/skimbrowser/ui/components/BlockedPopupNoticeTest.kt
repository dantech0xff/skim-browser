package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.SnackbarPlacement
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockedPopupNoticeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openShowsTheHeldPopup() {
        val controller = noticeController()
        val actions = mutableListOf<SnackbarAction>()
        showBlocked(controller, holdId = 1)

        composeRule.setSkimContent { Notice(controller, onAction = { actions += it }) }
        composeRule.onNodeWithText("Popup blocked").assertExists()
        composeRule.onNodeWithText("Open").performClick()

        assertEquals(listOf(SnackbarAction.OpenBlockedPopup("tab", 1)), actions)
    }

    @Test
    fun alwaysAllowKeepsTheSiteAndTheHold() {
        val controller = noticeController()
        val actions = mutableListOf<SnackbarAction>()
        showBlocked(controller, holdId = 2)

        composeRule.setSkimContent { Notice(controller, onAction = { actions += it }) }
        composeRule.onNodeWithText("Always allow on this site").performClick()

        assertEquals(
            listOf(SnackbarAction.AlwaysAllowPopups("daylightjournal.com", "tab", 2)),
            actions,
        )
    }

    @Test
    fun aReplacementDropsTheHeldPopup() {
        val controller = noticeController()
        val closed = mutableListOf<SnackbarAction?>()
        val open = SnackbarAction.OpenBlockedPopup("tab", 4)
        showBlocked(controller, holdId = 4)

        composeRule.setSkimContent { Notice(controller, onClosed = { closed += it }) }
        composeRule.onNodeWithText("Open").assertExists()

        controller.show(UiText.Res(R.string.snackbar_copied))
        composeRule.waitForIdle()

        assertEquals(listOf<SnackbarAction?>(open), closed)
    }

    private fun noticeController() = SnackbarController(UndoRegistry(CoroutineScope(Dispatchers.Unconfined)))

    private fun showBlocked(controller: SnackbarController, holdId: Long) {
        controller.show(
            UiText.Res(R.string.snackbar_popup_blocked),
            SnackbarAction.OpenBlockedPopup("tab", holdId),
            secondary = SnackbarAction.AlwaysAllowPopups("daylightjournal.com", "tab", holdId),
        )
    }
}

@androidx.compose.runtime.Composable
private fun Notice(
    controller: SnackbarController,
    onAction: (SnackbarAction) -> Unit = {},
    onClosed: (SnackbarAction?) -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        SkimSnackbarHost(
            controller = controller,
            placement = SnackbarPlacement.Bottom,
            onAction = onAction,
            onClosed = onClosed,
        )
    }
}
