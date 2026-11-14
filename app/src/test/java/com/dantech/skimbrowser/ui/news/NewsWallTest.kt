package com.dantech.skimbrowser.ui.news

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.news.NewsTopic
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class NewsWallTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aTopicAStoryAndALongPressDoWhatTheySay() {
        val calls = mutableListOf<String>()
        val card = NewsCardUi("https://example.com/a", "Harbor plan", "bbc", "BBC", UiText.Raw("2 h ago"), null)
        composeRule.setSkimContent {
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
                        onTopic = { calls += "topic:$it" },
                        onOpen = { calls += "open:${it.link}" },
                        onOpenInNewTab = { calls += "new:${it.link}" },
                        onShare = { calls += "share:${it.link}" },
                        onHideSource = { calls += "hide:${it.sourceId}" },
                        onToggleSource = { _, _ -> },
                        onAddFeed = {},
                        onDismissFeedError = {},
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Tech").performClick()
        composeRule.onNodeWithText("Harbor plan").performClick()
        composeRule.onNodeWithText("Harbor plan").performTouchInput { longClick() }
        composeRule.onNodeWithText("Open in new tab").performClick()

        assertEquals(listOf("topic:TECH", "open:https://example.com/a", "new:https://example.com/a"), calls)
    }
}
