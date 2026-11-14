package com.dantech.skimbrowser.ui.news

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.news.NewsTopic
import com.dantech.skimbrowser.ui.text.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The news wall inside the New tab page scroll. The first story is the lead card.
 * A long press offers a new tab, share, and hiding the source.
 */
fun LazyListScope.newsWall(
    state: NewsWallUiState,
    actions: NewsActions,
    allowThumbnails: () -> Boolean = { true },
) {
    item(key = "news-head") { NewsHead(state, actions) }
    val articles = state.articles
    if (articles.isEmpty()) {
        item(key = "news-empty") {
            Text(
                text = stringResource(if (state.refreshing) R.string.news_loading else R.string.news_empty),
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        item(key = "news-hero-${articles.first().link}") {
            NewsCard(
                articles.first(),
                actions,
                hero = true,
                allowThumbnails = allowThumbnails,
                modifier = Modifier.padding(top = 12.dp).animateItem(),
            )
        }
        items(
            count = articles.size - 1,
            key = { index -> articles[index + 1].link },
        ) { index ->
            NewsCard(
                articles[index + 1],
                actions,
                hero = false,
                allowThumbnails = allowThumbnails,
                modifier = Modifier.padding(top = 8.dp).animateItem(),
            )
        }
    }
}

@Composable
private fun NewsHead(state: NewsWallUiState, actions: NewsActions) {
    var sourcesOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.news_section),
                modifier = Modifier.weight(1f).semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            TextButton(onClick = { sourcesOpen = true }) {
                Text(stringResource(R.string.news_sources))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NewsTopic.entries.forEach { topic ->
                FilterChip(
                    selected = state.topic == topic,
                    onClick = { actions.onTopic(topic) },
                    label = { Text(stringResource(topic.label)) },
                )
            }
        }
    }
    if (sourcesOpen) {
        NewsSourcesDialog(state, actions, onDismiss = { sourcesOpen = false })
    }
}

@Composable
private fun NewsCard(
    card: NewsCardUi,
    actions: NewsActions,
    hero: Boolean,
    allowThumbnails: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().combinedClickable(
            onClick = { actions.onOpen(card) },
            onLongClick = { menu = true },
        ),
        shape = MaterialTheme.shapes.large,
        color = if (hero) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(if (hero) 0.dp else 12.dp)) {
            if (hero) {
                card.thumbnailUrl?.let { NewsThumbnail(it, allowThumbnails, Modifier.fillMaxWidth().height(180.dp)) }
                Column(Modifier.padding(16.dp)) { CardText(card, hero = true) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    card.thumbnailUrl?.let {
                        NewsThumbnail(it, allowThumbnails, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                    }
                    CardText(card, hero = false, modifier = Modifier.weight(1f))
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.news_open_new_tab)) },
                    onClick = {
                        menu = false
                        actions.onOpenInNewTab(card)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.news_share)) },
                    onClick = {
                        menu = false
                        actions.onShare(card)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.news_hide_source)) },
                    onClick = {
                        menu = false
                        actions.onHideSource(card)
                    },
                )
            }
        }
    }
}

@Composable
private fun CardText(card: NewsCardUi, hero: Boolean, modifier: Modifier = Modifier) {
    val time = card.timeText.asString()
    Column(modifier) {
        Text(
            text = card.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = if (hero) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            maxLines = if (hero) 3 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (time.isBlank()) card.sourceName else stringResource(R.string.news_meta, card.sourceName, time),
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewsThumbnail(url: String, allowThumbnails: () -> Boolean, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadNewsBitmapIfAllowed(allowThumbnails(), url) }.getOrNull()
        }
        if (loaded != null) bitmap = loaded.asImageBitmap()
    }
    val image = bitmap ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun NewsSourcesDialog(state: NewsWallUiState, actions: NewsActions, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.news_sources)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.sources.forEach { source ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(source.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = source.shown,
                            onCheckedChange = { actions.onToggleSource(source.id, it) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        if (state.feedError) actions.onDismissFeedError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.news_feed_url)) },
                    singleLine = true,
                    isError = state.feedError,
                    supportingText = if (state.feedError) {
                        { Text(stringResource(R.string.news_feed_url_invalid)) }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onAddFeed(draft) }) {
                Text(stringResource(R.string.news_add_feed))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.news_done)) }
        },
    )
}

private val NewsTopic.label: Int
    get() = when (this) {
        NewsTopic.TOP -> R.string.news_topic_top
        NewsTopic.TECH -> R.string.news_topic_tech
        NewsTopic.BUSINESS -> R.string.news_topic_business
        NewsTopic.WORLD -> R.string.news_topic_world
        NewsTopic.SPORTS -> R.string.news_topic_sports
        NewsTopic.ENTERTAINMENT -> R.string.news_topic_entertainment
    }
