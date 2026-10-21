package com.dantech.skimbrowser.browser

import android.webkit.WebViewClient

/**
 * The error state (11) for a failed main-frame load of [url], from the WebView's [errorCode] and
 * [description] (Chromium's name, such as "net::ERR_NAME_NOT_RESOLVED").
 *
 * "No internet" needs proof that the phone is offline: no default network at all
 * ([hasActiveNetwork] false), or `ERR_INTERNET_DISCONNECTED`, which the WebView reports with the
 * same code as a DNS failure. A network that is merely not validated (a captive portal, a blocked
 * connectivity check) is not enough, so a mistyped address there still reads "Can't reach this
 * site", and the No internet state, which reloads by itself, cannot get stuck.
 */
fun loadErrorOf(errorCode: Int, description: String?, url: String, hasActiveNetwork: Boolean): LoadError {
    val named = description?.let { ERROR_NAME.find(it)?.value?.take(MAX_CODE_NAME_LENGTH) }
    return when {
        !hasActiveNetwork || named == INTERNET_DISCONNECTED ->
            LoadError.NoInternet(url, named ?: INTERNET_DISCONNECTED)
        errorCode == WebViewClient.ERROR_TIMEOUT || named?.endsWith("_TIMED_OUT") == true ->
            LoadError.TimedOut(url, named ?: "ERR_TIMED_OUT")
        else -> LoadError.Unreachable(url, named ?: codeNameOf(errorCode))
    }
}

/** The Chromium name of a WebView error code, for a description that does not carry one. */
private fun codeNameOf(errorCode: Int): String = when (errorCode) {
    WebViewClient.ERROR_HOST_LOOKUP -> "ERR_NAME_NOT_RESOLVED"
    WebViewClient.ERROR_CONNECT -> "ERR_CONNECTION_FAILED"
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "ERR_SSL_PROTOCOL_ERROR"
    WebViewClient.ERROR_REDIRECT_LOOP -> "ERR_TOO_MANY_REDIRECTS"
    WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "ERR_UNKNOWN_URL_SCHEME"
    WebViewClient.ERROR_BAD_URL -> "ERR_INVALID_URL"
    else -> "ERR_FAILED"
}

private const val INTERNET_DISCONNECTED = "ERR_INTERNET_DISCONNECTED"
private const val MAX_CODE_NAME_LENGTH = 64
private val ERROR_NAME = Regex("ERR_[A-Z0-9_]+")
