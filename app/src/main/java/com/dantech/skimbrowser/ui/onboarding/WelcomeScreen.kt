package com.dantech.skimbrowser.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.ShapeArt
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonSize
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * Welcome (01), on the first launch only: the Skim logo, the art, the promise, Get started and the
 * legal line. Terms of Service and Privacy Policy are links that open [termsUrl] and [privacyUrl]
 * through [onOpenLegal]; a link whose URL is empty (a debug build without them) is plain text.
 *
 * The middle block is centered in the space above the button and scrolls when the text is too large
 * for the screen.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onOpenLegal: (url: String) -> Unit,
    termsUrl: String,
    privacyUrl: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = 24.dp, top = 16.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Logo()
                WelcomeArt()
                Promise()
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SkimButton(
                text = stringResource(R.string.welcome_get_started),
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
                size = SkimButtonSize.M,
            )
            Text(
                text = legalText(termsUrl, privacyUrl, onOpenLegal),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The logo: the Sunny mark in primary (the brand, not an AI surface) beside the wordmark, 600. */
@Composable
private fun Logo() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SunnyMark(size = 36.dp, iconSize = 20.dp, brand = true)
        Text(
            stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** The 300 by 240 art: an article, a search and the Skim AI shapes, overlapping as drawn. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeArt() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(300.dp, 240.dp).clearAndSetSemantics {}) {
        ShapeArt(
            MaterialShapes.Cookie9Sided, size = 104.dp, icon = R.drawable.ic_article, iconSize = 40.dp,
            container = colors.primaryContainer, content = colors.onPrimaryContainer,
            modifier = Modifier.offset(184.dp, 4.dp),
        )
        ShapeArt(
            MaterialShapes.Clover4Leaf, size = 92.dp, icon = R.drawable.ic_travel_explore, iconSize = 36.dp,
            container = colors.secondaryContainer, content = colors.onSecondaryContainer,
            modifier = Modifier.offset(12.dp, 140.dp),
        )
        SunnyMark(size = 148.dp, iconSize = 60.dp, modifier = Modifier.offset(76.dp, 46.dp))
    }
}

/** The headline and the line under it, centered, at most 340 dp wide. */
@Composable
private fun Promise() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.widthIn(max = 340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.welcome_headline),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmallEmphasized.copy(lineBreak = LineBreak.Heading),
        )
        Text(
            stringResource(R.string.welcome_body),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * "By continuing you agree to the Terms of Service and Privacy Policy." with the two names as
 * links in primary, weight 500, underlined. The names go where the translation puts `%1$s` and
 * `%2$s`.
 */
@Composable
private fun legalText(termsUrl: String, privacyUrl: String, onOpenLegal: (String) -> Unit): AnnotatedString {
    val template = stringResource(R.string.welcome_legal)
    val names = listOf(
        stringResource(R.string.welcome_terms_of_service) to termsUrl,
        stringResource(R.string.welcome_privacy_policy) to privacyUrl,
    )
    val linkStyles = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        ),
    )
    return buildAnnotatedString {
        var start = 0
        for (match in PLACEHOLDER.findAll(template)) {
            append(template, start, match.range.first)
            val (name, url) = names[match.groupValues[1].toInt() - 1]
            if (url.isBlank()) {
                append(name)
            } else {
                withLink(LinkAnnotation.Clickable(tag = url, styles = linkStyles) { onOpenLegal(url) }) { append(name) }
            }
            start = match.range.last + 1
        }
        append(template, start, template.length)
    }
}

/** `%1$s` and `%2$s` in the legal line. */
private val PLACEHOLDER = Regex("%([12])\\\$s")

@PreviewLightDark
@Composable
private fun WelcomeScreenPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        WelcomeScreen(
            onGetStarted = {},
            onOpenLegal = {},
            termsUrl = "https://example.com/terms",
            privacyUrl = "https://example.com/privacy",
        )
    }
}
