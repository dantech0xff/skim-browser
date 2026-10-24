package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
) {
    val colors = MaterialTheme.colorScheme
    val showReader = readerAvailable && indicator != SiteIndicator.Error
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurfaceVariant)
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
                modifier = Modifier.fillMaxWidth().height(BarHeight),
            ) {
                Row(
                    // Without a trailing button the address keeps 16 dp from the end, as on 11.
                    modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = if (showReader) 4.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (indicator) {
                        SiteIndicator.Secure, SiteIndicator.NotSecure -> {
                            val secure = indicator == SiteIndicator.Secure
                            IconButton(onClick = onSiteInfo, colors = iconColors) {
                                Icon(
                                    painterResource(if (secure) R.drawable.ic_lock else R.drawable.ic_public),
                                    stringResource(if (secure) R.string.cd_site_info_secure else R.string.cd_site_info),
                                    Modifier.size(SiteIconSize),
                                )
                            }
                        }

                        SiteIndicator.Error -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
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
                            .height(48.dp)
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
                        IconButton(onClick = onReader, colors = iconColors) {
                            Icon(
                                painterResource(R.drawable.ic_chrome_reader_mode),
                                stringResource(R.string.cd_reader_view),
                            )
                        }
                    }
                }
            }
        }
        if (loadProgress != null) {
            val reducedMotion = rememberReducedMotion()
            val loadingLabel = stringResource(R.string.cd_loading_page)
            LinearWavyProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    // Centered on the bottom edge of the frame: half of the 10 dp indicator below it.
                    .offset(y = WavyProgressIndicatorDefaults.LinearContainerHeight / 2)
                    .semantics { contentDescription = loadingLabel },
                color = colors.primary,
                trackColor = colors.secondaryContainer,
                gapSize = 8.dp,
                amplitude = if (reducedMotion) FlatWave else WavyProgressIndicatorDefaults.indicatorAmplitude,
            )
        }
    }
}

private val BarHeight = 56.dp
private val SiteIconSize = 20.dp
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
