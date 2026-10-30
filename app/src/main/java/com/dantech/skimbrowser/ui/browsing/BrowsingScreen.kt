package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
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
 * A tab's page (05): [page] (the tab's WebView) fills the screen, under the status bar and the
 * navigation bar. Address, find and the toolbar buttons are one floating cluster at the bottom.
 * Find in page (05c) takes the address row's place. The keyboard lifts the cluster and shortens
 * the page to the cluster's top; at rest the page stays full bleed.
 *
 * Pulling down at the top of the page reloads it. The indicator clears the status bar. With Remove
 * animations on, the indicator is a still frame. The pull is off while the page failed to load:
 * Page failed to load (11) then covers the page, hidden from accessibility.
 */
@Composable
fun BrowsingScreen(
    state: BrowsingUiState,
    findQuery: TextFieldState,
    actions: BrowsingActions,
    page: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (imeOpen) {
                        Modifier
                            .windowInsetsPadding(WindowInsets.ime)
                            .padding(bottom = ToolbarMetrics.BrowsingClusterHeight + ToolbarMetrics.BottomOffset)
                    } else {
                        Modifier
                    },
                ),
        ) {
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
        BrowsingChrome(
            state = state,
            findQuery = findQuery,
            actions = actions,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.ime)
                        .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                )
                .padding(bottom = ToolbarMetrics.BottomOffset)
                .padding(horizontal = 16.dp),
        )
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
    val modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
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
