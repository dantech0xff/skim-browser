package com.dantech.skimbrowser.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.db.entity.MessageFeedback
import com.dantech.skimbrowser.system.Clipboard
import com.dantech.skimbrowser.ui.components.AiLoadingIndicator
import com.dantech.skimbrowser.ui.components.AiSuggestionChip
import com.dantech.skimbrowser.ui.components.AiWavyProgress
import com.dantech.skimbrowser.ui.components.AnswerCard
import com.dantech.skimbrowser.ui.components.AnswerText
import com.dantech.skimbrowser.ui.components.ChatInputPill
import com.dantech.skimbrowser.ui.components.CompactIconButton
import com.dantech.skimbrowser.ui.components.SkimSnackbar
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.components.UserBubble
import com.dantech.skimbrowser.ui.navigation.AskTabsKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.coroutines.launch

/** What the across-tabs sheet can ask the screen to do. */
data class AskTabsActions(
    val onClose: () -> Unit,
    val onSend: (String) -> Unit,
    val onStop: () -> Unit,
    val onChip: (String) -> Unit,
    val onRetry: () -> Unit,
    val onCopy: () -> Unit,
    val onGood: () -> Unit,
    val onPoor: () -> Unit,
    val onOpenGroup: (String) -> Unit,
)

/** Ask Skim about the open tabs (06b), for [key]'s prompt or a saved chat. */
@Composable
fun AskTabsRoute(key: AskTabsKey, container: AppContainer, navigator: SkimNavigator) {
    val model = viewModel(key = "ask-tabs-${key.prompt}-${key.savedChatId}") {
        AskTabsViewModel(
            key = key,
            ai = container.skimAi,
            sessions = container.chatSessions,
            tabs = container.tabManager,
            sites = container.sitePermissionRepository,
            network = container.networkMonitor,
            savedState = createSavedStateHandle(),
            appScope = container.appScope,
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.snackbar_copied)
    AskTabsSheet(
        state = state,
        snackbar = snackbar,
        actions = AskTabsActions(
            onClose = navigator::back,
            onSend = model::ask,
            onStop = model::stop,
            onChip = model::ask,
            onRetry = model::retry,
            onCopy = {
                val text = tabsAnswerText(model.state.value)
                if (text.isNotEmpty() && Clipboard.copy(context, text, sensitive = false)) {
                    scope.launch { snackbar.showSnackbar(copied) }
                }
            },
            onGood = { model.feedback(good = true) },
            onPoor = { model.feedback(good = false) },
            onOpenGroup = { tabId ->
                navigator.back()
                navigator.openTabs(incognito = false, focusTabId = tabId)
            },
        ),
    )
}

/**
 * Ask Skim about the open tabs (06b). The header is Ask Skim and how many tabs are being read,
 * then the answer, the suggested questions, the follow-ups, and the input. "Copied to clipboard"
 * uses [snackbar], which stays inside the sheet.
 *
 * [bodyHeight] fixes the scrolling region so a screenshot can match the board. On screen the region
 * fills the sheet.
 */
@Composable
fun AskTabsSheet(
    state: AskTabsUiState,
    actions: AskTabsActions,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    field: TextFieldState = rememberTextFieldState(),
    bodyHeight: Dp? = null,
) {
    Box(modifier.fillMaxWidth().then(if (bodyHeight == null) Modifier.fillMaxHeight() else Modifier)) {
        Column(if (bodyHeight == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            AskTabsHeader(state.tabCount, state.showSubtitle, actions.onClose)
            Column(
                Modifier
                    .then(if (bodyHeight != null) Modifier.height(bodyHeight) else Modifier.weight(1f))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.prompt?.let { UserBubble(it) }
                OverviewBlock(state.overview, actions)
                val ready = state.overview as? TabsOverviewState.Ready
                if (ready != null && !state.generating && ready.followUps.isNotEmpty()) {
                    FollowUps(ready.followUps, actions.onChip)
                }
                state.turns.forEach { turn -> FollowUpBlock(turn) }
                if (state.generating && state.overview is TabsOverviewState.Ready && state.turns.lastOrNull()?.answer == null) {
                    AiLoadingIndicator(stringResource(R.string.cd_writing_summary))
                }
                AnswerProblem(state.answerError, actions.onRetry)
            }
            Box(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
                ChatInputPill(
                    state = field,
                    placeholder = stringResource(R.string.ai_ask_anything),
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
private fun AskTabsHeader(tabCount: Int, showSubtitle: Boolean, onClose: () -> Unit) {
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
                stringResource(R.string.ask_skim),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showSubtitle) {
                Text(
                    pluralStringResource(R.plurals.ai_reading_tabs, tabCount, tabCount),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
private fun OverviewBlock(overview: TabsOverviewState, actions: AskTabsActions) {
    when (overview) {
        TabsOverviewState.Idle -> Unit
        TabsOverviewState.Generating -> GeneratingOverview()
        is TabsOverviewState.Ready -> OverviewCard(overview, actions)
        TabsOverviewState.Offline -> AiNoticeCard(
            icon = R.drawable.ic_wifi_off,
            title = stringResource(R.string.ai_offline_title),
            body = stringResource(R.string.ai_offline_body),
            onRetry = actions.onRetry,
        )
        TabsOverviewState.ServerError -> AiNoticeCard(
            icon = R.drawable.ic_error,
            title = stringResource(R.string.ai_server_error_title),
            body = stringResource(R.string.ai_server_error_body),
            onRetry = actions.onRetry,
        )
        TabsOverviewState.Refused -> AiNoticeCard(
            icon = R.drawable.ic_info,
            title = stringResource(R.string.ai_refusal_title),
            body = stringResource(R.string.ai_refusal_body),
        )
    }
}

@Composable
private fun GeneratingOverview() {
    AnswerCard(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AiLoadingIndicator(stringResource(R.string.cd_writing_summary))
        AiWavyProgress()
    }
}

@Composable
private fun OverviewCard(overview: TabsOverviewState.Ready, actions: AskTabsActions) {
    AnswerCard(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(overview.intro, style = MaterialTheme.typography.bodyMedium)
        overview.groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupLink(group.name, onClick = group.firstTabId?.let { id -> { actions.onOpenGroup(id) } })
                Text(group.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OverviewFeedback(actions, overview.feedback)
    }
}

@Composable
private fun GroupLink(name: String, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = name, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = colors.primary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.primary,
        )
    }
}

@Composable
private fun OverviewFeedback(actions: AskTabsActions, feedback: MessageFeedback) {
    Row(
        Modifier
            .offset(x = -(8.dp + ButtonFrameOverhang))
            .drawnHeight(ButtonFrameOverhang),
        horizontalArrangement = Arrangement.spacedBy(-ButtonFrameOverhang),
    ) {
        val colors = MaterialTheme.colorScheme
        CompactIconButton(R.drawable.ic_content_copy, stringResource(R.string.cd_copy_answer), onClick = actions.onCopy)
        CompactIconButton(
            R.drawable.ic_thumb_up,
            stringResource(R.string.cd_good_answer),
            onClick = actions.onGood,
            modifier = selectedFeedback(feedback == MessageFeedback.GOOD, colors.secondaryContainer),
        )
        CompactIconButton(
            R.drawable.ic_thumb_down,
            stringResource(R.string.cd_poor_answer),
            onClick = actions.onPoor,
            modifier = selectedFeedback(feedback == MessageFeedback.POOR, colors.secondaryContainer),
        )
    }
}

@Composable
private fun FollowUps(questions: List<String>, onChip: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEach { question ->
            AiSuggestionChip(question, onClick = { onChip(question) })
        }
    }
}

@Composable
private fun FollowUpBlock(turn: TabsAnswer) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UserBubble(turn.question)
        if (turn.answer != null) AnswerText(turn.answer)
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

private fun selectedFeedback(selected: Boolean, color: androidx.compose.ui.graphics.Color): Modifier =
    if (selected) Modifier.background(color, CircleShape) else Modifier

private val ButtonFrameOverhang = 4.dp

/** Lays out a row of 48 dp touch frames in the space of the 40 dp buttons. */
private fun Modifier.drawnHeight(overhang: Dp): Modifier = layout { measurable, constraints ->
    val inset = overhang.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = 2 * inset))
    layout(placeable.width, (placeable.height - 2 * inset).coerceAtLeast(0)) { placeable.place(0, -inset) }
}
