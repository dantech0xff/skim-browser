package com.dantech.skimbrowser.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import com.dantech.skimbrowser.domain.usecase.SetSearchEngine
import com.dantech.skimbrowser.domain.usecase.SetTheme
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings reads the stored engine and theme, and the default-browser flag the screen reports. */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun themeAndSearchEngineAreStoredAndTheDefaultBrowserFlagUpdates() = runTest {
        val settings = FakeSettingsRepository()
        val model = SettingsViewModel(settings, SetSearchEngine(settings), SetTheme(settings), versionName = "1.0", versionCode = 1)
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
