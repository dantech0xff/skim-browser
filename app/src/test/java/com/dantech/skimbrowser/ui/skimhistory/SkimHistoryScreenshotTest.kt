package com.dantech.skimbrowser.ui.skimhistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.home.ShortcutDialogProperties
import com.dantech.skimbrowser.ui.text.DateBucket
import com.dantech.skimbrowser.ui.text.UiText
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Skim history (04c) and the dialog that clears it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SkimHistoryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skimHistory() {
        composeRule.setSkimContent {
            SkimHistoryScreen(
                state = SkimHistoryUiState(loaded = true, sections = board()),
                searchState = null,
                onBack = {},
                onSearch = {},
                onCloseSearch = {},
                onDeleteAll = {},
                onOpen = {},
                onRemove = {},
            )
        }
        composeRule.waitForIdle()
        captureBoard("04c-skim-history")
        composeRule.assertA11y()
    }

    @Test
    fun empty() {
        composeRule.setSkimContent {
            SkimHistoryScreen(
                state = SkimHistoryUiState(loaded = true),
                searchState = null,
                onBack = {},
                onSearch = {},
                onCloseSearch = {},
                onDeleteAll = {},
                onOpen = {},
                onRemove = {},
            )
        }
        composeRule.waitForIdle()
        captureExtra("04c-skim-history--empty")
        composeRule.assertA11y()
    }

    @Test
    fun clearDialog() {
        composeRule.setSkimContent {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow))
            Dialog(onDismissRequest = {}, properties = ShortcutDialogProperties) {
                ClearSkimHistoryDialog(onClear = {}, onDismiss = {})
            }
        }
        composeRule.waitForIdle()
        captureExtra("04c-skim-history--clear")
        composeRule.assertA11y()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureExtra(name: String) {
        captureScreenRoboImage("src/test/screenshots/boards/$name.png")
    }

    private fun board() = listOf(
        SkimHistorySection(
            DateBucket.Today,
            listOf(
                row(
                    1,
                    ChatKind.PAGE,
                    "Why heat pumps are winning in cold climates",
                    UiText.Plural(R.plurals.skim_history_questions_about, 1, listOf(1, "daylightjournal.com")),
                    "D",
                ),
                row(
                    2,
                    ChatKind.TABS,
                    "Summarize my 7 open tabs",
                    UiText.Plural(R.plurals.skim_history_answers_across_tabs, 1, listOf(1, 7)),
                    "S",
                ),
            ),
        ),
        SkimHistorySection(
            DateBucket.Yesterday,
            listOf(
                row(
                    3,
                    ChatKind.PAGE,
                    "Osaka night buses: a complete guide",
                    UiText.Res(R.string.skim_history_summary_of, listOf("tabi-note.jp")),
                    "T",
                ),
                row(
                    4,
                    ChatKind.PAGE,
                    "Kotlin 2.2 release notes",
                    UiText.Plural(R.plurals.skim_history_questions_about, 3, listOf(3, "kotlinlang.org")),
                    "K",
                ),
            ),
        ),
        SkimHistorySection(
            DateBucket.EarlierThisWeek,
            listOf(
                row(
                    5,
                    ChatKind.PAGE,
                    "Heat pump rebates 2026",
                    UiText.Res(R.string.skim_history_summary_of, listOf("energysaver.example")),
                    "E",
                ),
            ),
        ),
    )

    private fun row(id: Long, kind: ChatKind, title: String, subtitle: UiText, letter: String) = SkimHistoryRow(
        chatId = id,
        kind = kind,
        title = title,
        subtitle = subtitle,
        letter = letter,
    )
}
