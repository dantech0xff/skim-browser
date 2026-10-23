package com.dantech.skimbrowser.domain.browsing

/**
 * The live pages Clear browsing data can touch. Absent when no Activity is up, so a cache clear
 * waits for the next one.
 */
interface OpenPages {
    fun clearHistory(tabId: String)

    fun clearCache()
}
