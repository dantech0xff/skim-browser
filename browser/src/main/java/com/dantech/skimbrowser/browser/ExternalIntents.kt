package com.dantech.skimbrowser.browser

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import java.net.URISyntaxException
import java.util.Locale

/** What a page's main-frame navigation to a URL does (`shouldOverrideUrlLoading`). */
sealed interface ExternalNav {
    /** A web page, or a URL the browser engine handles itself (`about:`, `blob:`, `data:`). */
    data object InWebView : ExternalNav

    /** Neither loaded nor handed to another app. */
    data object Blocked : ExternalNav

    /**
     * Handed to Android. When no app can take [intent], [fallbackUrl] (only ever http or https)
     * loads in the tab instead.
     */
    data class Launch(val intent: Intent, val fallbackUrl: String?) : ExternalNav
}

/**
 * Decides a navigation to [uri] that the page of a tab ([incognito] or not) started. `file:`,
 * `content:` and `javascript:` never load. Any other non-web scheme (`tel:`, `mailto:`, `intent:`,
 * `market:` and so on) goes to another app, and only right after a user gesture ([hasGesture]), so
 * a page cannot open apps by itself.
 *
 * An incognito tab only hands over the links that dial, write an email or write a text message:
 * any other app, another browser above all, would get the page's address outside incognito.
 *
 * The intent is made safe the way Chrome does it: only activities that accept the BROWSABLE
 * category can receive it, no explicit component or selector can pick a private activity, no
 * launch flag survives (so a page can neither clear Skim's task nor grant access to a URI), an
 * intent aimed at Skim itself ([ownPackage]) is refused, and so is a target that is itself a
 * `file:` or `content:` URI. An `intent:` URL's `browser_fallback_url` is kept only when it is http
 * or https.
 */
fun externalNavigation(uri: Uri, hasGesture: Boolean, incognito: Boolean, ownPackage: String): ExternalNav {
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return ExternalNav.Blocked
    if (scheme in IN_WEBVIEW_SCHEMES) return ExternalNav.InWebView
    if (scheme in BLOCKED_SCHEMES || !hasGesture) return ExternalNav.Blocked
    if (incognito && scheme !in INCOGNITO_SCHEMES) return ExternalNav.Blocked

    val intent = if (scheme == INTENT_SCHEME) {
        parseIntentUrl(uri.toString()) ?: return ExternalNav.Blocked
    } else {
        Intent(Intent.ACTION_VIEW, uri)
    }
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    intent.flags = 0
    if (intent.`package` == ownPackage) return ExternalNav.Blocked
    if (intent.data?.scheme?.lowercase(Locale.ROOT) in BLOCKED_SCHEMES) return ExternalNav.Blocked

    val fallback = intent.getStringExtra(FALLBACK_URL_EXTRA)?.takeIf(::isHttpUrl)
    return ExternalNav.Launch(intent, fallback)
}

private fun parseIntentUrl(url: String): Intent? = try {
    Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
} catch (_: URISyntaxException) {
    null
} catch (_: RuntimeException) {
    null
}

private fun isHttpUrl(url: String): Boolean {
    val parsed = url.trim().toUri()
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    return (scheme == "http" || scheme == "https") && !parsed.host.isNullOrEmpty()
}

private const val INTENT_SCHEME = "intent"
private const val FALLBACK_URL_EXTRA = "browser_fallback_url"

private val IN_WEBVIEW_SCHEMES = setOf("http", "https", "about", "blob", "data")
private val BLOCKED_SCHEMES = setOf("file", "content", "javascript")

/** What an incognito tab may hand to another app: a call, an email, a text message. */
private val INCOGNITO_SCHEMES = setOf("tel", "mailto", "sms", "smsto")
