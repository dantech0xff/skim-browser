package com.dantech.skimbrowser.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.MenuEntry
import com.dantech.skimbrowser.ui.components.SkimMenu
import kotlinx.collections.immutable.persistentListOf

/**
 * Group options (15): Rename group and Ungroup, then Close N tabs on its own. Choosing an item
 * runs it and closes the menu. Anchored to the layout it is placed in, so put it in the options
 * button. [anchorContentDescription] is the group's name plus "group".
 */
@Composable
fun TabGroupMenu(
    expanded: Boolean,
    groupTitle: String,
    tabCount: Int,
    onRename: () -> Unit,
    onUngroup: () -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
) {
    SkimMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        groups = persistentListOf(
            persistentListOf(
                MenuEntry(stringResource(R.string.rename_group), R.drawable.ic_drive_file_rename_outline, onRename),
                MenuEntry(stringResource(R.string.tab_group_ungroup), R.drawable.ic_layers_clear, onUngroup),
            ),
            persistentListOf(
                MenuEntry(
                    pluralStringResource(R.plurals.tab_group_close_tabs, tabCount, tabCount),
                    R.drawable.ic_close,
                    onClose,
                ),
            ),
        ),
        anchorContentDescription = stringResource(R.string.cd_tab_group_menu, groupTitle),
    )
}
