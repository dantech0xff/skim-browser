package com.dantech.skimbrowser.browser.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.browser.SkimWebView

/**
 * Shows the WebView of tab [tabId] from the Activity's WebView pool ([LocalBrowserHost]).
 *
 * The WebView belongs to the pool, not to this composable: leaving the composition takes the
 * tab's thumbnail, pauses the WebView and detaches it, and the pool decides when it is destroyed.
 * When the pool replaces the tab's WebView (its renderer went away) the view is built again; while
 * the tab's renderer crashed too often nothing is drawn, so the error screen above it shows.
 */
@Composable
fun WebViewHost(
    tabId: String,
    modifier: Modifier = Modifier,
    /** CSS pixels of document padding above the page. It scrolls away under the status bar. */
    contentTop: Int = 0,
    /** CSS pixels of document padding below the page. It scrolls away under the bottom pill. */
    contentBottom: Int = 0,
    /** Page scroll, in device pixels. The delta is positive when the page moves down. */
    onPageScroll: ((scrollY: Int, dy: Int) -> Unit)? = null,
) {
    val pool = LocalBrowserHost.current.pool
    val generation by remember(pool, tabId) { pool.generation(tabId) }.collectAsState()
    val current = generation ?: return
    key(tabId, current) {
        AndroidView(
            factory = { context -> pool.showInHost(tabId) ?: View(context) },
            modifier = modifier,
            update = { view ->
                (view as? SkimWebView)?.let { web ->
                    web.setContentScrollPadding(contentTop, contentBottom)
                    web.onPageScroll = onPageScroll
                }
            },
            onRelease = { view ->
                (view as? SkimWebView)?.onPageScroll = null
                pool.releaseFromHost(tabId, view)
            },
        )
    }
}
