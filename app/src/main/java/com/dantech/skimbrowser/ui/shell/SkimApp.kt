package com.dantech.skimbrowser.ui.shell

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.dantech.skimbrowser.di.ShellEntryPoint
import com.dantech.skimbrowser.domain.browsing.WindowPrivacy
import com.dantech.skimbrowser.ui.browsing.DownloadNotice
import com.dantech.skimbrowser.ui.browsing.DownloadSnackbarEffect
import com.dantech.skimbrowser.ui.components.SkimSnackbarHost
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.NavigatorEntryPoint
import com.dantech.skimbrowser.ui.navigation.SkimNavDisplay
import com.dantech.skimbrowser.ui.navigation.WelcomeKey
import com.dantech.skimbrowser.ui.navigation.reconcileBackStack
import com.dantech.skimbrowser.ui.navigation.rememberShell
import com.dantech.skimbrowser.ui.navigation.skimNavConfig
import com.dantech.skimbrowser.ui.navigation.topScreen
import dagger.hilt.EntryPoints
import com.dantech.skimbrowser.domain.tab.TabsState
import com.dantech.skimbrowser.ui.snackbar.SnackbarPlacement
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import com.dantech.skimbrowser.ui.theme.isDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app in the main window.
 *
 * It starts once the settings are read and the saved tabs restored (or given up on: a restore that
 * fails or takes too long still opens the browser, on a New tab). The first launch opens on Welcome
 * (01), later ones on the browser with the last tab; a back stack saved before the Activity or the
 * process went away wins over both, once the keys of tabs that did not come back are dropped.
 *
 * What is on screen decides the window: the dark scheme and FLAG_SECURE while it shows incognito
 * content, system bar icons that read on the app's colors, and the Recents preview hidden while an
 * incognito tab is open. The one snackbar host sits where the top screen places it and carries out
 * snackbar actions with this window's navigator. Each link in [pendingLinks] (from another app)
 * opens in a new normal tab, shown at once.
 */
@Composable
fun SkimApp(
    secure: WindowPrivacy,
    openExternal: (Intent) -> Unit,
    downloadNotices: Flow<DownloadNotice>,
    pendingLinks: ReceiveChannel<String>,
) {
    val shell = rememberShell()
    val settings by remember(shell) { shell.settings().settings }.collectAsStateWithLifecycle(initialValue = null)
    val onboarded by produceState<Boolean?>(initialValue = null, shell) { value = startUp(shell) }
    val theme = settings?.theme ?: ThemeSetting.System
    val started = onboarded
    if (started == null) {
        // Until the settings and tabs are read: the empty window, in the app's colors.
        SystemBarsEffect(theme.isDark(isSystemInDarkTheme(), incognito = false))
        SkimTheme(theme) { Surface(Modifier.fillMaxSize()) {} }
        return
    }

    val tabs = shell.tabManager()
    val tabsState by tabs.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(skimNavConfig, if (started) BrowserKey else WelcomeKey)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navigatorFactory = remember(context) {
        EntryPoints.get(context, NavigatorEntryPoint::class.java).navigatorFactory()
    }
    val navigator = remember(backStack, scope, secure) {
        // A restored back stack may point at tabs that did not come back; it is fixed once, before it shows.
        backStack.reconcileWith(tabsState)
        navigatorFactory.create(backStack, secure, scope)
    }
    val keys = backStack.toList()
    val incognitoSurface = secureTarget(keys, tabsState)

    SecureFlagEffect(
        target = incognitoSurface,
        hasIncognitoTabs = tabsState.tabs.any { it.incognito },
        armed = navigator.secureArming,
        secure = secure,
    )
    SystemBarsEffect(theme.isDark(isSystemInDarkTheme(), incognitoSurface))
    // A download that ends is told on whatever screen is up, with the one snackbar host below.
    DownloadSnackbarEffect(downloadNotices, shell.snackbar(), secure)
    LaunchedEffect(navigator) {
        // The channel belongs to the Activity, so each link is received here exactly once.
        for (url in pendingLinks) {
            tabs.newTab(incognito = false, url = url, openedByExternalApp = true)
            navigator.showBrowser()
        }
    }

    SkimTheme(theme, incognito = incognitoSurface) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                SkimNavDisplay(backStack, navigator, secure)
                SkimSnackbarHost(
                    controller = shell.snackbar(),
                    placement = topScreen(keys)?.placement ?: SnackbarPlacement.Bottom,
                    onAction = { action -> interpret(action, navigator, shell.undo(), openExternal) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Reads whether onboarding is done and waits for the saved tabs. Neither can hold the start up for
 * long: settings that cannot be read count as a first launch, and tabs that are not restored in
 * time (or not at all) leave the browser to open a New tab.
 */
private suspend fun startUp(shell: ShellEntryPoint): Boolean {
    val onboarded = withTimeoutOrNull(START_TIMEOUT_MS) { shell.settings().settings.first().onboardingCompleted }
    if (onboarded == null) Log.w(TAG, "Settings were not read in time")
    try {
        if (withTimeoutOrNull(START_TIMEOUT_MS) { shell.tabManager().restore() } == null) {
            Log.w(TAG, "Saved tabs were not restored in time")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Saved tabs could not be restored: ${e.javaClass.simpleName}")
    }
    return onboarded == true
}

/** Drops the keys of tabs that are not open, in place. */
private fun NavBackStack<NavKey>.reconcileWith(tabs: TabsState) {
    val keys = toList()
    val reconciled = reconcileBackStack(keys, tabs)
    if (reconciled == keys) return
    clear()
    addAll(reconciled)
}

private const val TAG = "SkimApp"

/** Longer than the tab manager's own restore timeout, which normally ends the wait first. */
private const val START_TIMEOUT_MS = 8_000L
