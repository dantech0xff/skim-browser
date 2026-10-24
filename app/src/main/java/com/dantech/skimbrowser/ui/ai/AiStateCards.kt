package com.dantech.skimbrowser.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.AiLoadingIndicator
import com.dantech.skimbrowser.ui.components.AiWavyProgress
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonStyle
import com.dantech.skimbrowser.ui.components.SkimCard

/** The summary label: filled auto_awesome and "Summary", both tertiary. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SummaryLabel() {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_auto_awesome_filled),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.tertiary,
        )
        Text(
            stringResource(R.string.ai_summary),
            color = colors.tertiary,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
    }
}

/** Summary points, one line each, with the bullet in a 20 dp column. */
@Composable
internal fun SummaryBullets(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Row {
                Box(Modifier.width(20.dp), contentAlignment = Alignment.TopCenter) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                }
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Writing the summary (06c): the summary label, the loading indicator with its two lines, and the
 * wavy progress. TalkBack hears the card as a polite live region.
 */
@Composable
internal fun GeneratingCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SkimCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.surfaceContainerLowest,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SummaryLabel()
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AiLoadingIndicator(stringResource(R.string.cd_writing_summary))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_writing_summary), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ai_reading_page),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        AiWavyProgress()
    }
}

/** A notice in the summary slot (06c): an icon, a title, a body, and Try again when [onRetry] is set. */
@Composable
internal fun AiNoticeCard(
    icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    SkimCard(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceContainerLowest,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onRetry != null) {
            SkimButton(
                text = stringResource(R.string.action_try_again),
                onClick = onRetry,
                modifier = Modifier.align(Alignment.End),
                style = SkimButtonStyle.Tonal,
            )
        }
    }
}

/** The notice for [summary], or null when the summary has text of its own. */
@Composable
internal fun SummaryNotice(summary: SummaryState, onRetry: () -> Unit): (@Composable ColumnScope.() -> Unit)? {
    val notice: @Composable ColumnScope.() -> Unit = when (summary) {
        SummaryState.NotEnoughText -> {
            {
                AiNoticeCard(
                    icon = R.drawable.ic_info,
                    title = stringResource(R.string.ai_not_enough_text_title),
                    body = stringResource(R.string.ai_not_enough_text_body),
                )
            }
        }
        SummaryState.Offline -> {
            {
                AiNoticeCard(
                    icon = R.drawable.ic_wifi_off,
                    title = stringResource(R.string.ai_offline_title),
                    body = stringResource(R.string.ai_offline_body),
                    onRetry = onRetry,
                )
            }
        }
        SummaryState.ServerError -> {
            {
                AiNoticeCard(
                    icon = R.drawable.ic_error,
                    title = stringResource(R.string.ai_server_error_title),
                    body = stringResource(R.string.ai_server_error_body),
                    onRetry = onRetry,
                )
            }
        }
        SummaryState.Refused -> {
            {
                AiNoticeCard(
                    icon = R.drawable.ic_info,
                    title = stringResource(R.string.ai_refusal_title),
                    body = stringResource(R.string.ai_refusal_body),
                )
            }
        }
        else -> return null
    }
    return notice
}
