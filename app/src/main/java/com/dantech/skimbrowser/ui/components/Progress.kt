package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion

/**
 * The loading indicator while Skim writes (06c): a 48 dp tertiaryContainer circle with a shape
 * morphing in onTertiaryContainer. The morph follows the material3 sequence, starting at
 * Cookie9Sided, the frame the board draws.
 *
 * With Remove animations on it is that frame, still: Cookie9Sided at 30 dp in the circle.
 *
 * @param contentDescription what is loading, for example "Writing summary"; accessibility reads
 *   the indicator as a progress bar with this label.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiLoadingIndicator(contentDescription: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val labelled = modifier.semantics { this.contentDescription = contentDescription }
    if (rememberReducedMotion()) {
        Box(
            modifier = labelled
                .progressSemantics()
                .size(IndicatorSize)
                .clip(CircleShape)
                .background(colors.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(StillShapeSize)
                    .clip(MaterialShapes.Cookie9Sided.toDrawnShape())
                    .background(colors.onTertiaryContainer),
            )
        }
    } else {
        ContainedLoadingIndicator(
            modifier = labelled,
            containerColor = colors.tertiaryContainer,
            indicatorColor = colors.onTertiaryContainer,
            polygons = AiIndicatorPolygons,
        )
    }
}

/**
 * Wavy progress while Skim writes (06c, 06b): an indeterminate wave in tertiary over a flat
 * tertiaryContainer track, stroke 4, amplitude 3, wavelength 40 and a gap of 8. It fills the width.
 *
 * With Remove animations on it is a still frame with a flat wave: the line covers the same part of
 * the track as the board draws, followed by the gap, the track and its stop dot.
 *
 * It is decoration: the [AiLoadingIndicator] beside it is the progress bar accessibility reads.
 */
@Composable
fun AiWavyProgress(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val drawn = modifier.fillMaxWidth().clearAndSetSemantics {}
    if (rememberReducedMotion()) {
        LinearWavyProgressIndicator(
            progress = { StillFrameProgress },
            modifier = drawn,
            color = colors.tertiary,
            trackColor = colors.tertiaryContainer,
            gapSize = WaveTrackGap,
            amplitude = { 0f },
            wavelength = Wavelength,
        )
    } else {
        LinearWavyProgressIndicator(
            modifier = drawn,
            color = colors.tertiary,
            trackColor = colors.tertiaryContainer,
            gapSize = WaveTrackGap,
            wavelength = Wavelength,
        )
    }
}

/** The material3 morph sequence of the loading indicator, started at Cookie9Sided. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val AiIndicatorPolygons: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Pill,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Oval,
    MaterialShapes.SoftBurst,
)

private val IndicatorSize = 48.dp
private val StillShapeSize = 30.dp
private val WaveTrackGap = 8.dp
private val Wavelength = 40.dp

/** Where the wave ends on 06c: 184 of 348 dp. */
private const val StillFrameProgress = 0.53f

@PreviewLightDark
@Composable
private fun AiProgressPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AiLoadingIndicator(contentDescription = stringResource(R.string.cd_writing_summary))
            AiWavyProgress()
        }
    }
}
