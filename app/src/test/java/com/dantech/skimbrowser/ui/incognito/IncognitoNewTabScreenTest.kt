package com.dantech.skimbrowser.ui.incognito

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the incognito New tab page (09c) lets the user do: search, speak, and nothing else. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class IncognitoNewTabScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    @Test
    fun theSearchBarOpensSearchAndTheMicStartsVoiceSearch() {
        show(voiceAvailable = true)

        composeRule.onNodeWithText("Search or type URL").performClick()
        composeRule.onNodeWithContentDescription("Voice search").performClick()

        assertEquals(listOf("search", "voice"), calls)
    }

    @Test
    fun withoutAnAppForSpeechInputThereIsNoMic() {
        show(voiceAvailable = false)

        composeRule.onAllNodesWithContentDescription("Voice search").assertCountEquals(0)
        composeRule.onNodeWithText("Search or type URL").assertExists()
    }

    @Test
    fun onlyTheSearchBarAndItsMicCanBeTapped() {
        show(voiceAvailable = true)

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertCountEquals(2)
        composeRule.onNodeWithText("You're incognito").assertExists()
        composeRule.onNodeWithText("What you type into forms").assertExists()
        composeRule.onNodeWithText("Your internet provider").assertExists()
    }

    private fun show(voiceAvailable: Boolean) {
        composeRule.setSkimContent(incognito = true) {
            IncognitoNewTabScreen(
                voiceAvailable = voiceAvailable,
                onSearch = { calls += "search" },
                onVoice = { calls += "voice" },
                toolbar = {},
            )
        }
        composeRule.waitForIdle()
    }
}
