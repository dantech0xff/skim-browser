package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A single-choice connected button group (09, 09b, 08b): the [options] share the width, 40 dp high
 * and 2 dp apart. The [selected] option is a full pill in primary with a check, its label in
 * labelLargeEmphasized; the others are surfaceContainer, 20 dp round on the outer side and 8 dp
 * inside. One option is always selected, so tapping the selected one does nothing.
 *
 * Each button keeps a 48 dp touch target: the group lays out 48 dp high with the 40 dp buttons
 * centered, 4 dp below its top.
 *
 * Accessibility reads the options as radio buttons in a group labelled [contentDescription] ("Tab
 * list", "Font").
 *
 * @param fontFamilies the font of each option's label, by position; null keeps the app font. The
 *   Font group of 08b shows Serif in a serif face.
 */
@Composable
fun ConnectedToggleGroup(
    options: ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fontFamilies: ImmutableList<FontFamily?> = persistentListOf(),
) {
    require(options.size >= 2) { "A connected group needs at least two options, got ${options.size}." }
    require(selected in options.indices) { "Option $selected is not one of the ${options.size} options." }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val buttonColors = ToggleButtonDefaults.colors(
        containerColor = colors.surfaceContainer,
        contentColor = colors.onSurfaceVariant,
        checkedContainerColor = colors.primary,
        checkedContentColor = colors.onPrimary,
    )
    val leadingShapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val middleShapes = ButtonGroupDefaults.connectedMiddleButtonShapes()
    val trailingShapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
    Row(
        modifier = modifier
            .selectableGroup()
            .semantics {
                isTraversalGroup = true
                this.contentDescription = contentDescription
            },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val checked = index == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { nowChecked -> if (nowChecked) onSelect(index) },
                // Outside the button's own Checkbox role, so this one wins: the options are exclusive.
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                icon = if (checked) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
                shapes = when (index) {
                    0 -> leadingShapes
                    options.lastIndex -> trailingShapes
                    else -> middleShapes
                },
                colors = buttonColors,
            ) {
                Text(
                    text = label,
                    style = if (checked) typography.labelLargeEmphasized else typography.labelLarge,
                    fontFamily = fontFamilies.getOrNull(index),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ConnectedToggleGroupPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        var tabList by remember { mutableIntStateOf(0) }
        var font by remember { mutableIntStateOf(0) }
        Column(
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectedToggleGroup(
                options = persistentListOf(stringResource(R.string.tabs_segment_open), stringResource(R.string.tabs_segment_incognito)),
                selected = tabList,
                onSelect = { tabList = it },
                contentDescription = stringResource(R.string.cd_tab_list),
            )
            ConnectedToggleGroup(
                options = persistentListOf(stringResource(R.string.reader_font_sans_serif), stringResource(R.string.reader_font_serif)),
                selected = font,
                onSelect = { font = it },
                contentDescription = stringResource(R.string.reader_font),
                fontFamilies = persistentListOf(null, FontFamily.Serif),
            )
        }
    }
}
