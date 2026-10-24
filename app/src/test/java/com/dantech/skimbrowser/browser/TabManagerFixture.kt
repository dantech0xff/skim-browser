package com.dantech.skimbrowser.browser

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.domain.history.HistoryEntry
import com.dantech.skimbrowser.data.db.entity.TabEntity
import com.dantech.skimbrowser.data.repo.HistoryRepository
import com.dantech.skimbrowser.data.repo.TabRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.longQuery
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * A [TabManager] as the app builds it, over a real in-memory database, a real settings file and
 * real state and thumbnail stores in [root], with fakes only on the WebView side: [engine] (attach
 * it to drive pages), [profiles] and [network]. Commands run on the test thread, standing for the
 * main thread; page work runs on [main], on the test's virtual clock; Room writes run on a real
 * thread, so check the database after [settle].
 *
 * A second fixture over the same [db] and [root] is the app after process death; close the first
 * one's settings with [closeSettings] before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TabManagerFixture(
    private val scope: TestScope,
    private val root: File,
    val db: SkimDatabase = inMemorySkimDatabase(),
    supportsIncognito: Boolean = true,
    restoreTimeoutMs: Long = TabManager.RESTORE_TIMEOUT_MS,
    /** Ids of new tabs are "<prefix>-1", "<prefix>-2"…; a fixture after process death needs another prefix. */
    private val idPrefix: String = "tab",
    /** Where the write queue runs; a test dispatcher holds every write back until the test lets it run. */
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    val main = StandardTestDispatcher(scope.testScheduler)
    val tabRepo = TabRepository(db)
    val history = HistoryRepository(db.historyDao(), db)
    private val settingsScope = CoroutineScope(Dispatchers.IO + Job())
    val settings = SettingsRepository(SettingsRepository.createDataStore(settingsScope) { File(root, "skim_settings.preferences_pb") })
    val profiles = FakeBrowserProfiles(isMultiProfileSupported = supportsIncognito)
    val sharedDir = File(root, "shared/incognito")
    val incognito = IncognitoSession(profiles, sharedDir, scope.backgroundScope, main, clock = ::now)
    val stateDir = File(root, "tabstate")
    val stateStore = TabStateStore(stateDir, { WEBVIEW_VERSION }, scope.backgroundScope, main)
    val thumbsDir = File(root, "thumbs")
    val thumbnails = ThumbnailStore(thumbsDir, scope.backgroundScope, main)
    val network = FakeNetworkMonitor()
    val scripts = FakePageScripts()
    private var lastTabNumber = 0

    val manager = TabManager(
        tabRepo = tabRepo,
        history = history,
        settings = settings,
        incognito = incognito,
        stateStore = stateStore,
        thumbnails = thumbnails,
        network = network,
        appScope = scope.backgroundScope,
        scripts = scripts,
        clock = ::now,
        elapsedRealtime = { scope.testScheduler.currentTime },
        newTabId = { "$idPrefix-${++lastTabNumber}" },
        ioDispatcher = ioDispatcher,
        mainDispatcher = main,
        restoreTimeoutMs = restoreTimeoutMs,
    )

    val engine = FakeTabEngine(manager.events, webViewFactory = { WebView(ApplicationProvider.getApplicationContext<Context>()) })

    fun now(): Long = EPOCH + scope.testScheduler.currentTime

    fun tab(id: String): Tab? = manager.state.value.tab(id)

    fun tabIds(): List<String> = manager.state.value.tabs.map { it.id }

    /** Waits for every queued Room, settings and file write. */
    suspend fun settle() {
        manager.awaitWrites()
        stateStore.awaitIdle()
        thumbnails.awaitIdle()
    }

    /** The tab rows that are open, by group and position. */
    suspend fun openRows(): List<TabEntity> = db.tabDao().openTabs()

    fun closedRowCount(): Long = db.longQuery("SELECT COUNT(*) FROM tabs WHERE closedAt IS NOT NULL")

    fun rowCount(): Long = db.longQuery("SELECT COUNT(*) FROM tabs")

    suspend fun historyEntries(): List<HistoryEntry> = history.observe(null).first()

    suspend fun lastTabId(): String? = settings.settings.first().lastTabId

    /** Releases the settings file, so another fixture can open it. */
    suspend fun closeSettings() {
        settingsScope.coroutineContext.job.cancelAndJoin()
    }

    /** Releases everything; the database too unless another fixture still uses it. */
    suspend fun close(closeDatabase: Boolean = true) {
        closeSettings()
        if (closeDatabase) db.close()
    }

    companion object {
        const val EPOCH = 1_760_000_000_000L
        const val WEBVIEW_VERSION = "com.google.android.webview/141.0.7390.122"
    }
}
