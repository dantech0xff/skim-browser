package com.dantech.skimbrowser.ui.browser

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.newTab
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
 * Carries out this Back on [tab]: system Back in the browser does, and so does the toolbar's Back
 * on a page. [activity] goes to the background when the tab another app opened closes.
 */
@MainThread
fun BrowserBack.perform(tab: Tab, tabs: TabsController, navigator: SkimNavigator, activity: Activity?) {
    when (this) {
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
    BackHandler(enabled = back != BrowserBack.Leave) { back.perform(tab, tabs, navigator, activity) }
    Box(Modifier.fillMaxSize()) {
        if (tab.url == null) newTabPage(tab) else page(tab, runtime)
    }
}

/** Opens a New tab while this screen is in the back stack and no tab is current. */
@Composable
private fun OpenTabWhenNone(tabs: TabsController) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // A screen leaving the back stack is held below STARTED: it waits here and opens nothing.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (tabs.state.value.currentTabId == null) tabs.newTab(incognito = false)
        }
    }
}
