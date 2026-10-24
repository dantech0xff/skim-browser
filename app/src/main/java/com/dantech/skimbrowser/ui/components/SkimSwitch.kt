package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * The design's switch (10, site information on 15), 52 by 32. On: a primary track and a 24 dp
 * onPrimary thumb holding a check in primary. Off: a surfaceContainerHighest track with a 2 dp
 * outline border and a 16 dp outline thumb.
 *
 * In a row whose whole surface is the switch, pass a null [onCheckedChange] and put
 * [toggleableRow] on the row: the switch is then only drawn, and the row is the one control that
 * accessibility reads, with the row's text as its label.
 */
@Composable
fun SkimSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = if (checked) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
        enabled = enabled,
        colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
    )
}

/**
 * Makes the whole row a switch (the 72 dp rows of 10): tapping anywhere on it calls
 * [onCheckedChange] with the opposite of [checked], and accessibility reads one Switch labelled with
 * the row's text. Pair it with a [SkimSwitch] whose onCheckedChange is null.
 */
fun Modifier.toggleableRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
): Modifier = toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)

@PreviewLightDark
@Composable
private fun SkimSwitchPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        val colors = MaterialTheme.colorScheme
        var askFirst by remember { mutableStateOf(true) }
        var keepHistory by remember { mutableStateOf(false) }
        Column(Modifier.background(colors.surfaceContainerLow).padding(vertical = 16.dp)) {
            SegmentedColumn {
                SkimListItem(
                    headline = stringResource(R.string.ai_settings_ask_before_sending),
                    supporting = stringResource(R.string.ai_settings_ask_before_sending_summary),
                    index = 0,
                    count = 2,
                    onClick = null,
                    modifier = Modifier.toggleableRow(askFirst, onCheckedChange = { askFirst = it }),
                    leading = { IconTile(R.drawable.ic_cloud_upload, colors.secondaryContainer, colors.onSecondaryContainer) },
                    trailing = { SkimSwitch(askFirst, onCheckedChange = null) },
                )
                SkimListItem(
                    headline = stringResource(R.string.ai_settings_keep_history),
                    supporting = stringResource(R.string.ai_settings_keep_history_summary),
                    index = 1,
                    count = 2,
                    onClick = null,
                    modifier = Modifier.toggleableRow(keepHistory, onCheckedChange = { keepHistory = it }),
                    leading = { IconTile(R.drawable.ic_history, colors.secondaryContainer, colors.onSecondaryContainer) },
                    trailing = { SkimSwitch(keepHistory, onCheckedChange = null) },
                )
            }
        }
    }
}
