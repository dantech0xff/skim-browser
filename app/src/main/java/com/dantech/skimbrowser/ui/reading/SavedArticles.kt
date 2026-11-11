package com.dantech.skimbrowser.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.AiReply
import com.dantech.skimbrowser.domain.page.PageBlock
import com.dantech.skimbrowser.domain.reading.SavedArticle
import com.dantech.skimbrowser.domain.reading.SavedArticleRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.reader.ReaderArticle
import com.dantech.skimbrowser.ui.reader.ReaderScreen
import com.dantech.skimbrowser.ui.reader.ReaderUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SavedArticlesViewModel @Inject constructor(
    saved: SavedArticleRepository,
) : ViewModel() {
    val articles: StateFlow<List<SavedArticle>> = saved.articles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}

@Composable
fun SavedArticlesRoute(navigator: SkimNavigator) {
    val model = hiltViewModel<SavedArticlesViewModel>()
    val articles by model.articles.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
            stringResource(R.string.read_later_title),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (articles.isEmpty()) {
            Text(
                stringResource(R.string.read_later_empty),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        LazyColumn {
            items(articles, key = { it.id }) { article ->
                SkimListItem(
                    headline = article.title,
                    supporting = article.host,
                    index = 0,
                    count = 1,
                    onClick = { navigator.openSavedArticle(article.id) },
                )
            }
        }
    }
}

@HiltViewModel(assistedFactory = SavedArticleViewModel.Factory::class)
class SavedArticleViewModel @AssistedInject constructor(
    @Assisted private val articleId: Long,
    private val saved: SavedArticleRepository,
    private val settings: SettingsRepository,
    private val assistant: AiAssistant,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(articleId: Long): SavedArticleViewModel
    }

    private val article = MutableStateFlow<SavedArticle?>(null)
    private val summarizing = MutableStateFlow(false)

    val state: StateFlow<SavedReaderState> = kotlinx.coroutines.flow.combine(
        article,
        settings.settings,
        summarizing,
    ) { savedArticle, prefs, busy ->
        val current = savedArticle ?: return@combine SavedReaderState.Missing
        SavedReaderState.Ready(
            article = ReaderArticle(
                host = current.host,
                title = current.title,
                byline = current.byline,
                blocks = current.blocks.toImmutableList(),
            ),
            summary = current.summary,
            summarizing = busy,
            scalePercent = prefs.readerTextScalePercent,
            font = prefs.readerFont,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SavedReaderState.Loading)

    init {
        viewModelScope.launch { article.value = saved.article(articleId) }
    }

    fun setScale(percent: Int) {
        viewModelScope.launch { settings.setReaderTextScale(percent) }
    }

    fun setFont(font: com.dantech.skimbrowser.domain.settings.ReaderFont) {
        viewModelScope.launch { settings.setReaderFont(font) }
    }

    /** Asks for three points and keeps them on the article. No chat row is written. */
    fun summarize() {
        val current = article.value ?: return
        if (summarizing.value) return
        viewModelScope.launch {
            summarizing.value = true
            val text = StringBuilder()
            assistant.summarizeSaved(
                host = current.host,
                url = current.url,
                title = current.title,
                paragraphs = current.blocks.map(PageBlock::text),
                languageTag = Locale.getDefault().toLanguageTag(),
            ).collect { reply ->
                if (reply is AiReply.Text) text.append(reply.delta)
            }
            if (text.isNotEmpty()) {
                saved.setSummary(current.id, text.toString())
                article.value = current.copy(summary = text.toString())
            }
            summarizing.value = false
        }
    }
}

sealed interface SavedReaderState {
    data object Loading : SavedReaderState
    data object Missing : SavedReaderState
    data class Ready(
        val article: ReaderArticle,
        val summary: String?,
        val summarizing: Boolean,
        val scalePercent: Int,
        val font: com.dantech.skimbrowser.domain.settings.ReaderFont,
    ) : SavedReaderState
}

@Composable
fun SavedArticleRoute(articleId: Long, navigator: SkimNavigator) {
    val model = hiltViewModel<SavedArticleViewModel, SavedArticleViewModel.Factory>(
        key = "saved-$articleId",
    ) { factory ->
        factory.create(articleId)
    }
    val state by model.state.collectAsStateWithLifecycle()
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    when (val current = state) {
        SavedReaderState.Loading, SavedReaderState.Missing -> Unit
        is SavedReaderState.Ready -> ReaderScreen(
            article = current.article,
            summary = current.summary,
            summarizing = current.summarizing,
            scalePercent = current.scalePercent,
            font = current.font,
            panelOpen = panelOpen,
            canGoBack = false,
            canGoForward = false,
            tabCount = 0,
            incognito = false,
            actions = com.dantech.skimbrowser.ui.reader.ReaderActions(
                onExit = navigator::back,
                onToggleTextSize = { open -> panelOpen = open },
                onClosePanel = { panelOpen = false },
                onScale = model::setScale,
                onFont = model::setFont,
                onBack = navigator::back,
                onForward = {},
                onTabs = {},
                onMore = {},
                onSummarize = model::summarize,
            ),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
            highlightIndex = null,
            highlightNonce = 0,
            aiFab = {},
        )
    }
}
