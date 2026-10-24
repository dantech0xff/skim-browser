package com.dantech.skimbrowser.ui.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.MenuEntry
import com.dantech.skimbrowser.ui.components.SkimMenu
import kotlinx.collections.immutable.persistentListOf

/**
 * Bookmark options (15): Edit and Move to folder, then Delete on its own. Choosing an item runs
 * it and closes the menu. Anchored to the layout it is placed in, so put it in the options button.
 */
@Composable
fun BookmarkMenu(
    expanded: Boolean,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    SkimMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        groups = persistentListOf(
            persistentListOf(
                MenuEntry(stringResource(R.string.action_edit), R.drawable.ic_edit, onEdit),
                MenuEntry(stringResource(R.string.move_to_folder), R.drawable.ic_drive_file_move, onMove),
            ),
            persistentListOf(MenuEntry(stringResource(R.string.action_delete), R.drawable.ic_delete, onDelete)),
        ),
        anchorContentDescription = stringResource(R.string.cd_bookmark_options),
    )
}
