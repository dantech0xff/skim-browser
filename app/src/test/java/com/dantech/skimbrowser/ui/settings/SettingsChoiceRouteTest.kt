package com.dantech.skimbrowser.ui.settings

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.di.AppVersion
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Choosing a search engine or a theme stores that choice and closes the dialog. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class SettingsChoiceRouteTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun choosingASearchEngineStoresItAndCloses() {
        val settings = FakeSettingsRepository()
        val model = SettingsViewModel(settings, AppVersion("1.0", 1))
        var finished = 0
        composeRule.setSkimContent {
            SearchEngineRoute(onFinished = { finished += 1 }, model = model)
        }

        composeRule.onNodeWithText(context.getString(R.string.search_engine_duckduckgo)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, finished)
        assertEquals(SearchEngine.DUCKDUCKGO, stored(settings).searchEngine)
        assertEquals(ThemeSetting.System, stored(settings).theme)
    }

    @Test
    fun choosingAThemeStoresItAndCloses() {
        val settings = FakeSettingsRepository()
        val model = SettingsViewModel(settings, AppVersion("1.0", 1))
        var finished = 0
        composeRule.setSkimContent {
            ThemeRoute(onFinished = { finished += 1 }, model = model)
        }

        composeRule.onNodeWithText(context.getString(R.string.theme_dark)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, finished)
        assertEquals(ThemeSetting.Dark, stored(settings).theme)
        assertEquals(SearchEngine.GOOGLE, stored(settings).searchEngine)
    }

    @Test
    fun cancelClosesWithoutSaving() {
        val settings = FakeSettingsRepository()
        val model = SettingsViewModel(settings, AppVersion("1.0", 1))
        var finished = 0
        composeRule.setSkimContent {
            SearchEngineRoute(onFinished = { finished += 1 }, model = model)
        }

        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, finished)
        assertEquals(SearchEngine.GOOGLE, stored(settings).searchEngine)
    }

    private fun stored(settings: FakeSettingsRepository) = runBlocking { settings.settings.first() }
}
