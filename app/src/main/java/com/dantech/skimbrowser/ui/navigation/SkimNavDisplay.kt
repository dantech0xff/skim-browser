package com.dantech.skimbrowser.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.BuildConfig
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.ui.browser.BrowserRoute
import com.dantech.skimbrowser.ui.browsing.BrowsingRoute
import com.dantech.skimbrowser.ui.home.NewTabPageRoute
import com.dantech.skimbrowser.ui.home.ShortcutDialogProperties
import com.dantech.skimbrowser.ui.home.ShortcutDialogRoute
import com.dantech.skimbrowser.ui.onboarding.SetupRoute
import com.dantech.skimbrowser.ui.onboarding.WelcomeScreen
import com.dantech.skimbrowser.ui.shell.rememberSecureEntryDecorator

/**
 * Shows the [backStack]: each key as its screen, a key whose entry has [BottomSheetSceneStrategy]
 * metadata as a sheet and one with [DialogSceneStrategy] metadata as a dialog, over the entries
 * below it. Back, swiping a sheet down and tapping a scrim go back through [navigator]. Each entry
 * keeps its saved state and view models while it is in the back stack, and holds FLAG_SECURE while
 * its content shows incognito content.
 *
 * Every destination has a key; its entry is registered in [skimEntries] by the part of the app that
 * draws it. A key without an entry yet shows nothing, and Back leaves it.
 */
@Composable
fun SkimNavDisplay(
    backStack: NavBackStack<NavKey>,
    navigator: SkimNavigator,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val secure = LocalBrowserHost.current.secure
    val tabs by container.tabManager.state.collectAsStateWithLifecycle()
    val entries = remember(navigator, container) { skimEntries(navigator, container) }
    val strategies = remember { listOf(DialogSceneStrategy<NavKey>(), BottomSheetSceneStrategy<NavKey>()) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = navigator::back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberSecureEntryDecorator(backStack.toList(), { entries(it).contentKey }, tabs, secure),
        ),
        sceneStrategies = strategies,
        entryProvider = entries,
    )
}

/** The entry of every key that has a screen; sheets add `bottomSheet` metadata, dialogs `dialog`. */
private fun skimEntries(navigator: SkimNavigator, container: AppContainer): (NavKey) -> NavEntry<NavKey> =
    entryProvider(fallback = { key -> NavEntry(key) {} }) {
        entry<WelcomeKey> {
            WelcomeScreen(
                onGetStarted = { navigator.push(SetupKey) },
                onOpenLegal = { url ->
                    // Back on the tab's first page closes it and returns here.
                    container.tabManager.newTab(incognito = false, url = url, closeOnBack = true)
                    navigator.push(BrowserKey)
                },
                termsUrl = BuildConfig.TERMS_URL,
                privacyUrl = BuildConfig.PRIVACY_URL,
            )
        }
        entry<SetupKey> {
            SetupRoute(container, navigator)
        }
        entry<BrowserKey> {
            BrowserRoute(
                container = container,
                navigator = navigator,
                newTabPage = { tab -> NewTabPageRoute(tab, container, navigator) },
                page = { tab, runtime -> BrowsingRoute(tab, runtime, container, navigator) },
            )
        }
        entry<ShortcutDialogKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) { key ->
            ShortcutDialogRoute(key, container, navigator)
        }
    }
