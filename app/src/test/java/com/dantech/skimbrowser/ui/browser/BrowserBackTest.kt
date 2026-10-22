package com.dantech.skimbrowser.ui.browser

import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabRuntime
import org.junit.Assert.assertEquals
import org.junit.Test

/** What system Back does on the browser, in the order the browser asks. */
class BrowserBackTest {

    private val page = Tab(
        id = "tab-1",
        incognito = false,
        url = "https://example.com/",
        title = "Example",
        groupId = null,
        position = 0,
        createdAt = 1L,
        lastAccessedAt = 1L,
    )
    private val firstPage = TabRuntime(canGoBack = false)
    private val laterPage = TabRuntime(canGoBack = true)

    @Test
    fun aPageWithHistoryGoesBackFirst() {
        assertEquals(BrowserBack.GoBack, browserBack(page.copy(openedByExternalApp = true, closeOnBack = true, fromNewTabPage = true), laterPage))
    }

    @Test
    fun theFirstPageOfATabAnotherAppOpenedClosesItAndLeaves() {
        assertEquals(BrowserBack.CloseAndLeave, browserBack(page.copy(openedByExternalApp = true, fromNewTabPage = true), firstPage))
    }

    @Test
    fun theFirstPageOfATabOpenedForAScreenClosesItAndReturns() {
        assertEquals(BrowserBack.CloseAndReturn, browserBack(page.copy(closeOnBack = true, fromNewTabPage = true), firstPage))
    }

    @Test
    fun theFirstPageOfATabStartedFromTheNewTabPageReturnsToIt() {
        assertEquals(BrowserBack.NewTabPage, browserBack(page.copy(fromNewTabPage = true), firstPage))
    }

    @Test
    fun otherwiseBackLeavesTheBrowser() {
        assertEquals(BrowserBack.Leave, browserBack(page, firstPage))
        assertEquals("the New tab page", BrowserBack.Leave, browserBack(page.copy(url = null), firstPage))
    }
}
