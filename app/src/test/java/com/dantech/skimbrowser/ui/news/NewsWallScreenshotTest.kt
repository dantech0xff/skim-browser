package com.dantech.skimbrowser.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.news.NewsTopic
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.text.UiText
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The news wall under the New tab page. Board 04 does not draw it. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class NewsWallScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun light() = capture(dark = false, "news-wall-light")

    @Test
    fun dark() = capture(dark = true, "news-wall-dark")

    private fun capture(dark: Boolean, name: String) {
        val lead = NewsCardUi("https://example.com/lead", "Harbor plan moves ahead", "bbc", "BBC", UiText.Raw("2 h ago"), null)
        val next = NewsCardUi("https://example.com/next", "Chip supply eases", "guardian", "The Guardian", UiText.Raw("Yesterday"), null)
        composeRule.setSkimContent(dark = dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    newsWall(
                        NewsWallUiState(
                            ready = true,
                            enabled = true,
                            topic = NewsTopic.TOP,
                            articles = persistentListOf(lead, next),
                        ),
                        NewsActions({}, {}, {}, {}, {}, { _, _ -> }, {}, {}),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("$GOLDENS/$name.png")
        composeRule.assertA11y()
    }

    private companion object {
        const val GOLDENS = "src/test/screenshots/components"
    }
}
