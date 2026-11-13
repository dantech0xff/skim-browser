package com.dantech.skimbrowser.ui.browsing

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.phishing.PhishingReason
import com.dantech.skimbrowser.domain.phishing.SafetyWarning
import com.dantech.skimbrowser.domain.text.cutTo
import com.dantech.skimbrowser.domain.text.toVisibleText
import com.dantech.skimbrowser.ui.components.ShapeArt
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonSize
import com.dantech.skimbrowser.ui.components.SkimButtonStyle
import com.dantech.skimbrowser.ui.components.ToolbarMetrics

/**
 * A full-screen warning over a page. [Strong] is for a confirmed threat. [Soft] is for an address
 * that only looks suspicious. Back to safety is the primary action. Proceeding stays behind Advanced.
 *
 * The page underneath does not receive touches. System Back does what [onBackToSafety] does.
 * An interstitial for a certificate can use this same layout.
 */
enum class SafetyWarningTone { Strong, Soft }

/** The warning for [warning], with the reason in plain words. */
@Composable
fun SafetyWarningContent(
    warning: SafetyWarning,
    onBackToSafety: () -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title: String
    val body: String
    val tone: SafetyWarningTone
    when (warning) {
        is SafetyWarning.SafeBrowsing -> {
            title = stringResource(R.string.safety_dangerous_title)
            body = stringResource(R.string.safety_dangerous_body)
            tone = SafetyWarningTone.Strong
        }
        is SafetyWarning.Heuristic -> {
            title = stringResource(R.string.safety_check_title)
            body = heuristicBody(warning)
            tone = SafetyWarningTone.Soft
        }
    }
    SafetyWarningPage(
        title = title,
        body = body,
        detail = warning.url.toVisibleText().cutTo(MAX_WARNING_URL),
        onBackToSafety = onBackToSafety,
        onProceed = onProceed,
        tone = tone,
        modifier = modifier,
    )
}

@Composable
private fun heuristicBody(warning: SafetyWarning.Heuristic): String {
    val brand = warning.brand
    return when (warning.reason) {
        PhishingReason.USER_INFO -> stringResource(R.string.safety_user_info)
        PhishingReason.RAW_IP -> stringResource(R.string.safety_raw_ip)
        PhishingReason.MIXED_SCRIPT -> stringResource(R.string.safety_mixed_script)
        PhishingReason.SUBDOMAIN -> named(R.string.safety_subdomain, brand)
        PhishingReason.LOOKALIKE -> named(R.string.safety_lookalike, brand)
        PhishingReason.HOMOGRAPH -> named(R.string.safety_homograph, brand)
    }
}

@Composable
private fun named(withBrand: Int, brand: String?): String =
    if (brand.isNullOrEmpty()) stringResource(R.string.safety_mixed_script) else stringResource(withBrand, brand)

private const val MAX_WARNING_URL = 256

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SafetyWarningPage(
    title: String,
    body: String,
    detail: String,
    onBackToSafety: () -> Unit,
    onProceed: () -> Unit,
    tone: SafetyWarningTone,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val art = if (tone == SafetyWarningTone.Strong) {
        WarningArt(MaterialShapes.SoftBurst, R.drawable.ic_error, colors.errorContainer, colors.onErrorContainer)
    } else {
        WarningArt(MaterialShapes.Clover8Leaf, R.drawable.ic_shield, colors.secondaryContainer, colors.onSecondaryContainer)
    }
    var advanced by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBackToSafety)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .pointerInput(Unit) {},
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = SideMargin, end = SideMargin, bottom = AboveToolbar),
            verticalArrangement = Arrangement.spacedBy(BlockGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShapeArt(
                shape = art.shape,
                size = ArtSize,
                icon = art.icon,
                iconSize = ArtIconSize,
                container = art.container,
                content = art.content,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    style = typography.headlineMediumEmphasized.copy(lineBreak = LineBreak.Heading),
                )
                Text(
                    text = body,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph),
                )
                Text(
                    text = detail,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = typography.bodySmall,
                )
            }
            Column(
                Modifier.widthIn(max = ButtonColumnWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SkimButton(
                    text = stringResource(R.string.safety_back),
                    onClick = onBackToSafety,
                    modifier = Modifier.fillMaxWidth(),
                    size = SkimButtonSize.M,
                    icon = R.drawable.ic_arrow_back,
                )
                SkimButton(
                    text = stringResource(R.string.safety_advanced),
                    onClick = { advanced = !advanced },
                    modifier = Modifier.fillMaxWidth(),
                    size = SkimButtonSize.M,
                    style = SkimButtonStyle.Text,
                )
                if (advanced) {
                    Text(
                        text = stringResource(R.string.safety_proceed_note),
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = typography.bodyMedium,
                    )
                    SkimButton(
                        text = stringResource(R.string.safety_proceed),
                        onClick = onProceed,
                        modifier = Modifier.fillMaxWidth(),
                        size = SkimButtonSize.M,
                        style = SkimButtonStyle.Text,
                    )
                }
            }
        }
    }
}

private class WarningArt(
    val shape: RoundedPolygon,
    @param:DrawableRes val icon: Int,
    val container: Color,
    val content: Color,
)

private val SideMargin = 32.dp
private val BlockGap = 32.dp
private val ArtSize = 128.dp
private val ArtIconSize = 56.dp
private val ButtonColumnWidth = 280.dp
private val AboveToolbar = ToolbarMetrics.BrowsingBottomOffset + ToolbarMetrics.BrowsingClusterHeight + 32.dp
