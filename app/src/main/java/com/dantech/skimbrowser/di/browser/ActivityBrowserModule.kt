package com.dantech.skimbrowser.di.browser

import android.app.Activity
import androidx.activity.ComponentActivity
import com.dantech.skimbrowser.browser.ActivityHost
import com.dantech.skimbrowser.browser.BrowserHost
import com.dantech.skimbrowser.browser.FullscreenController
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.InterruptGate
import com.dantech.skimbrowser.browser.TabEngineSource
import com.dantech.skimbrowser.browser.TabEventSink
import com.dantech.skimbrowser.browser.TabStateStore
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.browser.RequestShield
import com.dantech.skimbrowser.browser.ShieldStatRecorder
import com.dantech.skimbrowser.browser.ProcessSafetyGate
import com.dantech.skimbrowser.browser.TabNavigations
import com.dantech.skimbrowser.browser.WebViewPool
import com.dantech.skimbrowser.browser.WindowSecureController
import com.dantech.skimbrowser.browser.downloads.BlobBridge
import com.dantech.skimbrowser.browser.downloads.DownloadActions
import com.dantech.skimbrowser.browser.downloads.DownloadRouter
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.browser.downloads.InProcessDownloader
import com.dantech.skimbrowser.browser.downloads.MediaStoreDownloads
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.tab.TabsController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/**
 * One browser host per activity. The download client's HTTP client is not built while this
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
        tabs: TabsController,
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
        tabs: TabsController,
        incognito: IncognitoSession,
        profiles: BrowserProfiles,
        tracker: DownloadTracker,
        httpClient: Provider<OkHttpClient>,
        blob: BlobBridge,
        activityHost: ActivityHost,
        @AppScope scope: CoroutineScope,
        log: DownloadLog,
    ): DownloadRouter {
        val store = MediaStoreDownloads(activity.applicationContext)
        return DownloadRouter(
            tabs = tabs,
            incognito = incognito,
            profiles = profiles,
            tracker = tracker,
            inProcess = InProcessDownloader({ httpClient.get() }, store),
            store = store,
            blob = blob,
            activityHost = activityHost,
            scope = scope,
            log = log,
        )
    }

    @Provides
    @ActivityScoped
    fun pool(
        activity: ComponentActivity,
        tabs: TabsController,
        tabEvents: TabEventSink,
        engines: TabEngineSource,
        incognito: IncognitoSession,
        stateStore: TabStateStore,
        thumbnails: ThumbnailStore,
        activityHost: ActivityHost,
        fullscreen: FullscreenController,
        sitePermissions: SitePermissionBridge,
        downloads: DownloadRouter,
        shield: RequestShield,
        safety: ProcessSafetyGate,
        navigations: TabNavigations,
        gate: InterruptGate,
        stats: ShieldStatRecorder,
    ): WebViewPool = WebViewPool(
        activity = activity,
        tabs = tabs,
        tabEvents = tabEvents,
        engines = engines,
        incognito = incognito,
        stateStore = stateStore,
        thumbnails = thumbnails,
        activityHost = activityHost,
        fullscreen = fullscreen,
        sitePermissions = sitePermissions,
        downloads = downloads,
        shield = shield,
        safety = safety,
        navigations = navigations,
        interrupts = gate::policy,
        stats = stats,
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
        actions: DownloadActions,
    ): BrowserHost {
        actions.attach(downloads) { pool.webViewIfReady(it) }
        return BrowserHost(pool, sitePermissions, downloads, blob, activityHost, fullscreen, secure)
    }
}
