package com.dantech.skimbrowser.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.MenuEntry
import com.dantech.skimbrowser.ui.components.SkimMenu

/**
 * The menu of a shortcut on Home (15), opened by a long press: Edit shortcut, then Remove in a group
 * of its own. Choosing either closes it.
 *
 * Place it in the box that holds the shortcut's tile and name. As drawn, it opens 8 dp in from the
 * start of that cell and 8 dp under the name; with no room below it opens above the cell instead.
 *
 * @param name the shortcut's name, announced with the menu ("Wikipedia shortcut").
 */
@Composable
fun BoxScope.ShortcutMenu(
    name: String,
    expanded: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The menu anchors to the layout it is placed in: this box reaches past the cell by the gap.
    Box(Modifier.matchParentSize().menuAnchor()) {
        SkimMenu(
            expanded = expanded,
            onDismiss = onDismiss,
            groups = listOf(
                listOf(MenuEntry(stringResource(R.string.shortcut_menu_edit), R.drawable.ic_edit, onEdit)),
                listOf(MenuEntry(stringResource(R.string.action_remove), R.drawable.ic_delete, onRemove)),
            ),
            anchorContentDescription = stringResource(R.string.cd_shortcut_menu, name),
        )
    }
}

/**
 * Lays the anchor out [MenuGap] in from the cell's start and [MenuGap] past its bottom, while it
 * takes no more room than the cell: the menu under it then sits where 15 draws it, and the menu
 * above it (no room below) still clears the tile.
 */
private fun Modifier.menuAnchor(): Modifier = layout { measurable, constraints ->
    val gap = MenuGap.roundToPx()
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed((width - gap).coerceAtLeast(0), height + gap))
    layout(width, height) { placeable.placeRelative(gap, 0) }
}

private val MenuGap = 8.dp
