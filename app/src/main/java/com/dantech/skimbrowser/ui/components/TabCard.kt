package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * A tab in the tab switcher (09, 09b): radius 20, a 44 dp title row (a 20 dp favicon letter tile,
 * the [title] in labelMedium, Close tab), then the [preview] of the page, 92 dp high with radius 12,
 * cropped from its top. Without a preview the frame is surfaceContainerLowest. The card is 144 dp
 * high; its width comes from the grid.
 *
 * The card is surfaceContainerLow, and surfaceContainer in the dark scheme (09b), where
 * surfaceContainerLow would hardly stand out from the background. The current tab gets a 3 dp ring
 * in primary inside its edge. A tab with a Skim chat shows the Sunny mark, 24 dp, 8 dp from the
 * bottom end of the preview; an [incognito] tab never does, as Skim AI is off in incognito.
 *
 * The card is a button labelled by its title, read as selected when it [isCurrent]. Close tab is
 * drawn 36 dp with an 18 dp icon; its 48 dp touch target fits in the top end corner of the card, so
 * the title row stays 44 dp high and nothing outside the card takes its touches.
 */
@Composable
fun TabCard(
    title: String,
    letter: String,
    preview: ImageBitmap?,
    isCurrent: Boolean,
    hasSkimChat: Boolean,
    incognito: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (incognito || isDarkScheme()) colors.surfaceContainer else colors.surfaceContainerLow
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(container)
            .then(if (isCurrent) Modifier.border(CurrentRingWidth, colors.primary, CardShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = isCurrent },
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TitleRowHeight)
                    // The end of the row is Close tab's touch target.
                    .padding(start = 12.dp, end = CloseTarget),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaviconLetterTile(
                    letter = letter,
                    size = FaviconSize,
                    textStyle = MaterialTheme.typography.labelMediumEmphasized.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    container = colors.surfaceContainerHighest,
                    content = colors.onSurfaceVariant,
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .padding(start = PreviewInset, end = PreviewInset, bottom = PreviewInset)
                    .fillMaxWidth()
                    .height(PreviewHeight)
                    .clip(PreviewShape)
                    .background(colors.surfaceContainerLowest),
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        alignment = Alignment.TopCenter,
                        contentScale = ContentScale.Crop,
                    )
                }
                if (hasSkimChat && !incognito) {
                    SunnyMark(
                        size = BadgeSize,
                        iconSize = BadgeIconSize,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(PreviewInset),
                    )
                }
            }
        }
        CompactIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.cd_close_tab),
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd),
            iconSize = CloseIconSize,
            visualSize = CloseSize,
        )
    }
}

private val CardShape = RoundedCornerShape(20.dp)
private val CurrentRingWidth = 3.dp
private val TitleRowHeight = 44.dp
private val FaviconSize = 20.dp
private val CloseSize = 36.dp
private val CloseIconSize = 18.dp

/** Close tab lays out 48 dp square, from the top end corner of the card. */
private val CloseTarget = 48.dp
private val PreviewHeight = 92.dp
private val PreviewInset = 8.dp
private val PreviewShape = RoundedCornerShape(12.dp)
private val BadgeSize = 24.dp
private val BadgeIconSize = 14.dp

@PreviewLightDark
@Composable
private fun TabCardPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TabCard(
                title = "Why heat pumps are winning in cold climates",
                letter = siteLetter(host = "daylightjournal.com", title = null),
                preview = null,
                isCurrent = true,
                hasSkimChat = true,
                incognito = false,
                onClick = {},
                onClose = {},
                modifier = Modifier.width(184.dp),
            )
            TabCard(
                title = "Heat pump sizing calculator",
                letter = siteLetter(host = "heatcalc.example", title = null),
                preview = null,
                isCurrent = false,
                hasSkimChat = false,
                incognito = false,
                onClick = {},
                onClose = {},
                modifier = Modifier.width(184.dp),
            )
        }
    }
}
