package com.dantech.skimbrowser.domain.browsing

/**
 * The live pages Clear browsing data can touch. Absent when no Activity is up, so a cache clear
 * waits for the next one.
 */
interface OpenPages {
    fun clearHistory(tabId: String)

    fun clearCache()
}

/** Forgets site data. An incognito tab uses that session's profile for one site. */
interface SiteDataCleaner {
    suspend fun clearSite(host: String, incognitoTab: Boolean)

    /**
     * Clear browsing data for the default profile. [pages] is the Activity's live pages, or null
     * when no Activity is up.
     */
    suspend fun clearAll(history: Boolean, cookies: Boolean, cache: Boolean, pages: OpenPages?)
}
