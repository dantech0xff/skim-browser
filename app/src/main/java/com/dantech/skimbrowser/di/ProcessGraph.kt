package com.dantech.skimbrowser.di

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.ai.AiDataEraser
import com.dantech.skimbrowser.ai.AiGate
import com.dantech.skimbrowser.ai.ChatSessionStore
import com.dantech.skimbrowser.ai.ClaudeClientProvider
import com.dantech.skimbrowser.ai.SkimAiService
import com.dantech.skimbrowser.ai.TabGrouping
import com.dantech.skimbrowser.ai.di.ClaudeClientFactory
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.BrowsingDataCleaner
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
import com.dantech.skimbrowser.data.di.DataGeneration
import com.dantech.skimbrowser.domain.ai.AskPage
import com.dantech.skimbrowser.domain.ai.AskTabs
import com.dantech.skimbrowser.domain.ai.CollectOpenTabs
import com.dantech.skimbrowser.domain.ai.OverviewTabs
import com.dantech.skimbrowser.domain.ai.RetryAnswer
import com.dantech.skimbrowser.domain.ai.SummarizePage
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
import com.dantech.skimbrowser.domain.usecase.MatchBookmarks
import com.dantech.skimbrowser.domain.usecase.MatchHistory
import com.dantech.skimbrowser.domain.usecase.MoveBookmark
import com.dantech.skimbrowser.domain.usecase.ReadSavedMessages
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
import com.dantech.skimbrowser.domain.usecase.UpdateBookmark
import com.dantech.skimbrowser.domain.usecase.UpdateSavedChat
import com.dantech.skimbrowser.domain.usecase.UpdateSavedMessage
import com.dantech.skimbrowser.domain.usecase.UpdateShortcut
import com.dantech.skimbrowser.search.SuggestionService
import com.dantech.skimbrowser.system.ShareImage
import com.dantech.skimbrowser.ui.reader.ReaderHighlights
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.SnackbarUndo
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableJob
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
 * One generation of the process graph. [close] drops it, and the next getter builds another with
 * today's constructors. Hilt keeps this object; it does not keep the database or the tab manager.
 */
