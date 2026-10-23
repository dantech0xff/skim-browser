package com.dantech.skimbrowser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import com.dantech.skimbrowser.browser.ActivityHost
import com.dantech.skimbrowser.browser.ActivityResultHost
import com.dantech.skimbrowser.browser.BrowserHost
import com.dantech.skimbrowser.browser.FullscreenController
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.browser.WebViewPool
import com.dantech.skimbrowser.browser.WindowSecureController
import com.dantech.skimbrowser.browser.downloads.BlobBridge
import com.dantech.skimbrowser.browser.downloads.DownloadRouter
import com.dantech.skimbrowser.browser.downloads.InProcessDownloader
import com.dantech.skimbrowser.browser.downloads.MediaStoreDownloads
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.shell.IncomingIntents
import com.dantech.skimbrowser.ui.shell.SkimApp
import kotlinx.coroutines.channels.Channel

/**
 * The only activity. Rotation, dark mode and font changes are declared in the manifest's
 * configChanges, so they reach the composition without recreating the activity.
 *
 * It owns everything of the browser that needs an Activity (the WebView pool, site permission
 * requests, downloads, blob reads, the result launchers, full screen and the window's privacy
 * switches) as one [BrowserHost], provided to the screens as [LocalBrowserHost], and attaches the
 * pool to the process's tab manager while it lives. While it is started it hears DownloadManager
 * finish Skim's downloads.
 *
 * It shows the app ([SkimApp]) and hands it the web links other apps send: the one that started it
 * and, as the activity is single-task, each later one through [onNewIntent].
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as SkimApplication).container

    private lateinit var activityHost: ActivityResultHost
    private lateinit var browserHost: BrowserHost

    /**
     * Links other apps sent, waiting for the app to open them. It belongs to this Activity, so a
     * link is opened exactly once: a recreated Activity, or Skim reopened from the launcher, never
     * opens an earlier one again.
     */
    private val pendingLinks = Channel<String>(Channel.BUFFERED)
    private var navigator: SkimNavigator? = null

    /** The Activity's browser host, for the device tests of its life cycle. */
    @get:VisibleForTesting
    internal val browserHostForTests: BrowserHost get() = browserHost

    /** The navigator of the app on screen, once it has started; for the device tests of the shell. */
    @get:VisibleForTesting
    internal val navigatorForTests: SkimNavigator? get() = navigator

    private val downloadFinished = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, NO_DOWNLOAD)
            if (id != NO_DOWNLOAD) container.downloadTracker.onDownloadComplete(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        // Result launchers must be registered before the Activity starts.
        activityHost = ActivityResultHost(this)
        browserHost = createBrowserHost(this, container, activityHost)
        container.tabManager.attach(browserHost.pool)
        container.browsingDataCleaner.applyPendingCacheClear(browserHost.pool)
        // Deleting old incognito profiles needs the main thread; it waits until the first frame is drawn.
        window.decorView.post { container.incognitoSession.purgeStale() }
        // A recreated Activity gets the intent that started the first one again: that link was opened.
        if (savedInstanceState == null) receiveLink(intent)
        setContent {
            CompositionLocalProvider(LocalBrowserHost provides browserHost) {
                SkimApp(container, browserHost, pendingLinks, onNavigator = { navigator = it })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveLink(intent)
    }

    /** Queues the web link of [intent] from another app, if it carries one. */
    private fun receiveLink(intent: Intent?) {
        IncomingIntents.urlFrom(intent)?.let { pendingLinks.trySend(it) }
    }

    override fun onStart() {
        super.onStart()
        browserHost.pool.onStart()
        // Only DownloadManager holds the permission to send this broadcast.
        ContextCompat.registerReceiver(
            this,
            downloadFinished,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            DOWNLOAD_COMPLETED_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
        // Downloads that finished while the Activity was stopped were not heard.
        container.downloadTracker.refresh()
    }

    override fun onStop() {
        unregisterReceiver(downloadFinished)
        browserHost.pool.onStop()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::browserHost.isInitialized) browserHost.pool.onTrimMemory(level)
    }

    override fun onDestroy() {
        if (::browserHost.isInitialized) {
            val tabs = container.tabManager
            // Leaving for good ends what Undo still held, while the pool can still clear an incognito
            // session's cache through a WebView of its profile.
            if (isFinishing) tabs.finalizePendingCloses()
            browserHost.destroy()
            activityHost.destroy()
            tabs.detach(browserHost.pool)
        }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MainActivity"
        const val NO_DOWNLOAD = -1L

        /** DownloadManager's signature permission: no other app can fake a finished download. */
        const val DOWNLOAD_COMPLETED_PERMISSION = "android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS"
    }
}

/**
 * The browser host of [activity] over [container]'s process-wide parts, built the one way the app
 * and its device tests both use. Call it in `onCreate`, before the Activity starts; the caller
 * attaches the pool to the tab manager and [BrowserHost.destroy]s the host in `onDestroy`.
 */
@MainThread
internal fun createBrowserHost(activity: ComponentActivity, container: AppContainer, activityHost: ActivityHost): BrowserHost {
    val fullscreen = FullscreenController(activity)
    val sitePermissions = SitePermissionBridge(
        context = activity.applicationContext,
        tabs = container.tabManager.state,
        repository = container.sitePermissionRepository,
        incognitoSession = container.incognitoSession,
        activityHost = activityHost,
        scope = container.appScope,
    )
    val blob = BlobBridge(activity.assets)
    val store = MediaStoreDownloads(activity.applicationContext)
    val downloads = DownloadRouter(
        tabs = container.tabManager,
        incognito = container.incognitoSession,
        profiles = container.browserProfiles,
        tracker = container.downloadTracker,
        inProcess = InProcessDownloader({ container.httpClient }, store),
        store = store,
        blob = blob,
        activityHost = activityHost,
        scope = container.appScope,
    )
    val pool = WebViewPool(
        activity = activity,
        tabs = container.tabManager,
        incognito = container.incognitoSession,
        stateStore = container.tabStateStore,
        thumbnails = container.thumbnails,
        activityHost = activityHost,
        fullscreen = fullscreen,
        sitePermissions = sitePermissions,
        downloads = downloads,
    )
    return BrowserHost(pool, sitePermissions, downloads, blob, activityHost, fullscreen, WindowSecureController(activity))
}
