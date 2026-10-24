package com.dantech.skimbrowser.ui.skimhistory

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

/** Clears every saved chat, then closes. Leaving the dialog does not cancel the delete. */
@Composable
fun ClearSkimHistoryRoute(navigator: SkimNavigator) {
    val model = hiltViewModel<ClearSkimHistoryViewModel>()
    val scope = rememberCoroutineScope()
    ClearSkimHistoryDialog(
        onClear = {
            val deleting = model.clear()
            scope.launch {
                deleting.join()
                navigator.back()
            }
        },
        onDismiss = navigator::back,
    )
}
