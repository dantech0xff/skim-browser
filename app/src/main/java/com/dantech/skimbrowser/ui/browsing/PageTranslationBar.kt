package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.translate.PageTranslation

/**
 * The bar above the browsing pill while a page is being translated, or while a translation is
 * showing. Idle draws nothing. Allowing a site is a separate dialog, so that failure is not drawn
 * here.
 */
@Composable
fun PageTranslationBar(
    state: PageTranslation,
    onShowOriginal: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = messageOf(state) ?: return
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 480.dp),
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (state) {
                is PageTranslation.Ready -> {
                    TextButton(onClick = { onShowOriginal(!state.showingOriginal) }) {
                        Text(
                            stringResource(
                                if (state.showingOriginal) R.string.translate_show_translation else R.string.translate_show_original,
                            ),
                        )
                    }
                }
                is PageTranslation.Failed -> {
                    if (state.reason == PageTranslation.Note.Offline || state.reason == PageTranslation.Note.Unavailable) {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.translate_retry)) }
                    }
                }
                else -> Unit
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.cd_close_translation),
                )
            }
        }
    }
}

@Composable
private fun messageOf(state: PageTranslation): String? = when (state) {
    PageTranslation.Idle -> null
    is PageTranslation.Translating -> if (state.total > 0) {
        stringResource(R.string.translate_progress, state.done, state.total)
    } else {
        stringResource(R.string.translate_working)
    }
    is PageTranslation.Ready -> when (state.note) {
        null -> stringResource(R.string.translate_done)
        PageTranslation.Note.Incomplete -> stringResource(R.string.translate_partial)
        PageTranslation.Note.Offline -> stringResource(R.string.translate_offline)
        PageTranslation.Note.Limit -> stringResource(R.string.translate_limit)
        PageTranslation.Note.NotAllowed -> null
        PageTranslation.Note.Unavailable -> stringResource(R.string.translate_unavailable)
        PageTranslation.Note.Empty -> stringResource(R.string.translate_empty)
        PageTranslation.Note.Incognito -> stringResource(R.string.translate_incognito)
    }
    is PageTranslation.Failed -> when (state.reason) {
        PageTranslation.Note.NotAllowed -> null
        PageTranslation.Note.Offline -> stringResource(R.string.translate_offline)
        PageTranslation.Note.Limit -> stringResource(R.string.translate_limit)
        PageTranslation.Note.Empty -> stringResource(R.string.translate_empty)
        PageTranslation.Note.Incognito -> stringResource(R.string.translate_incognito)
        PageTranslation.Note.Unavailable, PageTranslation.Note.Incomplete -> stringResource(R.string.translate_unavailable)
    }
}
