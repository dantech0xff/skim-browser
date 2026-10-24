package com.dantech.skimbrowser

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.settings.SearchEngine
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.shortcut.ShortcutRepository
import com.dantech.skimbrowser.domain.tab.PersistedTab
import com.dantech.skimbrowser.domain.tab.TabRepository
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config

/**
 * Each method gets its own component. The `shared_` pair writes on one component and reads the same
 * files back on the next, after `@After` released the first.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppContainerStorageTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val name = TestName()

    @Inject lateinit var database: Provider<SkimDatabase>
    @Inject lateinit var dataStore: Provider<DataStore<Preferences>>
    @Inject lateinit var settings: Provider<SettingsRepository>
    @Inject lateinit var tabRepository: Provider<TabRepository>
    @Inject lateinit var historyRepository: Provider<HistoryRepository>
    @Inject lateinit var bookmarkRepository: Provider<BookmarkRepository>
    @Inject lateinit var shortcutRepository: Provider<ShortcutRepository>
    @Inject lateinit var chatRepository: Provider<ChatRepository>
    @Inject lateinit var sitePermissionRepository: Provider<SitePermissionRepository>
    @Inject @AppScope lateinit var appScope: Provider<CoroutineScope>
    @Inject lateinit var resources: ReleaseProcessResources

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = if (name.methodName.startsWith("shared_")) File(folder.root, "shared").apply { mkdirs() } else folder.newFolder()
        TestStorage.current.set(AppStorage.temp(dir))
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        resources.release()
        TestStorage.current.set(null)
    }

    @Test
    fun nothingIsOpenedBeforeFirstUse() {
        assertFalse(File(dir, "skim.db").exists())
        assertFalse(File(dir, "skim_settings.preferences_pb").exists())
    }

    @Test
    fun shared_aWritesSettingsHistoryAndLastTabId() = runTest {
        tabRepository.get().upsert(
            PersistedTab(id = "tab-1", url = URL, title = "Heat pumps", position = 0, createdAt = 1, lastAccessedAt = 2),
        )
        historyRepository.get().recordVisit(URL, "Heat pumps", at = 3)
        bookmarkRepository.get().addToMobile("Heat pumps", URL)
        shortcutRepository.get().add("Wikipedia", "wikipedia.org")
        chatRepository.get().createChat(
            SavedChat(kind = ChatKind.PAGE, url = URL, host = "daylightjournal.com", title = "Heat pumps", createdAt = 4, updatedAt = 4),
        )
        sitePermissionRepository.get().setSkimAi("daylightjournal.com", SkimAiState.ALLOWED)
        settings.get().setTheme(ThemeSetting.Dark)
        settings.get().setSearchEngine(SearchEngine.DUCKDUCKGO)
        val installId = settings.get().installId()
        settings.get().setLastTabId("tab-inflight")
        File(dir, INSTALL_ID_FILE).writeText(installId)
    }

    @Test
    fun shared_bReadsInstallIdLastTabIdHistoryAndSavedRows() = runTest {
        val written = File(dir, INSTALL_ID_FILE)
        assertTrue("shared_a did not run first", written.isFile)

        assertEquals(listOf("tab-1"), tabRepository.get().loadOpen().first.map { it.id })
        assertEquals(listOf(URL), historyRepository.get().observe(null).first().map { it.url })
        assertEquals(listOf("Heat pumps"), bookmarkRepository.get().observeRoot().first().recentlyAdded.map { it.title })
        assertEquals(listOf("Wikipedia"), shortcutRepository.get().observe().first().map { it.name })
        assertEquals(1, chatRepository.get().observeCount().first())
        assertEquals(SkimAiState.ALLOWED, sitePermissionRepository.get().get("daylightjournal.com")?.skimAi)
        val saved = settings.get().settings.first()
        assertEquals(ThemeSetting.Dark, saved.theme)
        assertEquals(SearchEngine.DUCKDUCKGO, saved.searchEngine)
        assertEquals("tab-inflight", saved.lastTabId)
        assertEquals(written.readText(), settings.get().installId())
        assertTrue(File(dir, "skim.db").exists())
        assertTrue(File(dir, "skim_settings.preferences_pb").exists())
        assertFalse(ApplicationProvider.getApplicationContext<android.app.Application>().getDatabasePath("skim.db").exists())
    }

    @Test
    fun releaseStopsTheAppScopeAndCanBeCalledAgain() {
        val scope = appScope.get()
        settings.get()

        resources.release()
        resources.release()

        assertFalse(scope.isActive)
    }

    @Test
    fun eachDependencyIsCreatedOnce() {
        assertSame(database.get(), database.get())
        assertSame(dataStore.get(), dataStore.get())
        assertSame(settings.get(), settings.get())
        assertSame(tabRepository.get(), tabRepository.get())
        assertSame(historyRepository.get(), historyRepository.get())
        assertSame(bookmarkRepository.get(), bookmarkRepository.get())
        assertSame(shortcutRepository.get(), shortcutRepository.get())
        assertSame(chatRepository.get(), chatRepository.get())
        assertSame(sitePermissionRepository.get(), sitePermissionRepository.get())
    }

    companion object {
        private const val URL = "https://daylightjournal.com/energy/heat-pumps-cold"
        private const val INSTALL_ID_FILE = "install-id.txt"

        @field:ClassRule
        @JvmField
        val folder = TemporaryFolder()
    }
}
