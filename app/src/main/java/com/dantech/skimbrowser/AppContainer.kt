package com.dantech.skimbrowser

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dantech.skimbrowser.ai.AiDataEraser
import com.dantech.skimbrowser.ai.AiGate
import com.dantech.skimbrowser.ai.ChatSessionStore
import com.dantech.skimbrowser.ai.ClaudeClientProvider
import com.dantech.skimbrowser.ai.SkimAiService
import com.dantech.skimbrowser.domain.undo.PendingUndo
import com.dantech.skimbrowser.domain.ai.AskPage
import com.dantech.skimbrowser.domain.ai.AskTabs
import com.dantech.skimbrowser.domain.ai.CollectOpenTabs
import com.dantech.skimbrowser.domain.ai.OverviewTabs
import com.dantech.skimbrowser.domain.ai.RetryAnswer
import com.dantech.skimbrowser.domain.ai.SummarizePage
import com.dantech.skimbrowser.ai.TabGrouping
import com.dantech.skimbrowser.browser.BrowsingDataCleaner
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
import com.dantech.skimbrowser.domain.tab.OpenUrlInCurrentTab
import com.dantech.skimbrowser.domain.tab.TabRepository
import com.dantech.skimbrowser.domain.usecase.AddBookmark
import com.dantech.skimbrowser.domain.usecase.AddSavedMessage
import com.dantech.skimbrowser.domain.usecase.AddShortcut
import com.dantech.skimbrowser.domain.usecase.ClearBrowsingData
import com.dantech.skimbrowser.domain.usecase.ClearSkimHistory
import com.dantech.skimbrowser.domain.usecase.CompleteOnboarding
import com.dantech.skimbrowser.domain.usecase.CreateSavedChat
import com.dantech.skimbrowser.domain.usecase.DeleteAllAiData
import com.dantech.skimbrowser.domain.usecase.DeleteAllSkimChats
import com.dantech.skimbrowser.domain.usecase.DeleteBookmark
import com.dantech.skimbrowser.domain.usecase.ImportBookmarks
import com.dantech.skimbrowser.domain.usecase.LoadSavedChat
import com.dantech.skimbrowser.domain.usecase.ReadSavedMessages
import com.dantech.skimbrowser.domain.usecase.MatchBookmarks
import com.dantech.skimbrowser.domain.usecase.MatchHistory
import com.dantech.skimbrowser.domain.usecase.MoveBookmark
import com.dantech.skimbrowser.domain.usecase.RemoveHistoryEntry
import com.dantech.skimbrowser.domain.usecase.RemoveShortcut
import com.dantech.skimbrowser.domain.usecase.RemoveSkimChat
import com.dantech.skimbrowser.domain.usecase.RestoreBookmark
import com.dantech.skimbrowser.domain.usecase.RestoreHistoryEntry
import com.dantech.skimbrowser.domain.usecase.RestoreShortcut
import com.dantech.skimbrowser.domain.usecase.RestoreSkimChat
import com.dantech.skimbrowser.domain.usecase.SetAskBeforeSending
import com.dantech.skimbrowser.domain.usecase.SetKeepChatHistory
import com.dantech.skimbrowser.domain.usecase.SetReaderFont
import com.dantech.skimbrowser.domain.usecase.SetReaderTextScale
import com.dantech.skimbrowser.domain.usecase.SetSearchEngine
import com.dantech.skimbrowser.domain.usecase.SetSitePermission
import com.dantech.skimbrowser.domain.usecase.SetSkimAi
import com.dantech.skimbrowser.domain.usecase.SetTheme
import com.dantech.skimbrowser.domain.usecase.UpdateSavedChat
import com.dantech.skimbrowser.domain.usecase.UpdateSavedMessage
import com.dantech.skimbrowser.domain.usecase.UpdateBookmark
import com.dantech.skimbrowser.domain.usecase.UpdateShortcut
import com.dantech.skimbrowser.search.SuggestionService
import com.dantech.skimbrowser.system.ShareImage
import com.dantech.skimbrowser.system.purgeOldShares
import com.dantech.skimbrowser.ui.reader.ReaderHighlights
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.SnackbarUndo
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
import kotlinx.coroutines.launch
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
        SettingsStore.createDataStore(
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

    private val settingsStore: SettingsStore by lazy { SettingsStore(dataStore) }

    /** User settings. Screens read this; writes from a screen go through a use case below. */
    val settings: SettingsRepository get() = settingsStore

    private val tabStore: TabStore by lazy { TabStore(database) }

    val tabRepository: TabRepository get() = tabStore

    private val historyStore: HistoryStore by lazy { HistoryStore(database.historyDao(), database) }

    val historyRepository: HistoryRepository get() = historyStore

    private val bookmarkStore: BookmarkStore by lazy { BookmarkStore(database.bookmarkDao()) }

    val bookmarkRepository: BookmarkRepository get() = bookmarkStore

    private val shortcutStore: ShortcutStore by lazy { ShortcutStore(database.shortcutDao()) }

    val shortcutRepository: ShortcutRepository get() = shortcutStore

    /**
     * The chat table. Screens and the transcript engine see [chatRepository]; this concrete store
     * only maps Room rows.
     */
    private val chatStore: ChatStore by lazy { ChatStore(database.skimChatDao(), database) }

    val chatRepository: ChatRepository get() = chatStore

    private val siteStore: SitePermissionStore by lazy {
        SitePermissionStore(database.sitePermissionDao(), database)
    }

    val sitePermissionRepository: SitePermissionRepository get() = siteStore

    val removeHistoryEntry: RemoveHistoryEntry by lazy { RemoveHistoryEntry(historyRepository) }
    val restoreHistoryEntry: RestoreHistoryEntry by lazy { RestoreHistoryEntry(historyRepository) }
    val matchHistory: MatchHistory by lazy { MatchHistory(historyRepository) }
    val addBookmark: AddBookmark by lazy { AddBookmark(bookmarkRepository) }
    val updateBookmark: UpdateBookmark by lazy { UpdateBookmark(bookmarkRepository) }
    val moveBookmark: MoveBookmark by lazy { MoveBookmark(bookmarkRepository) }
    val deleteBookmark: DeleteBookmark by lazy { DeleteBookmark(bookmarkRepository) }
    val restoreBookmark: RestoreBookmark by lazy { RestoreBookmark(bookmarkRepository) }
    val matchBookmarks: MatchBookmarks by lazy { MatchBookmarks(bookmarkRepository) }
    val addShortcut: AddShortcut by lazy { AddShortcut(shortcutRepository) }
    val updateShortcut: UpdateShortcut by lazy { UpdateShortcut(shortcutRepository) }
    val removeShortcut: RemoveShortcut by lazy { RemoveShortcut(shortcutRepository) }
    val restoreShortcut: RestoreShortcut by lazy { RestoreShortcut(shortcutRepository) }
    val setSearchEngine: SetSearchEngine by lazy { SetSearchEngine(settings) }
    val setTheme: SetTheme by lazy { SetTheme(settings) }
    val setAskBeforeSending: SetAskBeforeSending by lazy { SetAskBeforeSending(settings) }
    val setKeepChatHistory: SetKeepChatHistory by lazy { SetKeepChatHistory(settings) }
    val setReaderTextScale: SetReaderTextScale by lazy { SetReaderTextScale(settings) }
    val setReaderFont: SetReaderFont by lazy { SetReaderFont(settings) }
    val completeOnboarding: CompleteOnboarding by lazy { CompleteOnboarding(settings) }
    val loadSavedChat: LoadSavedChat by lazy { LoadSavedChat(chatRepository) }
    val createSavedChat: CreateSavedChat by lazy { CreateSavedChat(chatRepository) }
    val readSavedMessages: ReadSavedMessages by lazy { ReadSavedMessages(chatRepository) }
    val addSavedMessage: AddSavedMessage by lazy { AddSavedMessage(chatRepository) }
    val updateSavedChat: UpdateSavedChat by lazy { UpdateSavedChat(chatRepository) }
    val updateSavedMessage: UpdateSavedMessage by lazy { UpdateSavedMessage(chatRepository) }
    val deleteAllSkimChats: DeleteAllSkimChats by lazy { DeleteAllSkimChats(chatRepository) }
    val removeSkimChat: RemoveSkimChat by lazy { RemoveSkimChat(chatRepository) }
    val restoreSkimChat: RestoreSkimChat by lazy { RestoreSkimChat(chatRepository) }
    val setSkimAi: SetSkimAi by lazy { SetSkimAi(sitePermissionRepository) }
    val setSitePermission: SetSitePermission by lazy { SetSitePermission(sitePermissionRepository) }

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

    /** Opens a URL in the current tab, or a new normal tab when none is current. */
    val openUrlInCurrentTab: OpenUrlInCurrentTab by lazy { OpenUrlInCurrentTab(tabManager) }

    /** Deletions that can still be undone; their blocks run on [appScope]. */
    val undoRegistry: UndoRegistry by lazy { UndoRegistry(appScope) }

    /** The app's one snackbar, shown by the main window's host. */
    val snackbarController: SnackbarController by lazy { SnackbarController(undoRegistry) }

    /** Finalizes a waiting undo and dismisses its snackbar before a bulk delete. */
    val pendingUndo: PendingUndo by lazy { SnackbarUndo(undoRegistry, snackbarController) }

    /** Citations that scroll Reader view to a block. Lives with the process, like the snackbar. */
    val readerHighlights: ReaderHighlights by lazy { ReaderHighlights() }

    /** Imports a picked bookmarks file into a new Imported folder. */
    val bookmarkImporter: BookmarkFiles by lazy { BookmarkImporter(app.contentResolver, bookmarkStore) }

    val importBookmarks: ImportBookmarks by lazy { ImportBookmarks(bookmarkImporter) }

    /** The search engine's suggestions for the search field, over the connections of [httpClient]. */
    val suggestionService: SuggestionService by lazy { SuggestionService(httpClient) }

    /** Images prepared for the share sheet, written under the cache and shared through the file provider. */
    val shareImage: ShareImage by lazy {
        ShareImage(
            httpClient = httpClient,
            cacheDir = app.cacheDir,
            incognito = incognitoSession,
            profiles = browserProfiles,
        )
    }

    /** Deletes one site's data, and all browsing data when Settings asks. */
    val browsingDataCleaner: BrowsingDataCleaner by lazy {
        BrowsingDataCleaner(
            history = historyRepository,
            tabRepo = tabRepository,
            tabs = tabManager,
            stateStore = tabStateStore,
            thumbnails = thumbnails,
            incognito = incognitoSession,
            pendingUndo = pendingUndo,
        )
    }

    /**
     * The Claude client. A debug build sends [BuildConfig.ANTHROPIC_API_KEY] when one is set.
     * Without a key the client names the install and sends no key.
     */
    val claudeClients: ClaudeClientProvider by lazy {
        ClaudeClientProvider(
            baseUrl = BuildConfig.AI_BASE_URL,
            apiKey = (BuildConfig.ANTHROPIC_API_KEY as String?).takeIf { !it.isNullOrBlank() },
            installId = settings::installId,
        )
    }

    /** The only path that builds a model request and opens a connection. */
    val skimAi: SkimAiService by lazy { SkimAiService(claudeClients, sitePermissionRepository, settings) }

    val summarizePage: SummarizePage by lazy { SummarizePage(skimAi) }

    val askPage: AskPage by lazy { AskPage(skimAi) }

    val retryAnswer: RetryAnswer by lazy { RetryAnswer(skimAi) }

    val overviewTabs: OverviewTabs by lazy { OverviewTabs(skimAi) }

    val askTabs: AskTabs by lazy { AskTabs(skimAi) }

    val collectOpenTabs: CollectOpenTabs by lazy { CollectOpenTabs(tabManager, sitePermissionRepository) }

    /** Page chats for this process: saved while Keep chat history is on, otherwise memory only. */
    val chatSessions: ChatSessionStore by lazy {
        ChatSessionStore(
            chats = chatRepository,
            createChat = createSavedChat,
            messages = readSavedMessages,
            addMessage = addSavedMessage,
            updateChat = updateSavedChat,
            updateMessage = updateSavedMessage,
            tabs = tabManager,
            settings = settings,
            appScope = appScope,
        )
    }

    /** Groups ungrouped normal tabs once per set, and keeps the Undo for the tabs screen. */
    val tabGrouping: TabGrouping by lazy {
        TabGrouping(
            ai = skimAi,
            tabs = tabManager,
            tabRepo = tabRepository,
            sites = sitePermissionRepository,
            network = networkMonitor,
            appScope = appScope,
        )
    }

    /** Whether Ask Skim may read a tab's page, and the record of a site the person allowed. */
    val aiGate: AiGate by lazy { AiGate(sitePermissionRepository, settings, setSkimAi) }

    /** Clear browsing data. The Activity's pages are passed per call. */
    val clearBrowsingData: ClearBrowsingData by lazy {
        ClearBrowsingData { history, cookies, cache, pages ->
            browsingDataCleaner.clearAll(history, cookies, cache, pages)
        }
    }

    /** Deletes saved Skim chats, and every site's Skim AI decision when all AI data is deleted. */
    val aiDataEraser: AiDataEraser by lazy {
        AiDataEraser(
            deleteAllChats = deleteAllSkimChats,
            sites = sitePermissionRepository,
            pendingUndo = pendingUndo,
        )
    }

    /** Deletes every saved chat and every site's Skim AI decision. */
    val deleteAllAiData: DeleteAllAiData by lazy { DeleteAllAiData(aiDataEraser) }

    /** Deletes every saved chat and leaves site decisions untouched. */
    val clearSkimHistory: ClearSkimHistory by lazy { ClearSkimHistory(aiDataEraser) }

    init {
        // Incognito shares are removed entirely; a normal share older than a day is too.
        appScope.launch(Dispatchers.IO) { purgeOldShares(app.cacheDir) }
    }

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
