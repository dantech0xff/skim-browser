package com.dantech.skimbrowser.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.SkimDialogContent
import com.dantech.skimbrowser.ui.components.SkimTextField
import com.dantech.skimbrowser.ui.navigation.ShortcutDialogKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator

/**
 * The window of the shortcut dialog destination: the navigation's dialog scene opens it with these
 * properties, and the dialog draws its own 344 dp surface in it.
 */
val ShortcutDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

/**
 * The [ShortcutDialogKey] destination: Add shortcut, or Edit shortcut for its shortcut. Its view
 * model lives in the destination's own store, gone once the dialog closes; Cancel and a saved
 * shortcut take the key off the back stack.
 */
@Composable
fun ShortcutDialogRoute(key: ShortcutDialogKey, container: AppContainer, navigator: SkimNavigator) {
    val dialog = viewModel {
        ShortcutDialogViewModel(
            key.shortcutId,
            container.shortcutRepository,
            container.addShortcut,
            container.updateShortcut,
            container.appScope,
        )
    }
    ShortcutDialog(dialog, onClose = { navigator.remove(key) })
}

/**
 * Add shortcut, or Edit shortcut filled in from the shortcut (15): Name and URL, the URL focused
 * once the fields are ready, then Cancel and Save. Save is on only while the URL is a web address;
 * it also runs from the keyboard's Done on the URL. [onClose] runs on Cancel and once the shortcut
 * is saved (or the one being edited turns out to be gone).
 */
@Composable
fun ShortcutDialog(dialog: ShortcutDialogViewModel, onClose: () -> Unit) {
    val state by dialog.state.collectAsStateWithLifecycle()
    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(state.done) { if (state.done) onClose() }
    LaunchedEffect(state.loaded) { if (state.loaded) urlFocus.requestFocus() }
    SkimDialogContent(
        title = stringResource(if (state.editing) R.string.shortcut_menu_edit else R.string.dialog_add_shortcut_title),
        confirm = DialogAction(stringResource(R.string.action_save), onClick = dialog::save, enabled = state.canSave),
        dismiss = DialogAction(stringResource(R.string.action_cancel), onClick = onClose),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SkimTextField(
                    state = dialog.name,
                    label = stringResource(R.string.field_name),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                SkimTextField(
                    state = dialog.url,
                    label = stringResource(R.string.field_url),
                    focusRequester = urlFocus,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { dialog.save() },
                )
            }
        },
    )
}
