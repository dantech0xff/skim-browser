package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion

/** What the leading control of the address bar shows. */
enum class SiteIndicator {
    /** https: the lock, which opens Site information (05). */
    Secure,

    /** http: the globe, which also opens Site information. */
    NotSecure,

    /** The page failed to load (11): a still error icon, and no Reader view. */
    Error,
}

/**
 * The address bar of 05 to 05d and 11: a 56 dp pill in surfaceContainerHigh inside an 8 by 16
 * frame on surface, with the site indicator, the address as a button (it opens 04b with the URL
 * ready to edit) and Reader view when the page has an article.
 *
 * While [loadProgress] is set, wavy progress in primary runs along the bottom edge of the frame,
 * centered on it as on 05 (Loading), so it overlaps the top of the page by 5 dp; the bar draws
 * above later siblings for that. With Remove animations on, the wave is flat.
 *
 * @param address the text to show, already shortened by the caller (host on 05, URL without the
 *   scheme on 11).
 * @param loadProgress 0 to 1 while the page loads, null once it is done.
 * @param embedded the lock, a centered domain inside a hairline field, and Reader view, as a row for
 *   the browsing pill. The framed bar is the default.
 */
@Composable
fun AddressBar(
    address: String,
    indicator: SiteIndicator,
    readerAvailable: Boolean,
    loadProgress: Float?,
    onSiteInfo: () -> Unit,
    onEditAddress: () -> Unit,
    onReader: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val showReader = readerAvailable && indicator != SiteIndicator.Error
    if (embedded) {
        CompactAddressField(address, indicator, showReader, onSiteInfo, onEditAddress, onReader, modifier.fillMaxHeight())
        return
    }
    Box(modifier.zIndex(1f)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().height(ToolbarMetrics.AddressRowHeight),
            ) {
                AddressControls(address, indicator, showReader, onSiteInfo, onEditAddress, onReader, Modifier.fillMaxSize())
            }
        }
        AddressLoadProgress(
            loadProgress,
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun AddressControls(
    address: String,
    indicator: SiteIndicator,
    showReader: Boolean,
    onSiteInfo: () -> Unit,
    onEditAddress: () -> Unit,
    onReader: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurfaceVariant)
    Row(
        // Without a trailing button the address keeps 16 dp from the end, as on 11.
        modifier = modifier.padding(start = 4.dp, end = if (showReader) 4.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (indicator) {
            SiteIndicator.Secure, SiteIndicator.NotSecure -> {
                val secure = indicator == SiteIndicator.Secure
                val siteLabel = stringResource(if (secure) R.string.cd_site_info_secure else R.string.cd_site_info)
                val siteIcon = painterResource(if (secure) R.drawable.ic_lock else R.drawable.ic_public)
                IconButton(onClick = onSiteInfo, colors = iconColors) {
                    Icon(siteIcon, siteLabel, Modifier.size(SiteIconSize))
                }
            }

            SiteIndicator.Error -> Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_error),
                    stringResource(R.string.cd_page_didnt_load),
                    Modifier.size(SiteIconSize),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        val editLabel = stringResource(R.string.cd_edit_address, address)
        TextButton(
            onClick = onEditAddress,
            modifier = Modifier
                .weight(1f)
                .height(IconSlot)
                .semantics { contentDescription = editLabel },
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = address,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showReader) {
            val readerLabel = stringResource(R.string.cd_reader_view)
            val readerIcon = painterResource(R.drawable.ic_chrome_reader_mode)
            IconButton(onClick = onReader, colors = iconColors) {
                Icon(readerIcon, readerLabel)
            }
        }
    }
}

/**
 * The address inside the browsing pill: the site mark, then the domain centered in a hairline field,
 * then Reader view. The field is the part that opens the address for editing.
 */
@Composable
private fun CompactAddressField(
    address: String,
    indicator: SiteIndicator,
    showReader: Boolean,
    onSiteInfo: () -> Unit,
    onEditAddress: () -> Unit,
    onReader: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (indicator) {
            SiteIndicator.Secure, SiteIndicator.NotSecure -> {
                val secure = indicator == SiteIndicator.Secure
                SlotButton(
                    onClick = onSiteInfo,
                    description = stringResource(if (secure) R.string.cd_site_info_secure else R.string.cd_site_info),
                ) {
                    Icon(
                        painterResource(if (secure) R.drawable.ic_lock else R.drawable.ic_public),
                        null,
                        Modifier.size(SiteIconSize),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            SiteIndicator.Error -> Box(Modifier.size(SlotSize), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_error),
                    stringResource(R.string.cd_page_didnt_load),
                    Modifier.size(SiteIconSize),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        val editLabel = stringResource(R.string.cd_edit_address, address)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 10.dp)
                .border(1.dp, colors.outlineVariant, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onEditAddress, role = Role.Button)
                .semantics { contentDescription = editLabel },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = address,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showReader) {
            SlotButton(onClick = onReader, description = stringResource(R.string.cd_reader_view)) {
                Icon(
                    painterResource(R.drawable.ic_chrome_reader_mode),
                    null,
                    Modifier.size(24.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Wavy progress centered on the bottom edge of [modifier], half of the 10 dp indicator below it. */
@Composable
internal fun AddressLoadProgress(
    loadProgress: Float?,
    modifier: Modifier = Modifier,
    overflow: Dp = WavyProgressIndicatorDefaults.LinearContainerHeight / 2,
) {
    if (loadProgress == null) return
    val colors = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    val loadingLabel = stringResource(R.string.cd_loading_page)
    LinearWavyProgressIndicator(
        progress = { loadProgress },
        modifier = modifier
            .offset(y = overflow)
            .semantics { contentDescription = loadingLabel },
        color = colors.primary,
        trackColor = colors.secondaryContainer,
        gapSize = 8.dp,
        amplitude = if (reducedMotion) FlatWave else WavyProgressIndicatorDefaults.indicatorAmplitude,
    )
}

@Composable
private fun SlotButton(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(SlotSize)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

private val SiteIconSize = 20.dp
private val IconSlot = 48.dp
private val SlotSize = 32.dp
private val FlatWave: (Float) -> Float = { 0f }

@PreviewLightDark
@Composable
private fun AddressBarPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
            AddressBar(
                address = "daylightjournal.com",
                indicator = SiteIndicator.Secure,
                readerAvailable = true,
                loadProgress = 0.58f,
                onSiteInfo = {},
                onEditAddress = {},
                onReader = {},
            )
            Box(Modifier.height(24.dp))
            AddressBar(
                address = "example.com/plain",
                indicator = SiteIndicator.NotSecure,
                readerAvailable = false,
                loadProgress = null,
                onSiteInfo = {},
                onEditAddress = {},
                onReader = {},
            )
            AddressBar(
                address = "nightbus-kansai.jp/timetable",
                indicator = SiteIndicator.Error,
                readerAvailable = true,
                loadProgress = null,
                onSiteInfo = {},
                onEditAddress = {},
                onReader = {},
            )
        }
    }
}
