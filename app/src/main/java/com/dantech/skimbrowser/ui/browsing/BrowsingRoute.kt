package com.dantech.skimbrowser.ui.browsing

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.browser.ui.WebViewHost
import com.dantech.skimbrowser.system.InternetPanel
import com.dantech.skimbrowser.ui.browser.browserBack
import com.dantech.skimbrowser.ui.browser.perform
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.search.SearchOverlay
import com.dantech.skimbrowser.ui.search.searchViewModel

/**
 * The page of [tab], the browser's slot once the tab shows one: Browsing (05), Find in page (05c)
 * and Page failed to load (11) over the tab's WebView, with the site permission requests of the tab
 * and Search & suggestions (04b) when the address is tapped.
 *
 * - The address opens the search layer with the page's URL selected, for this tab's kind: an
 *   address or a search entered there loads in this tab, and Ask Skim asks across the tabs.
 * - The lock (or globe) opens Site information, Reader view opens 08, More options 05b, Tabs 09 or
 *   09b, and the AI FAB 06; a long press on a link or an image of this tab's page opens 05d for it.
 * - The toolbar's Back does what system Back does in the tab ([browserBack]); system Back closes
 *   Find in page first. This screen's handler is registered after the browser's, so it runs first.
 *
 * The screen's view model lives in the browser destination's store, one for each tab shown.
 */
@Composable
fun BrowsingRoute(tab: Tab, runtime: TabRuntime, container: AppContainer, navigator: SkimNavigator) {
    val tabs = container.tabManager
    val host = LocalBrowserHost.current
    val activity = LocalActivity.current
    val context = LocalContext.current
    val browsing = viewModel(key = "$BROWSING_VIEW_MODEL_KEY:${tab.id}") { BrowsingViewModel(tab.id, tabs) }
    val state by browsing.state.collectAsStateWithLifecycle()
    val search = searchViewModel(container)
    var searching by remember(tab.id) { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        // Cancels a lookup still under way and keeps nothing of what was typed.
        search.clear()
    }

    BackHandler(enabled = state.find != null) { browsing.closeFind() }
    LaunchedEffect(tabs, tab.id) {
        tabs.hitTargets.collect { (tabId, target) ->
            if (tabId == tab.id) navigator.openLinkOptions(tab.id, target)
        }
    }

    BrowsingScreen(
        state = state,
        findQuery = browsing.findQuery,
        actions = BrowsingActions(
            onSiteInfo = { navigator.openSiteInfo(tab.id) },
            onEditAddress = {
                // Opened in the tap itself, so the field holds the URL from its first frame.
                search.open(tab.url.orEmpty(), selectAll = true, incognito = tab.incognito)
                searching = true
            },
            onReader = { navigator.openReader(tab.id) },
            onRefresh = browsing::refresh,
            onTryAgain = browsing::tryAgain,
            onInternetSettings = { InternetPanel.open(context) },
            onFindPrevious = browsing::findPrevious,
            onFindNext = browsing::findNext,
            onCloseFind = browsing::closeFind,
            onBack = { browserBack(tab, runtime).perform(tab, tabs, navigator, activity) },
            onForward = browsing::goForward,
            onTabs = { navigator.openTabs(tab.incognito) },
            onMore = { navigator.openMoreMenu(tab.id) },
            onAskPage = { navigator.openAskPage(tab.id) },
        ),
        page = { WebViewHost(tab.id, Modifier.fillMaxSize()) },
    )
    SitePermissionRequests(tab.id, host.sitePermissions)
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

/** The key prefix of the browsing view models in the browser destination's store. */
private const val BROWSING_VIEW_MODEL_KEY = "browsing"
