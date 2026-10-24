package com.dantech.skimbrowser.ui.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.SkimDialogContent

/** Delete all AI data (15). Delete is the one confirm drawn in error. The caller closes the dialog. */
@Composable
fun DeleteAiDataDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkimDialogContent(
        title = stringResource(R.string.delete_ai_data_title),
        confirm = DialogAction(stringResource(R.string.action_delete), onDelete),
        dismiss = DialogAction(stringResource(R.string.action_cancel), onDismiss),
        destructiveConfirm = true,
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        iconTint = MaterialTheme.colorScheme.error,
        modifier = modifier,
        text = { Text(stringResource(R.string.delete_ai_data_body)) },
    )
}
