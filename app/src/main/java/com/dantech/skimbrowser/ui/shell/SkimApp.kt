package com.dantech.skimbrowser.ui.shell

import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.domain.browsing.WindowPrivacy
import com.dantech.skimbrowser.ui.browsing.DownloadNotice
import com.dantech.skimbrowser.ui.browsing.DownloadSnackbarEffect
import com.dantech.skimbrowser.ui.components.SkimSnackbarHost
import com.dantech.skimbrowser.ui.navigation.SkimNavDisplay
import com.dantech.skimbrowser.ui.navigation.snackbarPlacement
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.isDark
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow

/**
 * The app in the main window, as [viewModel] holds it: the empty window in the app's colors until
 * the app has started (see [SkimAppViewModel]), then its back stack.
 *
 * What is on screen decides the window: the dark scheme and FLAG_SECURE while it shows incognito
 * content, system bar icons that read on the app's colors, and the Recents preview hidden while an
 * incognito tab is open. The one snackbar host sits where the top screen places it and carries out
 * snackbar actions with this window's navigator. Each link in [pendingLinks] (from another app)
 * opens in a new normal tab, shown at once.
 *
 * State, the back stack and the navigator come from [viewModel]. MainActivity hands in what a view
 * model may not hold or name: the window's [secure] flag, how to [openExternal] apps, the
 * [downloadNotices] the snackbar tells, and the links it received.
 */
@Composable
fun SkimApp(
    secure: WindowPrivacy,
    openExternal: (Intent) -> Unit,
    downloadNotices: Flow<DownloadNotice>,
    pendingLinks: ReceiveChannel<String>,
    viewModel: SkimAppViewModel = hiltViewModel(),
) {
    // The view model outlives this window; its navigator reaches the window only while it is composed.
    DisposableEffect(viewModel, secure) {
        viewModel.attachWindow(secure)
        onDispose { viewModel.detachWindow(secure) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.started) {
        // Until the settings and tabs are read: the empty window, in the app's colors.
        SystemBarsEffect(state.theme.isDark(isSystemInDarkTheme(), incognito = false))
        SkimTheme(state.theme) { Surface(Modifier.fillMaxSize()) {} }
        return
    }

    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val navigator = viewModel.navigator
    val keys = viewModel.backStack.toList()
    val incognitoSurface = secureTarget(keys, tabs)

    SecureFlagEffect(
        target = incognitoSurface,
        hasIncognitoTabs = tabs.tabs.any { it.incognito },
        armed = navigator.secureArming,
        secure = secure,
    )
    SystemBarsEffect(state.theme.isDark(isSystemInDarkTheme(), incognitoSurface))
    // A download that ends is told on whatever screen is up, with the one snackbar host below.
    DownloadSnackbarEffect(downloadNotices, viewModel.snackbar, secure)
    LaunchedEffect(viewModel, pendingLinks) {
        // The channel belongs to the Activity, so each link is received here exactly once.
        for (url in pendingLinks) viewModel.openExternalLink(url)
    }

    SkimTheme(state.theme, incognito = incognitoSurface) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                SkimNavDisplay(viewModel.backStack, navigator, tabs, secure)
                SkimSnackbarHost(
                    controller = viewModel.snackbar,
                    placement = snackbarPlacement(keys, tabs.currentTab?.url != null),
                    onAction = { action -> interpret(action, navigator, viewModel.undo, openExternal) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
