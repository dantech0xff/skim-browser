package com.dantech.skimbrowser.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.news.NewsTopic
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.ProvideHaptics
import com.dantech.skimbrowser.testing.RecordingHapticFeedback
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.home.HomeFixtures
import com.dantech.skimbrowser.ui.home.HomeScreen
import com.dantech.skimbrowser.ui.news.NewsActions
import com.dantech.skimbrowser.ui.news.NewsCardUi
import com.dantech.skimbrowser.ui.news.NewsWallUiState
import com.dantech.skimbrowser.ui.news.newsWall
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A long press that opens a menu asks for exactly one long-press haptic. `combinedClickable` asks
 * for it by default, so these tests guard that no caller turns it off or asks a second time.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class LongPressHapticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val haptics = RecordingHapticFeedback()

    @Test
    fun aLongPressOnAShortcutAsksForOneLongPressHaptic() {
        val recorder = HomeFixtures.Recorder()
        composeRule.setSkimContent {
            ProvideHaptics(haptics) { HomeScreen(HomeFixtures.Board, recorder.actions, toolbar = {}) }
        }

        composeRule.onNodeWithText("Wikipedia").performTouchInput { longClick() }

        composeRule.onNodeWithText("Edit shortcut").assertExists()
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun aLongPressOnANewsCardAsksForOneLongPressHaptic() {
        val card = NewsCardUi("https://example.com/a", "Harbor plan", "bbc", "BBC", UiText.Raw("2 h ago"), null)
        composeRule.setSkimContent {
            ProvideHaptics(haptics) {
                LazyColumn(Modifier.fillMaxSize()) {
                    newsWall(
                        NewsWallUiState(
                            ready = true,
                            enabled = true,
                            topic = NewsTopic.TOP,
                            articles = persistentListOf(card),
                            sources = persistentListOf(),
                        ),
                        NewsActions(
                            onTopic = {},
                            onOpen = {},
                            onOpenInNewTab = {},
                            onShare = {},
                            onHideSource = {},
                            onToggleSource = { _, _ -> },
                            onAddFeed = {},
                            onDismissFeedError = {},
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Harbor plan").performTouchInput { longClick() }

        composeRule.onNodeWithText("Open in new tab").assertExists()
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun aLongPressOnAListRowAsksForOneLongPressHapticAndATapForNone() {
        var longClicks = 0
        composeRule.setSkimContent {
            ProvideHaptics(haptics) {
                SkimListItem(
                    headline = "Heat pump sizing calculator",
                    index = 0,
                    count = 1,
                    onClick = {},
                    onLongClick = { longClicks++ },
                    modifier = Modifier.testTag(ROW),
                )
            }
        }

        composeRule.onNodeWithTag(ROW).performClick()
        assertTrue("a tap asks for nothing", haptics.performed.isEmpty())

        composeRule.onNodeWithTag(ROW).performTouchInput { longClick() }

        assertEquals(1, longClicks)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    private companion object {
        const val ROW = "row"
    }
}
