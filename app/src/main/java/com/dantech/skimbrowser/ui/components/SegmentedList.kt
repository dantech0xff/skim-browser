package com.dantech.skimbrowser.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import kotlin.math.min

/**
 * The shapes of item [index] in a segmented group of [count] items: 20 dp on the outer corners of
 * the group and 4 dp where two items meet, so a single item is 20 dp all round.
 *
 * ListItemDefaults.segmentedShapes rounds the outer corners to 16 dp, so the shapes are built here.
 * Every state keeps the item's own shape except pressed, which rounds all four corners to 20 dp
 * (the press shape morph of Material 3 Expressive). Selected, focused, hovered and dragged keep
 * the same shape so the corners never jump during those interactions.
 */
fun segmentedShapes(index: Int, count: Int): ListItemShapes {
    require(count > 0) { "A segmented group needs at least one item, got $count." }
    require(index in 0 until count) { "Item $index is outside a group of $count items." }
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
    return ListItemShapes(
        shape = shape,
        selectedShape = shape,
        pressedShape = PressedShape,
        focusedShape = shape,
        hoveredShape = shape,
        draggedShape = shape,
    )
}

/**
 * An item of a segmented list (04b, 04c, 10, 12, 13, 14): surfaceContainerLowest with the corners of
 * its place in the group ([index] of [count]), the leading tile 16 dp from the text, a headline in
 * bodyLarge and an optional supporting line in bodyMedium. One-line items are 56 dp high (8 dp
 * padding), two-line items 72 dp (12 dp padding); text that wraps makes the item taller, with the
 * tile and the trailing content kept centered, as on 10.
 *
 * The trailing content never makes the item taller than its 40 dp tile: a 48 dp icon button
 * overflows into the vertical padding, so a one-line item with one stays 56 dp, as drawn on 04b.
 *
 * With [onClick] null the item does not react to touch, like Version on 12. A switch row (10, site
 * information) passes `Modifier.toggleableRow(...)` as [modifier]: the item clips its press ripple
 * to the item's shape.
 *
 * @param trailingIsIconButton set when [trailing] is a 48 dp icon button (Remove, Bookmark options):
 *   the drawn rows put it right after the text and 4 dp from the end, so its icon lines up 16 dp
 *   from the edge like a switch or a value does.
 * @param singleLine keeps the headline and supporting text on one line each, ending in an ellipsis,
 *   for page titles and addresses (04b, 04c, 13, 14); settings labels wrap instead (10, 12).
 * @param onLongClick needs [onClick]: an item that only long-clicks would announce a click that does
 *   nothing.
 * @param headlineColor the headline's color where it differs, as for Delete all AI data on 10 (error).
 * @param headlineWeight the headline's weight where it differs (500 on that row).
 * @param supportingColor the supporting line's color where it differs, as for the Ask Skim row of 04b
 *   (tertiary).
 * @param supportingWeight the supporting line's weight where it differs (500 on that row).
 */
@Composable
fun SkimListItem(
    headline: String,
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    trailingIsIconButton: Boolean = false,
    singleLine: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    headlineColor: Color = Color.Unspecified,
    headlineWeight: FontWeight? = null,
    supportingColor: Color = Color.Unspecified,
    supportingWeight: FontWeight? = null,
) {
    require(onClick != null || onLongClick == null) { "An item with a long click needs a click too." }
    val shapes = remember(index, count) { segmentedShapes(index, count) }
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val contentPadding = PaddingValues(
        start = HorizontalPadding,
        end = if (trailing != null && trailingIsIconButton) IconButtonEndPadding else HorizontalPadding,
        top = if (supporting != null) TwoLinePadding else OneLinePadding,
        bottom = if (supporting != null) TwoLinePadding else OneLinePadding,
    )
    val maxLines = if (singleLine) 1 else Int.MAX_VALUE
    val headlineText: @Composable () -> Unit = {
        Text(
            text = headline,
            color = headlineColor,
            fontWeight = headlineWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val supportingText: @Composable (() -> Unit)? = supporting?.let { text ->
        {
            Text(
                text = text,
                color = supportingColor,
                fontWeight = supportingWeight,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val leadingSlot: @Composable (() -> Unit)? = leading?.let { content -> { LeadingSlot(content) } }
    val trailingSlot: @Composable (() -> Unit)? = trailing?.let { content ->
        { TrailingSlot(iconButton = trailingIsIconButton, content = content) }
    }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            leadingContent = leadingSlot,
            trailingContent = trailingSlot,
            supportingContent = supportingText,
            verticalAlignment = Alignment.CenterVertically,
            onLongClick = onLongClick,
            colors = colors,
            contentPadding = contentPadding,
            content = headlineText,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            // Clipped outside the caller's modifier, so a toggleableRow ripple keeps the item's shape.
            modifier = Modifier.clip(shapes.shape).then(modifier),
            leadingContent = leadingSlot,
            trailingContent = trailingSlot,
            supportingContent = supportingText,
            verticalAlignment = Alignment.CenterVertically,
            colors = colors,
            contentPadding = contentPadding,
            content = headlineText,
        )
    }
}

/**
 * A list item that only states something, as on the incognito new tab (09c): a 24 dp icon in
 * onSurfaceVariant and bodyLarge text on surfaceContainer, 48 dp high, with the corners of its place
 * in the group. It cannot be tapped.
 */
@Composable
fun StaticListItem(
    @DrawableRes icon: Int,
    text: String,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val shapes = remember(index, count) { segmentedShapes(index, count) }
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier.heightIn(min = StaticItemHeight),
        leadingContent = { LeadingSlot { Icon(painterResource(icon), contentDescription = null) } },
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        contentPadding = PaddingValues(horizontal = HorizontalPadding, vertical = StaticItemPadding),
    ) {
        Text(text)
    }
}

/**
 * The label above a segmented group: titleSmallEmphasized in primary, 32 dp from the edges. The
 * [first] label of a screen sits 12 dp below what precedes it, later ones 20 dp; 8 dp below it
 * comes the group. It is a heading for accessibility.
 */
@Composable
fun SectionLabel(text: String, first: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SectionLabelInset,
                end = SectionLabelInset,
                top = if (first) FirstSectionLabelTop else SectionLabelTop,
                bottom = SectionLabelBottom,
            )
            .semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}

/** A segmented group: its items 2 dp apart, with 16 dp margins on both sides. */
@Composable
fun SegmentedColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = HorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content,
    )
}

