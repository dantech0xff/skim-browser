package com.dantech.skimbrowser

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.settings.SearchEngine
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.tab.PersistedTab
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ProcessGraphStorageTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    @Inject lateinit var graph: ProcessGraph

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = folder.newFolder()
        TestStorage.current.set(AppStorage.temp(dir))
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        graph.close()
        TestStorage.current.set(null)
    }

    @Test
    fun nothingIsOpenedBeforeFirstUse() {
        assertFalse(File(dir, "skim.db").exists())
        assertFalse(File(dir, "skim_settings.preferences_pb").exists())
    }

    @Test
    fun aNewContainerOnTheSameStorageReadsEverythingBack() = runTest {
        var installId = ""

        graph.tabRepository().upsert(
            PersistedTab(id = "tab-1", url = URL, title = "Heat pumps", position = 0, createdAt = 1, lastAccessedAt = 2),
        )
        graph.historyRepository().recordVisit(URL, "Heat pumps", at = 3)
        graph.bookmarkRepository().addToMobile("Heat pumps", URL)
        graph.shortcutRepository().add("Wikipedia", "wikipedia.org")
        graph.chatRepository().createChat(
            SavedChat(kind = ChatKind.PAGE, url = URL, host = "daylightjournal.com", title = "Heat pumps", createdAt = 4, updatedAt = 4),
        )
        graph.sitePermissionRepository().setSkimAi("daylightjournal.com", SkimAiState.ALLOWED)
        graph.settings().setTheme(ThemeSetting.Dark)
        graph.settings().setSearchEngine(SearchEngine.DUCKDUCKGO)
        graph.settings().setLastTabId("tab-1")
        installId = graph.settings().installId()
        graph.close()

        assertEquals(listOf("tab-1"), graph.tabRepository().loadOpen().first.map { it.id })
        assertEquals(listOf(URL), graph.historyRepository().observe(null).first().map { it.url })
        assertEquals(listOf("Heat pumps"), graph.bookmarkRepository().observeRoot().first().recentlyAdded.map { it.title })
        assertEquals(listOf("Wikipedia"), graph.shortcutRepository().observe().first().map { it.name })
        assertEquals(1, graph.chatRepository().observeCount().first())
        assertEquals(SkimAiState.ALLOWED, graph.sitePermissionRepository().get("daylightjournal.com")?.skimAi)
        val settings = graph.settings().settings.first()
        assertEquals(ThemeSetting.Dark, settings.theme)
        assertEquals(SearchEngine.DUCKDUCKGO, settings.searchEngine)
        assertEquals("tab-1", settings.lastTabId)
        assertEquals(installId, graph.settings().installId())
        assertTrue(File(dir, "skim.db").exists())
        assertTrue(File(dir, "skim_settings.preferences_pb").exists())
        assertFalse(ApplicationProvider.getApplicationContext<android.app.Application>().getDatabasePath("skim.db").exists())
    }

    @Test
    fun closeReleasesTheSettingsFileForTheNextContainerAtOnce() = runTest {
        repeat(6) { round ->
            val expected = if (round == 0) 100 else 80 + (round - 1) * 10
            assertEquals(expected, graph.settings().settings.first().readerTextScalePercent)
            graph.settings().setReaderTextScale(80 + round * 10)
            graph.historyRepository().recordVisit("https://example.com/$round", "Round $round", at = round.toLong())
            graph.close()
        }

        assertEquals(6, graph.historyRepository().observe(null).first().size)
    }

    @Test
    fun closeWaitsForSettingsWritesStillInFlight() = runTest {
        repeat(5) { round ->
            val writing = CompletableDeferred<Unit>()
            repeat(20) { writer ->
                graph.appScope().launch {
                    repeat(50) { i ->
                        graph.settings().setLastTabId("tab-$round-$writer-$i")
                        writing.complete(Unit)
                    }
                }
            }
            writing.await()

            // Closed while writes are queued: the next read must be able to open the file.
            graph.close()
            assertTrue(graph.settings().settings.first().lastTabId.orEmpty().startsWith("tab-$round-"))
        }
    }

    @Test
    fun closeStopsTheAppScopeAndCanBeCalledAgain() {
        val scope = graph.appScope()
        graph.settings()

        graph.close()
        graph.close()

        assertFalse(scope.isActive)
        assertTrue(graph.appScope().isActive)
    }

    @Test
    fun eachDependencyIsCreatedOnce() {
        assertSame(graph.database(), graph.database())
        assertSame(graph.dataStore(), graph.dataStore())
        assertSame(graph.settings(), graph.settings())
        assertSame(graph.tabRepository(), graph.tabRepository())
        assertSame(graph.historyRepository(), graph.historyRepository())
        assertSame(graph.bookmarkRepository(), graph.bookmarkRepository())
        assertSame(graph.shortcutRepository(), graph.shortcutRepository())
        assertSame(graph.chatRepository(), graph.chatRepository())
        assertSame(graph.sitePermissionRepository(), graph.sitePermissionRepository())
    }

    private companion object {
        const val URL = "https://daylightjournal.com/energy/heat-pumps-cold"
    }
}
