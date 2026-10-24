package com.dantech.skimbrowser.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkimKeyHelpersTest {

    private data object Foreign : NavKey

    @Test
    fun sheetsAndDialogsAreOverlaysAndScreensAreNot() {
        assertTrue(MoreMenuKey("tab-1").isOverlay)
        assertTrue(ThemeDialogKey.isOverlay)
        assertFalse(BrowserKey.isOverlay)
        assertFalse(TabsKey(incognito = false).isOverlay)
    }

    @Test
    fun theTopScreenIsTheLastScreenUnderAnySheetOrDialog() {
        val keys = listOf(WelcomeKey, BrowserKey, MoreMenuKey("tab-1"), TabsKey(incognito = false), SiteInfoKey("tab-1"), ThemeDialogKey)

        assertEquals(TabsKey(incognito = false), topScreen(keys))
    }

    @Test
    fun withoutAScreenThereIsNoTopScreen() {
        assertNull(topScreen(emptyList()))
        assertNull(topScreen(listOf(MoreMenuKey("tab-1"), ThemeDialogKey)))
        assertNull(topScreen(listOf(Foreign)))
    }

    @Test
    fun whatIsOnScreenIsTheTopScreenAndEverythingOverItInOrder() {
        val keys = listOf(WelcomeKey, BrowserKey, MoreMenuKey("tab-1"), SettingsKey, SiteInfoKey("tab-1"), ThemeDialogKey)

        assertEquals(listOf(SettingsKey, SiteInfoKey("tab-1"), ThemeDialogKey), visibleKeys(keys))
    }

    @Test
    fun aBackStackOfOverlaysAloneShowsAllOfThem() {
        val keys = listOf(MoreMenuKey("tab-1"), ThemeDialogKey)

        assertEquals(keys, visibleKeys(keys))
    }

    @Test
    fun keysThatAreNotSkimKeysAreNeverShown() {
        assertEquals(listOf(BrowserKey), visibleKeys(listOf(Foreign, BrowserKey, Foreign)))
        assertEquals(emptyList<SkimKey>(), visibleKeys(listOf(Foreign)))
    }
}
