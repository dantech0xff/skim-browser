package com.dantech.skimbrowser.ui.skimhistory

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.SkimDialogContent
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.coroutines.launch

/** Clear Skim history. Clear is the primary action. Sites the person allowed stay allowed. */
@Composable
fun ClearSkimHistoryDialog(
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkimDialogContent(
        title = stringResource(R.string.clear_skim_history_title),
        confirm = DialogAction(stringResource(R.string.action_clear), onClear),
        dismiss = DialogAction(stringResource(R.string.action_cancel), onDismiss),
        modifier = modifier,
        text = { Text(stringResource(R.string.clear_skim_history_body)) },
    )
}

/** Clears every saved chat, then closes. */
@Composable
fun ClearSkimHistoryRoute(container: AppContainer, navigator: SkimNavigator) {
    val scope = rememberCoroutineScope()
    ClearSkimHistoryDialog(
        onClear = {
            scope.launch {
                container.aiDataEraser.clearSkimHistory()
                navigator.back()
            }
        },
        onDismiss = navigator::back,
    )
}
