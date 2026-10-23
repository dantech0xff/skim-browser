package com.dantech.skimbrowser.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.ui.navigation.ReaderKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.shell.TabAiFab
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion

/** Reader view (08) of [key]'s tab, with the text settings panel (08b) over it. */
@Composable
fun ReaderRoute(key: ReaderKey, container: AppContainer, navigator: SkimNavigator) {
    val model = viewModel(key = "reader-${key.tabId}") {
        ReaderViewModel(
            key.tabId,
            container.tabManager,
            container.settings,
            container.setReaderTextScale,
            container.setReaderFont,
            container.appScope,
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    val tabs by container.tabManager.state.collectAsStateWithLifecycle()
    val runtime by container.tabManager.runtime(key.tabId).collectAsStateWithLifecycle()
    val tab = tabs.tab(key.tabId)
    var panelOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler { navigator.remove(key) }
    LaunchedEffect(state, tab) {
        if (state is ReaderUiState.Unavailable || (state !is ReaderUiState.Loading && tab == null)) {
            navigator.remove(key)
        }
    }

    when (val current = state) {
        ReaderUiState.Loading, ReaderUiState.Unavailable -> {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        }
        is ReaderUiState.Ready -> {
            // Saved inside Ready, so a restored scroll applies once the article is on screen.
            val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            var highlightIndex by rememberSaveable { mutableIntStateOf(-1) }
            var highlightNonce by rememberSaveable { mutableIntStateOf(0) }
            val reduced = rememberReducedMotion()
            val article = current.article
            LaunchedEffect(listState) {
                container.readerHighlights.requests.collect { (tabId, index) ->
                    if (tabId != key.tabId || index !in article.blocks.indices) return@collect
                    val item = index + readerHeaderCount(article)
                    if (reduced) listState.scrollToItem(item) else listState.animateScrollToItem(item)
                    highlightIndex = index
                    highlightNonce += 1
                }
            }
            ReaderScreen(
                article = article,
                scalePercent = current.scalePercent,
                font = current.font,
                panelOpen = panelOpen,
                canGoBack = runtime.canGoBack,
                canGoForward = runtime.canGoForward,
                tabCount = tabs.tabs.count { it.incognito == tab?.incognito },
                incognito = tab?.incognito == true,
                actions = ReaderActions(
                    onExit = { navigator.remove(key) },
                    onToggleTextSize = { open -> panelOpen = open },
                    onClosePanel = { panelOpen = false },
                    onScale = model::setScale,
                    onFont = model::setFont,
                    onBack = {
                        navigator.remove(key)
                        model.goBack()
                    },
                    onForward = {
                        navigator.remove(key)
                        model.goForward()
                    },
                    onTabs = { navigator.openTabs(tab?.incognito == true) },
                    onMore = { navigator.openMoreMenu(key.tabId) },
                ),
                listState = listState,
                highlightIndex = highlightIndex.takeIf { it >= 0 },
                highlightNonce = highlightNonce,
                aiFab = {
                    val shown = tab
                    if (shown != null) {
                        TabAiFab(shown, runtime, onClick = { navigator.openAskPage(shown.id, fromReader = true) })
                    }
                },
            )
        }
    }
}
