package com.dantech.skimbrowser

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.repo.HistoryRepository
import com.dantech.skimbrowser.data.repo.ShortcutRepository
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.data.repo.TabRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/**
 * Manual dependency container: one per process, created in [SkimApplication.onCreate]. Storage
 * comes from [storage], so tests can run a container on a temporary directory. Nothing is opened
 * until first use: the database, the settings store and every repository are created lazily, once.
 */
class AppContainer(val app: Application, storage: AppStorage = AppStorage()) : Closeable {

    /**
     * The last failure of a coroutine in [appScope]. The shell reads it to fall back to a usable
     * screen instead of waiting forever on work that died.
     */
    val lastError = MutableStateFlow<Throwable?>(null)

    /**
     * Process-wide scope for work that outlives a screen. A failing child neither cancels its
     * siblings nor crashes the process: the error is logged and kept in [lastError].
     */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Uncaught exception in appScope", error)
            lastError.value = error
        },
    )

    /** Runs the settings store; cancelled with [appScope], which releases the settings file. */
    private val settingsJob = SupervisorJob(appScope.coroutineContext.job)

    private val databaseHolder = lazy { SkimDatabase.open(app, storage.databaseName) }
    private val dataStoreHolder = lazy {
        SettingsRepository.createDataStore(
            scope = CoroutineScope(appScope.coroutineContext + settingsJob + Dispatchers.IO),
            produceFile = { storage.settingsFile(app) },
        )
    }
    private val closed = AtomicBoolean(false)

    val database: SkimDatabase by databaseHolder

    val dataStore: DataStore<Preferences> by dataStoreHolder

    val settings: SettingsRepository by lazy { SettingsRepository(dataStore) }

    val tabRepository: TabRepository by lazy { TabRepository(database) }

    val historyRepository: HistoryRepository by lazy { HistoryRepository(database.historyDao(), database) }

    val bookmarkRepository: BookmarkRepository by lazy { BookmarkRepository(database.bookmarkDao()) }

    val shortcutRepository: ShortcutRepository by lazy { ShortcutRepository(database.shortcutDao()) }

    val chatRepository: ChatRepository by lazy { ChatRepository(database.skimChatDao(), database) }

    val sitePermissionRepository: SitePermissionRepository by lazy {
        SitePermissionRepository(database.sitePermissionDao(), database)
    }

    /**
     * Closes the database, then cancels [appScope] and blocks until the settings store has
     * released its file, so another container may open the same storage right after. Later calls
     * do nothing; a closed container must not be used again.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (databaseHolder.isInitialized()) database.close()
        appScope.cancel()
        runBlocking { settingsJob.join() }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
