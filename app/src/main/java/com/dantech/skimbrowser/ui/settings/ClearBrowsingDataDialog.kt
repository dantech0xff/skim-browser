package com.dantech.skimbrowser.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.SkimDialogContent

/**
 * Clear browsing data (15). History and cookies start checked; cached images start unchecked, and
 * that reset happens every time the dialog opens. Clear stays in primary and is off while nothing
 * is checked. [onClear] receives the three checks; the caller closes the dialog.
 */
@Composable
fun ClearBrowsingDataDialog(
    onClear: (history: Boolean, cookies: Boolean, cache: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var history by rememberSaveable { mutableStateOf(true) }
    var cookies by rememberSaveable { mutableStateOf(true) }
    var cache by rememberSaveable { mutableStateOf(false) }
    val anything = history || cookies || cache
    SkimDialogContent(
        title = stringResource(R.string.clear_browsing_data),
        confirm = DialogAction(
            label = stringResource(R.string.action_clear),
            onClick = { onClear(history, cookies, cache) },
            enabled = anything,
        ),
        dismiss = DialogAction(stringResource(R.string.action_cancel), onDismiss),
        modifier = modifier,
        text = {
            // SkimDialogContent places this slot in a Box, so the body, rows, and footnote stack
            // in one column instead of drawing on top of each other.
            Column {
                Text(stringResource(R.string.clear_data_body))
                Column(Modifier.padding(top = 8.dp).widen(CheckOutset)) {
                    CheckRow(stringResource(R.string.clear_data_browsing_history), history) { history = it }
                    CheckRow(
                        label = stringResource(R.string.cookies_and_site_data),
                        checked = cookies,
                        supporting = stringResource(R.string.clear_data_cookies_summary),
                        onCheckedChange = { cookies = it },
                    )
                    CheckRow(stringResource(R.string.clear_data_cache), cache) { cache = it }
                }
                Text(
                    text = stringResource(R.string.clear_data_footer),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    supporting: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(start = 2.dp, top = 4.dp, end = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Checkbox(checked = checked, onCheckedChange = null)
        }
        Column {
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Lets the child draw [extra] past this layout on both sides, the 12 dp the check rows reach. */
private fun Modifier.widen(extra: Dp): Modifier = layout { measurable, constraints ->
    val outset = extra.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(maxWidth = (constraints.maxWidth + outset * 2).coerceAtLeast(constraints.minWidth)),
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-outset, 0)
    }
}

private val CheckOutset = 12.dp
