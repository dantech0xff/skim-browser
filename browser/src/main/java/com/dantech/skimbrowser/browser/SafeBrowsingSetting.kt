package com.dantech.skimbrowser.browser

import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Turns WebView Safe Browsing on or off for [webView]. The manifest opts the process in. This
 * call is how the Settings switch turns it off for WebViews that are already showing, and how a
 * WebView that does not support the API is left alone.
 *
 * The check itself runs in Android System WebView. Skim does not receive the address.
 */
@MainThread
fun applySafeBrowsing(webView: WebView, enabled: Boolean) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) return
    try {
        WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, enabled)
    } catch (_: UnsupportedOperationException) {
        // The feature check can pass while this WebView build still refuses the call.
    }
}
