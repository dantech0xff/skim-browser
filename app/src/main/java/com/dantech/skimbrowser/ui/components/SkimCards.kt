package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * A card of the design: a rounded surface holding a column. [radius] is 20 dp for cards and 28 dp
 * for hero cards (03, 04); [color] is surfaceContainerLow for cards on surface, and
 * surfaceContainerLowest for cards on surfaceContainerLow. Card padding is 16 dp unless the board
 * draws another one (20 dp on 03).
 *
 * With [onClick] the whole card is one button: TalkBack reads its text as the button's label.
 */
@Composable
fun SkimCard(
    modifier: Modifier = Modifier,
    radius: Dp = CardRadius,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(CardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val column: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.semantics { role = Role.Button },
            shape = shape,
            color = color,
            content = column,
        )
    } else {
        Surface(modifier = modifier, shape = shape, color = color, content = column)
    }
}

/**
 * The AI hero card on Home (04), "Summarize my N open tabs": radius 28, tertiaryContainer, at least
 * 88 dp high. A 48 dp Cookie9Sided tile in onTertiaryContainer with the filled auto_awesome in
 * tertiaryContainer, the [title] in titleMediumEmphasized over the [body] in bodyMedium, and an
 * arrow at the end. The card is one button labelled by its text.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiHeroCard(title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SkimCard(
        modifier = modifier.fillMaxWidth(),
        radius = HeroRadius,
        color = colors.tertiaryContainer,
        onClick = onClick,
        contentPadding = HeroPadding,
    ) {
        Row(
            modifier = Modifier.heightIn(min = HeroMinHeight - HeroPadding.calculateTopPadding() * 2),
            horizontalArrangement = Arrangement.spacedBy(HeroGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeArt(
                shape = MaterialShapes.Cookie9Sided,
                size = HeroTileSize,
                icon = R.drawable.ic_auto_awesome_filled,
                iconSize = HeroIconSize,
                container = colors.onTertiaryContainer,
                content = colors.tertiaryContainer,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null)
        }
    }
}

/**
 * The card of an AI answer or summary (06, 06b, 06c): surfaceContainerLowest, radius 20, padding
 * 16 with 8 at the bottom, where the 40 dp copy and feedback buttons sit. Its parts sit 12 dp apart
 * (06; 06b uses 16).
 */
@Composable
fun AnswerCard(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    SkimCard(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentPadding = AnswerPadding,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

private val CardRadius = 20.dp
private val CardPadding = 16.dp
private val HeroRadius = 28.dp
private val HeroMinHeight = 88.dp
private val HeroPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 20.dp, bottom = 16.dp)
private val HeroGap = 16.dp
private val HeroTileSize = 48.dp
private val HeroIconSize = 24.dp
private val AnswerPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)

@PreviewLightDark
@Composable
private fun SkimCardPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .background(colors.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkimCard(Modifier.fillMaxWidth(), radius = HeroRadius, contentPadding = PaddingValues(20.dp)) {
                Text(stringResource(R.string.setup_import_title), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(stringResource(R.string.setup_import_body), style = MaterialTheme.typography.bodyMedium)
            }
            SkimCard(Modifier.fillMaxWidth(), onClick = {}) {
                Text(stringResource(R.string.home_continue_with_skim), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AiHeroCardPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            AiHeroCard(
                title = pluralStringResource(R.plurals.home_summarize_tabs, 7, 7),
                body = stringResource(R.string.home_summarize_tabs_body),
                onClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AnswerCardPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .background(colors.surfaceContainerLow)
                .padding(16.dp),
        ) {
            AnswerCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_auto_awesome_filled),
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = colors.tertiary,
                    )
                    Text(stringResource(R.string.ai_summary), color = colors.tertiary, style = MaterialTheme.typography.titleSmallEmphasized)
                }
                Text(stringResource(R.string.ai_not_enough_text_body), style = MaterialTheme.typography.bodyMedium)
                CompactIconButton(R.drawable.ic_content_copy, stringResource(R.string.cd_copy_summary), onClick = {})
            }
        }
    }
}
