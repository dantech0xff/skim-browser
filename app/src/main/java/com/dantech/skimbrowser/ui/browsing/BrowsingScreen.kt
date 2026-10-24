package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dantech.skimbrowser.ui.components.AddressBar
import com.dantech.skimbrowser.ui.components.AiFab
import com.dantech.skimbrowser.ui.components.BrowserToolbar
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.shell.AiFabMode
import com.dantech.skimbrowser.ui.shell.aiFabContentDescription
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion

/** What the controls of the browsing screen do (05, 05c, 11). */
@Immutable
data class BrowsingActions(
    /** The lock or globe before the address: the Site information sheet. */
    val onSiteInfo: () -> Unit,
    /** The address: Search & suggestions (04b) with the URL selected. */
    val onEditAddress: () -> Unit,
    val onReader: () -> Unit,
    /** Pull down at the top of the page. */
    val onRefresh: () -> Unit,
    val onTryAgain: () -> Unit,
    val onInternetSettings: () -> Unit,
    val onFindPrevious: () -> Unit,
    val onFindNext: () -> Unit,
    val onCloseFind: () -> Unit,
    /** The toolbar's Back, which does what system Back does in the tab. */
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onTabs: () -> Unit,
    val onMore: () -> Unit,
    /** The AI FAB, while it is on. */
    val onAskPage: () -> Unit,
)

/**
 * A tab's page (05): the status bar and the address bar on surface, then [page] (the tab's WebView)
 * down to the bottom edge, running under the floating toolbar and the navigation bar; the keyboard
 * pushes the page's bottom up. Find in page (05c) takes the address bar's place while it is open.
 *
 * Pulling down at the top of the page reloads it, with the loading indicator coming down from under
 * the bar; with Remove animations on, the indicator is a still frame. The pull is off while the page
 * failed to load: Page failed to load (11) then covers the page, which stays where it is below it,
 * hidden from accessibility.
 */
@Composable
fun BrowsingScreen(
    state: BrowsingUiState,
    findQuery: TextFieldState,
    actions: BrowsingActions,
    page: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .windowInsetsTopHeight(WindowInsets.safeDrawing),
            )
            val find = state.find
            if (find != null) {
                FindInPageBar(
                    query = findQuery,
                    state = find,
                    incognito = state.incognito,
                    onPrevious = actions.onFindPrevious,
                    onNext = actions.onFindNext,
                    onClose = actions.onCloseFind,
                )
            } else {
                AddressBar(
                    address = state.address,
                    indicator = state.indicator,
                    readerAvailable = state.readerAvailable,
                    loadProgress = state.progress,
                    onSiteInfo = actions.onSiteInfo,
                    onEditAddress = actions.onEditAddress,
                    onReader = actions.onReader,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                val error = state.loadError
                val pull = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = actions.onRefresh,
                    // While the error covers it, the page is out of reach for accessibility too.
                    modifier = Modifier.fillMaxSize().then(if (error != null) Modifier.clearAndSetSemantics {} else Modifier),
                    state = pull,
                    indicator = { RefreshIndicator(pull, state.refreshing) },
                    enabled = error == null,
                ) {
                    page()
                }
                if (error != null) {
                    LoadErrorContent(error, onTryAgain = actions.onTryAgain, onInternetSettings = actions.onInternetSettings)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = ToolbarMetrics.BottomOffset),
        ) {
            BrowserToolbar(
                canGoBack = state.backEnabled,
                canGoForward = state.forwardEnabled,
                tabCount = state.tabCount,
                incognito = state.incognito,
                onBack = actions.onBack,
                onForward = actions.onForward,
                onTabs = actions.onTabs,
                onMore = actions.onMore,
                aiFab = {
                    AiFab(
                        enabled = state.aiFab == AiFabMode.Enabled,
                        contentDescription = aiFabContentDescription(state.aiFab, isNewTabPage = false),
                        onClick = actions.onAskPage,
                    )
                },
            )
        }
    }
}

/**
 * Pull to refresh's loading indicator, at the top center of the page: Material's, which morphs
 * while the page reloads; with Remove animations on, a still frame that follows the pull and holds
 * its last shape while the page reloads.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.RefreshIndicator(pull: PullToRefreshState, refreshing: Boolean) {
    val modifier = Modifier.align(Alignment.TopCenter)
    if (!rememberReducedMotion()) {
        PullToRefreshDefaults.LoadingIndicator(state = pull, isRefreshing = refreshing, modifier = modifier)
        return
    }
    val width = LoadingIndicatorDefaults.ContainerWidth
    val height = LoadingIndicatorDefaults.ContainerHeight
    PullToRefreshDefaults.IndicatorBox(
        state = pull,
        isRefreshing = refreshing,
        modifier = modifier.size(width, height),
        containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor,
        elevation = PullToRefreshDefaults.LoadingIndicatorElevation,
    ) {
        ContainedLoadingIndicator(
            progress = { if (refreshing) 1f else pull.distanceFraction },
            modifier = Modifier.requiredSize(width, height),
            containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor,
            indicatorColor = PullToRefreshDefaults.loadingIndicatorColor,
        )
    }
}
