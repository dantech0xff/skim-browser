package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.SnackbarPlacement
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.snackbar.labelRes
import com.dantech.skimbrowser.ui.text.UiText
import com.dantech.skimbrowser.ui.text.asString
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

/**
 * Shows the message of [controller]: inverseSurface, radius 4, at least 48 high, the action in
 * inversePrimary; 10 s with an action and 4 s without (Material's Long and Short, which the system
 * lengthens for users who asked for more time). Only the shell's window has one.
 *
 * The host reports every close to the controller, including leaving the composition while a
 * message is on screen, so an Undo that was never tapped is always finalized.
 *
 * Place it at the bottom center of the screen; it pads the navigation bar inset and [placement]
 * itself, and keeps 16 dp from the sides.
 *
 * @param onAction carries out every action except [SnackbarAction.Undo], which the controller
 *   runs: switch to a tab, open Edit bookmark, open a download, open the imported folder.
 * @param onClosed runs when a message goes away without its action. A held popup uses it to cancel.
 */
@Composable
fun SkimSnackbarHost(
    controller: SnackbarController,
    placement: SnackbarPlacement,
    onAction: (SnackbarAction) -> Unit,
    modifier: Modifier = Modifier,
    onClosed: (SnackbarAction?) -> Unit = {},
) {
    val hostState = remember { SnackbarHostState() }
    val message by controller.current.collectAsStateWithLifecycle()
    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnClosed by rememberUpdatedState(onClosed)
    val shown = message
    Box(modifier) {
    if (shown != null && shown.secondary != null) {
        TwoActionNotice(
            message = shown,
            onAction = currentOnAction,
            onClosed = currentOnClosed,
            controller = controller,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = placement.bottomOffset),
        )
    }
    shown?.takeIf { it.secondary == null }?.let { single ->
        val text = single.text.asString()
        val actionLabel = single.action?.let { stringResource(it.labelRes()) }
        val settled = remember(single.id) { object { var done = false } }
        LaunchedEffect(single.id) {
            val result = hostState.showSnackbar(SkimSnackbarVisuals(text, actionLabel, single.isError))
            settled.done = true
            if (result == SnackbarResult.ActionPerformed) {
                try {
                    single.action?.takeUnless { it is SnackbarAction.Undo }?.let(currentOnAction)
                } finally {
                    controller.onActionClicked(single.id)
                }
            } else {
                controller.onDismissed(single.id)
                currentOnClosed(single.action)
            }
        }
        // Leaving the composition cancels showSnackbar without a result. A held popup still has to
        // be dropped. When the message was replaced or already handled, the controller ignores the stale id.
        DisposableEffect(single.id) {
            onDispose {
                controller.onDismissed(single.id)
                if (!settled.done) {
                    settled.done = true
                    currentOnClosed(single.action)
                }
            }
        }
    }
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = placement.bottomOffset),
    ) { data ->
        SkimSnackbar(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            isError = (data.visuals as? SkimSnackbarVisuals)?.isError == true,
            onAction = data::performAction,
        )
    }
    }
}

@Composable
private fun TwoActionNotice(
    message: SnackbarController.Message,
    onAction: (SnackbarAction) -> Unit,
    onClosed: (SnackbarAction?) -> Unit,
    controller: SnackbarController,
    modifier: Modifier = Modifier,
) {
    var acted by remember(message.id) { mutableStateOf(false) }
    val text = message.text.asString()
    val primaryAction = message.action
    val secondaryAction = message.secondary
    LaunchedEffect(message.id) {
        delay(10_000)
        if (!acted) controller.onDismissed(message.id)
    }
    DisposableEffect(message.id) {
        onDispose {
            if (!acted) {
                controller.onDismissed(message.id)
                onClosed(message.action)
            }
        }
    }
    Snackbar(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primaryAction != null) {
                    SnackbarActionButton(stringResource(primaryAction.labelRes())) {
                        acted = true
                        onAction(primaryAction)
                        controller.onActionClicked(message.id)
                    }
                }
                if (secondaryAction != null) {
                    SnackbarActionButton(stringResource(secondaryAction.labelRes())) {
                        acted = true
                        onAction(secondaryAction)
                        controller.onActionClicked(message.id)
                    }
                }
            }
        }
    }
}

/** The duration Material gives a snackbar: Long (10 s) when it has an action, Short (4 s) otherwise. */
internal fun snackbarDuration(hasAction: Boolean): SnackbarDuration =
    if (hasAction) SnackbarDuration.Long else SnackbarDuration.Short

private class SkimSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val isError: Boolean,
) : SnackbarVisuals {
    override val withDismissAction: Boolean get() = false
    override val duration: SnackbarDuration get() = snackbarDuration(actionLabel != null)
}

/**
 * One snackbar as drawn on 15. With an action the text stays on one line and ends with an ellipsis
 * (a long file name); without one it wraps, and an error of two lines is 68 high. An error is a live
 * region that interrupts, like role=alert.
 */
@Composable
internal fun SkimSnackbar(
    message: String,
    actionLabel: String?,
    isError: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = if (isError) modifier.semantics { liveRegion = LiveRegionMode.Assertive } else modifier,
        action = actionLabel?.let { label -> { SnackbarActionButton(label, onAction) } },
    ) {
        if (actionLabel != null) {
            Text(message, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(message)
        }
    }
}

/** The action is drawn 36 high with 8 on each side; its touch target is still 48. */
@Composable
private fun SnackbarActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label)
    }
}

@PreviewLightDark
@Composable
private fun SkimSnackbarPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(
            Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SkimSnackbar(stringResource(R.string.snackbar_tab_closed), stringResource(R.string.action_undo), false, {})
            SkimSnackbar(stringResource(R.string.snackbar_copied), null, false, {})
            SkimSnackbar(stringResource(R.string.snackbar_import_failed), null, true, {})
        }
    }
}

@PreviewLightDark
@Composable
private fun SkimSnackbarHostPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        val controller = remember {
            SnackbarController(UndoRegistry(CoroutineScope(Dispatchers.Unconfined))).apply { show(UiText.Res(R.string.snackbar_bookmark_added), SnackbarAction.EditBookmark(1)) }
        }
        Box(Modifier.background(MaterialTheme.colorScheme.surface).height(160.dp)) {
            SkimSnackbarHost(controller, SnackbarPlacement.Bottom, onAction = {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}
