package com.dantech.skimbrowser.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.settings.SearchEngine
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings (12), AI & privacy (10) and the dialogs of board 15 that those screens open. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SettingsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settings() {
        composeRule.setSkimContent {
            SettingsScreen(
                state = SettingsUiState(
                    searchEngine = SearchEngine.GOOGLE,
                    theme = ThemeSetting.System,
                    isDefaultBrowser = true,
                    askBeforeSending = true,
                    versionName = "1.0.0",
                    versionCode = 100,
                ),
                onBack = {},
                onSearchEngine = {},
                onDefaultBrowser = {},
                onTheme = {},
                onAiPrivacy = {},
                onClearData = {},
                onPrivacyPolicy = {},
                onTerms = {},
                onLicenses = {},
            )
        }
        composeRule.waitForIdle()
        captureBoard("12-settings")
        composeRule.assertA11y()
    }

    @Test
    fun aiPrivacy() {
        composeRule.setSkimContent {
            AiPrivacyScreen(
                state = AiPrivacyUiState(
                    loaded = true,
                    askBeforeSending = true,
                    keepChatHistory = true,
                    conversationCount = 5,
                ),
                onBack = {},
                onAskBeforeSending = {},
                onKeepChatHistory = {},
                onSkimHistory = {},
                onDeleteAll = {},
                onDataPolicy = {},
            )
        }
        composeRule.waitForIdle()
        captureBoard("10-ai-privacy-settings")
        composeRule.assertA11y()
    }

    @Test
    fun searchEngineDialog() {
        showDialog {
            SearchEngineDialog(selected = SearchEngine.GOOGLE, onSelect = {}, onDismiss = {})
        }
        captureDialog("dialog-search-engine-15")
    }

    @Test
    fun themeDialog() {
        showDialog {
            ThemeDialog(selected = ThemeSetting.System, onSelect = {}, onDismiss = {})
        }
        captureDialog("dialog-theme-15")
    }

    @Test
    fun clearBrowsingDataDialog() {
        showDialog {
            ClearBrowsingDataDialog(onClear = { _, _, _ -> }, onDismiss = {})
        }
        captureDialog("dialog-clear-browsing-data-15")
    }

    @Test
    fun deleteAiDataDialog() {
        showDialog {
            DeleteAiDataDialog(onDelete = {}, onDismiss = {})
        }
        captureDialog("dialog-delete-ai-data-15")
    }

    private fun showDialog(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setSkimContent {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow))
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                content()
            }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureDialog(name: String) {
        captureScreenRoboImage("src/test/screenshots/components/$name.png")
        composeRule.assertA11y()
    }
}
