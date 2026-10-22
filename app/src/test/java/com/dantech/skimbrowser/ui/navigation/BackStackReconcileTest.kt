package com.dantech.skimbrowser.ui.navigation

import com.dantech.skimbrowser.browser.HitTarget
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabsState
import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackReconcileTest {

    private val normal = tab("normal")
    private val other = tab("other")
    private val incognitoTab = tab("incognito", incognito = true)

    @Test
    fun keysOfAnIncognitoTabThatDidNotComeBackAreDropped() {
        // After process death only the normal tabs are back.
        val restored = TabsState(tabs = listOf(normal), currentTabId = normal.id)
        val keys = listOf(
            BrowserKey,
            ReaderKey(incognitoTab.id),
            MoreMenuKey(incognitoTab.id),
            LinkOptionsKey(incognitoTab.id, HitTarget.Link("https://example.com/", text = null)),
            SiteInfoKey(incognitoTab.id),
        )

        assertEquals(listOf(BrowserKey), reconcileBackStack(keys, restored))
    }

    @Test
    fun keysOfAPurgedNormalTabAreDroppedAndTheRestKeepsItsOrder() {
        val restored = TabsState(tabs = listOf(normal), currentTabId = normal.id)
        val keys = listOf(
            BrowserKey,
            ReaderKey(other.id),
            AskPageKey(tabId = other.id, fromReader = true),
            SettingsKey,
            AiPermissionKey(normal.id),
        )

        assertEquals(listOf(BrowserKey, SettingsKey, AiPermissionKey(normal.id)), reconcileBackStack(keys, restored))
    }

    @Test
    fun theReaderOfATabThatIsStillOpenIsKept() {
        val open = TabsState(tabs = listOf(normal, other), currentTabId = other.id)
        val keys = listOf(BrowserKey, ReaderKey(other.id), AskPageKey(tabId = other.id, fromReader = true))

        assertEquals(keys, reconcileBackStack(keys, open))
    }

    @Test
    fun incognitoTabsBecomeTabsOnceNoIncognitoTabIsOpen() {
        val restored = TabsState(tabs = listOf(normal), currentTabId = normal.id)

        assertEquals(
            listOf(BrowserKey, TabsKey(incognito = false)),
            reconcileBackStack(listOf(BrowserKey, TabsKey(incognito = true)), restored),
        )
    }

    @Test
    fun incognitoTabsStayWhileAnIncognitoTabIsOpen() {
        // The Activity was recreated in the same process: the incognito tab is still open.
        val open = TabsState(tabs = listOf(normal, incognitoTab), currentTabId = incognitoTab.id)
        val keys = listOf(BrowserKey, TabsKey(incognito = true, focusTabId = incognitoTab.id))

        assertEquals(keys, reconcileBackStack(keys, open))
    }

    @Test
    fun aTabsScreenForgetsAFocusTabThatIsGone() {
        val restored = TabsState(tabs = listOf(normal), currentTabId = normal.id)

        assertEquals(
            listOf(BrowserKey, TabsKey(incognito = false)),
            reconcileBackStack(listOf(BrowserKey, TabsKey(incognito = false, focusTabId = other.id)), restored),
        )
    }

    @Test
    fun keysWithoutATabAreKept() {
        val none = TabsState()
        val keys = listOf(
            WelcomeKey,
            SetupKey,
            BookmarksKey(folderId = 3),
            AskPageKey(savedChatId = 9),
            AskTabsKey(prompt = "Compare these"),
            EditBookmarkKey(bookmarkId = 4),
        )

        assertEquals(keys, reconcileBackStack(keys, none))
    }

    @Test
    fun aBackStackLeftWithNothingShowsTheBrowser() {
        assertEquals(listOf(BrowserKey), reconcileBackStack(listOf(ReaderKey(incognitoTab.id)), TabsState()))
        assertEquals(listOf(BrowserKey), reconcileBackStack(emptyList(), TabsState()))
    }

    private fun tab(id: String, incognito: Boolean = false) = Tab(
        id = id,
        incognito = incognito,
        url = "https://example.com/$id",
        title = id,
        groupId = null,
        position = 0,
        createdAt = 1L,
        lastAccessedAt = 1L,
    )
}
