package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import kotlin.math.roundToInt

/** Reader text sizes of 08b: 80 to 200 percent in steps of 10. */
internal const val ReaderSizeMinPercent = 80
internal const val ReaderSizeMaxPercent = 200
private const val ReaderSizeStepPercent = 10

/** The 11 stops between the ends give the 13 sizes of 80, 90, … 200 percent. */
private const val ReaderSizeSteps = (ReaderSizeMaxPercent - ReaderSizeMinPercent) / ReaderSizeStepPercent - 1

/**
 * The size control of the reader text settings (08b): a small A, the slider, a large A. The slider
 * runs from 80 to 200 percent in steps of 10 and applies live: every stop calls [onPercent], and
 * the slider shows [percent] (it moves once the caller passes the new value back).
 *
 * The track is the Expressive one drawn on the board: 16 dp, active part in primary, inactive part
 * in secondaryContainer with a primary stop dot at its end, a 4 by 44 handle 6 dp from each side.
 * The steps have no tick marks, as drawn. Accessibility reads it as "Size" with the percentage as
 * its state ("110%").
 */
@Composable
fun ReaderSizeSlider(percent: Int, onPercent: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shown = readerSizePercent(percent.toFloat())
    val state = rememberSliderState(
        value = shown.toFloat(),
        steps = ReaderSizeSteps,
        trackRange = ReaderSizeMinPercent.toFloat()..ReaderSizeMaxPercent.toFloat(),
    )
    // The caller owns the size: the slider follows it whenever it changes.
    SideEffect { state.value = shown.toFloat() }
    val colors = MaterialTheme.colorScheme
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.primary,
        activeTrackColor = colors.primary,
        inactiveTrackColor = colors.secondaryContainer,
    )
    val label = stringResource(R.string.reader_size)
    val percentLabel = stringResource(R.string.reader_size_percent, shown)
    val glyph = stringResource(R.string.reader_size_glyph)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GlyphGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = glyph,
            modifier = Modifier.clearAndSetSemantics {},
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            state = state,
            onValueChange = { value -> onPercent(readerSizePercent(value)) },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = label
                    stateDescription = percentLabel
                },
            colors = sliderColors,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = sliderColors,
                    drawTick = { _, _ -> },
                )
            },
        )
        Text(
            text = glyph,
            modifier = Modifier.clearAndSetSemantics {},
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/** The reader size [value] stands for: the nearest step of 10 percent within 80 to 200. */
internal fun readerSizePercent(value: Float): Int {
    val steps = ((value - ReaderSizeMinPercent) / ReaderSizeStepPercent).roundToInt()
    return (ReaderSizeMinPercent + steps * ReaderSizeStepPercent).coerceIn(ReaderSizeMinPercent, ReaderSizeMaxPercent)
}

private val GlyphGap = 16.dp

@PreviewLightDark
@Composable
private fun ReaderSizeSliderPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        var percent by remember { mutableIntStateOf(110) }
        ReaderSizeSlider(
            percent = percent,
            onPercent = { percent = it },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(24.dp),
        )
    }
}
