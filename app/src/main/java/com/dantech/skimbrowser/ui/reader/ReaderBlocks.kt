package com.dantech.skimbrowser.ui.reader

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dantech.skimbrowser.domain.page.PageBlock
import com.dantech.skimbrowser.domain.page.PageContent
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SkimSettings
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.ui.theme.GoogleSansFlex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The article Reader view shows. [blocks] is what a citation indexes. */
@Immutable
data class ReaderArticle(
    val host: String,
    val title: String,
    val byline: String?,
    val blocks: ImmutableList<PageBlock>,
)

/** Body copy of 08 before the text-size scale: 17 sp on 28 sp. */
internal val ReaderParagraph = TextStyle(fontSize = 17.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)

/** Code blocks stay monospaced. 14 sp on 20 sp, then the same scale as body copy. */
internal val ReaderCode = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Normal,
    fontFamily = FontFamily.Monospace,
)

/** 80, 90, … 200. Thirteen stops, the same ones the size slider uses. */
internal val ReaderScalePercents: IntProgression = SkimSettings.READER_TEXT_SCALE_RANGE step 10

/**
 * [base] at [percent] of its size. 100 leaves it alone; 110 turns 17/28 into 18.7/30.8.
 * Domain and byline are not passed through here: they stay at the size the board draws them.
 */
internal fun readerBodyStyle(base: TextStyle, percent: Int): TextStyle {
    val factor = percent.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE) / 100f
    return base.copy(fontSize = base.fontSize * factor, lineHeight = base.lineHeight * factor)
}

/** Sans is the app face. Serif is the platform serif, which is what 08b draws for that option. */
@Composable
@ReadOnlyComposable
internal fun readerTypeface(font: ReaderFont): FontFamily = when (font) {
    ReaderFont.SANS_SERIF -> GoogleSansFlex
    ReaderFont.SERIF -> FontFamily.Serif
}

/** How many rows sit above the blocks: the site, the title, and the byline when there is one. */
internal fun readerHeaderCount(article: ReaderArticle): Int = 2 + if (article.byline.isNullOrBlank()) 0 else 1

/**
 * The article in [page], or null when Readability says it is not an article.
 * A leading block that repeats the title is dropped, so the title is not shown twice.
 */
internal fun articleOf(page: PageContent): ReaderArticle? {
    if (!page.readerable) return null
    val blocks = page.blocks.dropLeadingTitle(page.title)
    return ReaderArticle(
        host = hostOf(page.url).orEmpty(),
        title = page.title,
        byline = page.byline?.takeIf { it.isNotBlank() },
        blocks = blocks.toImmutableList(),
    )
}

private fun List<PageBlock>.dropLeadingTitle(title: String): List<PageBlock> {
    val first = firstOrNull() ?: return this
    val same = title.isNotBlank() && first.text.trim() == title.trim()
    return if (same) drop(1) else this
}

/** The unscaled title face: 28/36, weight 500. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@ReadOnlyComposable
internal fun readerTitleBase(): TextStyle = MaterialTheme.typography.headlineMediumEmphasized

/** Headings inside the article: 22/28, weight 500, then the text-size scale. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@ReadOnlyComposable
internal fun readerHeadingBase(): TextStyle = MaterialTheme.typography.titleLargeEmphasized
