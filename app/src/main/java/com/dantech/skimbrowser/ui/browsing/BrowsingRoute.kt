package com.dantech.skimbrowser.ui.browsing

import com.dantech.skimbrowser.ui.navigation.rememberShell
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.browser.ui.WebViewHost
import com.dantech.skimbrowser.system.InternetPanel
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
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
fun BrowsingRoute(tab: Tab, runtime: TabRuntime, navigator: SkimNavigator) {
    val shell = rememberShell()
    val tabs = shell.tabs()
    val host = LocalBrowserHost.current
    val activity = LocalActivity.current
    val context = LocalContext.current
    val browsing = hiltViewModel<BrowsingViewModel, BrowsingViewModel.Factory>(
        key = "$BROWSING_VIEW_MODEL_KEY:${tab.id}",
    ) { factory ->
        factory.create(tab.id)
    }
    val state by browsing.state.collectAsStateWithLifecycle()
    val search = searchViewModel()
    var searching by remember(tab.id) { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        // Cancels a lookup still under way and keeps nothing of what was typed.
        search.clear()
    }

    val density = LocalDensity.current
    val chromeScroll = remember(tab.id, density.density) {
        BrowsingChromeScroll(with(density) { 32.dp.roundToPx() })
    }
    var chromeCollapsed by remember(tab.id) { mutableStateOf(false) }

    BackHandler(enabled = state.find != null) { browsing.closeFind() }
    LaunchedEffect(shell, tab.id) {
        shell.tabManager().hitTargets.collect { (tabId, target) ->
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
        chromeCollapsed = chromeCollapsed,
        page = {
            // Insets are device pixels. A WebView's CSS px is one dp, so the page would pad by the
            // screen density if these were passed through unchanged.
            val top = density.cssPx(WindowInsets.statusBars.getTop(density))
            // The keyboard already lifts the page. At rest the page fills the screen, so the document
            // itself keeps the status bar and the pill clear, and that space scrolls away.
            val bottom = if (WindowInsets.ime.getBottom(density) > 0) {
                0
            } else {
                val pill = with(density) {
                    (ToolbarMetrics.BrowsingBottomOffset + ToolbarMetrics.BrowsingClusterHeight).roundToPx()
                }
                density.cssPx(WindowInsets.navigationBars.getBottom(density) + pill)
            }
            WebViewHost(
                tab.id,
                Modifier.fillMaxSize(),
                contentTop = top,
                contentBottom = bottom,
                onPageScroll = { scrollY, dy ->
                    chromeScroll.onScrolled(scrollY, dy, chromeCollapsed)?.let { chromeCollapsed = it }
                },
            )
        },
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

/** Device pixels as CSS pixels. WebView lays a page out in dp. */
private fun Density.cssPx(devicePx: Int): Int = (devicePx / density).roundToInt()
