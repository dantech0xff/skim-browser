package com.dantech.skimbrowser.domain.tab

import com.dantech.skimbrowser.domain.page.HighlightRequest
import com.dantech.skimbrowser.domain.page.PageContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/** Records the two tab commands [OpenUrlInCurrentTab] can make. */
internal class RecordingTabs(currentTabId: String? = null) : TabsController {
    override val state: StateFlow<TabsState> = MutableStateFlow(TabsState(currentTabId = currentTabId))
    val opened = mutableListOf<Opened>()
    val loads = mutableListOf<Pair<String, String>>()

    data class Opened(val incognito: Boolean, val url: String?)

    override fun openNewTab(
        incognito: Boolean,
        url: String?,
        background: Boolean,
        openedByExternalApp: Boolean,
        closeOnBack: Boolean,
        openerTabId: String?,
    ): String {
        opened += Opened(incognito, url)
        return "opened"
    }

    override fun load(tabId: String, url: String) {
        loads += tabId to url
    }

    override fun runtime(tabId: String): StateFlow<TabRuntime> = error("unused")
    override fun select(tabId: String) = error("unused")
    override fun returnToNewTabPage(tabId: String) = error("unused")
    override fun goBack(tabId: String): Boolean = error("unused")
    override fun goForward(tabId: String) = error("unused")
    override fun reload(tabId: String) = error("unused")
    override fun setDesktopSite(tabId: String, on: Boolean) = error("unused")
    override fun find(tabId: String, query: String) = error("unused")
    override fun findNext(tabId: String, forward: Boolean) = error("unused")
    override fun clearFind(tabId: String) = error("unused")
    override fun close(tabIds: List<String>): CloseToken = error("unused")
    override fun undoClose(token: CloseToken) = error("unused")
    override fun finalizeClose(token: CloseToken) = error("unused")
    override fun replaceLayout(layout: GroupLayout, expectedVersion: Long?, snapshot: GroupingSnapshot?): Boolean =
        error("unused")
    override suspend fun extractPage(tabId: String): PageContent? = error("unused")
    override suspend fun extractLivePage(tabId: String): PageContent? = error("unused")
    override suspend fun highlightBlock(tabId: String, request: HighlightRequest): Boolean = error("unused")
}
