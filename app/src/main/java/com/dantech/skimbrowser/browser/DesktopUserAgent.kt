package com.dantech.skimbrowser.browser

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

/**
 * The user agent for Desktop site, built from the WebView's own [defaultUa] so the engine version
 * stays true: the Android platform part becomes `(X11; Linux x86_64)` and the tokens that mark a
 * mobile WebView (`; wv`, `Version/4.0`, `Mobile`) go, as Chrome on Linux reports itself.
 * Applying it to an agent that is already the desktop one changes nothing.
 */
fun desktopUserAgent(defaultUa: String): String =
    defaultUa
        .replace(ANDROID_PLATFORM, DESKTOP_PLATFORM)
        .replace(WEBVIEW_TOKEN, "")
        .replace(VERSION_TOKEN, "")
        .replace(MOBILE_TOKEN, "")
        .replace(SPACES, " ")
        .trim()

/**
 * Switches [webView] to Desktop site, or back to the WebView's defaults when [on] is false: the
 * user agent string, the wide viewport with overview zoom that desktop pages are laid out for, and
 * where the WebView supports them the user-agent client hints (not mobile, platform Linux), so a
 * site sees the same answer both ways. It applies from the next navigation; the caller reloads.
 */
@MainThread
fun applyDesktopMode(webView: WebView, on: Boolean) {
    val settings = webView.settings
    // Null restores the WebView's default user agent.
    settings.userAgentString = if (on) desktopUserAgent(WebSettings.getDefaultUserAgent(webView.context)) else null
    settings.useWideViewPort = on
    settings.loadWithOverviewMode = on
    if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
        if (on) {
            // The WebView's own hints are kept from before the first override, to give them back.
            defaultHints.getOrPut(settings) { WebSettingsCompat.getUserAgentMetadata(settings) }
            val desktop = UserAgentMetadata.Builder().setMobile(false).setPlatform(DESKTOP_HINT_PLATFORM).build()
            WebSettingsCompat.setUserAgentMetadata(settings, desktop)
        } else {
            // A WebView never switched to Desktop site still has its own hints: nothing to give back.
            defaultHints.remove(settings)?.let { WebSettingsCompat.setUserAgentMetadata(settings, it) }
        }
    }
}

/** The client hints each WebView had before Desktop site replaced them; read on the main thread only. */
private val defaultHints = WeakHashMap<WebSettings, UserAgentMetadata>()

private const val DESKTOP_PLATFORM = "(X11; Linux x86_64)"
private const val DESKTOP_HINT_PLATFORM = "Linux"
private val ANDROID_PLATFORM = Regex("""\(Linux;[^)]*\bAndroid\b[^)]*\)""")
private val WEBVIEW_TOKEN = Regex(""";\s*wv(?=[;)])""")
private val VERSION_TOKEN = Regex("""(?<=\s|^)Version/\d+(\.\d+)*(\s|$)""")
private val MOBILE_TOKEN = Regex("""(?<=\s|^)Mobile(\s|$)""")
private val SPACES = Regex("""\s{2,}""")
