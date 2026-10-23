package com.dantech.skimbrowser.ui.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.db.entity.MessageFeedback
import com.dantech.skimbrowser.ui.components.AiLoadingIndicator
import com.dantech.skimbrowser.ui.components.AiSuggestionChip
import com.dantech.skimbrowser.ui.components.AnswerCard
import com.dantech.skimbrowser.ui.components.AnswerText
import com.dantech.skimbrowser.ui.components.ChatInputPill
import com.dantech.skimbrowser.ui.components.CitationChip
import com.dantech.skimbrowser.ui.components.CompactIconButton
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonStyle
import com.dantech.skimbrowser.ui.components.SkimSnackbar
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.components.UserBubble
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion

/** What the sheet can ask the screen to do. */
data class AskPageActions(
    val onClose: () -> Unit,
    val onSend: (String) -> Unit,
    val onStop: () -> Unit,
    val onChip: (String) -> Unit,
    val onRetry: () -> Unit,
    val onCopy: () -> Unit,
    val onGood: () -> Unit,
    val onPoor: () -> Unit,
    val onCitation: (Int) -> Unit,
)

/**
 * Ask about this page (06, 06c). The header is the page and Close, then the summary, the suggested
 * questions, the conversation, and the input. "Copied to clipboard" uses [snackbar], which stays
 * inside the sheet.
 *
 * [bodyHeight] fixes the scrolling region so a screenshot can match a board's sheet. On screen the
 * region fills the sheet, which opens at half height and drags to full height.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AskPageSheet(
    state: AskPageUiState,
    actions: AskPageActions,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    field: TextFieldState = rememberTextFieldState(),
    bodyHeight: Dp? = null,
) {
    val reduced = rememberReducedMotion()
    val fade = if (reduced) snap<Float>() else MaterialTheme.motionScheme.slowEffectsSpec()
    Box(modifier.fillMaxWidth().then(if (bodyHeight == null) Modifier.fillMaxHeight() else Modifier)) {
        Column(if (bodyHeight == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            AskPageHeader(state.title, state.host, actions.onClose)
            Column(
                Modifier
                    .then(if (bodyHeight != null) Modifier.height(bodyHeight) else Modifier.weight(1f))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedContent(
                    targetState = state.summary,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                    label = "summary",
                ) { summary ->
                    SummaryBlock(summary, actions)
                }
                if (state.showChips) SuggestedQuestions(actions.onChip)
                state.turns.forEach { turn ->
                    QuestionBlock(turn, state, actions)
                }
                if (state.waitingForAnswer && state.turns.lastOrNull()?.answer == null) {
                    AiLoadingIndicator(stringResource(R.string.cd_writing_summary))
                }
                AnswerProblem(state.answerError, actions.onRetry)
            }
            Box(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
                ChatInputPill(
                    state = field,
                    placeholder = stringResource(R.string.ai_ask_about_page),
                    enabled = state.inputEnabled,
                    generating = state.generating,
                    onSend = {
                        val text = field.text.toString()
                        field.edit { replace(0, length, "") }
                        actions.onSend(text)
                    },
                    onStop = actions.onStop,
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 80.dp),
        ) { data ->
            SkimSnackbar(message = data.visuals.message, actionLabel = data.visuals.actionLabel, isError = false, onAction = {})
        }
    }
}

@Composable
private fun AskPageHeader(title: String, host: String, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SunnyMark(size = 44.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(host, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurfaceVariant),
        ) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_close))
        }
    }
}

@Composable
private fun SummaryBlock(summary: SummaryState, actions: AskPageActions) {
    val notice = SummaryNotice(summary, actions.onRetry)
    if (notice != null) {
        Column { notice() }
        return
    }
    when (summary) {
        SummaryState.Generating -> GeneratingCard()
        is SummaryState.Streaming -> SummaryTextCard(summaryBullets(summary.text))
        is SummaryState.Done -> SummaryTextCard(
            lines = summary.bullets,
            actions = actions,
            feedback = summary.feedback,
        )
        is SummaryState.Interrupted -> InterruptedCard(summary.text, actions.onRetry)
        else -> Unit
    }
}

@Composable
private fun SummaryTextCard(
    lines: List<String>,
    actions: AskPageActions? = null,
    feedback: MessageFeedback = MessageFeedback.NONE,
) {
    AnswerCard {
        SummaryLabel()
        SummaryBullets(lines)
        if (actions != null) {
            FeedbackRow(actions, feedback)
        }
    }
}

@Composable
private fun FeedbackRow(actions: AskPageActions, feedback: MessageFeedback) {
    Row(
        Modifier
            .offset(x = -(8.dp + ButtonFrameOverhang))
            .drawnHeight(ButtonFrameOverhang),
        horizontalArrangement = Arrangement.spacedBy(-ButtonFrameOverhang),
    ) {
        val colors = MaterialTheme.colorScheme
        CompactIconButton(R.drawable.ic_content_copy, stringResource(R.string.cd_copy_summary), onClick = actions.onCopy)
        CompactIconButton(
            R.drawable.ic_thumb_up,
            stringResource(R.string.cd_good_summary),
            onClick = actions.onGood,
            modifier = selectedFeedback(feedback == MessageFeedback.GOOD, colors.secondaryContainer),
        )
        CompactIconButton(
            R.drawable.ic_thumb_down,
            stringResource(R.string.cd_poor_summary),
            onClick = actions.onPoor,
            modifier = selectedFeedback(feedback == MessageFeedback.POOR, colors.secondaryContainer),
        )
    }
}

@Composable
private fun InterruptedCard(text: String, onRetry: () -> Unit) {
    AnswerCard(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryLabel()
        val lines = summaryBullets(text)
        if (lines.isNotEmpty()) SummaryBullets(lines)
        SkimButton(
            text = stringResource(R.string.action_try_again),
            onClick = onRetry,
            modifier = Modifier.align(Alignment.End),
            style = SkimButtonStyle.Tonal,
        )
    }
}

@Composable
private fun SuggestedQuestions(onChip: (String) -> Unit) {
    val keyNumbers = stringResource(R.string.ai_chip_key_numbers)
    val explain = stringResource(R.string.ai_chip_explain_simply)
    val catch = stringResource(R.string.ai_chip_the_catch)
    FlowRow(Modifier.drawnHeight(ChipFrameOverhang), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AiSuggestionChip(keyNumbers, onClick = { onChip(keyNumbers) })
        AiSuggestionChip(explain, onClick = { onChip(explain) })
        AiSuggestionChip(catch, onClick = { onChip(catch) })
    }
}

@Composable
private fun QuestionBlock(turn: QaTurn, state: AskPageUiState, actions: AskPageActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UserBubble(turn.question)
        if (turn.answer != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnswerText(turn.answer)
                turn.paragraphs.forEach { paragraph ->
                    CitationChip(
                        paragraph = paragraph,
                        enabled = state.citationsEnabled,
                        onClick = { actions.onCitation(paragraph) },
                        modifier = Modifier.drawnHeight(ChipFrameOverhang),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnswerProblem(error: AnswerError?, onRetry: () -> Unit) {
    if (error == null) return
    when (error) {
        AnswerError.Server -> AiNoticeCard(
            icon = R.drawable.ic_error,
            title = stringResource(R.string.ai_server_error_title),
            body = stringResource(R.string.ai_server_error_body),
            onRetry = onRetry,
        )
        AnswerError.Offline -> AiNoticeCard(
            icon = R.drawable.ic_wifi_off,
            title = stringResource(R.string.ai_offline_title),
            body = stringResource(R.string.ai_offline_body),
            onRetry = onRetry,
        )
        AnswerError.Refused -> AiNoticeCard(
            icon = R.drawable.ic_info,
            title = stringResource(R.string.ai_refusal_title),
            body = stringResource(R.string.ai_refusal_body),
        )
    }
}

/** The 48 dp touch frame of a 40 dp button reaches 4 dp past it on each side. */
private fun selectedFeedback(selected: Boolean, color: androidx.compose.ui.graphics.Color): Modifier =
    if (selected) Modifier.background(color, CircleShape) else Modifier

private val ButtonFrameOverhang = 4.dp

/** The 48 dp touch frame of a 32 dp chip reaches 8 dp past it on each side. */
private val ChipFrameOverhang = 8.dp

/**
 * Lays out content [overhang] shorter at the top and the bottom, so a row of buttons with 48 dp
 * touch frames takes the space of their drawn shapes.
 */
private fun Modifier.drawnHeight(overhang: Dp): Modifier = layout { measurable, constraints ->
    val inset = overhang.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = 2 * inset))
    layout(placeable.width, placeable.height - 2 * inset) { placeable.place(0, -inset) }
}