/** The list item keeps 12 dp after its leading content; the drawn rows keep 16. */
@Composable
private fun LeadingSlot(content: @Composable () -> Unit) {
    Box(Modifier.padding(end = ExtraGap)) { content() }
}

/**
 * Holds the trailing content as the drawn rows do. It reports at most the tile's 40 dp of height,
 * centering anything taller over the item's vertical padding. An icon button keeps its full 48 dp
 * target (the list item lowers the minimum for trailing content by the padding around it) and moves
 * back over the 12 dp the list item keeps before trailing content; anything else gets 16 dp.
 */
@Composable
private fun TrailingSlot(iconButton: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides MinTouchTarget) {
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = min(placeable.height, TileSize.roundToPx())
                val shift = if (iconButton) TrailingGap.roundToPx() else -ExtraGap.roundToPx()
                val width = (placeable.width - shift).coerceAtLeast(0)
                layout(width, height) {
                    placeable.placeRelative(x = -shift, y = (height - placeable.height) / 2)
                }
            },
        ) {
            content()
        }
    }
}

private val OuterCorner = 20.dp
private val InnerCorner = 4.dp
private val PressedShape = RoundedCornerShape(OuterCorner)
private val HorizontalPadding = 16.dp
private val IconButtonEndPadding = 4.dp
private val OneLinePadding = 8.dp
private val TwoLinePadding = 12.dp
private val TileSize = 40.dp
private val MinTouchTarget = 48.dp

/** Space the list item keeps between the text and the trailing content. */
private val TrailingGap = 12.dp

/** Added to the 12 dp the list item keeps beside leading and trailing content: the rows keep 16. */
private val ExtraGap = 4.dp
private val StaticItemHeight = 48.dp
private val StaticItemPadding = 12.dp
private val SectionLabelInset = 32.dp
private val FirstSectionLabelTop = 12.dp
private val SectionLabelTop = 20.dp
private val SectionLabelBottom = 8.dp

@PreviewLightDark
@Composable
private fun SegmentedListPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        val colors = MaterialTheme.colorScheme
        Column(Modifier.background(colors.surfaceContainerLow).padding(bottom = 16.dp)) {
            SectionLabel(stringResource(R.string.settings_section_general), first = true)
            SegmentedColumn {
                SkimListItem(
                    headline = stringResource(R.string.settings_search_engine),
                    supporting = stringResource(R.string.search_engine_google),
                    index = 0,
                    count = 2,
                    onClick = {},
                    leading = { IconTile(R.drawable.ic_search, colors.secondaryContainer, colors.onSecondaryContainer) },
                )
                SkimListItem(
                    headline = stringResource(R.string.settings_version),
                    index = 1,
                    count = 2,
                    onClick = null,
                    leading = { IconTile(R.drawable.ic_info, colors.surfaceContainer, colors.onSurfaceVariant) },
                    trailing = {
                        Text(
                            stringResource(R.string.settings_version_value, "1.0.0", 100),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )
            }
            SectionLabel(stringResource(R.string.date_today), first = false)
            SegmentedColumn {
                SkimListItem(
                    headline = "Why heat pumps are winning in cold climates",
                    supporting = "daylightjournal.com",
                    index = 0,
                    count = 1,
                    onClick = {},
                    singleLine = true,
                    leading = { FaviconLetterTile(siteLetter(host = "daylightjournal.com", title = null)) },
                    trailing = {
                        IconButton(onClick = {}) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_remove_from_history))
                        }
                    },
                    trailingIsIconButton = true,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun StaticListItemPreview() {
    SkimTheme(ThemeSetting.System, incognito = true, dynamicColor = false) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(bottom = 16.dp)) {
            SectionLabel(stringResource(R.string.incognito_wont_save), first = true)
            SegmentedColumn {
                StaticListItem(R.drawable.ic_history, stringResource(R.string.incognito_pages_you_visit), 0, 2)
                StaticListItem(R.drawable.ic_cookie, stringResource(R.string.cookies_and_site_data), 1, 2)
            }
        }
    }
}
