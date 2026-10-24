package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.ai.TabGrouping
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.domain.ai.AskPageAccess
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.IncognitoSupport
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.system.ShareImage
import com.dantech.skimbrowser.ui.reader.ReaderHighlights
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/**
 * Process types a route still reads after its view model is created. No client, store, or database.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShellEntryPoint {
    fun tabs(): TabsController
    fun tabManager(): TabManager
    fun settings(): SettingsRepository
    @AppScope fun appScope(): CoroutineScope
    fun snackbar(): SnackbarController
    fun undo(): UndoRegistry
    fun incognito(): IncognitoSupport
    fun aiGate(): AskPageAccess
    fun downloads(): DownloadTracker
    fun shareImage(): ShareImage
    fun highlights(): ReaderHighlights
    fun grouping(): TabGrouping
    fun thumbnails(): ThumbnailStore
}
