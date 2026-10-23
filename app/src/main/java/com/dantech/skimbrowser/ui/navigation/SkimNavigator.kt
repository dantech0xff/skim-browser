package com.dantech.skimbrowser.ui.navigation

import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ai.AiGate
import com.dantech.skimbrowser.ai.AiGateDecision
import com.dantech.skimbrowser.domain.tab.HitTarget
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.WindowSecureController
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.ui.shell.INCOGNITO_SURFACE_OWNER
import com.dantech.skimbrowser.ui.shell.secureTarget
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Every move between destinations: the back stack the shell saves, the current tab and the
 * window's FLAG_SECURE. Called on the main thread.
 *
 * Before a change puts incognito content on screen (an incognito tab becomes current, a screen,
 * sheet or dialog of one is shown, or Back reveals one), FLAG_SECURE is turned on, so no incognito
 * frame is ever drawn without it. The shell lets the flag go once nothing incognito is on screen.
 */
@MainThread
class SkimNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val tabs: TabManager,
    private val incognito: IncognitoSession,
    private val secure: WindowSecureController,
    private val snackbar: SnackbarController,
    private val aiGate: AiGate,
    private val scope: CoroutineScope,
) {
    private val arming = mutableIntStateOf(0)

    /**
     * Grows each time FLAG_SECURE is turned on ahead of a change. The shell reads it to settle the
     * flag after each one, so a change that did not end on incognito content lets the flag go.
     */
    val secureArming: Int get() = arming.intValue

    /** The back stack, bottom to top, for device tests. */
    @get:VisibleForTesting
    internal val keysForTests: List<NavKey> get() = backStack.toList()

    /** Shows [key] on top; nothing happens when it is already on top (a second tap). */
    fun push(key: SkimKey) {
        if (backStack.lastOrNull() == key) return
        show(backStack + key)
    }

    /** Leaves the top destination; the last one is never removed (Back then leaves the app). */
    fun back() {
        if (backStack.size <= 1) return
        show(backStack.dropLast(1))
    }

    /** Shows [key] in place of the top destination. */
    fun replaceTop(key: SkimKey) {
        show(backStack.dropLast(1) + key)
    }

    /** Removes the topmost [key] from the back stack, wherever it is, and nothing else. */
    fun remove(key: SkimKey) {
        val index = backStack.lastIndexOf(key)
        if (index < 0) return
        show(backStack.filterIndexed { i, _ -> i != index })
    }

    /** Start browsing (03): onboarding leaves the back stack and the browser is all that is left. */
    fun finishOnboarding() {
        show(listOf(BrowserKey))
    }

    /** Shows the current tab, closing what is open over the browser. */
    fun showBrowser() {
        show(withBrowserOnTop())
    }

    /** Makes [tabId] the current tab and shows it; nothing happens when it is not open. */
    fun showTab(tabId: String) {
        val now = tabs.state.value
        if (now.tab(tabId) == null) return
        val next = withBrowserOnTop()
        armIf(secureTarget(next, now.copy(currentTabId = tabId)))
        tabs.select(tabId)
        show(next)
    }

    /**
     * Opens a tab showing [url] (the New tab page when null) and shows it. An incognito tab needs
     * WebView profiles; without them nothing opens and a snackbar says why.
     */
    fun openNewTab(incognito: Boolean, url: String? = null) {
        if (incognito && !this.incognito.isSupported) {
            incognitoUnavailable()
            return
        }
        armIf(incognito)
        tabs.newTab(incognito = incognito, url = url)
        showBrowser()
    }

    /**
     * Opens [url] in a tab behind the current one and returns its id; null, with a snackbar, when an
     * incognito tab cannot open. Without a current tab the new tab is shown at once.
     */
    fun openInBackground(url: String, incognito: Boolean): String? {
        if (incognito && !this.incognito.isSupported) {
            incognitoUnavailable()
            return null
        }
        armIf(incognito && tabs.state.value.currentTabId == null)
        return tabs.newTab(incognito = incognito, url = url, background = true)
    }

    /** Tabs (09) or Incognito tabs (09b); switching between the two replaces the screen. */
    fun openTabs(incognito: Boolean, focusTabId: String? = null) {
        val key = TabsKey(incognito, focusTabId)
        if (backStack.lastOrNull() is TabsKey) replaceTop(key) else push(key)
    }

    fun openReader(tabId: String) = push(ReaderKey(tabId))

    fun openMoreMenu(tabId: String) = push(MoreMenuKey(tabId))

    fun openLinkOptions(tabId: String, target: HitTarget) = push(LinkOptionsKey(tabId, target))

    fun openSiteInfo(tabId: String) = push(SiteInfoKey(tabId))

    /**
     * Ask Skim about [tabId]. Incognito does nothing. A site that has not been allowed opens the
     * permission dialog; an allowed site opens the sheet with that host remembered.
     */
    fun openAskPage(tabId: String, fromReader: Boolean = false): Job = scope.launch {
            val tab = tabs.state.value.tab(tabId) ?: return@launch
            when (aiGate.decide(tab)) {
                AiGateDecision.OffInIncognito -> Unit
                AiGateDecision.NeedsPermission -> push(AiPermissionKey(tabId, fromReader))
                AiGateDecision.Allowed -> push(
                    AskPageKey(
                        tabId = tabId,
                        fromReader = fromReader,
                        approvedHost = hostOf(tab.url.orEmpty()),
                    ),
                )
            }
    }

    fun openSavedChat(chatId: Long) = push(AskPageKey(savedChatId = chatId))

    fun openAskTabs(prompt: String? = null) = push(AskTabsKey(prompt = prompt))

    fun openSavedTabsChat(chatId: Long) = push(AskTabsKey(savedChatId = chatId))

    fun openSettings() = push(SettingsKey)

    fun openAiPrivacy() = push(AiPrivacyKey)

    fun openHistory() = push(HistoryKey)

    fun openBookmarks(folderId: Long? = null) = push(BookmarksKey(folderId))

    fun openSkimHistory() = push(SkimHistoryKey)

    fun openLicenses() = push(LicensesKey)

    fun openDialog(key: DialogKey) = push(key)

    /** The back stack with the browser on top: everything above it closed, or the browser added. */
    private fun withBrowserOnTop(): List<NavKey> {
        val index = backStack.lastIndexOf(BrowserKey)
        return if (index >= 0) backStack.subList(0, index + 1).toList() else backStack + BrowserKey
    }

    /**
     * Makes the back stack [keys] (never empty: the browser then), turning FLAG_SECURE on first when
     * they show incognito content. Keys both share at the bottom stay in place.
     */
    private fun show(keys: List<NavKey>) {
        val next = keys.ifEmpty { listOf(BrowserKey) }
        armIf(secureTarget(next, tabs.state.value))
        var common = 0
        while (common < backStack.size && common < next.size && backStack[common] == next[common]) common++
        while (backStack.size > common) backStack.removeAt(backStack.lastIndex)
        backStack.addAll(next.subList(common, next.size))
    }

    private fun armIf(needed: Boolean) {
        if (!needed) return
        secure.setSecure(INCOGNITO_SURFACE_OWNER, true)
        arming.intValue++
    }

    private fun incognitoUnavailable() {
        snackbar.show(UiText.Res(R.string.incognito_unavailable))
    }
}
