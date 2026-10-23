package com.dantech.skimbrowser.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.settings.ReaderFont
import com.dantech.skimbrowser.ui.components.ConnectedToggleGroup
import com.dantech.skimbrowser.ui.components.ReaderSizeSlider
import com.dantech.skimbrowser.ui.components.SheetHandle
import com.dantech.skimbrowser.ui.components.SheetShape

/**
 * Text settings (08b), drawn as a sheet along the bottom with no scrim. A scrim would take the
 * first tap, so Exit would need a second one. Taps on the article, above this panel, close it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderTextPanel(
    percent: Int,
    font: ReaderFont,
    onPercent: (Int) -> Unit,
    onFont: (ReaderFont) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(R.string.cd_text_settings)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = SheetShape)
            .background(colors.surfaceContainerLow, SheetShape)
            .semantics { paneTitle = title }
            .navigationBarsPadding()
            .padding(bottom = 32.dp),
    ) {
        SheetHandle()
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_text),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.reader_size),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(R.string.reader_size_percent, percent),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ReaderSizeSlider(percent = percent, onPercent = onPercent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.reader_font),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                ConnectedToggleGroup(
                    options = listOf(
                        stringResource(R.string.reader_font_sans_serif),
                        stringResource(R.string.reader_font_serif),
                    ),
                    selected = if (font == ReaderFont.SERIF) 1 else 0,
                    onSelect = { index -> onFont(if (index == 1) ReaderFont.SERIF else ReaderFont.SANS_SERIF) },
                    contentDescription = stringResource(R.string.reader_font),
                    fontFamilies = listOf(null, FontFamily.Serif),
                )
            }
        }
    }
}
