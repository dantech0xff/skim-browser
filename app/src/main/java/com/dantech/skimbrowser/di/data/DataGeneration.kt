package com.dantech.skimbrowser.di.data

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.importer.BookmarkImporter
import com.dantech.skimbrowser.data.repo.BookmarkRepository as BookmarkStore
import com.dantech.skimbrowser.data.repo.ChatRepository as ChatStore
import com.dantech.skimbrowser.data.repo.HistoryRepository as HistoryStore
import com.dantech.skimbrowser.data.repo.ShortcutRepository as ShortcutStore
import com.dantech.skimbrowser.data.repo.SitePermissionRepository as SitePermissionStore
import com.dantech.skimbrowser.data.repo.TabRepository as TabStore
import com.dantech.skimbrowser.data.settings.SettingsRepository as SettingsStore
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.domain.bookmark.BookmarkFiles
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.shortcut.ShortcutRepository
import com.dantech.skimbrowser.domain.tab.TabRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * One opening of Room, DataStore, and the repositories. [ProcessGraph] drops the instance on
 * [ProcessGraph.close] and the next read builds another. This is the only caller of [SkimDatabase.open].
 */
class DataGeneration(
    private val app: Application,
    private val storage: AppStorage,
    appScope: CoroutineScope,
    settingsJob: CompletableJob,
) {
    private val databaseHolder = lazy { SkimDatabase.open(app, storage.databaseName) }
    private val dataStoreHolder = lazy {
        SettingsStore.createDataStore(
            scope = CoroutineScope(appScope.coroutineContext + settingsJob + Dispatchers.IO),
            produceFile = { storage.settingsFile(app) },
        )
    }
    private val settingsHolder = lazy { SettingsStore(dataStore) }
    private val tabHolder = lazy { TabStore(database) }
    private val historyHolder = lazy { HistoryStore(database.historyDao(), database) }
    private val bookmarkHolder = lazy { BookmarkStore(database.bookmarkDao()) }
    private val shortcutHolder = lazy { ShortcutStore(database.shortcutDao()) }
    private val chatHolder = lazy { ChatStore(database.skimChatDao(), database) }
    private val siteHolder = lazy { SitePermissionStore(database.sitePermissionDao(), database) }
    private val importerHolder = lazy { BookmarkImporter(app.contentResolver, bookmarkHolder.value) }

    val database: SkimDatabase by databaseHolder
    val dataStore: DataStore<Preferences> by dataStoreHolder
    val settings: SettingsRepository by settingsHolder
    val tabRepository: TabRepository by tabHolder
    val historyRepository: HistoryRepository by historyHolder
    val bookmarkRepository: BookmarkRepository by bookmarkHolder
    val shortcutRepository: ShortcutRepository by shortcutHolder
    val chatRepository: ChatRepository by chatHolder
    val sitePermissionRepository: SitePermissionRepository by siteHolder
    val bookmarkImporter: BookmarkFiles by importerHolder

    fun closeDatabaseIfOpen() {
        if (databaseHolder.isInitialized()) database.close()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataBindingsModule {
    @Provides fun database(graph: ProcessGraph): SkimDatabase = graph.database()
    @Provides fun dataStore(graph: ProcessGraph): DataStore<Preferences> = graph.dataStore()
    @Provides fun settings(graph: ProcessGraph): SettingsRepository = graph.settings()
    @Provides fun tabs(graph: ProcessGraph): TabRepository = graph.tabRepository()
    @Provides fun history(graph: ProcessGraph): HistoryRepository = graph.historyRepository()
    @Provides fun bookmarks(graph: ProcessGraph): BookmarkRepository = graph.bookmarkRepository()
    @Provides fun shortcuts(graph: ProcessGraph): ShortcutRepository = graph.shortcutRepository()
    @Provides fun chats(graph: ProcessGraph): ChatRepository = graph.chatRepository()
    @Provides fun sites(graph: ProcessGraph): SitePermissionRepository = graph.sitePermissionRepository()
    @Provides fun bookmarkFiles(graph: ProcessGraph): BookmarkFiles = graph.bookmarkImporter()
}
