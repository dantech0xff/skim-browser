package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * The collapsed search bar of Home (04) and the incognito new tab (09c). It takes no input: tapping
 * it opens Search & suggestions (04b) with the keyboard up.
 *
 * 04: 56 dp pill in surfaceContainerHigh, the search icon, "Search or ask anything" and the mic.
 * 09c draws a different bar, not just the dark one: surfaceContainerHighest, a 20 dp visibility_off
 * icon in a 48 dp cell and "Search or type URL"; its dark colors come from the incognito theme.
 *
 * @param onVoice starts voice search; null hides the mic.
 */
@Composable
fun SearchBarField(
    incognito: Boolean,
    onClick: () -> Unit,
    onVoice: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        color = if (incognito) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
    ) {
        Row(
            // Without the mic the text keeps 16 dp from the end, like the address bar.
            modifier = Modifier.fillMaxSize().padding(
                start = if (incognito) 4.dp else 16.dp,
                end = if (onVoice != null) 4.dp else 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (incognito) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_visibility_off),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            } else {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.onSurfaceVariant)
            }
            Text(
                text = stringResource(if (incognito) R.string.incognito_search_hint else R.string.search_hint),
                // 04 keeps 8 dp between the search icon and the text.
                modifier = Modifier.weight(1f).padding(start = if (incognito) 0.dp else 8.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onVoice != null) {
                IconButton(
                    onClick = onVoice,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurfaceVariant),
                ) {
                    Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.cd_voice_search))
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SearchBarFieldPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            SearchBarField(incognito = false, onClick = {}, onVoice = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun SearchBarFieldIncognitoPreview() {
    SkimTheme(ThemeSetting.System, incognito = true, dynamicColor = false) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            SearchBarField(incognito = true, onClick = {}, onVoice = {})
        }
    }
}
