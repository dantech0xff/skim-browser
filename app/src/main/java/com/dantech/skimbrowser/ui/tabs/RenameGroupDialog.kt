package com.dantech.skimbrowser.ui.tabs

import com.dantech.skimbrowser.ui.navigation.rememberShell
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.flowOf
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.SkimDialogContent
import com.dantech.skimbrowser.ui.components.SkimTextField
import com.dantech.skimbrowser.ui.navigation.RenameGroupKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator

/**
 * Rename group (15). The field is focused as the dialog opens. Save stays off while the name is
 * empty or only spaces; saving trims the name and closes. A group that is already gone closes the
 * dialog.
 */
@Composable
fun RenameGroupRoute(key: RenameGroupKey, navigator: SkimNavigator) {
    val tabs by rememberShell().tabs().state.collectAsStateWithLifecycle()
    val group = tabs.groups.firstOrNull { it.id == key.groupId }
    val model = hiltViewModel<TabsViewModel, TabsViewModel.Factory> { factory ->
        factory.create(incognito = false, grouping = flowOf(null), grouper = null)
    }
    LaunchedEffect(group) {
        if (group == null) navigator.back()
    }
    if (group == null) return
    RenameGroupDialog(
        initialName = group.title,
        onSave = { title ->
            model.rename(group.id, title)
            navigator.back()
        },
        onDismiss = navigator::back,
    )
}

@Composable
fun RenameGroupDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState(initialName)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val trimmed = name.text.toString().trim()
    SkimDialogContent(
        title = stringResource(R.string.rename_group),
        confirm = DialogAction(
            label = stringResource(R.string.action_save),
            onClick = { onSave(name.text.toString()) },
            enabled = trimmed.isNotEmpty(),
        ),
        dismiss = DialogAction(stringResource(R.string.action_cancel), onDismiss),
        text = {
            SkimTextField(
                state = name,
                label = stringResource(R.string.field_group_name),
                focusRequester = focus,
            )
        },
    )
}
