package com.dantech.skimbrowser.ui.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.LoadError
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TabRuntime
import com.dantech.skimbrowser.data.hostOf
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonSize
import com.dantech.skimbrowser.ui.navigation.SkimNavigator

/** What system Back does on the browser, by priority. */
enum class BrowserBack {
    /** The tab's own page history. */
    GoBack,

    /** First page of a tab another app opened: the tab closes and Skim goes back to that app. */
    CloseAndLeave,

    /** First page of a tab opened for a screen (a legal link of 01): the tab closes, that screen shows. */
    CloseAndReturn,

    /** First page of a tab started from the New tab page: the tab shows the New tab page again. */
    NewTabPage,

    /** Nothing in the tab: Back goes where it would without the browser (out of the app). */
    Leave,
}

/** What Back does on [tab] with its page's [runtime]. */
fun browserBack(tab: Tab, runtime: TabRuntime): BrowserBack = when {
    runtime.canGoBack -> BrowserBack.GoBack
    tab.openedByExternalApp -> BrowserBack.CloseAndLeave
    tab.closeOnBack -> BrowserBack.CloseAndReturn
    tab.fromNewTabPage -> BrowserBack.NewTabPage
    else -> BrowserBack.Leave
}

/**
 * The current tab: [newTabPage] while it shows the New tab page, [page] once it shows a page. With
 * no current tab (every tab closed, or a start without saved tabs) a New tab opens, but only while
 * this screen is in the back stack: on its way out it opens nothing.
 *
 * System Back follows [browserBack]. Controls drawn inside the slots (find in page, the search
 * field, full screen video) register their own Back handlers later, so theirs run first.
 */
@Composable
fun BrowserRoute(
    container: AppContainer,
    navigator: SkimNavigator,
    newTabPage: @Composable (Tab) -> Unit,
    page: @Composable (Tab, TabRuntime) -> Unit,
) {
    val tabs = container.tabManager
    val state by tabs.state.collectAsStateWithLifecycle()
    val tab = state.currentTab
    if (tab == null) {
        OpenTabWhenNone(tabs)
        return
    }
    val runtime by remember(tab.id) { tabs.runtime(tab.id) }.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val back = browserBack(tab, runtime)
    BackHandler(enabled = back != BrowserBack.Leave) {
        when (back) {
            BrowserBack.GoBack -> tabs.goBack(tab.id)
            BrowserBack.CloseAndLeave -> {
                tabs.finalizeClose(tabs.close(listOf(tab.id)))
                activity?.moveTaskToBack(true)
            }
            BrowserBack.CloseAndReturn -> {
                tabs.finalizeClose(tabs.close(listOf(tab.id)))
                navigator.back()
            }
            BrowserBack.NewTabPage -> tabs.returnToNewTabPage(tab.id)
            BrowserBack.Leave -> Unit
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (tab.url == null) newTabPage(tab) else page(tab, runtime)
    }
}

/** Opens a New tab while this screen is in the back stack and no tab is current. */
@Composable
private fun OpenTabWhenNone(tabs: TabManager) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // A screen leaving the back stack is held below STARTED: it waits here and opens nothing.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (tabs.state.value.currentTabId == null) tabs.newTab(incognito = false)
        }
    }
}

/**
 * A page whose renderer crashed more often than the tab may recover from on its own, until the
 * full Page failed to load layout replaces it: what happened, and Try again.
 */
@Composable
fun PageStoppedWorking(tab: Tab, error: LoadError.Crashed, onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val url = error.url.ifEmpty { tab.url.orEmpty() }
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.error_crashed_title),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.error_crashed_body, hostOf(url) ?: url),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        SkimButton(
            text = stringResource(R.string.action_try_again),
            onClick = onTryAgain,
            size = SkimButtonSize.M,
            icon = R.drawable.ic_refresh,
        )
    }
}
