package com.dantech.skimbrowser.ui.home

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.system.VoiceInput
import com.dantech.skimbrowser.ui.components.BrowserToolbar
import com.dantech.skimbrowser.ui.incognito.IncognitoNewTabScreen
import com.dantech.skimbrowser.ui.navigation.ShortcutDialogKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.search.SearchOverlay
import com.dantech.skimbrowser.ui.search.searchViewModel
import com.dantech.skimbrowser.ui.shell.TabAiFab

/**
 * The New tab page of [tab], the browser's slot while the tab shows no page: Home (04) for a normal
 * tab, the incognito New tab page (09c) for an incognito one, with the floating toolbar and the
 * search layer (04b) both open.
 *
 * The toolbar's Back and Forward are off; Tabs opens Tabs or Incognito tabs (09, 09b) for the kind
 * of tab, More options its More menu (05b), and the AI FAB opens Ask Skim (06b) on Home and is off in
 * incognito. The search bar, voice search and the Ask Skim row all go through the search layer: an
 * address or a search opens in this tab, which then shows the page.
 *
 * The search layer is open only in this composition: it is not saved, so it never comes back open
 * after the process was gone, with a new search view model that knows nothing of what was typed.
 */
@Composable
fun NewTabPageRoute(tab: Tab, container: AppContainer, navigator: SkimNavigator) {
    val tabs = container.tabManager
    val context = LocalContext.current
    val search = searchViewModel(container)
    val voiceAvailable = remember(context) { VoiceInput.isAvailable(context.applicationContext) }
    var searching by remember(tab.id) { mutableStateOf(false) }
    val openSearch: (String) -> Unit = { text ->
        search.open(text, incognito = tab.incognito)
        searching = true
    }
    val closeSearch = {
        searching = false
        // Cancels a lookup still under way and keeps nothing of what was typed.
        search.clear()
    }
    val voice = rememberLauncherForActivityResult(VoiceInput.RecognizeSpeech()) { spoken -> spoken?.let(openSearch) }
    val startVoice = { launchVoiceInput(voice) }

    val tabsState by tabs.state.collectAsStateWithLifecycle()
    val runtime by remember(tab.id) { tabs.runtime(tab.id) }.collectAsStateWithLifecycle()
    val toolbar = @Composable {
        BrowserToolbar(
            canGoBack = false,
            canGoForward = false,
            tabCount = tabsState.tabs.count { it.incognito == tab.incognito },
            incognito = tab.incognito,
            onBack = {},
            onForward = {},
            onTabs = { navigator.openTabs(tab.incognito) },
            onMore = { navigator.openMoreMenu(tab.id) },
            aiFab = { TabAiFab(tab, runtime, onClick = { navigator.openAskTabs() }) },
        )
    }

    if (tab.incognito) {
        IncognitoNewTabScreen(
            voiceAvailable = voiceAvailable,
            onSearch = { openSearch("") },
            onVoice = startVoice,
            toolbar = toolbar,
        )
    } else {
        val home = viewModel(key = HOME_VIEW_MODEL_KEY) {
            HomeViewModel(
                tabs = tabs,
                shortcuts = container.shortcutRepository,
                removeShortcut = container.removeShortcut,
                restoreShortcut = container.restoreShortcut,
                chats = container.chatRepository,
                snackbar = container.snackbarController,
                undo = container.undoRegistry,
                appScope = container.appScope,
                voiceAvailable = voiceAvailable,
            )
        }
        val state by home.state.collectAsStateWithLifecycle()
        val actions = HomeActions(
            onSearch = { openSearch("") },
            onVoice = startVoice,
            onSummarizeTabs = { prompt -> navigator.openAskTabs(prompt) },
            onOpenShortcut = { shortcut -> home.openShortcut(shortcut.url) },
            onAddShortcut = { navigator.openDialog(ShortcutDialogKey()) },
            onEditShortcut = { shortcut -> navigator.openDialog(ShortcutDialogKey(shortcut.id)) },
            onRemoveShortcut = { shortcut -> home.removeShortcut(shortcut.id) },
            onOpenChat = { chatId -> navigator.openSavedChat(chatId) },
            onSeeAllChats = { navigator.openSkimHistory() },
        )
        HomeScreen(state, actions, toolbar)
    }

    if (searching) {
        SearchOverlay(
            search = search,
            incognito = tab.incognito,
            onOpenUrl = { url ->
                tabs.load(tab.id, url)
                closeSearch()
            },
            onAskSkim = { text ->
                closeSearch()
                navigator.openAskTabs(text)
            },
            onClose = closeSearch,
        )
    }
}

/** Starts speech input; with no app to take it after all (it went away since the mic showed), nothing happens. */
private fun launchVoiceInput(launcher: ManagedActivityResultLauncher<Unit, String?>) {
    try {
        launcher.launch(Unit)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app took the speech input request")
    }
}

private const val TAG = "NewTabPageRoute"

/** The key of Home's view model in the browser destination's store. */
private const val HOME_VIEW_MODEL_KEY = "home"
