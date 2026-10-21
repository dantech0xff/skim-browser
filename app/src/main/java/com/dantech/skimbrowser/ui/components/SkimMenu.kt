package com.dantech.skimbrowser.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/** An item of a [SkimMenu]: its [label], a 24 dp leading [icon], and what choosing it does. */
data class MenuEntry(val label: String, @DrawableRes val icon: Int, val onClick: () -> Unit)

/**
 * A menu as drawn on 15 (shortcut, tab group and bookmark menus), anchored below the control that
 * opened it: it anchors to the layout it is placed in, so put it in a Box with just that control.
 * It is 220 dp wide; each of [groups] is a
 * surfaceContainer group with radius 16 and 4 dp of padding, 2 dp from the next. Items are 48 dp
 * high with radius 12: a 24 dp icon in onSurfaceVariant, 12 dp, then the label in labelLarge.
 *
 * A destructive item (Remove, Close N tabs, Delete) gets a group of its own, last, and keeps the
 * onSurface label of the other items, as drawn.
 *
 * Choosing an item runs its action, then [onDismiss]; a tap outside the menu and Back also call
 * [onDismiss]. The caller keeps [expanded].
 *
 * @param anchorContentDescription what the menu is for, announced when it opens: "Wikipedia
 *   shortcut", "Heat pump research group", "Bookmark options".
 */
@Composable
fun SkimMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    groups: List<List<MenuEntry>>,
    anchorContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val itemColors = MenuDefaults.itemColors(textColor = colors.onSurface, leadingIconColor = colors.onSurfaceVariant)
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
            .width(MenuWidth)
            .semantics { paneTitle = anchorContentDescription },
    ) {
        groups.forEachIndexed { groupIndex, entries ->
            if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            // Items keep 4 dp from the sides of their group, so the group pads only top and bottom.
            DropdownMenuGroup(
                shapes = GroupShapes,
                containerColor = colors.surfaceContainer,
                contentPadding = PaddingValues(vertical = GroupPadding),
            ) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        onClick = {
                            entry.onClick()
                            onDismiss()
                        },
                        text = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = ItemShape,
                        modifier = Modifier.heightIn(min = ItemHeight),
                        leadingIcon = {
                            Icon(painterResource(entry.icon), contentDescription = null, Modifier.size(IconSize))
                        },
                        colors = itemColors,
                        horizontalArrangement = Arrangement.spacedBy(IconGap),
                        contentPadding = PaddingValues(horizontal = ItemPadding),
                    )
                }
            }
        }
    }
}

private val MenuWidth = 220.dp

/**
 * Every group is round on all four corners, as drawn; material3's groupShape(index, count) rounds
 * only the outer corners of the first and last group (16 dp) and the inner ones to 8 dp.
 */
private val GroupShapes = MenuGroupShapes(shape = RoundedCornerShape(16.dp), inactiveShape = RoundedCornerShape(16.dp))
private val GroupPadding = 4.dp

/** Round on all four corners, as drawn; material3's itemShape(index, count) is 12 dp outside, 4 dp inside. */
private val ItemShape = RoundedCornerShape(12.dp)
private val ItemHeight = 48.dp
private val ItemPadding = 12.dp
private val IconSize = 24.dp
private val IconGap = 12.dp

@PreviewLightDark
@Composable
private fun SkimMenuPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            // The menu anchors to the parent it is placed in: a Box holding just the control.
            Box {
                CompactIconButton(R.drawable.ic_more_vert, stringResource(R.string.cd_group_options), onClick = {}, iconSize = 24.dp)
                SkimMenu(
                    expanded = true,
                    onDismiss = {},
                    groups = listOf(
                        listOf(
                            MenuEntry(stringResource(R.string.rename_group), R.drawable.ic_drive_file_rename_outline) {},
                            MenuEntry(stringResource(R.string.tab_group_ungroup), R.drawable.ic_layers_clear) {},
                        ),
                        listOf(MenuEntry(pluralStringResource(R.plurals.tab_group_close_tabs, 4, 4), R.drawable.ic_close) {}),
                    ),
                    anchorContentDescription = stringResource(R.string.cd_tab_group_menu, "Heat pump research"),
                )
            }
        }
    }
}
