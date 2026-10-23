package com.dantech.skimbrowser.ui.ai

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.db.entity.MessageFeedback
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.ANSWER
import com.dantech.skimbrowser.ui.components.PAGE_HOST
import com.dantech.skimbrowser.ui.components.PAGE_TITLE
import com.dantech.skimbrowser.ui.components.PageBehind
import com.dantech.skimbrowser.ui.components.QUESTION
import com.dantech.skimbrowser.ui.components.SkimBottomSheet
import com.dantech.skimbrowser.ui.components.rememberSkimSheetState
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ask about this page (06, 06c), over the address bar. The page slot stays empty. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class AskPageScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun askAboutThisPage() {
        show(answered(), bodyHeight = 560.dp)
        captureBoard("06-ask-about-this-page")
        composeRule.assertA11y()
    }

    @Test
    fun writingTheSummary() {
        // The same still frame the progress golden uses, so the wave and the shape are mid-motion.
        composeRule.mainClock.autoAdvance = false
        show(AskPageUiState(title = PAGE_TITLE, host = PAGE_HOST, summary = SummaryState.Generating, generating = true, inputEnabled = true), bodyHeight = 424.dp)
        composeRule.mainClock.advanceTimeBy(900)
        composeRule.waitForIdle()
        captureBoard("06c-ai-generating-errors")
        composeRule.assertA11y()
    }

    @Test
    fun notEnoughText() {
        show(AskPageUiState(title = PAGE_TITLE, host = PAGE_HOST, summary = SummaryState.NotEnoughText, inputEnabled = true), bodyHeight = 424.dp)
        captureBoard("06c-ai-generating-errors--not-enough-text")
        composeRule.assertA11y()
    }

    @Test
    fun noConnection() {
        show(AskPageUiState(title = PAGE_TITLE, host = PAGE_HOST, summary = SummaryState.Offline), bodyHeight = 424.dp)
        captureBoard("06c-ai-generating-errors--no-connection")
        composeRule.assertA11y()
    }

    @Test
    fun serverError() {
        show(AskPageUiState(title = PAGE_TITLE, host = PAGE_HOST, summary = SummaryState.ServerError, inputEnabled = true), bodyHeight = 424.dp)
        captureExtra("06c-ai-generating-errors--server")
        composeRule.assertA11y()
    }

    @Test
    fun refused() {
        show(AskPageUiState(title = PAGE_TITLE, host = PAGE_HOST, summary = SummaryState.Refused, inputEnabled = true), bodyHeight = 424.dp)
        captureExtra("06c-ai-generating-errors--refused")
        composeRule.assertA11y()
    }

    @Test
    fun interrupted() {
        show(
            AskPageUiState(
                title = PAGE_TITLE,
                host = PAGE_HOST,
                summary = SummaryState.Interrupted(SummaryPoints.first()),
                inputEnabled = true,
            ),
            bodyHeight = 424.dp,
        )
        captureExtra("06c-ai-generating-errors--interrupted")
        composeRule.assertA11y()
    }

    private fun show(state: AskPageUiState, bodyHeight: Dp) {
        composeRule.setSkimContent { Sheet(state, bodyHeight) }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureExtra(name: String) {
        captureScreenRoboImage("src/test/screenshots/boards/$name.png")
    }
}

/** The first-time permission dialog (06d), over the page and the toolbar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class AiPermissionDialogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstTimeOnASite() {
        composeRule.setSkimContent {
            PageBehind(toolbar = true)
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {},
                properties = com.dantech.skimbrowser.ui.home.ShortcutDialogProperties,
            ) {
                AiPermissionDialog(host = PAGE_HOST, onAllow = {}, onNotNow = {})
            }
        }
        composeRule.waitForIdle()
        captureBoard("06d-ai-permission-first-time-per-site")
        composeRule.assertA11y()
    }
}

@Composable
private fun Sheet(state: AskPageUiState, bodyHeight: Dp) {
    PageBehind()
    SkimBottomSheet(
        onDismiss = {},
        paneTitle = stringResource(R.string.ai_ask_about_page),
        state = rememberSkimSheetState(skipPartiallyExpanded = true),
    ) {
        AskPageSheet(
            state = state,
            actions = AskPageActions(
                onClose = {},
                onSend = {},
                onStop = {},
                onChip = {},
                onRetry = {},
                onCopy = {},
                onGood = {},
                onPoor = {},
                onCitation = {},
            ),
            snackbar = remember { SnackbarHostState() },
            bodyHeight = bodyHeight,
        )
    }
}

private fun answered() = AskPageUiState(
    title = PAGE_TITLE,
    host = PAGE_HOST,
    summary = SummaryState.Done(
        text = SummaryPoints.joinToString("\n") { "- $it" },
        bullets = SummaryPoints,
        feedback = MessageFeedback.NONE,
        messageId = 1,
    ),
    turns = listOf(QaTurn(question = QUESTION, answer = ANSWER, paragraphs = listOf(6), stopped = false)),
    showChips = true,
    inputEnabled = true,
    citationsEnabled = true,
)

private val SummaryPoints = listOf(
    "Modern cold-climate heat pumps keep heating at minus 25 °C, so the old \"not below freezing\" rule no longer holds.",
    "The gains come from variable-speed compressors and refrigerants that stay efficient in cold air.",
    "Switching from oil saves roughly \$300 to \$900 a year; from gas the savings are smaller and depend on local prices.",
)
