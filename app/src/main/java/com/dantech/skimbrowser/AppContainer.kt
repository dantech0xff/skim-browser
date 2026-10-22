package com.dantech.skimbrowser

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.ConnectivityNetworkMonitor
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.NetworkMonitor
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TabStateStore
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.browser.WebViewProfiles
import com.dantech.skimbrowser.browser.downloads.AndroidSystemDownloads
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.browser.page.PageScriptSource
import com.dantech.skimbrowser.browser.page.PageScripts
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.importer.BookmarkImporter
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.repo.HistoryRepository
import com.dantech.skimbrowser.data.repo.ShortcutRepository
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.data.repo.TabRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.search.SuggestionService
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * Manual dependency container: one per process, created in [SkimApplication.onCreate]. Storage
 * comes from [storage], so tests can run a container on a temporary directory. Nothing is opened
 * until first use: the database, the settings store, every repository, the HTTP client, the
 * browser's process-wide parts (tabs, incognito session, WebView profiles, network monitor,
 * stores, page scripts, download tracker), the snackbar with its undo registry, the bookmark
 * importer and the search suggestions are created lazily, once. What lives only as long as the
 * Activity (the WebView pool and the rest of the Activity's browser host, the navigator) is never
 * kept here.
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
    private val networkMonitorHolder = lazy { ConnectivityNetworkMonitor(app) }
    private val httpClientHolder = lazy {
        OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
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
     * The app's one HTTP client, shared so that every caller pools connections and threads: 15 s to
     * connect and 15 s at most between two reads. It carries no interceptor: the search suggestions
     * go over it with what the user types, and nothing else may ride along. A caller that needs one
     * derives its own client with `newBuilder()`.
     */
    val httpClient: OkHttpClient by httpClientHolder

    /** The default network, watched from first use until [close]. */
    val networkMonitor: NetworkMonitor by networkMonitorHolder

    /** The WebView profiles: the incognito session's, and the cookies of incognito downloads. */
    val browserProfiles: BrowserProfiles by lazy { WebViewProfiles() }

    val incognitoSession: IncognitoSession by lazy {
        IncognitoSession(browserProfiles, storage.incognitoSharedDir(app), appScope)
    }

    val tabStateStore: TabStateStore by lazy {
        TabStateStore(storage.tabStateDir(app), { TabStateStore.webViewVersionOf(app) }, appScope)
    }

    val thumbnails: ThumbnailStore by lazy { ThumbnailStore(storage.thumbnailDir(app), appScope) }

    val pageScripts: PageScriptSource by lazy { PageScripts(app.assets) { PageScripts.runOnDevice(app) } }

    /** How downloads end, for every screen; it follows the DownloadManager downloads Skim enqueued. */
    val downloadTracker: DownloadTracker by lazy { DownloadTracker(AndroidSystemDownloads(app), appScope) }

    val tabManager: TabManager by lazy {
        TabManager(
            tabRepo = tabRepository,
            history = historyRepository,
            settings = settings,
            incognito = incognitoSession,
            stateStore = tabStateStore,
            thumbnails = thumbnails,
            network = networkMonitor,
            appScope = appScope,
            scripts = pageScripts,
        )
    }

    /** Deletions that can still be undone; their blocks run on [appScope]. */
    val undoRegistry: UndoRegistry by lazy { UndoRegistry(appScope) }

    /** The app's one snackbar, shown by the main window's host. */
    val snackbarController: SnackbarController by lazy { SnackbarController(undoRegistry) }

    /** Imports a picked bookmarks file into a new Imported folder. */
    val bookmarkImporter: BookmarkImporter by lazy { BookmarkImporter(app.contentResolver, bookmarkRepository) }

    /** The search engine's suggestions for the search field, over the connections of [httpClient]. */
    val suggestionService: SuggestionService by lazy { SuggestionService(httpClient) }

    /**
     * Stops watching the network, lets the HTTP client's connections and threads go, closes the
     * database, then cancels [appScope] and blocks until the settings store has released its file,
     * so another container may open the same storage right after. Later calls do nothing; a closed
     * container must not be used again.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (networkMonitorHolder.isInitialized()) networkMonitorHolder.value.close()
        if (httpClientHolder.isInitialized()) {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        }
        if (databaseHolder.isInitialized()) database.close()
        appScope.cancel()
        runBlocking { settingsJob.join() }
    }

    private companion object {
        const val TAG = "AppContainer"
        const val HTTP_TIMEOUT_SECONDS = 15L
    }
}
