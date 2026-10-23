package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 */
@Composable
fun SkimSnackbarHost(
    controller: SnackbarController,
    placement: SnackbarPlacement,
    onAction: (SnackbarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostState = remember { SnackbarHostState() }
    val message by controller.current.collectAsStateWithLifecycle()
    val currentOnAction by rememberUpdatedState(onAction)
    message?.let { shown ->
        val text = shown.text.asString()
        val actionLabel = shown.action?.let { stringResource(it.labelRes()) }
        LaunchedEffect(shown.id) {
            val result = hostState.showSnackbar(SkimSnackbarVisuals(text, actionLabel, shown.isError))
            if (result == SnackbarResult.ActionPerformed) {
                try {
                    shown.action?.takeUnless { it is SnackbarAction.Undo }?.let(currentOnAction)
                } finally {
                    controller.onActionClicked(shown.id)
                }
            } else {
                controller.onDismissed(shown.id)
            }
        }
        // Leaving the composition cancels showSnackbar without a result: report it as a close.
        // When the message was replaced or already handled, the controller ignores the stale id.
        DisposableEffect(shown.id) {
            onDispose { controller.onDismissed(shown.id) }
        }
    }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
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
