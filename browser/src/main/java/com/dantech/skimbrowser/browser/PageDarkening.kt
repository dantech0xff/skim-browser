package com.dantech.skimbrowser.browser

import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Turns algorithmic darkening on or off for [webView]. A WebView that does not support it is left
 * as it is. The caller decides dark from the theme: Dark is on, Light is off, System follows night
 * mode. Incognito chrome does not force this; the theme does.
 */
@MainThread
fun applyAlgorithmicDarkening(webView: WebView, dark: Boolean) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
    try {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
    } catch (_: UnsupportedOperationException) {
        // The feature check can pass while this WebView build still refuses the call.
    }
}
