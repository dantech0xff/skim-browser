package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.data.shortcut.ShortcutSeeder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/** What [com.dantech.skimbrowser.SkimApplication.onCreate] starts. Nothing else. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StartupEntryPoint {
    @AppScope fun appScope(): CoroutineScope
    fun tabManager(): TabManager
    fun shortcutSeeder(): ShortcutSeeder
}
