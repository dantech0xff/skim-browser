package com.dantech.skimbrowser.browser

import androidx.annotation.MainThread

/**
 * The WebView profiles (androidx.webkit `ProfileStore`), behind an interface so the incognito
 * session can be tested without a WebView. Every call is made on the main thread, as `ProfileStore`
 * requires; no call but the two feature checks is made when [isMultiProfileSupported] is false.
 */
@MainThread
interface BrowserProfiles {
    /** `WebViewFeature.MULTI_PROFILE`: without it there is no incognito at all. */
    val isMultiProfileSupported: Boolean

    /** `WebViewFeature.HTTP_CACHE_MANAGER`: a profile's HTTP cache can be turned off. */
    val hasHttpCacheManager: Boolean

    fun getOrCreate(name: String): BrowserProfile

    /** The profile named [name], or null when there is none. */
    fun get(name: String): BrowserProfile?

    fun names(): List<String>

    /** Deletes the profile and its data; false when it is in use or was loaded in this process. */
    fun delete(name: String): Boolean
}

/** One WebView profile. Each call acts on this profile's own stores, never on the default ones. */
@MainThread
interface BrowserProfile {
    val name: String

    /** Sets the profile's HTTP cache quota to 0; only where [BrowserProfiles.hasHttpCacheManager]. */
    fun disableHttpCache()

    /** Removes every cookie of the profile and flushes its cookie store to disk. */
    fun clearCookies()

    /**
     * Deletes the profile's web storage: local and session storage, IndexedDB, and where the
     * WebView can delete them (`DELETE_BROWSING_DATA`) service workers and Cache Storage too.
     */
    fun clearWebStorage()

    /** Forgets the geolocation answers the profile's sites were given. */
    fun clearGeolocationPermissions()

    /**
     * The `Cookie` header this profile's cookie store sends to [url], or null when it has none:
     * what a download of the profile's tabs sends, never the default profile's cookies.
     */
    fun cookieHeader(url: String): String?
}
