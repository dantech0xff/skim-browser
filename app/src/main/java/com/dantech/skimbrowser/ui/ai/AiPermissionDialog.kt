package com.dantech.skimbrowser.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.SkimDialogContent
import com.dantech.skimbrowser.ui.components.SunnyMark

/**
 * The first time Skim reads [host] (06d). Allow remembers the site and opens the summary. Not now
 * closes the dialog and sends nothing, so the next time asks again.
 */
@Composable
fun AiPermissionDialog(host: String, onAllow: () -> Unit, onNotNow: () -> Unit) {
    SkimDialogContent(
        title = stringResource(R.string.ai_permission_title),
        confirm = DialogAction(stringResource(R.string.action_allow), onAllow),
        dismiss = DialogAction(stringResource(R.string.action_not_now), onNotNow),
        icon = { SunnyMark(size = 56.dp, iconSize = 28.dp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.ai_permission_body, host),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.ai_permission_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}
