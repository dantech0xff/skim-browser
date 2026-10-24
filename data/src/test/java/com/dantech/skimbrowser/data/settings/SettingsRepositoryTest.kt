package com.dantech.skimbrowser.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import java.io.File
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var file: File

    /** Every store opened by a test with the scope that runs it, newest last. */
    private val stores = mutableListOf<Pair<CoroutineScope, DataStore<Preferences>>>()

    @Before
    fun setUp() {
        file = File(folder.newFolder(), "skim_settings.preferences_pb")
    }

    @After
    fun tearDown() = runBlocking { closeStores() }

    @Test
    fun defaultsAreTheDrawnSettings() = runTest {
        val settings = SettingsRepository(newStore()).settings.first()

        assertEquals(SearchEngine.GOOGLE, settings.searchEngine)
        assertEquals(ThemeSetting.System, settings.theme)
        assertTrue(settings.askBeforeSending)
        assertTrue(settings.keepChatHistory)
        assertEquals(100, settings.readerTextScalePercent)
        assertEquals(ReaderFont.SANS_SERIF, settings.readerFont)
        assertFalse(settings.onboardingCompleted)
        assertNull(settings.lastTabId)
    }

    @Test
    fun everySettingIsReadBackFromANewStoreOnTheSameFile() = runTest {
        SettingsRepository(newStore()).apply {
            setSearchEngine(SearchEngine.ECOSIA)
            setTheme(ThemeSetting.Dark)
            setAskBeforeSending(false)
            setKeepChatHistory(false)
            setReaderTextScale(130)
            setReaderFont(ReaderFont.SERIF)
            setOnboardingCompleted()
            setLastTabId("tab-7")
        }
        closeStores()

        assertEquals(
            SkimSettings(
                searchEngine = SearchEngine.ECOSIA,
                theme = ThemeSetting.Dark,
                askBeforeSending = false,
                keepChatHistory = false,
                readerTextScalePercent = 130,
                readerFont = ReaderFont.SERIF,
                onboardingCompleted = true,
                lastTabId = "tab-7",
            ),
            SettingsRepository(newStore()).settings.first(),
        )
    }

    @Test
    fun unknownNamesAndWrongTypesReadAsDefaults() = runTest {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("search_engine")] = "YAHOO"
            it[stringPreferencesKey("theme")] = "Purple"
            it[intPreferencesKey("reader_font")] = 3
            it[stringPreferencesKey("ask_before_sending")] = "no"
            it[stringPreferencesKey("reader_text_scale_percent")] = "huge"
            it[intPreferencesKey("last_tab_id")] = 9
        }

        assertEquals(SkimSettings(), SettingsRepository(store).settings.first())
    }

    @Test
    fun theReaderTextScaleStaysBetween80And200() = runTest {
        val store = newStore()
        val repository = SettingsRepository(store)

        repository.setReaderTextScale(50)
        assertEquals(80, repository.settings.first().readerTextScalePercent)
        repository.setReaderTextScale(500)
        assertEquals(200, repository.settings.first().readerTextScalePercent)
        store.edit { it[intPreferencesKey("reader_text_scale_percent")] = 999 }
        assertEquals(200, repository.settings.first().readerTextScalePercent)
        store.edit { it[intPreferencesKey("reader_text_scale_percent")] = -40 }
        assertEquals(80, repository.settings.first().readerTextScalePercent)
    }

    @Test
    fun theLastTabCanBeForgotten() = runTest {
        val repository = SettingsRepository(newStore())

        repository.setLastTabId("tab-1")
        repository.setLastTabId(null)

        assertNull(repository.settings.first().lastTabId)
    }

    @Test
    fun theInstallIdIsAUuidThatNeverChanges() = runTest {
        val first = SettingsRepository(newStore()).installId()
        assertTrue(first, UUID_FORMAT.matches(first))
        assertEquals(first, SettingsRepository(stores.last().second).installId())
        closeStores()

        assertEquals(first, SettingsRepository(newStore()).installId())
    }

    @Test
    fun racingFirstCallsAgreeOnOneInstallId() = runTest {
        val repository = SettingsRepository(newStore())

        val ids = withContext(Dispatchers.Default) { (1..32).map { async { repository.installId() } }.awaitAll() }

        assertEquals(1, ids.toSet().size)
    }

    @Test
    fun aStoredInstallIdThatIsNotAUuidIsReplaced() = runTest {
        val store = newStore()
        store.edit { it[stringPreferencesKey("install_id")] = "abc\r\nX-Injected: 1" }

        val id = SettingsRepository(store).installId()

        assertTrue(id, UUID_FORMAT.matches(id))
        assertEquals(id, SettingsRepository(store).installId())
    }

    @Test
    fun aCorruptSettingsFileReadsAsDefaultsAndCanBeWrittenAgain() = runTest {
        file.writeBytes(byteArrayOf(0x0A, 0xFF.toByte(), 0x7F, 0x01, 0x00, 0x13, 0x37))
        val repository = SettingsRepository(newStore())

        assertEquals(SkimSettings(), repository.settings.first())
        repository.setTheme(ThemeSetting.Light)
        assertEquals(ThemeSetting.Light, repository.settings.first().theme)
    }

    @Test
    fun theInstallIdIsNotPartOfTheSettings() = runTest {
        val repository = SettingsRepository(newStore())

        val id = repository.installId()

        assertFalse(repository.settings.first().toString().contains(id))
        assertNotEquals(id, repository.settings.first().lastTabId)
    }

    private fun newStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        return SettingsRepository.createDataStore(scope) { file }.also { stores += scope to it }
    }

    /** Cancels every open store and waits until each has released the file. */
    private suspend fun closeStores() {
        stores.forEach { (scope, _) -> scope.coroutineContext.job.cancelAndJoin() }
        stores.clear()
    }

    private companion object {
        val UUID_FORMAT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
