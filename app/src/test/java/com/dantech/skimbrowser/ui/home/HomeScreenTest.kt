package com.dantech.skimbrowser.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What each control of Home (04) asks for, and what the first Home leaves out. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val recorder = HomeFixtures.Recorder()

    // The search bar

    @Test
    fun theSearchBarOpensSearchAndTheMicStartsVoiceSearch() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("Search or ask anything").performClick()
        composeRule.onNodeWithContentDescription("Voice search").performClick()

        assertEquals(listOf("search", "voice"), recorder.calls)
    }

    @Test
    fun withoutAnAppForSpeechInputThereIsNoMic() {
        setHome(HomeFixtures.Board.copy(voiceAvailable = false))

        composeRule.onAllNodesWithContentDescription("Voice search").assertCountEquals(0)
        composeRule.onNodeWithText("Search or ask anything").assertExists()
    }

    @Test
    fun theSunnyMarkReadsAsSkim() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithContentDescription("Skim").assertExists()
    }

    // Summarize my N open tabs

    @Test
    fun theHeroCardAsksSkimToSummarizeTheTabsWithItsTitle() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("Summarize my 7 open tabs").performClick()

        assertEquals(listOf("summarize:Summarize my 7 open tabs"), recorder.calls)
    }

    // Shortcuts

    @Test
    fun aTapOpensTheShortcut() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("GitHub").performClick()

        assertEquals(listOf("open:GitHub"), recorder.calls)
    }

    @Test
    fun aLongPressOpensTheShortcutsMenuUnderItsTileAndNothingElse() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("Wikipedia").performTouchInput { longClick() }

        composeRule.onNodeWithText("Edit shortcut").assertExists()
        composeRule.onNodeWithText("Remove").assertExists()
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Wikipedia shortcut")).assertExists()
        assertTrue("a long press opens nothing", recorder.calls.isEmpty())
    }

    @Test
    fun theTileTurnsSecondaryContainerWhileItsMenuIsOpen() {
        var highlight = Color.Unspecified
        var tile = Color.Unspecified
        composeRule.setSkimContent {
            highlight = MaterialTheme.colorScheme.secondaryContainer
            tile = MaterialTheme.colorScheme.surfaceContainerHighest
            HomeScreen(HomeFixtures.Board, recorder.actions, toolbar = {})
        }
        assertTrue(hasColor("Wikipedia", tile))

        composeRule.onNodeWithText("Wikipedia").performTouchInput { longClick() }

        assertTrue(hasColor("Wikipedia", highlight))
        assertTrue("the others keep theirs", !hasColor("GitHub", highlight))
    }

    @Test
    fun editShortcutAsksToEditThatShortcutAndClosesTheMenu() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("YouTube").performTouchInput { longClick() }
        composeRule.onNodeWithText("Edit shortcut").performClick()

        assertEquals(listOf("edit:3"), recorder.calls)
        composeRule.onAllNodesWithText("Edit shortcut").assertCountEquals(0)
    }

    @Test
    fun removeAsksToRemoveThatShortcutAndClosesTheMenu() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("Hacker News").performTouchInput { longClick() }
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(listOf("remove:4"), recorder.calls)
        composeRule.onAllNodesWithText("Remove").assertCountEquals(0)
    }

    @Test
    fun addOpensTheAddShortcutDialog() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("Add").performClick()

        assertEquals(listOf("add"), recorder.calls)
    }

    // Continue with Skim

    @Test
    fun aContinueCardOpensItsChat() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("Why heat pumps are winning in cold climates").performClick()

        assertEquals(listOf("chat:11"), recorder.calls)
    }

    @Test
    fun eachContinueCardOpensItsOwnChat() {
        setHome(HomeFixtures.Board)

        // The second card sits under the floating toolbar until Home is scrolled to its end.
        composeRule.onNodeWithText("Osaka night buses: a complete guide").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf("chat:12"), recorder.calls)
    }

    @Test
    fun seeAllOpensSkimHistory() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("See all").performScrollTo().performClick()

        assertEquals(listOf("see all"), recorder.calls)
    }

    @Test
    fun theCardsShowWhenEachChatLastChanged() {
        setHome(HomeFixtures.Board)

        composeRule.onNodeWithText("2 h ago").assertExists()
        composeRule.onNodeWithText("Yesterday").assertExists()
    }

    // The first Home

    @Test
    fun theFirstHomeHasNoHeroCardNoContinueWithSkimAndOnlyTheAddTile() {
        setHome(HomeFixtures.FirstHome)

        composeRule.onAllNodesWithText("Summarize my", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Continue with Skim").assertCountEquals(0)
        composeRule.onAllNodesWithText("See all").assertCountEquals(0)
        composeRule.onNodeWithText("Shortcuts").assertExists()
        composeRule.onNodeWithText("Add").assertExists()
        HomeFixtures.Shortcuts.forEach { composeRule.onAllNodesWithText(it.name).assertCountEquals(0) }
    }

    @Test
    fun withOneTabToSummarizeTheHeroCardShows() {
        setHome(HomeFixtures.FirstHome.copy(eligibleTabCount = 1))

        composeRule.onNodeWithText("Summarize my 1 open tab").assertExists()
        composeRule.onAllNodesWithText("Continue with Skim").assertCountEquals(0)
    }

    private fun setHome(state: HomeUiState) {
        composeRule.setSkimContent {
            HomeScreen(state, recorder.actions, toolbar = { HomeFixtures.Toolbar(HomeFixtures.newTabPage(), tabCount = 1) })
        }
        composeRule.waitForIdle()
    }

    /** Whether the cell named [name] draws [color] (its tile). */
    private fun hasColor(name: String, color: Color): Boolean {
        val image = composeRule.onNodeWithText(name).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return pixels.any { it == color.toArgb() }
    }
}
