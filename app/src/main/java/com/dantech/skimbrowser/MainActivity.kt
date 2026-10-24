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
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import com.dantech.skimbrowser.browser.BrowserHost
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.browser.BrowsingDataCleaner
import dagger.hilt.android.AndroidEntryPoint
import com.dantech.skimbrowser.ui.shell.IncomingIntents
import com.dantech.skimbrowser.ui.shell.SkimApp
import javax.inject.Inject
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
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var browserHost: BrowserHost
    @Inject lateinit var tabs: TabManager
    @Inject lateinit var downloads: DownloadTracker
    @Inject lateinit var browsingData: BrowsingDataCleaner
    @Inject lateinit var incognito: IncognitoSession

    /**
     * Links other apps sent, waiting for the app to open them. It belongs to this Activity, so a
     * link is opened exactly once: a recreated Activity, or Skim reopened from the launcher, never
     * opens an earlier one again.
     */
    private val pendingLinks = Channel<String>(Channel.BUFFERED)

    /** The Activity's browser host, for the device tests of its life cycle. */
    @get:VisibleForTesting
    internal val browserHostForTests: BrowserHost get() = browserHost

    private val downloadFinished = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, NO_DOWNLOAD)
            if (id != NO_DOWNLOAD) downloads.onDownloadComplete(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        tabs.attach(browserHost.pool)
        browsingData.applyPendingCacheClear(browserHost.pool)
        // Deleting old incognito profiles needs the main thread; it waits until the first frame is drawn.
        window.decorView.post { incognito.purgeStale() }
        // A recreated Activity gets the intent that started the first one again: that link was opened.
        if (savedInstanceState == null) receiveLink(intent)
        setContent {
            CompositionLocalProvider(LocalBrowserHost provides browserHost) {
                SkimApp(browserHost, pendingLinks)
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
        downloads.refresh()
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
            val openTabs = tabs
            // Leaving for good ends what Undo still held, while the pool can still clear an incognito
            // session's cache through a WebView of its profile.
            if (isFinishing) openTabs.finalizePendingCloses()
            browserHost.destroy()
            openTabs.detach(browserHost.pool)
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

