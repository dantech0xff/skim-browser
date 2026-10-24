package com.dantech.skimbrowser.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dantech.skimbrowser.ReleaseProcessResources
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TabStateStore
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/** Process types for the device rules, which are not the Hilt test instance and cannot `@Inject`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeviceAccess {
    fun tabManager(): TabManager
    fun incognitoSession(): IncognitoSession
    fun tabStateStore(): TabStateStore
    fun thumbnails(): ThumbnailStore
    fun downloadTracker(): DownloadTracker
    fun snackbarController(): SnackbarController
    fun settings(): SettingsRepository
    fun historyRepository(): HistoryRepository
    fun dataStore(): DataStore<Preferences>
    fun sitePermissionRepository(): SitePermissionRepository
    fun browserProfiles(): BrowserProfiles
    fun httpClient(): OkHttpClient
    @AppScope fun appScope(): CoroutineScope
    fun release(): ReleaseProcessResources
}
