package com.dantech.skimbrowser.ui.reader

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.page.PageBlock
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.ui.components.BrowserToolbar
import com.dantech.skimbrowser.ui.components.TextSizeToggle
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion

/** What the article asks its owner to do. The route owns navigation. */
class ReaderActions(
    val onExit: () -> Unit,
    val onToggleTextSize: (Boolean) -> Unit,
    val onClosePanel: () -> Unit,
    val onScale: (Int) -> Unit,
    val onFont: (ReaderFont) -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onTabs: () -> Unit,
    val onMore: () -> Unit,
)

/**
 * Reader view once the article is ready. [panelOpen] replaces the floating toolbar with the text
 * settings panel. [highlightIndex] is a block of [article]; [highlightNonce] changes for each
 * citation so the same block can flash again.
 */
@Composable
fun ReaderScreen(
    article: ReaderArticle,
    scalePercent: Int,
    font: ReaderFont,
    panelOpen: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    incognito: Boolean,
    actions: ReaderActions,
    listState: LazyListState,
    highlightIndex: Int?,
    highlightNonce: Int,
    aiFab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val belowArticle = if (panelOpen) {
        320.dp
    } else {
        navigationBar + ToolbarMetrics.BottomOffset + ToolbarMetrics.Height + 16.dp
    }
    val reduced = rememberReducedMotion()
    val typeface = readerTypeface(font)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TitleRow(panelOpen, actions.onExit, actions.onToggleTextSize)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(panelOpen) {
                        if (!panelOpen) return@pointerInput
                        detectTapGestures { actions.onClosePanel() }
                    },
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = belowArticle),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "host") { Domain(article.host) }
                item(key = "title") {
                    Text(
                        text = article.title,
                        modifier = Modifier.semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = readerBodyStyle(readerTitleBase(), scalePercent).copy(fontFamily = typeface),
                    )
                }
                if (!article.byline.isNullOrBlank()) {
                    item(key = "byline") { Byline(article.byline) }
                }
                itemsIndexed(article.blocks, key = { _, block -> block.id }) { index, block ->
                    ReaderBlock(
                        block = block,
                        scalePercent = scalePercent,
                        font = font,
                        first = index == 0,
                        highlighted = highlightIndex == index,
                        highlightNonce = highlightNonce,
                        reducedMotion = reduced,
                    )
                }
            }
        }
        if (panelOpen) {
            ReaderTextPanel(
                percent = scalePercent,
                font = font,
                onPercent = actions.onScale,
                onFont = actions.onFont,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            BrowserToolbar(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                tabCount = tabCount,
                incognito = incognito,
                onBack = actions.onBack,
                onForward = actions.onForward,
                onTabs = actions.onTabs,
                onMore = actions.onMore,
                aiFab = aiFab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = ToolbarMetrics.BottomOffset),
            )
        }
    }
}

@Composable
private fun TitleRow(panelOpen: Boolean, onExit: () -> Unit, onToggleTextSize: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.cd_exit_reader_view),
                tint = colors.onSurface,
            )
        }
        TextSizeToggle(checked = panelOpen, onCheckedChange = onToggleTextSize)
    }
}

@Composable
private fun Domain(host: String) {
    Text(
        text = host,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun Byline(byline: String) {
    Text(
        text = byline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ReaderBlock(
    block: PageBlock,
    scalePercent: Int,
    font: ReaderFont,
    first: Boolean,
    highlighted: Boolean,
    highlightNonce: Int,
    reducedMotion: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val typeface = readerTypeface(font)
    val heading = block.tag in HEADINGS
    val quote = block.tag == "blockquote"
    val caption = block.tag == "figcaption"
    val code = block.tag == "pre"
    val base = when {
        code -> ReaderCode
        caption -> MaterialTheme.typography.bodySmall
        heading && block.tag == "h1" -> readerTitleBase()
        heading -> readerHeadingBase()
        else -> ReaderParagraph
    }
    val scaled = if (caption) base else readerBodyStyle(base, scalePercent)
    val style = if (code) scaled else scaled.copy(fontFamily = typeface)
    val color = if (quote || caption) colors.onSurfaceVariant else colors.onSurface
    val indent = if (block.tag == "li" || quote) 16.dp else 0.dp
    val text = if (block.tag == "li") "• ${block.text}" else block.text
    // Reader highlight is primary at 32%, so the sentence stays readable on the saturated accent.
    val flash = colors.primary.copy(alpha = 0.32f)
    val wash = remember { Animatable(Color.Transparent) }
    LaunchedEffect(highlightNonce) {
        if (!highlighted) return@LaunchedEffect
        wash.snapTo(flash)
        if (reducedMotion) {
            withFrameNanos { }
            wash.snapTo(Color.Transparent)
        } else {
            wash.animateTo(Color.Transparent, tween(FLASH_MS))
        }
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (first) 4.dp else 0.dp, start = indent)
            .background(wash.value),
        color = color,
        style = style,
    )
}

private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

private const val FLASH_MS = 2_400
