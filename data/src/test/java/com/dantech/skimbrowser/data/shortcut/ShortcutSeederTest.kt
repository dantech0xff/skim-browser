package com.dantech.skimbrowser.data.shortcut

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.repo.ShortcutRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.domain.shortcut.ShortcutCatalog
import com.dantech.skimbrowser.domain.shortcut.ShortcutPreset
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutSeederTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: SkimDatabase
    private val stores = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
    }

    @After
    fun tearDown() = runBlocking {
        stores.forEach { it.coroutineContext.job.cancelAndJoin() }
        stores.clear()
        db.close()
    }

    @Test
    fun anEmptyHomeGainsThePresetsInOrderAndDeletingThemDoesNotBringThemBack() = runTest {
        val store = newStore()
        val catalog = FakeCatalog(presets = PRESETS)
        val shortcuts = ShortcutRepository(db.shortcutDao()) { ShortcutSeeder(db.shortcutDao(), catalog, store).seed() }

        assertEquals(listOf("Wikipedia", "GitHub"), shortcuts.observe().first().map { it.name })
        assertEquals(listOf(0, 1), shortcuts.observe().first().map { it.position })

        shortcuts.observe().first().forEach { shortcuts.remove(it.id) }
        ShortcutSeeder(db.shortcutDao(), catalog, store).seed()

        assertEquals(emptyList<String>(), shortcuts.observe().first().map { it.name })
    }

    @Test
    fun shortcutsTheUserAlreadyHasAreLeftAlone() = runTest {
        ShortcutRepository(db.shortcutDao()).add("Mine", "https://example.com")
        seeder(PRESETS).seed()

        val stored = ShortcutRepository(db.shortcutDao()).observe().first()
        assertEquals(listOf("Mine"), stored.map { it.name })
        assertEquals(listOf(0), stored.map { it.position })
    }

    @Test
    fun aCatalogThatCannotBeReadIsTriedAgain() = runTest {
        val catalog = FakeCatalog(failure = IOException("offline"))
        val seeder = seeder(catalog)
        seeder.seed()
        assertEquals(0, db.shortcutDao().count())

        catalog.failure = null
        catalog.presets = PRESETS
        seeder.seed()

        assertEquals(listOf("Wikipedia", "GitHub"), ShortcutRepository(db.shortcutDao()).observe().first().map { it.name })
    }

    @Test
    fun rowsThatAreNotWebShortcutsAreDropped() = runTest {
        seeder(
            listOf(
                ShortcutPreset("Ok", "https://ok.example"),
                ShortcutPreset(" ", "https://blank.example"),
                ShortcutPreset("Script", "javascript:alert(1)"),
            ),
        ).seed()

        assertEquals(listOf("Ok" to "https://ok.example"), ShortcutRepository(db.shortcutDao()).observe().first().map { it.name to it.url })
    }

    @Test
    fun seedingTwiceAtOnceWritesThePresetsOnce() = runTest {
        val seeder = seeder(PRESETS)
        withContext(Dispatchers.Default) {
            (1..8).map { async { seeder.seed() } }.awaitAll()
        }

        assertEquals(listOf("Wikipedia", "GitHub"), ShortcutRepository(db.shortcutDao()).observe().first().map { it.name })
    }

    private fun seeder(presets: List<ShortcutPreset>): ShortcutSeeder = seeder(FakeCatalog(presets = presets))

    private fun seeder(catalog: ShortcutCatalog): ShortcutSeeder = ShortcutSeeder(db.shortcutDao(), catalog, newStore())

    private fun newStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(folder.newFolder(), "skim_settings.preferences_pb")
        return SettingsRepository.createDataStore(scope) { file }.also { stores += scope }
    }

    private class FakeCatalog(
        var presets: List<ShortcutPreset> = emptyList(),
        var failure: Exception? = null,
    ) : ShortcutCatalog {
        override suspend fun presets(): List<ShortcutPreset> {
            failure?.let { throw it }
            return presets
        }
    }

    private companion object {
        val PRESETS = listOf(
            ShortcutPreset("Wikipedia", "https://www.wikipedia.org"),
            ShortcutPreset("GitHub", "https://github.com"),
        )
    }
}
