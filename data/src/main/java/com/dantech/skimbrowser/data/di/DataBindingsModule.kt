package com.dantech.skimbrowser.data.di

import com.dantech.skimbrowser.data.importer.BookmarkImporter
import com.dantech.skimbrowser.data.repo.BookmarkRepository as BookmarkStore
import com.dantech.skimbrowser.data.repo.ChatRepository as ChatStore
import com.dantech.skimbrowser.data.repo.HistoryRepository as HistoryStore
import com.dantech.skimbrowser.data.repo.ShortcutRepository as ShortcutStore
import com.dantech.skimbrowser.data.repo.SitePermissionRepository as SitePermissionStore
import com.dantech.skimbrowser.data.repo.TabRepository as TabStore
import com.dantech.skimbrowser.data.settings.SettingsRepository as SettingsStore
import com.dantech.skimbrowser.domain.bookmark.BookmarkFiles
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.shortcut.ShortcutRepository
import com.dantech.skimbrowser.domain.tab.TabRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Each store is the singleton its class is scoped to, so the domain interface is the same object. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindingsModule {
    @Binds abstract fun settings(store: SettingsStore): SettingsRepository
    @Binds abstract fun tabs(store: TabStore): TabRepository
    @Binds abstract fun history(store: HistoryStore): HistoryRepository
    @Binds abstract fun bookmarks(store: BookmarkStore): BookmarkRepository
    @Binds abstract fun shortcuts(store: ShortcutStore): ShortcutRepository
    @Binds abstract fun chats(store: ChatStore): ChatRepository
    @Binds abstract fun sites(store: SitePermissionStore): SitePermissionRepository
    @Binds abstract fun bookmarkFiles(importer: BookmarkImporter): BookmarkFiles
}
