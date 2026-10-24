package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import androidx.annotation.MainThread
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature

/**
 * [BrowserProfiles] over androidx.webkit's [ProfileStore]. The two feature checks are read once:
 * the WebView provider cannot change while the process lives. Every other call is made on the
 * main thread and fails with [UnsupportedOperationException] where profiles are not supported,
 * as `ProfileStore` itself would.
 */
@MainThread
class WebViewProfiles : BrowserProfiles {

    override val isMultiProfileSupported: Boolean by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }

    override val hasHttpCacheManager: Boolean by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.HTTP_CACHE_MANAGER)
    }

    override fun getOrCreate(name: String): BrowserProfile =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewProfile(name, ProfileStore.getInstance().getOrCreateProfile(name))
        } else {
            throw unsupported()
        }

    override fun get(name: String): BrowserProfile? =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getProfile(name)?.let { WebViewProfile(name, it) }
        } else {
            throw unsupported()
        }

    override fun names(): List<String> =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().allProfileNames
        } else {
            throw unsupported()
        }

    override fun delete(name: String): Boolean =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().deleteProfile(name)
        } else {
            throw unsupported()
        }

    private fun unsupported() = UnsupportedOperationException("WebView profiles (MULTI_PROFILE) are not supported")

    /**
     * One profile's own stores; nothing here touches the default profile's. A [Profile] only
     * exists where `MULTI_PROFILE` is supported, as only `ProfileStore` makes one, so its stores
     * need no feature check of their own.
     */
    @SuppressLint("RequiresFeature")
    private class WebViewProfile(override val name: String, private val profile: Profile) : BrowserProfile {

        override fun disableHttpCache() {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.HTTP_CACHE_MANAGER)) {
                profile.httpCache.setQuotaBytes(0)
            } else {
                throw UnsupportedOperationException("The HTTP cache of a profile cannot be managed (HTTP_CACHE_MANAGER)")
            }
        }

        override fun clearCookies() {
            val cookies = profile.cookieManager
            cookies.removeAllCookies(null)
            cookies.flush()
        }

        override fun clearWebStorage() {
            val storage = profile.webStorage
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                // Everything the sites stored. deleteAllData alone keeps service worker
                // registrations and Cache Storage, which would outlive the session on disk.
                WebStorageCompat.deleteBrowsingData(storage) {}
            } else {
                storage.deleteAllData()
            }
        }

        override fun clearGeolocationPermissions() {
            profile.geolocationPermissions.clearAll()
        }

        override fun cookieHeader(url: String): String? = profile.cookieManager.getCookie(url)?.takeIf { it.isNotBlank() }

        override fun expireCookiesForHost(host: String) {
            val cookies = profile.cookieManager
            expireHostCookies(
                host,
                read = { cookies.getCookie(it) },
                write = { url, value -> cookies.setCookie(url, value) },
                flush = { cookies.flush() },
            )
        }

        override fun deleteOriginsForHost(host: String) {
            val storage = profile.webStorage
            storage.deleteOrigin("https://$host")
            storage.deleteOrigin("http://$host")
        }
    }
}

/**
 * Expires every cookie [read] returns for http and https of [host]. [host] is already a site key
 * (lowercase, no "www."). Names come from the `Cookie` header; each is cleared for `path=/` and
 * for the host's domain, then [flush] runs once.
 */
fun expireHostCookies(
    host: String,
    read: (url: String) -> String?,
    write: (url: String, value: String) -> Unit,
    flush: () -> Unit,
) {
    if (host.isEmpty() || host.any { it.isWhitespace() || it == '/' }) return
    for (url in listOf("https://$host", "http://$host")) {
        cookieNames(read(url)).forEach { name ->
            val cleared = "$name=; Max-Age=0; path=/"
            write(url, cleared)
            write(url, "$cleared; domain=.$host")
        }
    }
    flush()
}

/** Cookie names in a `Cookie` header (`a=1; b=2`), in order, without blanks or duplicates. */
fun cookieNames(header: String?): List<String> {
    if (header.isNullOrBlank()) return emptyList()
    val names = ArrayList<String>()
    header.split(';').forEach { part ->
        val name = part.substringBefore('=').trim()
        if (name.isNotEmpty() && name !in names) names += name
    }
    return names
}
