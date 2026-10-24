package com.dantech.skimbrowser.di.browser

import android.app.Activity
import androidx.activity.ComponentActivity
import com.dantech.skimbrowser.browser.ActivityHost
import com.dantech.skimbrowser.browser.BrowserHost
import com.dantech.skimbrowser.browser.FullscreenController
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TabStateStore
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.browser.WebViewPool
import com.dantech.skimbrowser.browser.WindowSecureController
import com.dantech.skimbrowser.browser.downloads.BlobBridge
import com.dantech.skimbrowser.browser.downloads.DownloadRouter
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.browser.downloads.InProcessDownloader
import com.dantech.skimbrowser.browser.downloads.MediaStoreDownloads
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineScope

/**
 * One browser host per activity. The download client's HTTP getter is not called while this
 * module builds the host.
 */
@Module
@InstallIn(ActivityComponent::class)
object ActivityBrowserModule {
    @Provides
    @ActivityScoped
    fun windowSecure(activity: Activity): WindowSecureController = WindowSecureController(activity)

    @Provides
    @ActivityScoped
    fun fullscreen(activity: ComponentActivity): FullscreenController = FullscreenController(activity)

    @Provides
    @ActivityScoped
    fun blob(activity: Activity): BlobBridge = BlobBridge(activity.assets)

    @Provides
    @ActivityScoped
    fun sitePermissions(
        activity: Activity,
        tabs: TabManager,
        repository: SitePermissionRepository,
        incognito: IncognitoSession,
        activityHost: ActivityHost,
        @AppScope scope: CoroutineScope,
    ): SitePermissionBridge = SitePermissionBridge(
        context = activity.applicationContext,
        tabs = tabs.state,
        repository = repository,
        incognitoSession = incognito,
        activityHost = activityHost,
        scope = scope,
    )

    @Provides
    @ActivityScoped
    fun downloads(
        activity: Activity,
        tabs: TabManager,
        incognito: IncognitoSession,
        profiles: BrowserProfiles,
        tracker: DownloadTracker,
        graph: ProcessGraph,
        blob: BlobBridge,
        activityHost: ActivityHost,
        @AppScope scope: CoroutineScope,
    ): DownloadRouter {
        val store = MediaStoreDownloads(activity.applicationContext)
        return DownloadRouter(
            tabs = tabs,
            incognito = incognito,
            profiles = profiles,
            tracker = tracker,
            inProcess = InProcessDownloader({ graph.httpClient() }, store),
            store = store,
            blob = blob,
            activityHost = activityHost,
            scope = scope,
        )
    }

    @Provides
    @ActivityScoped
    fun pool(
        activity: ComponentActivity,
        tabs: TabManager,
        incognito: IncognitoSession,
        stateStore: TabStateStore,
        thumbnails: ThumbnailStore,
        activityHost: ActivityHost,
        fullscreen: FullscreenController,
        sitePermissions: SitePermissionBridge,
        downloads: DownloadRouter,
    ): WebViewPool = WebViewPool(
        activity = activity,
        tabs = tabs,
        incognito = incognito,
        stateStore = stateStore,
        thumbnails = thumbnails,
        activityHost = activityHost,
        fullscreen = fullscreen,
        sitePermissions = sitePermissions,
        downloads = downloads,
    )

    @Provides
    @ActivityScoped
    fun browserHost(
        pool: WebViewPool,
        sitePermissions: SitePermissionBridge,
        downloads: DownloadRouter,
        blob: BlobBridge,
        activityHost: ActivityHost,
        fullscreen: FullscreenController,
        secure: WindowSecureController,
    ): BrowserHost = BrowserHost(pool, sitePermissions, downloads, blob, activityHost, fullscreen, secure)
}
