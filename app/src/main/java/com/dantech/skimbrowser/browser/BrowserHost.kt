package com.dantech.skimbrowser.browser

import androidx.annotation.MainThread
import androidx.compose.runtime.staticCompositionLocalOf
import com.dantech.skimbrowser.browser.downloads.BlobBridge
import com.dantech.skimbrowser.browser.downloads.DownloadRouter
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge

/**
 * Everything of the browser that lives as long as the Activity, for the screens: the WebView pool,
 * the pages' permission requests, downloads and blob reads, the Activity's pickers and app
 * launches, full-screen web content and the window's privacy switches. The Activity makes one in
 * `onCreate`, provides it as [LocalBrowserHost] and [destroy]s it in `onDestroy`; none of it
 * belongs in the app container, which outlives the Activity.
 */
data class BrowserHost(
    val pool: WebViewPool,
    val sitePermissions: SitePermissionBridge,
    val downloads: DownloadRouter,
    val blob: BlobBridge,
    val activityHost: ActivityHost,
    val fullscreen: FullscreenController,
    val secure: WindowSecureController,
) {
    /**
     * The Activity is being destroyed: permission requests still waiting are denied and a blob
     * read still running ends (its state in the page dropped while its WebView lives), then the
     * pool saves and destroys every WebView. Downloads already running go on in the process,
     * without the Activity.
     */
    @MainThread
    fun destroy() {
        sitePermissions.destroy()
        blob.destroy()
        downloads.destroy()
        pool.destroyAll()
    }
}

/** The Activity's [BrowserHost]; reading it outside the Activity's content is an error. */
val LocalBrowserHost = staticCompositionLocalOf<BrowserHost> { error("BrowserHost not provided") }
