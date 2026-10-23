package com.dantech.skimbrowser.ui.ai

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.system.Clipboard
import com.dantech.skimbrowser.ui.navigation.AiPermissionKey
import com.dantech.skimbrowser.ui.navigation.AskPageKey
import com.dantech.skimbrowser.ui.navigation.LocalSkimSheetState
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.coroutines.launch

/** Ask about this page (06), for [key]'s tab or a saved chat. */
@Composable
fun AskPageRoute(key: AskPageKey, container: AppContainer, navigator: SkimNavigator) {
    val model = viewModel(key = "ask-${key.tabId}-${key.savedChatId}-${key.fromReader}") {
        AskPageViewModel(
            key = key,
            tabs = container.tabManager,
            sessions = container.chatSessions,
            summarizePage = container.summarizePage,
            askPage = container.askPage,
            retryAnswer = container.retryAnswer,
            network = container.networkMonitor,
            readerHighlights = container.readerHighlights,
            loadSavedChat = container.loadSavedChat,
            savedState = createSavedStateHandle(),
            appScope = container.appScope,
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val sheet = LocalSkimSheetState.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.snackbar_copied)
    LaunchedEffect(model) {
        model.effects.collect { effect ->
            when (effect) {
                is AskPageEffect.NeedsPermission -> navigator.replaceTop(AiPermissionKey(effect.tabId, effect.fromReader))
            }
        }
    }
    AskPageSheet(
        state = state,
        snackbar = snackbar,
        actions = AskPageActions(
            onClose = navigator::back,
            onSend = model::ask,
            onStop = model::stop,
            onChip = model::ask,
            onRetry = model::retry,
            onCopy = {
                val text = (model.state.value.summary as? SummaryState.Done)?.text.orEmpty()
                if (text.isNotEmpty() && Clipboard.copy(context, text, sensitive = false)) {
                    scope.launch { snackbar.showSnackbar(copied) }
                }
            },
            onGood = { model.feedback(good = true) },
            onPoor = { model.feedback(good = false) },
            onCitation = { paragraph ->
                model.highlight(paragraph)
                scope.launch { sheet?.partialExpand() }
            },
        ),
    )
}

/** The first-time permission dialog (06d) for [key]'s site. */
@Composable
fun AiPermissionRoute(key: AiPermissionKey, container: AppContainer, navigator: SkimNavigator) {
    val tabs by container.tabManager.state.collectAsStateWithLifecycle()
    val host = hostOf(tabs.tab(key.tabId)?.url.orEmpty()).orEmpty()
    val model = viewModel(key = "allow-${key.tabId}") {
        AiPermissionViewModel(container.setSkimAi)
    }
    AiPermissionDialog(
        host = host,
        onAllow = {
            if (host.isEmpty()) {
                navigator.back()
            } else {
                model.allow(host) {
                    navigator.replaceTop(
                        AskPageKey(tabId = key.tabId, fromReader = key.fromReader, approvedHost = host),
                    )
                }
            }
        },
        onNotNow = navigator::back,
    )
}
