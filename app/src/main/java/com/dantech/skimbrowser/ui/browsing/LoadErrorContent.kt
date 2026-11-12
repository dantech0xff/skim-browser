package com.dantech.skimbrowser.ui.browsing

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.certificate.CertificateFault
import com.dantech.skimbrowser.domain.certificate.CertificateWarning
import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.ui.components.ShapeArt
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonSize
import com.dantech.skimbrowser.ui.components.SkimButtonStyle
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * Page failed to load (11), over the page, which stays loaded below it so its history is kept. The
 * state follows [error]: Can't reach this site (SoftBurst, public_off), No internet connection
 * (Cookie12Sided, wifi_off, with Internet settings) and This site is taking too long (Clover8Leaf,
 * schedule); a page whose renderer crashed too often shows This page stopped working (SoftBurst,
 * error). Each has the 128 dp art in primaryContainer, the title, the body naming the site, Try
 * again, and the error's code in bodySmall. A certificate refusal uses the same page: the host, one
 * sentence for the primary error, Back to safety, and Proceed anyway under Advanced.
 *
 * The content is centered in the screen, clear of the status bar and the bottom cluster, and scrolls
 * when the screen is too short for it. It is opaque and takes every touch, so the page below gets none.
 *
 * @param onInternetSettings opens Android's Internet panel; only No internet offers it.
 * @param warning when set, this is the certificate interstitial instead of [error].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadErrorContent(
    error: LoadError?,
    onTryAgain: () -> Unit,
    onInternetSettings: () -> Unit,
    onOpenHttp: (() -> Unit)? = null,
    warning: CertificateWarning? = null,
    onLeaveUnsafeCertificate: () -> Unit = {},
    onProceedDespiteCertificate: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val certificate = warning
    val look = if (certificate == null) errorLookOf(error!!) else certificateLook(certificate.fault)
    val site = if (certificate == null) pageAddressOf(error!!.url) else certificate.host
    var advanced by remember(certificate) { mutableStateOf(false) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            // Touches on the error never reach the page below.
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
                shape = look.shape,
                size = ArtSize,
                icon = look.icon,
                iconSize = ArtIconSize,
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(look.title),
                    modifier = Modifier.semantics { heading() },
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    style = typography.headlineMediumEmphasized.copy(lineBreak = LineBreak.Heading),
                )
                if (certificate != null) {
                    Text(site, color = colors.onSurface, textAlign = TextAlign.Center, style = typography.titleMedium)
                }
                Text(
                    text = if (certificate != null || !look.bodyNamesSite) stringResource(look.body) else stringResource(look.body, site),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph),
                )
            }
            Column(
                Modifier.widthIn(max = ButtonColumnWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkimButton(
                    text = stringResource(if (certificate != null) R.string.certificate_back_to_safety else R.string.action_try_again),
                    onClick = if (certificate != null) onLeaveUnsafeCertificate else onTryAgain,
                    modifier = Modifier.fillMaxWidth(),
                    size = SkimButtonSize.M,
                    icon = if (certificate != null) R.drawable.ic_arrow_back else R.drawable.ic_refresh,
                )
                if (certificate != null) {
                    SkimButton(
                        text = stringResource(R.string.certificate_advanced),
                        onClick = { advanced = !advanced },
                        modifier = Modifier.fillMaxWidth(),
                        size = SkimButtonSize.M,
                        style = SkimButtonStyle.Text,
                    )
                    if (advanced) {
                        SkimButton(
                            text = stringResource(R.string.certificate_proceed),
                            onClick = onProceedDespiteCertificate,
                            modifier = Modifier.fillMaxWidth(),
                            size = SkimButtonSize.M,
                            style = SkimButtonStyle.Tonal,
                        )
                    }
                } else if (onOpenHttp != null) {
                    SkimButton(
                        text = stringResource(R.string.error_open_http),
                        onClick = onOpenHttp,
                        modifier = Modifier.fillMaxWidth(),
                        size = SkimButtonSize.M,
                        style = SkimButtonStyle.Tonal,
                        icon = R.drawable.ic_public,
                    )
                }
                if (certificate == null && error is LoadError.NoInternet) {
                    SkimButton(
                        text = stringResource(R.string.error_internet_settings),
                        onClick = onInternetSettings,
                        modifier = Modifier.fillMaxWidth(),
                        size = SkimButtonSize.M,
                        style = SkimButtonStyle.Tonal,
                        icon = R.drawable.ic_wifi,
                    )
                }
            }
            if (certificate == null) Text(error!!.codeName, color = colors.onSurfaceVariant, style = typography.bodySmall)
        }
    }
}

/** What a state of Page failed to load shows: its art, title and body. */
internal class ErrorLook(
    val shape: RoundedPolygon,
    @param:DrawableRes val icon: Int,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    /** The body names the site (every state but No internet). */
    val bodyNamesSite: Boolean,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun certificateLook(fault: CertificateFault): ErrorLook = ErrorLook(
    MaterialShapes.SoftBurst,
    R.drawable.ic_lock,
    R.string.certificate_error_title,
    certificateExplanation(fault),
    bodyNamesSite = false,
)

@StringRes
internal fun certificateExplanation(fault: CertificateFault): Int = when (fault) {
    CertificateFault.NotYetValid -> R.string.certificate_error_not_yet_valid
    CertificateFault.Expired -> R.string.certificate_error_expired
    CertificateFault.HostnameMismatch -> R.string.certificate_error_hostname
    CertificateFault.Untrusted -> R.string.certificate_error_untrusted
    CertificateFault.DateInvalid -> R.string.certificate_error_date
    CertificateFault.Invalid -> R.string.certificate_error_invalid
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun errorLookOf(error: LoadError): ErrorLook = when (error) {
    is LoadError.Unreachable ->
        ErrorLook(MaterialShapes.SoftBurst, R.drawable.ic_public_off, R.string.error_unreachable_title, R.string.error_unreachable_body, bodyNamesSite = true)
    is LoadError.NoInternet ->
        ErrorLook(MaterialShapes.Cookie12Sided, R.drawable.ic_wifi_off, R.string.error_no_internet_title, R.string.error_no_internet_body, bodyNamesSite = false)
    is LoadError.TimedOut ->
        ErrorLook(MaterialShapes.Clover8Leaf, R.drawable.ic_schedule, R.string.error_timed_out_title, R.string.error_timed_out_body, bodyNamesSite = true)
    is LoadError.Crashed ->
        ErrorLook(MaterialShapes.SoftBurst, R.drawable.ic_error, R.string.error_crashed_title, R.string.error_crashed_body, bodyNamesSite = true)
}

private val SideMargin = 32.dp
private val BlockGap = 32.dp
private val ArtSize = 128.dp
private val ArtIconSize = 56.dp
private val ButtonColumnWidth = 280.dp

/** Space kept under the content for the bottom cluster, above the navigation bar inset. */
private val AboveToolbar = ToolbarMetrics.BrowsingBottomOffset + ToolbarMetrics.BrowsingClusterHeight + 32.dp

@PreviewLightDark
@Composable
private fun LoadErrorContentPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        LoadErrorContent(
            error = LoadError.NoInternet("https://nightbus-kansai.jp/timetable", "ERR_INTERNET_DISCONNECTED"),
            onTryAgain = {},
            onInternetSettings = {},
        )
    }
}
