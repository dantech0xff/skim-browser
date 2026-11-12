package com.dantech.skimbrowser.browser

import android.os.Build
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Turns algorithmic darkening on or off for [webView]. WebView reads `isLightTheme` from the
 * application theme, which otherwise follows system night mode, so this sets that attribute to
 * match [dark]. Compose colors do not reach it. A WebView that does not support the API is left
 * unchanged. The caller decides dark from the theme: Dark is on, Light is off, System follows
 * night mode. Incognito chrome does not force this; the theme does.
 */
@MainThread
fun applyAlgorithmicDarkening(webView: WebView, dark: Boolean) {
    // Chromium reads the application theme (DarkModeHelper.getLightTheme). The setter below
    // does nothing when the flag already matches, so that path is forced to write again.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        webView.context.applicationContext.theme.applyStyle(
            if (dark) R.style.PageDarkeningDark else R.style.PageDarkeningLight,
            true,
        )
    }
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
    try {
        // The setter does nothing when the flag already matches, so the new theme would be ignored.
        if (WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings) == dark) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, !dark)
        }
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
    } catch (_: UnsupportedOperationException) {
        // The feature check can pass while this WebView build still refuses the call.
    }
}
