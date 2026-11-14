package com.dantech.skimbrowser.browser.di

import android.app.Application
import android.webkit.CookieManager
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.browser.downloads.MediaStoreDownloads
import com.dantech.skimbrowser.browser.downloads.VideoSaver
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.video.FacebookVideoExtractor
import com.dantech.skimbrowser.domain.video.GenericVideoExtractor
import com.dantech.skimbrowser.domain.video.RedditVideoExtractor
import com.dantech.skimbrowser.domain.video.TikTokVideoExtractor
import com.dantech.skimbrowser.domain.video.VideoCommands
import com.dantech.skimbrowser.domain.video.VideoExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal object VideoModule {
    @Provides @IntoSet fun genericExtractor(): VideoExtractor = GenericVideoExtractor()

    @Provides @IntoSet fun tikTokExtractor(): VideoExtractor = TikTokVideoExtractor()

    @Provides @IntoSet fun facebookExtractor(): VideoExtractor = FacebookVideoExtractor()

    @Provides @IntoSet fun redditExtractor(): VideoExtractor = RedditVideoExtractor()

    @Provides
    @Singleton
    fun videoSaver(
        app: Application,
        client: Provider<OkHttpClient>,
        log: DownloadLog,
        tracker: DownloadTracker,
        tabs: TabsController,
        incognito: IncognitoSession,
        profiles: BrowserProfiles,
        @AppScope scope: CoroutineScope,
    ): VideoSaver = VideoSaver(
        httpClient = { client.get() },
        store = MediaStoreDownloads(app),
        log = log,
        tracker = tracker,
        tabs = tabs,
        cookies = { private, url -> cookieHeader(incognito, profiles, private, url) },
        workDir = File(app.cacheDir, "video"),
        scope = scope,
    )

    @Provides fun videoCommands(saver: VideoSaver): VideoCommands = saver
}

internal fun cookieHeader(
    incognito: IncognitoSession,
    profiles: BrowserProfiles,
    private: Boolean,
    url: String,
): String? {
    if (!private) {
        return try {
            CookieManager.getInstance().getCookie(url)
        } catch (_: RuntimeException) {
            null
        }
    }
    val name = when (val session = incognito.state.value) {
        is IncognitoSession.State.Active -> session.profile
        is IncognitoSession.State.Ending -> session.profile
        IncognitoSession.State.None -> return null
    }
    return try {
        profiles.get(name)?.cookieHeader(url)
    } catch (_: RuntimeException) {
        null
    }
}