@Singleton
class ProcessGraph @Inject constructor(
    val app: Application,
    private val storage: AppStorage,
    val lastError: MutableStateFlow<Throwable?>,
    private val claudeClientFactory: ClaudeClientFactory,
) {
    private val monitor = java.lang.Object()
    private var generation: Generation? = null
    private var users = 0
    private var closing = false

    fun appScope(): CoroutineScope = use { appScope }
    fun database(): SkimDatabase = use { data.database }
    fun dataStore(): DataStore<Preferences> = use { data.dataStore }
    fun settings(): SettingsRepository = use { data.settings }
    fun tabRepository(): TabRepository = use { data.tabRepository }
    fun historyRepository(): HistoryRepository = use { data.historyRepository }
    fun bookmarkRepository(): BookmarkRepository = use { data.bookmarkRepository }
    fun shortcutRepository(): ShortcutRepository = use { data.shortcutRepository }
    fun chatRepository(): ChatRepository = use { data.chatRepository }
    fun sitePermissionRepository(): SitePermissionRepository = use { data.sitePermissionRepository }
    fun bookmarkImporter(): BookmarkFiles = use { data.bookmarkImporter }
    fun httpClient(): OkHttpClient = use { httpClient }
    fun networkMonitor(): NetworkMonitor = use { networkMonitor }
    fun browserProfiles(): BrowserProfiles = use { browserProfiles }
    fun incognitoSession(): IncognitoSession = use { incognitoSession }
    fun tabStateStore(): TabStateStore = use { tabStateStore }
    fun thumbnails(): ThumbnailStore = use { thumbnails }
    fun pageScripts(): PageScriptSource = use { pageScripts }
    fun downloadTracker(): DownloadTracker = use { downloadTracker }
    fun tabManager(): TabManager = use { tabManager }
    fun undoRegistry(): UndoRegistry = use { undoRegistry }
    fun snackbarController(): SnackbarController = use { snackbarController }
    fun pendingUndo(): SnackbarUndo = use { pendingUndo }
    fun readerHighlights(): ReaderHighlights = use { readerHighlights }
    fun suggestionService(): SuggestionService = use { suggestionService }
    fun shareImage(): ShareImage = use { shareImage }
    fun browsingDataCleaner(): BrowsingDataCleaner = use { browsingDataCleaner }
    fun claudeClients(): ClaudeClientProvider = use { claudeClients }
    fun skimAi(): SkimAiService = use { skimAi }
    fun chatSessions(): ChatSessionStore = use { chatSessions }
    fun tabGrouping(): TabGrouping = use { tabGrouping }
    fun aiGate(): AiGate = use { aiGate }
    fun aiDataEraser(): AiDataEraser = use { aiDataEraser }

    fun removeHistoryEntry(): RemoveHistoryEntry = use { removeHistoryEntry }
    fun restoreHistoryEntry(): RestoreHistoryEntry = use { restoreHistoryEntry }
    fun matchHistory(): MatchHistory = use { matchHistory }
    fun addBookmark(): AddBookmark = use { addBookmark }
    fun updateBookmark(): UpdateBookmark = use { updateBookmark }
    fun moveBookmark(): MoveBookmark = use { moveBookmark }
    fun deleteBookmark(): DeleteBookmark = use { deleteBookmark }
    fun restoreBookmark(): RestoreBookmark = use { restoreBookmark }
    fun matchBookmarks(): MatchBookmarks = use { matchBookmarks }
    fun addShortcut(): AddShortcut = use { addShortcut }
    fun updateShortcut(): UpdateShortcut = use { updateShortcut }
    fun removeShortcut(): RemoveShortcut = use { removeShortcut }
    fun restoreShortcut(): RestoreShortcut = use { restoreShortcut }
    fun setSearchEngine(): SetSearchEngine = use { setSearchEngine }
    fun setTheme(): SetTheme = use { setTheme }
    fun setAskBeforeSending(): SetAskBeforeSending = use { setAskBeforeSending }
    fun setKeepChatHistory(): SetKeepChatHistory = use { setKeepChatHistory }
    fun setReaderTextScale(): SetReaderTextScale = use { setReaderTextScale }
    fun setReaderFont(): SetReaderFont = use { setReaderFont }
    fun completeOnboarding(): CompleteOnboarding = use { completeOnboarding }
    fun loadSavedChat(): LoadSavedChat = use { loadSavedChat }
    fun createSavedChat(): CreateSavedChat = use { createSavedChat }
    fun readSavedMessages(): ReadSavedMessages = use { readSavedMessages }
    fun addSavedMessage(): AddSavedMessage = use { addSavedMessage }
    fun updateSavedChat(): UpdateSavedChat = use { updateSavedChat }
    fun updateSavedMessage(): UpdateSavedMessage = use { updateSavedMessage }
    fun deleteAllSkimChats(): DeleteAllSkimChats = use { deleteAllSkimChats }
    fun removeSkimChat(): RemoveSkimChat = use { removeSkimChat }
    fun restoreSkimChat(): RestoreSkimChat = use { restoreSkimChat }
    fun setSkimAi(): SetSkimAi = use { setSkimAi }
    fun setSitePermission(): SetSitePermission = use { setSitePermission }
    fun openUrlInCurrentTab(): OpenUrlInCurrentTab = use { openUrlInCurrentTab }
    fun importBookmarks(): ImportBookmarks = use { importBookmarks }
    fun summarizePage(): SummarizePage = use { summarizePage }
    fun askPage(): AskPage = use { askPage }
    fun retryAnswer(): RetryAnswer = use { retryAnswer }
    fun overviewTabs(): OverviewTabs = use { overviewTabs }
    fun askTabs(): AskTabs = use { askTabs }
    fun collectOpenTabs(): CollectOpenTabs = use { collectOpenTabs }
    fun clearBrowsingData(): ClearBrowsingData = use { clearBrowsingData }
    fun deleteAllAiData(): DeleteAllAiData = use { deleteAllAiData }
    fun clearSkimHistory(): ClearSkimHistory = use { clearSkimHistory }

    /**
     * Closes whatever this generation opened, then forgets it. A second call with nothing open
     * returns. The next getter builds a new generation.
     */
    fun close() {
        val retiring = synchronized(monitor) {
            val current = generation ?: return
            closing = true
            while (users > 0) monitor.wait()
            generation = null
            current
        }
        try {
            retiring.close()
        } finally {
            synchronized(monitor) {
                closing = false
                monitor.notifyAll()
            }
        }
    }

    private fun <T> use(block: Generation.() -> T): T {
        val current = synchronized(monitor) {
            while (closing) monitor.wait()
            users++
            generation ?: Generation(app, storage, lastError, claudeClientFactory).also { generation = it }
        }
        return try {
            current.block()
        } finally {
            synchronized(monitor) {
                users--
                if (users == 0) monitor.notifyAll()
            }
        }
    }
}

