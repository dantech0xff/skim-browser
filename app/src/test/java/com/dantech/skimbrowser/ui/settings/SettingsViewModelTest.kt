package com.dantech.skimbrowser.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.settings.SearchEngine
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Settings reads the stored engine and theme, and the default-browser flag the screen reports. */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun themeAndSearchEngineAreStoredAndTheDefaultBrowserFlagUpdates() = runTest {
        val settings = SettingsRepository(
            SettingsRepository.createDataStore(scope) { File(folder.root, "settings.preferences_pb") },
        )
        val model = SettingsViewModel(settings, versionName = "1.0", versionCode = 1)
        model.state.first { it.searchEngine == SearchEngine.GOOGLE }

        assertFalse(model.state.value.isDefaultBrowser)
        assertEquals("1.0", model.state.value.versionName)
        assertEquals(1, model.state.value.versionCode)

        model.setDefaultBrowser(true)
        assertTrue(model.state.first { it.isDefaultBrowser }.isDefaultBrowser)

        model.setTheme(ThemeSetting.Dark)
        model.setSearchEngine(SearchEngine.DUCKDUCKGO)
        model.state.first { it.theme == ThemeSetting.Dark && it.searchEngine == SearchEngine.DUCKDUCKGO }
        val stored = settings.settings.first()
        assertEquals(ThemeSetting.Dark, stored.theme)
        assertEquals(SearchEngine.DUCKDUCKGO, stored.searchEngine)
    }
}
