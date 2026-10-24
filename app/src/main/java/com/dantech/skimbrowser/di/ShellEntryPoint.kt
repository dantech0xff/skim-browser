package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.ai.TabGrouping
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.system.ShareImage
import com.dantech.skimbrowser.ui.reader.ReaderHighlights
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/**
 * Process types a route still reads after its view model is created. No client, store, or database.
 * The shell itself gets its own from [com.dantech.skimbrowser.ui.shell.SkimAppViewModel].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShellEntryPoint {
    fun tabs(): TabsController
    fun tabManager(): TabManager
    fun settings(): SettingsRepository
    @AppScope fun appScope(): CoroutineScope
    fun snackbar(): SnackbarController
    fun shareImage(): ShareImage
    fun highlights(): ReaderHighlights
    fun grouping(): TabGrouping
    fun thumbnails(): ThumbnailStore
}
