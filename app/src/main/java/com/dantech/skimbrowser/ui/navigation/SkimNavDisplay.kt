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
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.HitTarget
import com.dantech.skimbrowser.ui.ai.AiPermissionRoute
import com.dantech.skimbrowser.ui.ai.AskPageRoute
import com.dantech.skimbrowser.ui.ai.AskTabsRoute
import com.dantech.skimbrowser.ui.bookmarks.BookmarksRoute
import com.dantech.skimbrowser.ui.bookmarks.EditBookmarkRoute
import com.dantech.skimbrowser.ui.bookmarks.MoveToFolderRoute
import com.dantech.skimbrowser.ui.history.HistoryRoute
import com.dantech.skimbrowser.ui.settings.AiPrivacyRoute
import com.dantech.skimbrowser.ui.skimhistory.ClearSkimHistoryRoute
import com.dantech.skimbrowser.ui.skimhistory.SkimHistoryRoute
import com.dantech.skimbrowser.ui.settings.ClearBrowsingDataRoute
import com.dantech.skimbrowser.ui.settings.DeleteAiDataRoute
import com.dantech.skimbrowser.ui.settings.LicensesRoute
import com.dantech.skimbrowser.ui.settings.SearchEngineRoute
import com.dantech.skimbrowser.ui.settings.SettingsRoute
import com.dantech.skimbrowser.ui.settings.ThemeRoute
import com.dantech.skimbrowser.ui.reader.ReaderRoute
import com.dantech.skimbrowser.ui.tabs.RenameGroupRoute
import com.dantech.skimbrowser.ui.tabs.TabsRoute
import com.dantech.skimbrowser.ui.browser.BrowserRoute
import com.dantech.skimbrowser.ui.browsing.BrowsingRoute
import com.dantech.skimbrowser.ui.browsing.LinkOptionsRoute
import com.dantech.skimbrowser.ui.browsing.MoreMenuRoute
import com.dantech.skimbrowser.ui.browsing.SiteInfoRoute
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
        entry<ReaderKey> { key -> ReaderRoute(key, container, navigator) }
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
        entry<MoreMenuKey>(metadata = BottomSheetSceneStrategy.bottomSheet(R.string.cd_more_options)) { key ->
            MoreMenuRoute(key, container, navigator)
        }
        entry<LinkOptionsKey>(
            metadata = { key ->
                BottomSheetSceneStrategy.bottomSheet(
                    if (key.target is HitTarget.Image) R.string.cd_image_options else R.string.cd_link_options,
                )
            },
        ) { key ->
            LinkOptionsRoute(key, container, navigator)
        }
        entry<SiteInfoKey>(metadata = BottomSheetSceneStrategy.bottomSheet(R.string.cd_site_info)) { key ->
            SiteInfoRoute(key, container, navigator)
        }
        entry<AskTabsKey>(
            metadata = BottomSheetSceneStrategy.bottomSheet(R.string.ask_skim),
        ) { key ->
            AskTabsRoute(key, container, navigator)
        }
        entry<AskPageKey>(
            metadata = BottomSheetSceneStrategy.bottomSheet(
                R.string.ai_ask_about_page,
                skipPartiallyExpanded = false,
            ),
        ) { key ->
            AskPageRoute(key, container, navigator)
        }
        entry<AiPermissionKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) { key ->
            AiPermissionRoute(key, container, navigator)
        }
        entry<EditBookmarkKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) { key ->
            EditBookmarkRoute(key, container, navigator)
        }
        entry<MoveToFolderKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) { key ->
            MoveToFolderRoute(key, container, navigator)
        }
        entry<TabsKey> { key -> TabsRoute(key, container, navigator) }
        entry<SettingsKey> { SettingsRoute(container, navigator) }
        entry<AiPrivacyKey> { AiPrivacyRoute(container, navigator) }
        entry<HistoryKey> { HistoryRoute(container, navigator) }
        entry<SkimHistoryKey> { SkimHistoryRoute(container, navigator) }
        entry<ClearSkimHistoryKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) {
            ClearSkimHistoryRoute(container, navigator)
        }
        entry<BookmarksKey> { key -> BookmarksRoute(key, container, navigator) }
        entry<LicensesKey> { LicensesRoute(navigator) }
        entry<SearchEngineDialogKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) {
            SearchEngineRoute(container, navigator)
        }
        entry<ThemeDialogKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) {
            ThemeRoute(container, navigator)
        }
        entry<ClearBrowsingDataKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) {
            ClearBrowsingDataRoute(container, navigator)
        }
        entry<DeleteAiDataKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) {
            DeleteAiDataRoute(container, navigator)
        }
        entry<RenameGroupKey>(metadata = DialogSceneStrategy.dialog(ShortcutDialogProperties)) { key ->
            RenameGroupRoute(key, container, navigator)
        }
    }
