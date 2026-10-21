package com.dantech.skimbrowser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.TabEntity
import com.dantech.skimbrowser.data.settings.SearchEngine
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContainerStorageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun nothingIsOpenedBeforeFirstUse() {
        val dir = folder.newFolder()

        AppContainer(app, AppStorage.temp(dir)).use {
            assertFalse(File(dir, "skim.db").exists())
            assertFalse(File(dir, "skim_settings.preferences_pb").exists())
        }
    }

    @Test
    fun aNewContainerOnTheSameStorageReadsEverythingBack() = runTest {
        val dir = folder.newFolder()
        val storage = AppStorage.temp(dir)
        var installId = ""

        AppContainer(app, storage).use { container ->
            container.tabRepository.upsert(
                TabEntity(id = "tab-1", url = URL, title = "Heat pumps", position = 0, createdAt = 1, lastAccessedAt = 2),
            )
            container.historyRepository.recordVisit(URL, "Heat pumps", at = 3)
            container.bookmarkRepository.addToMobile("Heat pumps", URL)
            container.shortcutRepository.add("Wikipedia", "wikipedia.org")
            container.chatRepository.createChat(
                SkimChatEntity(kind = ChatKind.PAGE, url = URL, host = "daylightjournal.com", title = "Heat pumps", createdAt = 4, updatedAt = 4),
            )
            container.sitePermissionRepository.setSkimAi("daylightjournal.com", SkimAiState.ALLOWED)
            container.settings.setTheme(ThemeSetting.Dark)
            container.settings.setSearchEngine(SearchEngine.DUCKDUCKGO)
            container.settings.setLastTabId("tab-1")
            installId = container.settings.installId()
        }

        AppContainer(app, storage).use { container ->
            assertEquals(listOf("tab-1"), container.tabRepository.loadOpen().first.map { it.id })
            assertEquals(listOf(URL), container.historyRepository.observe(null).first().map { it.url })
            assertEquals(listOf("Heat pumps"), container.bookmarkRepository.observeRoot().first().recentlyAdded.map { it.title })
            assertEquals(listOf("Wikipedia"), container.shortcutRepository.observe().first().map { it.name })
            assertEquals(1, container.chatRepository.observeCount().first())
            assertEquals(SkimAiState.ALLOWED, container.sitePermissionRepository.get("daylightjournal.com")?.skimAi)
            val settings = container.settings.settings.first()
            assertEquals(ThemeSetting.Dark, settings.theme)
            assertEquals(SearchEngine.DUCKDUCKGO, settings.searchEngine)
            assertEquals("tab-1", settings.lastTabId)
            assertEquals(installId, container.settings.installId())
        }
        assertTrue(File(dir, "skim.db").exists())
        assertTrue(File(dir, "skim_settings.preferences_pb").exists())
        assertFalse(app.getDatabasePath("skim.db").exists())
    }

    @Test
    fun closeReleasesTheSettingsFileForTheNextContainerAtOnce() = runTest {
        val storage = AppStorage.temp(folder.newFolder())

        repeat(6) { round ->
            AppContainer(app, storage).use { container ->
                val expected = if (round == 0) 100 else 80 + (round - 1) * 10
                assertEquals(expected, container.settings.settings.first().readerTextScalePercent)
                container.settings.setReaderTextScale(80 + round * 10)
                container.historyRepository.recordVisit("https://example.com/$round", "Round $round", at = round.toLong())
            }
        }

        AppContainer(app, storage).use { container ->
            assertEquals(6, container.historyRepository.observe(null).first().size)
        }
    }

    @Test
    fun closeWaitsForSettingsWritesStillInFlight() = runTest {
        val storage = AppStorage.temp(folder.newFolder())

        repeat(5) { round ->
            val container = AppContainer(app, storage)
            val writing = CompletableDeferred<Unit>()
            repeat(20) { writer ->
                container.appScope.launch {
                    repeat(50) { i ->
                        container.settings.setLastTabId("tab-$round-$writer-$i")
                        writing.complete(Unit)
                    }
                }
            }
            writing.await()

            // Closed while writes are queued: the next container must be able to open the file.
            container.close()
            AppContainer(app, storage).use { next ->
                assertTrue(next.settings.settings.first().lastTabId.orEmpty().startsWith("tab-$round-"))
            }
        }
    }

    @Test
    fun closeStopsTheAppScopeAndCanBeCalledAgain() {
        val container = AppContainer(app, AppStorage.temp(folder.newFolder()))
        container.settings

        container.close()
        container.close()

        assertFalse(container.appScope.isActive)
    }

    @Test
    fun eachDependencyIsCreatedOnce() {
        AppContainer(app, AppStorage.temp(folder.newFolder())).use { container ->
            assertSame(container.database, container.database)
            assertSame(container.dataStore, container.dataStore)
            assertSame(container.settings, container.settings)
            assertSame(container.tabRepository, container.tabRepository)
            assertSame(container.historyRepository, container.historyRepository)
            assertSame(container.bookmarkRepository, container.bookmarkRepository)
            assertSame(container.shortcutRepository, container.shortcutRepository)
            assertSame(container.chatRepository, container.chatRepository)
            assertSame(container.sitePermissionRepository, container.sitePermissionRepository)
        }
    }

    private companion object {
        const val URL = "https://daylightjournal.com/energy/heat-pumps-cold"
    }
}
