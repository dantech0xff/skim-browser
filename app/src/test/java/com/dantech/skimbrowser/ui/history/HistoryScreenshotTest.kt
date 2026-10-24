package com.dantech.skimbrowser.ui.history

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.history.HistoryEntry
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.text.DateBucket
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** History (13), with the visits drawn on the board. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class HistoryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun history() {
        composeRule.setSkimContent {
            HistoryScreen(
                state = HistoryUiState(
                    loaded = true,
                    sections = persistentListOf(
                        HistorySection(DateBucket.Today, Today.mapIndexed { index, row -> entry(index + 1L, row) }.toImmutableList()),
                        HistorySection(
                            DateBucket.Yesterday,
                            Yesterday.mapIndexed { index, row -> entry(index + 10L, row) }.toImmutableList(),
                        ),
                    ),
                ),
                searchState = null,
                onBack = {},
                onSearch = {},
                onCloseSearch = {},
                onClearData = {},
                onOpen = {},
                onRemove = {},
            )
        }
        composeRule.waitForIdle()
        captureBoard("13-history")
        composeRule.assertA11y()
    }

    private fun entry(id: Long, row: Triple<String, String, String>) = HistoryEntry(
        id = id,
        url = "https://${row.third}/",
        title = row.second,
        host = row.third,
        visitedAt = 1,
    )

    private companion object {
        val Today = listOf(
            Triple("D", "Why heat pumps are winning in cold climates", "daylightjournal.com"),
            Triple("H", "Heat pump sizing calculator", "heatcalc.example"),
            Triple("R", "Installer reviews near me", "localreviews.example"),
            Triple("E", "Heat pump rebates 2026", "energysaver.example"),
        )
        val Yesterday = listOf(
            Triple("T", "Osaka night buses: a complete guide", "tabi-note.jp"),
            Triple("N", "Namba hotels, 2 nights", "staysearch.example"),
            Triple("K", "Kotlin 2.2 release notes", "kotlinlang.org"),
        )
    }
}
