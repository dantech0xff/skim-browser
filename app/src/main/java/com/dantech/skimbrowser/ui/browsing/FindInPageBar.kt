package com.dantech.skimbrowser.ui.browsing

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.DisabledContentAlpha
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.search.NoKeyboardLearning
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * Find in page (05c), in place of the address bar: an 8 by 16 frame on surface around a 56 dp pill
 * in surfaceContainerHigh (padding 0 4 0 20, 4 between items) with the field in bodyLarge (labelled
 * "Find in page"), the count in bodyMedium and onSurfaceVariant, and Previous match, Next match and
 * Close find in page as 48 dp icon buttons.
 *
 * The field takes focus as the bar opens, and the keyboard's Search key moves to the next match. The
 * count shows once the WebView counted the field's text ([FindUiState.showsCount]), "0/0" without a
 * match, and accessibility announces it as it changes. Previous and Next stay on at the first and
 * last match, since they wrap around; they are off while there is no match to move to. In an
 * incognito tab the keyboard is asked not to learn from what is typed.
 *
 * @param embedded the field and match controls as a row for the browsing pill, with no frame of
 *   their own. The framed bar is the default.
 */
@Composable
fun FindInPageBar(
    query: TextFieldState,
    state: FindUiState,
    incognito: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val text = query.text.toString()
    val label = stringResource(R.string.find_in_page)
    val focus = remember { FocusRequester() }
    NoKeyboardLearning(enabled = incognito) {
        if (embedded) {
            FindRow(
                query, state, text, label, focus, colors, onPrevious, onNext, onClose,
                modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
                compact = true,
            )
        } else {
            Box(
                modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().height(ToolbarMetrics.AddressRowHeight),
                ) {
                    FindRow(
                        query, state, text, label, focus, colors, onPrevious, onNext, onClose,
                        Modifier.fillMaxSize().padding(start = 20.dp, end = 4.dp),
                    )
                }
            }
        }
    }
    LaunchedEffect(focus) { focus.requestFocus() }
}

@Composable
private fun FindRow(
    query: TextFieldState,
    state: FindUiState,
    text: String,
    label: String,
    focus: FocusRequester,
    colors: ColorScheme,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    compact: Boolean = false,
) {
    val typography = MaterialTheme.typography
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = query,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .semantics { contentDescription = label },
            textStyle = typography.bodyLarge.copy(color = colors.onSurface),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            onKeyboardAction = { onNext() },
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(colors.primary),
        )
        if (state.showsCount(text)) {
            Text(
                text = stringResource(R.string.find_match_count, state.activeMatch, state.matchCount),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.onSurfaceVariant,
                style = typography.bodyMedium,
                maxLines = 1,
            )
        }
        val canStep = state.canStep(text)
        FindButton(R.drawable.ic_keyboard_arrow_up, stringResource(R.string.cd_previous_match), onPrevious, enabled = canStep, compact = compact)
        FindButton(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.cd_next_match), onNext, enabled = canStep, compact = compact)
        FindButton(R.drawable.ic_close, stringResource(R.string.cd_close_find_in_page), onClose, enabled = true, compact = compact)
    }
}

@Composable
private fun FindButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    if (compact) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription,
                tint = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else DisabledContentAlpha),
            )
        }
        return
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = colors.onSurfaceVariant,
            disabledContentColor = colors.onSurface.copy(alpha = DisabledContentAlpha),
        ),
    ) {
        Icon(painterResource(icon), contentDescription)
    }
}

@PreviewLightDark
@Composable
private fun FindInPageBarPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        FindInPageBar(
            query = remember { TextFieldState("heat pump") },
            state = FindUiState(countedText = "heat pump", activeMatch = 2, matchCount = 6),
            incognito = false,
            onPrevious = {},
            onNext = {},
            onClose = {},
        )
    }
}
