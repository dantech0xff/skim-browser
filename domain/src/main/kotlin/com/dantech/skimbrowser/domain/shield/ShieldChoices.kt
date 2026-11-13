package com.dantech.skimbrowser.domain.shield

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether request blocking is on for a site, and the decision for one request. Normal-tab choices
 * are kept on the device. Incognito choices last only until the incognito session ends.
 */
interface ShieldChoices {
    /** Hosts of normal sites where blocking is off. */
    val disabledHosts: StateFlow<Set<String>>

    fun incognitoDisabled(host: String): Boolean

    suspend fun setDisabled(host: String, incognito: Boolean, disabled: Boolean)

    /**
     * True when [requestUrl] should be answered empty. The main frame is the caller's to allow:
     * this only looks at the hosts.
     */
    fun blocks(documentUrl: String?, requestUrl: String, incognito: Boolean): Boolean

    /** Forgets incognito choices. The session calls this when it ends. */
    fun clearIncognito()

    /**
     * Stylesheet that hides leftover ad elements on [documentUrl]. Empty when blocking is off
     * for that site, the URL has no site, or this shield does not hide elements. Null when the
     * site choice or the rules are not loaded yet. This does not wait and does not read the file.
     */
    fun cosmeticCss(documentUrl: String?, incognito: Boolean): String? = ""

    /** Reads the cosmetic rules if they are not loaded yet. Callers are off the main thread. */
    fun warmCosmetic() = Unit

    /**
     * Runs [action] once the site choice and the cosmetic rules are loaded. The action may run
     * on the caller's thread when they are already loaded, and on a background thread otherwise.
     */
    fun whenCosmeticReady(action: () -> Unit) {
        action()
    }

    /**
     * Stylesheet that hides cookie banners on [documentUrl]. Empty when hiding is off, the URL
     * has no site, or this shield does not hide banners. Null when the rules are not loaded yet.
     * This does not wait and does not read the file.
     */
    fun cookieCss(documentUrl: String?, incognito: Boolean): String? = ""

    companion object {
        /** Blocks nothing. WebView tests that do not care about the shield use this. */
        val AllowAll: ShieldChoices = object : ShieldChoices {
            override val disabledHosts: StateFlow<Set<String>> = MutableStateFlow(emptySet())
            override fun incognitoDisabled(host: String) = false
            override suspend fun setDisabled(host: String, incognito: Boolean, disabled: Boolean) = Unit
            override fun blocks(documentUrl: String?, requestUrl: String, incognito: Boolean) = false
            override fun clearIncognito() = Unit
        }
    }
}
