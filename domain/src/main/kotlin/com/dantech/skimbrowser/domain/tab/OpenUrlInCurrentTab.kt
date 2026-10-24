package com.dantech.skimbrowser.domain.tab

/**
 * Opens [url] in the current tab. When no tab is current, opens a new normal tab on that URL.
 * The caller shows the browser afterwards.
 */
class OpenUrlInCurrentTab(private val tabs: TabsController) {
    operator fun invoke(url: String) {
        val current = tabs.state.value.currentTabId
        if (current == null) {
            tabs.newTab(incognito = false, url = url)
        } else {
            tabs.load(current, url)
        }
    }
}
