package com.dantech.skimbrowser.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/** The drawn button heights: M 56 (Get started, Try again), S 40, XS 32 (AI suggestions, citations). */
enum class SkimButtonSize { M, S, XS }

/** Filled (primary), Tonal (secondaryContainer), Text (primary label) and Outlined (outlineVariant). */
enum class SkimButtonStyle { Filled, Tonal, Text, Outlined }

/**
 * A full-radius button of the design (01, 03, 06, 06c, 11, 15) sized with the ButtonDefaults size
 * helpers: M uses titleMedium, S and XS labelLarge, with an optional leading [icon]. The label stays
 * on one line, as drawn. An S text button keeps 12 dp on each side, like the dialog actions of 15.
 *
 * Buttons under 48 dp keep a 48 dp touch target, so S and XS lay out 48 dp high with the drawn
 * button centered.
 */
@Composable
fun SkimButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: SkimButtonSize = SkimButtonSize.S,
    style: SkimButtonStyle = SkimButtonStyle.Filled,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val height = size.containerHeight
    val shapes = ButtonDefaults.shapesFor(height)
    val sized = modifier.heightIn(min = height)
    val contentPadding = if (style == SkimButtonStyle.Text && size == SkimButtonSize.S) {
        ButtonDefaults.TextButtonContentPadding
    } else {
        ButtonDefaults.contentPaddingFor(height, hasStartIcon = icon != null)
    }
    val disabledContainer = disabledContainerColor()
    val disabledContent = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContentAlpha)
    val content: @Composable () -> Unit = { ButtonContent(text, icon, height) }
    when (style) {
        SkimButtonStyle.Filled -> Button(
            onClick = onClick,
            shapes = shapes,
            modifier = sized,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = disabledContainer,
                disabledContentColor = disabledContent,
            ),
            contentPadding = contentPadding,
        ) { content() }

        SkimButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick,
            shapes = shapes,
            modifier = sized,
            enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(
                disabledContainerColor = disabledContainer,
                disabledContentColor = disabledContent,
            ),
            contentPadding = contentPadding,
        ) { content() }

        SkimButtonStyle.Text -> TextButton(
            onClick = onClick,
            shapes = shapes,
            modifier = sized,
            enabled = enabled,
            colors = ButtonDefaults.textButtonColors(disabledContentColor = disabledContent),
            contentPadding = contentPadding,
        ) { content() }

        SkimButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick,
            shapes = shapes,
            modifier = sized,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = disabledContent),
            contentPadding = contentPadding,
        ) { content() }
    }
}

/**
 * A suggested question under an AI answer (06, 06b): an XS outlined button, 32 dp high, radius 16,
 * a 1 dp outlineVariant border and labelLarge in onSurfaceVariant. Named apart from the material3
 * SuggestionChip, which is a different component.
 */
@Composable
fun AiSuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SkimButton(text, onClick, modifier, size = SkimButtonSize.XS, style = SkimButtonStyle.Outlined)
}

/**
 * The source of an AI answer, "From paragraph N" (06): an XS tonal button with an 18 dp quote icon.
 * Tapping it lowers the sheet and highlights that paragraph on the page.
 *
 * When the page has changed since the answer, the paragraph no longer exists, so [enabled] is false:
 * the chip is drawn disabled (onSurface at 10%, 12% in dark, behind 38% content) and has no click
 * action at all, the same treatment as the disabled AI FAB. It keeps the enabled chip's size and
 * 48 dp layout, so nothing moves when it turns off.
 */
@Composable
fun CitationChip(paragraph: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val height = ButtonDefaults.ExtraSmallContainerHeight
    val label = stringResource(R.string.ai_citation_paragraph, paragraph)
    if (enabled) {
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapesFor(height),
            modifier = modifier.heightIn(min = height),
            contentPadding = CitationChipPadding,
        ) {
            CitationContent(label)
        }
    } else {
        Row(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = height)
                .clip(CircleShape)
                .background(disabledContainerColor())
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    disabled()
                }
                .padding(CitationChipPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContentAlpha),
            ) {
                CitationContent(label)
            }
        }
    }
}

/**
 * An icon button drawn smaller than 48 dp, keeping a 48 dp touch target: copy and feedback under an
 * AI answer (40 dp, icon 20), Close tab on a tab card (36 dp, icon 18), Group options (40 dp, icon
 * 24). The icon is onSurfaceVariant. It lays out 48 dp square with the drawn button centered.
 */
