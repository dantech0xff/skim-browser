package com.dantech.skimbrowser.browser

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Build
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.util.IdentityHashMap

/**
 * Turns algorithmic darkening on or off for [webView]. WebView reads `isLightTheme` from the
 * context it was created with, which otherwise follows system night mode, so this sets that
 * attribute to match [dark]. Compose colors do not reach it. A WebView that does not support the
 * API is left unchanged. The caller decides dark from the theme: Dark is on, Light is off, System
 * follows night mode. Incognito chrome does not force this; the theme does.
 */
@MainThread
fun applyAlgorithmicDarkening(webView: WebView, dark: Boolean) {
    // Chromium unwraps its own wrapper and reads that context's theme (DarkModeHelper.getLightTheme).
    // The application theme is a different object, so the overlay has to land on the WebView's context.
    // The setter below does nothing when the flag already matches, so that path is forced to write again.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        overlayPageTheme(webView.context, dark)
    }
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
    try {
        if (WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings) == dark) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, !dark)
        }
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
    } catch (_: UnsupportedOperationException) {
        // The feature check can pass while this WebView build still refuses the call.
    }
}

/** Sets `isLightTheme` on [context] and the contexts it wraps. Each of those holds its own theme. */
private fun overlayPageTheme(context: Context, dark: Boolean) {
    val style = if (dark) R.style.PageDarkeningDark else R.style.PageDarkeningLight
    val seen = IdentityHashMap<Resources.Theme, Boolean>()
    var current: Context? = context
    var steps = 0
    while (current != null && steps++ < 8) {
        val theme = current.theme
        if (seen.put(theme, true) == null) theme.applyStyle(style, true)
        val next = (current as? ContextWrapper)?.baseContext
        if (next == null || next === current) break
        current = next
    }
}
