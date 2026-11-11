package com.dantech.skimbrowser.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.AiFailure
import com.dantech.skimbrowser.domain.ai.AiReply
import com.dantech.skimbrowser.domain.page.PageBlock
import com.dantech.skimbrowser.domain.permission.ResolveSkimAiConsent
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiAudience
import com.dantech.skimbrowser.domain.permission.SkimAiConsent
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.reading.SavedArticle
import com.dantech.skimbrowser.domain.reading.SavedArticleRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.ui.ai.AiPermissionDialog
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SegmentedColumn
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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
    LargeTitleScaffold(
        title = stringResource(R.string.read_later_title),
        onBack = navigator::back,
    ) {
        if (articles.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.read_later_empty),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                SegmentedColumn {
                    articles.forEachIndexed { index, article ->
                        SkimListItem(
                            headline = article.title,
                            supporting = article.host,
                            index = index,
                            count = articles.size,
                            onClick = { navigator.openSavedArticle(article.id) },
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel(assistedFactory = SavedArticleViewModel.Factory::class)
class SavedArticleViewModel @AssistedInject constructor(
    @Assisted private val articleId: Long,
    private val saved: SavedArticleRepository,
    private val settings: SettingsRepository,
    private val sites: SitePermissionRepository,
    private val assistant: AiAssistant,
) : ViewModel() {

    private val resolveConsent = ResolveSkimAiConsent()

    @AssistedFactory
    interface Factory {
        fun create(articleId: Long): SavedArticleViewModel
    }

    private val loaded = MutableStateFlow(false)
    private val article = MutableStateFlow<SavedArticle?>(null)
    private val summarizing = MutableStateFlow(false)
    private val needsPermission = MutableStateFlow(false)
    private var request: Job? = null

    val state: StateFlow<SavedReaderState> = kotlinx.coroutines.flow.combine(
        loaded,
        article,
        settings.settings,
        summarizing,
        needsPermission,
    ) { ready, savedArticle, prefs, busy, ask ->
        if (!ready) return@combine SavedReaderState.Loading
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
            needsPermission = ask,
            scalePercent = prefs.readerTextScalePercent,
            font = prefs.readerFont,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SavedReaderState.Loading)

    init {
        viewModelScope.launch {
            article.value = saved.article(articleId)
            loaded.value = true
        }
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
        if (request?.isActive == true) return
        request?.cancel()
        request = viewModelScope.launch { runSummary(current) }
    }

    /** Remembers that this site may be read, then summarizes. A request already running is left alone. */
    fun allowAndSummarize() {
        val host = article.value?.host ?: return
        if (request?.isActive == true) return
        request?.cancel()
        request = viewModelScope.launch {
            sites.setSkimAi(host, SkimAiState.ALLOWED)
            needsPermission.value = false
            val current = article.value ?: return@launch
            runSummary(current)
        }
    }

    private suspend fun runSummary(current: SavedArticle) {
        if (!siteAllowed(current.host)) {
            needsPermission.value = true
            return
        }
        needsPermission.value = false
        summarizing.value = true
        try {
            val text = StringBuilder()
            var failed = false
            assistant.summarizeSaved(
                host = current.host,
                url = current.url,
                title = current.title,
                paragraphs = current.blocks.map(PageBlock::text),
                languageTag = Locale.getDefault().toLanguageTag(),
            ).collect { reply ->
                when (reply) {
                    is AiReply.Text -> text.append(reply.delta)
                    is AiReply.Failed -> {
                        failed = true
                        if (reply.failure is AiFailure.NotAllowed) needsPermission.value = true
                    }
                    else -> Unit
                }
            }
            if (!failed && text.isNotEmpty()) {
                saved.setSummary(current.id, text.toString())
                article.value = current.copy(summary = text.toString())
            }
        } finally {
            summarizing.value = false
        }
    }

    fun dismissPermission() {
        needsPermission.value = false
    }

    private suspend fun siteAllowed(host: String): Boolean {
        val state = sites.get(host)?.skimAi ?: SkimAiState.UNSET
        val askFirst = settings.settings.first().askBeforeSending
        return resolveConsent(state, askFirst, SkimAiAudience.PAGE) == SkimAiConsent.Allowed
    }
}

sealed interface SavedReaderState {
    data object Loading : SavedReaderState
    data object Missing : SavedReaderState
    data class Ready(
        val article: ReaderArticle,
        val summary: String?,
        val summarizing: Boolean,
        val needsPermission: Boolean,
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
        SavedReaderState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        SavedReaderState.Missing -> LargeTitleScaffold(
            title = stringResource(R.string.read_later_title),
            onBack = navigator::back,
        ) {
            item {
                Text(
                    stringResource(R.string.saved_article_missing),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        is SavedReaderState.Ready -> Box(Modifier.fillMaxSize()) {
            ReaderScreen(
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
            if (current.needsPermission) {
                Dialog(
                    onDismissRequest = model::dismissPermission,
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    AiPermissionDialog(
                        host = current.article.host,
                        onAllow = model::allowAndSummarize,
                        onNotNow = model::dismissPermission,
                    )
                }
            }
        }
    }
}