@Composable
fun CompactIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    visualSize: Dp = 40.dp,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        // The outer minimum keeps the 48 dp layout; the size inside it is the drawn button.
        modifier = modifier.minimumInteractiveComponentSize().size(visualSize),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = colors.onSurfaceVariant,
            disabledContentColor = colors.onSurface.copy(alpha = DisabledContentAlpha),
        ),
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(iconSize))
    }
}

/**
 * Send in the chat input (06, 06b): a 40 dp primary circle with a 20 dp upward arrow. Disabled while
 * the input is off (06c, No connection): onSurface at 10% (12% in dark) behind 38% content.
 */
@Composable
fun SendButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChatActionButton(R.drawable.ic_arrow_upward, stringResource(R.string.cd_send), onClick, modifier, enabled)
}

/** Stop in place of Send while Skim writes (06c): ends the stream and keeps the text so far. */
@Composable
fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChatActionButton(R.drawable.ic_stop_filled, stringResource(R.string.cd_stop), onClick, modifier, enabled = true)
}

/**
 * Text size on the reader (08, 08b), a toggle: off it is a plain icon button with the outlined icon
 * in onSurfaceVariant; on it becomes a 40 dp secondaryContainer square with radius 12 and the filled
 * icon, morphing between the two shapes.
 */
@Composable
fun TextSizeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurfaceVariant,
            checkedContainerColor = colors.secondaryContainer,
            checkedContentColor = colors.onSecondaryContainer,
        ),
    ) {
        Icon(
            painterResource(if (checked) R.drawable.ic_format_size_filled else R.drawable.ic_format_size),
            stringResource(R.string.cd_text_size),
        )
    }
}

/** Disabled containers are onSurface at 10%, and 12% in dark (tokens.md, disabled container). */
@Composable
@ReadOnlyComposable
internal fun disabledContainerColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkScheme()) 0.12f else 0.10f)

private val SkimButtonSize.containerHeight: Dp
    get() = when (this) {
        SkimButtonSize.M -> ButtonDefaults.MediumContainerHeight
        SkimButtonSize.S -> ButtonDefaults.MinHeight
        SkimButtonSize.XS -> ButtonDefaults.ExtraSmallContainerHeight
    }

@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int?, height: Dp) {
    if (icon != null) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
    }
    Text(text, style = ButtonDefaults.textStyleFor(height), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The citation icon is 18 dp with 8 dp before it and 4 dp after it (06). */
@Composable
private fun CitationContent(label: String) {
    Icon(painterResource(R.drawable.ic_format_quote), contentDescription = null, Modifier.size(CitationIconSize))
    Spacer(Modifier.width(ButtonDefaults.ExtraSmallIconSpacing))
    Text(
        label,
        style = ButtonDefaults.textStyleFor(ButtonDefaults.ExtraSmallContainerHeight),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChatActionButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = disabledContainerColor(),
            disabledContentColor = colors.onSurface.copy(alpha = DisabledContentAlpha),
        ),
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(ChatActionIconSize))
    }
}

private val CitationChipPadding = PaddingValues(start = 8.dp, end = 12.dp)
private val CitationIconSize = 18.dp
private val ChatActionIconSize = 20.dp

@PreviewLightDark
@Composable
private fun SkimButtonsPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(
            Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkimButton(
                stringResource(R.string.action_try_again),
                onClick = {},
                size = SkimButtonSize.M,
                icon = R.drawable.ic_refresh,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkimButtonStyle.entries.forEach { style ->
                    SkimButton(stringResource(R.string.setup_set_as_default), onClick = {}, style = style)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiSuggestionChip(stringResource(R.string.ai_chip_key_numbers), onClick = {})
                CitationChip(paragraph = 6, enabled = true, onClick = {})
                CitationChip(paragraph = 6, enabled = false, onClick = {})
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun IconButtonsPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        var textSize by remember { mutableStateOf(false) }
        Row(
            Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactIconButton(R.drawable.ic_content_copy, stringResource(R.string.cd_copy_summary), onClick = {})
            CompactIconButton(
                R.drawable.ic_close,
                stringResource(R.string.cd_close_tab),
                onClick = {},
                iconSize = 18.dp,
                visualSize = 36.dp,
            )
            SendButton(enabled = true, onClick = {})
            SendButton(enabled = false, onClick = {})
            StopButton(onClick = {})
            TextSizeToggle(checked = textSize, onCheckedChange = { textSize = it })
        }
    }
}
