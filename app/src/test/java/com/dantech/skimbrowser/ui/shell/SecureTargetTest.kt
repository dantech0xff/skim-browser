package com.dantech.skimbrowser.ui.shell

import com.dantech.skimbrowser.browser.HitTarget
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabsState
import com.dantech.skimbrowser.ui.navigation.AiPermissionKey
import com.dantech.skimbrowser.ui.navigation.AskPageKey
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.EditBookmarkKey
import com.dantech.skimbrowser.ui.navigation.LinkOptionsKey
import com.dantech.skimbrowser.ui.navigation.MoreMenuKey
import com.dantech.skimbrowser.ui.navigation.ReaderKey
import com.dantech.skimbrowser.ui.navigation.SettingsKey
import com.dantech.skimbrowser.ui.navigation.SiteInfoKey
import com.dantech.skimbrowser.ui.navigation.TabsKey
import com.dantech.skimbrowser.ui.navigation.ThemeDialogKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which back stacks put incognito content on screen, every entry shown counted. */
class SecureTargetTest {

    private val normal = tab("normal")
    private val incognito = tab("incognito", incognito = true)
    private val onNormal = TabsState(tabs = listOf(normal, incognito), currentTabId = normal.id)
    private val onIncognito = onNormal.copy(currentTabId = incognito.id)

    @Test
    fun theBrowserIsSecureOnAnIncognitoTabOnly() {
        assertTrue(secureTarget(listOf(BrowserKey), onIncognito))
        assertFalse(secureTarget(listOf(BrowserKey), onNormal))
        assertFalse("no current tab", secureTarget(listOf(BrowserKey), TabsState()))
    }

    @Test
    fun theMoreMenuOfAnIncognitoTabIsSecureOverANormalScreen() {
        assertTrue(secureTarget(listOf(BrowserKey, MoreMenuKey(incognito.id)), onNormal))
        assertFalse(secureTarget(listOf(BrowserKey, MoreMenuKey(normal.id)), onNormal))
    }

    @Test
    fun linkAndImageOptionsOfAnIncognitoTabAreSecure() {
        val link = LinkOptionsKey(incognito.id, HitTarget.Link("https://example.com/", text = "Example"))
        val image = LinkOptionsKey(incognito.id, HitTarget.Image("https://example.com/a.png", alt = null, linkUrl = null))

        assertTrue(secureTarget(listOf(BrowserKey, link), onNormal))
        assertTrue(secureTarget(listOf(BrowserKey, image), onNormal))
    }

    @Test
    fun siteInformationOfAnIncognitoTabIsSecure() {
        assertTrue(secureTarget(listOf(BrowserKey, SiteInfoKey(incognito.id)), onNormal))
        assertFalse(secureTarget(listOf(BrowserKey, SiteInfoKey(normal.id)), onNormal))
    }

    @Test
    fun theReaderOfAnIncognitoTabIsSecure() {
        assertTrue(secureTarget(listOf(BrowserKey, ReaderKey(incognito.id)), onNormal))
        assertFalse(secureTarget(listOf(BrowserKey, ReaderKey(normal.id)), onIncognito))
    }

    @Test
    fun theIncognitoTabsScreenIsSecureAndTheTabsScreenIsNot() {
        assertTrue(secureTarget(listOf(BrowserKey, TabsKey(incognito = true)), onNormal))
        assertFalse("09 over the browser of an incognito tab", secureTarget(listOf(BrowserKey, TabsKey(incognito = false)), onIncognito))
    }

    @Test
    fun aDialogWithoutATabOverAnIncognitoTabIsSecure() {
        assertTrue(secureTarget(listOf(BrowserKey, EditBookmarkKey(bookmarkId = 1)), onIncognito))
        assertTrue("dialog over a sheet over the tab", secureTarget(listOf(BrowserKey, MoreMenuKey(normal.id), ThemeDialogKey), onIncognito))
        assertFalse(secureTarget(listOf(BrowserKey, EditBookmarkKey(bookmarkId = 1)), onNormal))
    }

    @Test
    fun aDialogOfAnIncognitoTabOverANormalScreenIsSecure() {
        assertTrue(secureTarget(listOf(BrowserKey, AiPermissionKey(incognito.id)), onNormal))
        assertTrue(secureTarget(listOf(BrowserKey, AskPageKey(tabId = incognito.id)), onNormal))
    }

    @Test
    fun whatIsUnderTheTopScreenIsNotShown() {
        assertFalse(secureTarget(listOf(BrowserKey, SettingsKey), onIncognito))
        assertFalse(secureTarget(listOf(BrowserKey, MoreMenuKey(incognito.id), SettingsKey), onNormal))
        assertFalse(secureTarget(listOf(BrowserKey, TabsKey(incognito = true), SettingsKey), onNormal))
    }

    @Test
    fun aKeyOfATabThatIsNoLongerOpenStaysSecureUntilItLeavesTheBackStack() {
        // A page closed its own pop-up while a screen of it was up; the screen may still show the tab.
        val closed = TabsState(tabs = listOf(normal), currentTabId = normal.id)

        assertTrue(secureTarget(listOf(BrowserKey, MoreMenuKey(incognito.id)), closed))
        assertTrue(secureTarget(listOf(BrowserKey, ReaderKey("closed-meanwhile")), closed))
        assertFalse("the key is gone", secureTarget(listOf(BrowserKey), closed))
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
