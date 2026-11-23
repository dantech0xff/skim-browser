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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.domain.tab.TabGroup
import com.dantech.skimbrowser.domain.tab.TabNeighbor
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.theme.SkimChromeTheme
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.distinctUntilChanged

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
    val onTabs: () -> Unit,
    val onMore: () -> Unit,
    /** Download the video playing on this page. Shown only while [BrowsingUiState.videoAvailable]. */
    val onDownloadVideo: () -> Unit = {},
    /** Ask Skim in the pill or the collapsed chip, while it is on. */
    val onAskPage: () -> Unit,
    /** Opens the http page when https, tried first, could not connect. */
    val onOpenHttp: (() -> Unit)? = null,
    val onLeaveUnsafeCertificate: () -> Unit = {},
    val onProceedDespiteCertificate: () -> Unit = {},
    /** Back to safety on a scam warning. */
    val onBackToSafety: () -> Unit = {},
    /** Proceed anyway on a scam warning. The host is allowed for this process only. */
    val onProceedUnsafe: () -> Unit = {},
)

/**
 * A tab's page (05): [page] (the tab's WebView) fills the screen, under the status bar and the
 * navigation bar. Address, find and the toolbar buttons are one floating cluster at the bottom.
 * Find in page (05c) takes the address row's place. The keyboard lifts the cluster and shortens
 * the page to the cluster's top; at rest the page stays full bleed.
 *
 * Pulling down at the top of the page reloads it. The indicator clears the status bar. A pull that
 * reaches the point where a release reloads gives a threshold haptic, also with Remove animations
 * on. With Remove animations on, the indicator is a still frame. The pull is off while the page
 * failed to load:
 * Page failed to load (11) then covers the page, hidden from accessibility.
 *
 * While [chromeCollapsed] and the page is showing, the pill is a chip with the host; a tap on the
 * chip calls [onExpandChrome]. Find, the keyboard and a covered page keep the full pill.
 */
@Composable
fun BrowsingScreen(
    state: BrowsingUiState,
    findQuery: TextFieldState,
    actions: BrowsingActions,
    page: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    chromeCollapsed: Boolean = false,
    /** The collapsed chip was tapped: the full pill comes back. */
    onExpandChrome: () -> Unit = {},
    /** The tab a swipe toward the start of the strip opens, when there is one. */
    previousTab: TabNeighbor? = null,
    /** The tab a swipe toward the end of the strip opens, when there is one. */
    nextTab: TabNeighbor? = null,
    groups: List<TabGroup> = emptyList(),
    onSwipeToTab: (String) -> Unit = {},
    translation: @Composable () -> Unit = {},
) {
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val covered = state.covered
    val chip = showsChip(chromeCollapsed, state, imeOpen)
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (imeOpen) {
                        Modifier
                            .windowInsetsPadding(WindowInsets.ime)
                            .padding(bottom = ToolbarMetrics.BrowsingClusterHeight + ToolbarMetrics.BrowsingBottomOffset)
                    } else {
                        Modifier
                    },
                ),
        ) {
            val error = state.loadError
            val certificate = state.certificateWarning
            val safety = state.safetyWarning
            val pull = rememberPullToRefreshState()
            PullThresholdHaptic(pull, refreshing = state.refreshing, enabled = !covered)
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                // While a warning or error covers it, the page is out of reach for accessibility too.
                modifier = Modifier.fillMaxSize().then(if (covered) Modifier.clearAndSetSemantics {} else Modifier),
                state = pull,
                indicator = { RefreshIndicator(pull, state.refreshing) },
                enabled = !covered,
            ) {
                page()
            }
            if (certificate != null) {
                LoadErrorContent(
                    error = null,
                    onTryAgain = actions.onTryAgain,
                    onInternetSettings = actions.onInternetSettings,
                    onOpenHttp = actions.onOpenHttp,
                    warning = certificate,
                    onLeaveUnsafeCertificate = actions.onLeaveUnsafeCertificate,
                    onProceedDespiteCertificate = actions.onProceedDespiteCertificate,
                )
            } else if (safety != null) {
                SafetyWarningContent(
                    warning = safety,
                    onBackToSafety = actions.onBackToSafety,
                    onProceed = actions.onProceedUnsafe,
                )
            } else if (error != null) {
                LoadErrorContent(
                    error = error,
                    onTryAgain = actions.onTryAgain,
                    onInternetSettings = actions.onInternetSettings,
                    onOpenHttp = actions.onOpenHttp,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.ime)
                        .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                )
                .padding(bottom = ToolbarMetrics.BrowsingBottomOffset + ToolbarMetrics.BrowsingClusterHeight + 8.dp)
                .padding(horizontal = ToolbarMetrics.BrowsingSideInset),
        ) {
            translation()
        }
        val barModifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .union(WindowInsets.ime)
                    .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
            )
            .padding(bottom = ToolbarMetrics.BrowsingBottomOffset)
            .padding(horizontal = ToolbarMetrics.BrowsingSideInset)
        if (previousTab == null && nextTab == null) {
            BrowsingChrome(
                state = state,
                findQuery = findQuery,
                actions = actions,
                modifier = barModifier,
                collapsed = chip,
                onExpand = onExpandChrome,
            )
        } else {
            // The caption of a swipe floats over the page with the pill, in the same scheme.
            SkimChromeTheme {
                TabStripSwipe(
                    previous = previousTab,
                    next = nextTab,
                    groups = groups,
                    enabled = !covered && state.find == null,
                    onSelect = onSwipeToTab,
                    modifier = barModifier,
                ) { bar ->
                    BrowsingChrome(
                        state = state,
                        findQuery = findQuery,
                        actions = actions,
                        modifier = bar,
                        collapsed = chip,
                        onExpand = onExpandChrome,
                    )
                }
            }
        }
    }
}

/** A warning or the error screen covers the page. */
private val BrowsingUiState.covered: Boolean
    get() = loadError != null || certificateWarning != null || safetyWarning != null

/**
 * Whether the pill shows as the collapsed chip: the page scrolled down ([chromeCollapsed]), and
 * nothing needs the full pill: Find in page is closed, the page is not covered, the keyboard is down.
 */
internal fun showsChip(chromeCollapsed: Boolean, state: BrowsingUiState, imeOpen: Boolean): Boolean =
    chromeCollapsed && state.find == null && !state.covered && !imeOpen

/**
 * Asks for one threshold haptic each time [pull] reaches the distance where a release reloads,
 * while the page is not already reloading and the pull is [enabled].
 */
@Composable
private fun PullThresholdHaptic(pull: PullToRefreshState, refreshing: Boolean, enabled: Boolean) {
    val haptics = LocalHapticFeedback.current
    val latestHaptics by rememberUpdatedState(haptics)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(pull) {
        snapshotFlow { pull.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { reached ->
                if (reached && latestEnabled && !latestRefreshing) {
                    latestHaptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
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
