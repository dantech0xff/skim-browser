package com.dantech.skimbrowser.domain.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A URL opens in the current tab, or in a new normal tab when none is current. */
class OpenUrlInCurrentTabTest {

    @Test
    fun aCurrentTabLoadsTheUrl() {
        val tabs = RecordingTabs(currentTabId = "tab-1")

        OpenUrlInCurrentTab(tabs)("https://daylightjournal.com/cold")

        assertEquals(listOf("tab-1" to "https://daylightjournal.com/cold"), tabs.loads)
        assertTrue(tabs.opened.isEmpty())
    }

    @Test
    fun noCurrentTabOpensANormalTab() {
        val tabs = RecordingTabs()

        OpenUrlInCurrentTab(tabs)("https://daylightjournal.com/cold")

        assertEquals(
            listOf(RecordingTabs.Opened(incognito = false, url = "https://daylightjournal.com/cold")),
            tabs.opened,
        )
        assertTrue(tabs.loads.isEmpty())
    }
}