private class Generation(
    app: Application,
    storage: AppStorage,
    lastError: MutableStateFlow<Throwable?>,
    claudeClientFactory: ClaudeClientFactory,
) {
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Uncaught exception in appScope", error)
            lastError.value = error
        },
    )
    private val settingsJob: CompletableJob = SupervisorJob(appScope.coroutineContext.job)
    val data: DataGeneration = DataGeneration(app, storage, appScope, settingsJob)

    private val networkMonitorHolder = lazy { ConnectivityNetworkMonitor(app) }
    private val httpClientHolder = lazy {
        OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    val httpClient: OkHttpClient by httpClientHolder
    val networkMonitor: NetworkMonitor by networkMonitorHolder
    val browserProfiles: BrowserProfiles by lazy { WebViewProfiles() }
    val incognitoSession: IncognitoSession by lazy {
        IncognitoSession(browserProfiles, storage.incognitoSharedDir(app), appScope)
    }
    val tabStateStore: TabStateStore by lazy {
        TabStateStore(storage.tabStateDir(app), { TabStateStore.webViewVersionOf(app) }, appScope)
    }
    val thumbnails: ThumbnailStore by lazy { ThumbnailStore(storage.thumbnailDir(app), appScope) }
    val pageScripts: PageScriptSource by lazy { PageScripts(app.assets) { PageScripts.runOnDevice(app) } }
    val downloadTracker: DownloadTracker by lazy { DownloadTracker(AndroidSystemDownloads(app), appScope) }
    val tabManager: TabManager by lazy {
        TabManager(
            tabRepo = data.tabRepository,
            history = data.historyRepository,
            settings = data.settings,
            incognito = incognitoSession,
            stateStore = tabStateStore,
            thumbnails = thumbnails,
            network = networkMonitor,
            appScope = appScope,
            scripts = pageScripts,
        )
    }
    val undoRegistry: UndoRegistry by lazy { UndoRegistry(appScope) }
    val snackbarController: SnackbarController by lazy { SnackbarController(undoRegistry) }
    val pendingUndo: SnackbarUndo by lazy { SnackbarUndo(undoRegistry, snackbarController) }
    val readerHighlights: ReaderHighlights by lazy { ReaderHighlights() }
    val suggestionService: SuggestionService by lazy { SuggestionService(httpClient) }
    val shareImage: ShareImage by lazy {
        ShareImage(
            httpClient = httpClient,
            cacheDir = app.cacheDir,
            incognito = incognitoSession,
            profiles = browserProfiles,
        )
    }
    val browsingDataCleaner: BrowsingDataCleaner by lazy {
        BrowsingDataCleaner(
            history = data.historyRepository,
            tabRepo = data.tabRepository,
            tabs = tabManager,
            stateStore = tabStateStore,
            thumbnails = thumbnails,
            incognito = incognitoSession,
            pendingUndo = pendingUndo,
        )
    }
    val claudeClients: ClaudeClientProvider by lazy {
        claudeClientFactory.create(data.settings::installId)
    }
    val skimAi: SkimAiService by lazy { SkimAiService(claudeClients, data.sitePermissionRepository, data.settings) }
    val chatSessions: ChatSessionStore by lazy {
        ChatSessionStore(
            chats = data.chatRepository,
            createChat = createSavedChat,
            messages = readSavedMessages,
            addMessage = addSavedMessage,
            updateChat = updateSavedChat,
            updateMessage = updateSavedMessage,
            tabs = tabManager,
            settings = data.settings,
            appScope = appScope,
        )
    }
    val tabGrouping: TabGrouping by lazy {
        TabGrouping(
            ai = skimAi,
            tabs = tabManager,
            tabRepo = data.tabRepository,
            sites = data.sitePermissionRepository,
            network = networkMonitor,
            appScope = appScope,
        )
    }
    val aiGate: AiGate by lazy { AiGate(data.sitePermissionRepository, data.settings, setSkimAi) }
    val aiDataEraser: AiDataEraser by lazy {
        AiDataEraser(
            deleteAllChats = deleteAllSkimChats,
            sites = data.sitePermissionRepository,
            pendingUndo = pendingUndo,
        )
    }

    val removeHistoryEntry: RemoveHistoryEntry by lazy { RemoveHistoryEntry(data.historyRepository) }
    val restoreHistoryEntry: RestoreHistoryEntry by lazy { RestoreHistoryEntry(data.historyRepository) }
    val matchHistory: MatchHistory by lazy { MatchHistory(data.historyRepository) }
    val addBookmark: AddBookmark by lazy { AddBookmark(data.bookmarkRepository) }
    val updateBookmark: UpdateBookmark by lazy { UpdateBookmark(data.bookmarkRepository) }
    val moveBookmark: MoveBookmark by lazy { MoveBookmark(data.bookmarkRepository) }
    val deleteBookmark: DeleteBookmark by lazy { DeleteBookmark(data.bookmarkRepository) }
    val restoreBookmark: RestoreBookmark by lazy { RestoreBookmark(data.bookmarkRepository) }
    val matchBookmarks: MatchBookmarks by lazy { MatchBookmarks(data.bookmarkRepository) }
    val addShortcut: AddShortcut by lazy { AddShortcut(data.shortcutRepository) }
    val updateShortcut: UpdateShortcut by lazy { UpdateShortcut(data.shortcutRepository) }
    val removeShortcut: RemoveShortcut by lazy { RemoveShortcut(data.shortcutRepository) }
    val restoreShortcut: RestoreShortcut by lazy { RestoreShortcut(data.shortcutRepository) }
    val setSearchEngine: SetSearchEngine by lazy { SetSearchEngine(data.settings) }
    val setTheme: SetTheme by lazy { SetTheme(data.settings) }
    val setAskBeforeSending: SetAskBeforeSending by lazy { SetAskBeforeSending(data.settings) }
    val setKeepChatHistory: SetKeepChatHistory by lazy { SetKeepChatHistory(data.settings) }
    val setReaderTextScale: SetReaderTextScale by lazy { SetReaderTextScale(data.settings) }
    val setReaderFont: SetReaderFont by lazy { SetReaderFont(data.settings) }
    val completeOnboarding: CompleteOnboarding by lazy { CompleteOnboarding(data.settings) }
    val loadSavedChat: LoadSavedChat by lazy { LoadSavedChat(data.chatRepository) }
    val createSavedChat: CreateSavedChat by lazy { CreateSavedChat(data.chatRepository) }
    val readSavedMessages: ReadSavedMessages by lazy { ReadSavedMessages(data.chatRepository) }
    val addSavedMessage: AddSavedMessage by lazy { AddSavedMessage(data.chatRepository) }
    val updateSavedChat: UpdateSavedChat by lazy { UpdateSavedChat(data.chatRepository) }
    val updateSavedMessage: UpdateSavedMessage by lazy { UpdateSavedMessage(data.chatRepository) }
    val deleteAllSkimChats: DeleteAllSkimChats by lazy { DeleteAllSkimChats(data.chatRepository) }
    val removeSkimChat: RemoveSkimChat by lazy { RemoveSkimChat(data.chatRepository) }
    val restoreSkimChat: RestoreSkimChat by lazy { RestoreSkimChat(data.chatRepository) }
    val setSkimAi: SetSkimAi by lazy { SetSkimAi(data.sitePermissionRepository) }
    val setSitePermission: SetSitePermission by lazy { SetSitePermission(data.sitePermissionRepository) }
    val openUrlInCurrentTab: OpenUrlInCurrentTab by lazy { OpenUrlInCurrentTab(tabManager) }
    val importBookmarks: ImportBookmarks by lazy { ImportBookmarks(data.bookmarkImporter) }
    val summarizePage: SummarizePage by lazy { SummarizePage(skimAi) }
    val askPage: AskPage by lazy { AskPage(skimAi) }
    val retryAnswer: RetryAnswer by lazy { RetryAnswer(skimAi) }
    val overviewTabs: OverviewTabs by lazy { OverviewTabs(skimAi) }
    val askTabs: AskTabs by lazy { AskTabs(skimAi) }
    val collectOpenTabs: CollectOpenTabs by lazy { CollectOpenTabs(tabManager, data.sitePermissionRepository) }
    val clearBrowsingData: ClearBrowsingData by lazy {
        ClearBrowsingData { history, cookies, cache, pages ->
            browsingDataCleaner.clearAll(history, cookies, cache, pages)
        }
    }
    val deleteAllAiData: DeleteAllAiData by lazy { DeleteAllAiData(aiDataEraser) }
    val clearSkimHistory: ClearSkimHistory by lazy { ClearSkimHistory(aiDataEraser) }

    fun close() {
        if (networkMonitorHolder.isInitialized()) networkMonitorHolder.value.close()
        if (httpClientHolder.isInitialized()) {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        }
        data.closeDatabaseIfOpen()
        appScope.cancel()
        runBlocking { settingsJob.join() }
    }

    private companion object {
        const val TAG = "ProcessGraph"
        const val HTTP_TIMEOUT_SECONDS = 15L
    }
}
