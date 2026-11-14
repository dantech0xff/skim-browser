package com.dantech.skimbrowser.domain.interrupt

import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.registrableDomain

/**
 * What the page tried to do. A popup is `window.open`. A top-frame navigation is the page's own
 * load, the one `shouldOverrideUrlLoading` sees. A server 3xx redirect never arrives here: the
 * WebView follows it without asking.
 */
enum class InterruptKind { POPUP, TOP_FRAME }

enum class InterruptVerdict { ALLOW, BLOCK }

/**
 * The stored choices. Allow-lists hold the site key [hostOf] returns, so `www` is already gone.
 * [blockOddRedirects] covers cross-site page jumps only. An app link without a gesture is blocked
 * either way, because a page must not open another app by itself.
 */
data class InterruptPolicy(
    val blockPopups: Boolean = true,
    val blockOddRedirects: Boolean = true,
    val popupAllowHosts: Set<String> = emptySet(),
    val redirectAllowHosts: Set<String> = emptySet(),
)

/**
 * One navigation the WebView is about to make. [millisSinceInteraction] is null when this document
 * has not been touched. A touch is a finger down on the page.
 */
data class InterruptRequest(
    val kind: InterruptKind,
    val sourceUrl: String?,
    val targetUrl: String,
    val hasGesture: Boolean,
    val isMainFrame: Boolean = true,
    val millisSinceInteraction: Long? = null,
)

fun decideInterrupt(request: InterruptRequest, policy: InterruptPolicy): InterruptVerdict = when (request.kind) {
    InterruptKind.POPUP -> popupVerdict(request, policy)
    InterruptKind.TOP_FRAME -> navigationVerdict(request, policy)
}

const val IDLE_AFTER_TOUCH_MS = 30_000L

private fun popupVerdict(request: InterruptRequest, policy: InterruptPolicy): InterruptVerdict {
    if (!policy.blockPopups) return InterruptVerdict.ALLOW
    if (siteAllowed(request.sourceUrl, policy.popupAllowHosts)) return InterruptVerdict.ALLOW
    return if (request.hasGesture) InterruptVerdict.ALLOW else InterruptVerdict.BLOCK
}

private fun navigationVerdict(request: InterruptRequest, policy: InterruptPolicy): InterruptVerdict {
    if (!request.isMainFrame) return InterruptVerdict.ALLOW
    val targetScheme = schemeOf(request.targetUrl) ?: return InterruptVerdict.BLOCK
    if (targetScheme in SILENT_SCHEMES) return InterruptVerdict.ALLOW
    if (targetScheme == "http" || targetScheme == "https") return webVerdict(request, policy)
    if (targetScheme in IN_PAGE_SCHEMES) return InterruptVerdict.ALLOW
    if (request.hasGesture) return InterruptVerdict.ALLOW
    if (siteAllowed(request.sourceUrl, policy.redirectAllowHosts)) return InterruptVerdict.ALLOW
    return InterruptVerdict.BLOCK
}

private fun webVerdict(request: InterruptRequest, policy: InterruptPolicy): InterruptVerdict {
    if (request.hasGesture) return InterruptVerdict.ALLOW
    if (!policy.blockOddRedirects) return InterruptVerdict.ALLOW
    if (siteAllowed(request.sourceUrl, policy.redirectAllowHosts)) return InterruptVerdict.ALLOW
    if (!crossSite(request.sourceUrl, request.targetUrl)) return InterruptVerdict.ALLOW
    if (authContinuation(request.sourceUrl, request.targetUrl)) return InterruptVerdict.ALLOW
    val sourceHost = request.sourceUrl?.let(::hostOf)
    if (sourceHost != null && registrableDomain(sourceHost) in LOGIN_OR_PAY_DOMAINS) return InterruptVerdict.ALLOW
    val since = request.millisSinceInteraction
    val idle = since == null || since >= IDLE_AFTER_TOUCH_MS
    return if (idle) InterruptVerdict.BLOCK else InterruptVerdict.ALLOW
}

private fun siteAllowed(url: String?, hosts: Set<String>): Boolean {
    val host = url?.let(::hostOf) ?: return false
    return host in hosts
}

private fun crossSite(source: String?, target: String): Boolean {
    val sourceHost = source?.let(::hostOf) ?: return false
    val targetHost = hostOf(target) ?: return false
    return registrableDomain(sourceHost) != registrableDomain(targetHost)
}

private fun authContinuation(source: String?, target: String): Boolean =
    hasAuthQuery(target) || (source != null && hasAuthQuery(source))

private fun hasAuthQuery(url: String): Boolean {
    val names = queryNames(url)
    if (names.any { it in STRONG_AUTH_PARAMS }) return true
    return "code" in names && ("state" in names || "session_state" in names)
}

private fun queryNames(url: String): Set<String> {
    val query = url.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return emptySet()
    return query.split('&').map { it.substringBefore('=').trim().lowercase() }.toSet()
}

private fun schemeOf(url: String): String? {
    val trimmed = url.trim()
    val colon = trimmed.indexOf(':')
    if (colon <= 0) return null
    val raw = trimmed.substring(0, colon)
    if (raw.any { it.isWhitespace() }) return null
    return raw.lowercase()
}

private val SILENT_SCHEMES = setOf("file", "content", "javascript")
private val IN_PAGE_SCHEMES = setOf("about", "blob", "data")
private val STRONG_AUTH_PARAMS = setOf(
    "id_token",
    "access_token",
    "samlresponse",
    "oauth_token",
    "oauth_verifier",
    "id_token_hint",
)

/**
 * Registrable domains that complete a login or a payment by navigating on their own. A page on
 * one of these may leave the site without a gesture. A publisher page is not in this set.
 */
private val LOGIN_OR_PAY_DOMAINS = setOf(
    "google.com",
    "googleusercontent.com",
    "apple.com",
    "microsoft.com",
    "microsoftonline.com",
    "live.com",
    "windows.net",
    "facebook.com",
    "github.com",
    "amazon.com",
    "paypal.com",
    "stripe.com",
    "okta.com",
    "auth0.com",
    "yahoo.com",
    "linkedin.com",
)
