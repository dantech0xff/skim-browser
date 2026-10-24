package com.dantech.skimbrowser.ui.search

import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The search layer (04b) over a real search view model and an in-memory database; the engine is
 * Bing and suggests what each test gives it. The view model rests 150 ms on the main thread's clock
 * before a lookup, which the tests let pass themselves.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SearchOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rig = SearchRig()
    private val opened = mutableListOf<String>()
    private val asked = mutableListOf<String>()
    private var closes = 0
    private var browserBacks = 0
    private var shown by mutableStateOf(true)

    @After
    fun tearDown() = rig.close()

    // Typing

    @Test
    fun typingShowsAskSkimTheEnginesSuggestionsAndMatchingPages() {
        rig.seedHistory()
        rig.suggested["heat pump"] = SearchRig.HeatPumpSuggestions
        show(incognito = false)

        type("heat pump")

        composeRule.onNodeWithText("Ask Skim").assertExists()
        listOf("heat pump vs furnace", "heat pump cost", "heat pump rebates 2026").forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onAllNodesWithContentDescription("Edit search", substring = true).assertCountEquals(3)
        awaitText("Why heat pumps are winning in cold climates")
        composeRule.onNodeWithText("daylightjournal.com").assertExists()
        composeRule.onAllNodesWithText("Osaka night buses: a complete guide").assertCountEquals(0)
    }

    @Test
    fun inIncognitoThereIsNoAskSkimRowAndNoPageRow() {
        rig.seedHistory()
        rig.suggested["heat pump"] = SearchRig.HeatPumpSuggestions
        show(incognito = true)

        type("heat pump")

        composeRule.onNodeWithText("heat pump cost").assertExists()
        composeRule.onAllNodesWithText("Ask Skim").assertCountEquals(0)
        composeRule.onAllNodesWithText("Why heat pumps are winning in cold climates").assertCountEquals(0)
    }

    @Test
    fun beforeAnythingIsTypedThereAreNoRows() {
        show(incognito = false)

        composeRule.onNodeWithText("Search or ask anything").assertExists()
        composeRule.onAllNodesWithContentDescription("Suggestions").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Clear text").assertCountEquals(0)
    }

    @Test
    fun inIncognitoTheEmptyFieldAsksForASearchOrAnAddress() {
        show(incognito = true)

        composeRule.onNodeWithText("Search or type URL").assertExists()
        composeRule.onAllNodesWithText("Search or ask anything").assertCountEquals(0)
        field().assert(hasContentDescription("Search or type URL"))
    }

    @Test
    fun theFieldIsFocusedWhenTheLayerOpens() {
        show(incognito = false)

        field().assertIsFocused()
    }

    @Test
    fun openedWithAnAddressTheFieldStartsWithAllOfItSelected() {
        val address = "https://daylightjournal.com/energy/heat-pumps-cold"
        show(incognito = false, initialText = address, selectAll = true)

        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, address.length)))
    }

    // Enter

    @Test
    fun enterOpensAWebAddress() {
        show(incognito = false)

        type("example.com")
        field().performImeAction()

        assertEquals(listOf("https://example.com"), opened)
    }

    @Test
    fun enterSearchesOtherTextWithTheChosenEngine() {
        show(incognito = false)

        type("heat pump")
        field().performImeAction()

        assertEquals(listOf("https://www.bing.com/search?q=heat+pump"), opened)
    }

    @Test
    fun enterWithNoTextOpensNothing() {
        show(incognito = false)

        field().performImeAction()

        assertTrue(opened.isEmpty())
    }

    // The rows

    @Test
    fun aSuggestionOpensTheEnginesResultsForItEvenWhenItLooksLikeAnAddress() {
        rig.suggested["exa"] = listOf("example.com", "exam results")
        show(incognito = false)

        type("exa")
        composeRule.onNodeWithText("example.com").performClick()

        assertEquals(listOf("https://www.bing.com/search?q=example.com"), opened)
    }

    @Test
    fun theArrowCopiesTheSuggestionIntoTheFieldAndOpensNothing() {
        rig.suggested["heat pump"] = SearchRig.HeatPumpSuggestions
        show(incognito = false)

        type("heat pump")
        composeRule.onNodeWithContentDescription("Edit search: heat pump cost").performClick()

        field().assert(hasText("heat pump cost"))
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange("heat pump cost".length)))
        assertTrue(opened.isEmpty())
        assertEquals(0, closes)
    }

    @Test
    fun theAskSkimRowAsksWithTheTextWithoutItsOuterSpaces() {
        show(incognito = false)

        type("  heat pump ")
        composeRule.onNodeWithText("Ask Skim").performClick()

        assertEquals(listOf("heat pump"), asked)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun aPageRowOpensItsPage() {
        rig.seedHistory()
        show(incognito = false)

        type("heat pump")
        awaitText("Why heat pumps are winning in cold climates")
        composeRule.onNodeWithText("Why heat pumps are winning in cold climates").performClick()

        assertEquals(listOf(SearchRig.DAYLIGHT), opened)
    }

    // Clear text, Close search, Back

    @Test
    fun clearTextEmptiesTheFieldAndKeepsTheLayerOpen() {
        rig.suggested["heat pump"] = SearchRig.HeatPumpSuggestions
        show(incognito = false)
        type("heat pump")

        composeRule.onNodeWithContentDescription("Clear text").performClick()

        assertEquals("", rig.search.query.text.toString())
        field().assertIsFocused()
        composeRule.onNodeWithContentDescription("Close search").assertExists()
        composeRule.onAllNodesWithText("heat pump cost").assertCountEquals(0)
        assertEquals(0, closes)
    }

    @Test
    fun closeSearchClosesTheLayer() {
        show(incognito = false)

        composeRule.onNodeWithContentDescription("Close search").performClick()

        assertEquals(1, closes)
        composeRule.onAllNodesWithContentDescription("Close search").assertCountEquals(0)
    }

    @Test
    fun backClosesTheLayerBeforeTheScreenUnderItHearsOfIt() {
        show(incognito = false, browserBackHandler = true)

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(1, closes)
        assertEquals("the browser's Back handler", 0, browserBacks)
    }

    // The window

    @Test
    fun inIncognitoTheLayersWindowIsSecureEvenOverAWindowThatIsNot() {
        show(incognito = true)

        assertTrue(isSecure(latestDialog().window!!.attributes))
    }

    @Test
    fun inANormalTabTheLayersWindowFollowsTheWindowUnderIt() {
        show(incognito = false)

        assertFalse(isSecure(latestDialog().window!!.attributes))
    }

    @Test
    fun inIncognitoTheKeyboardIsAskedNotToLearnWhatIsTyped() {
        show(incognito = true)

        val info = checkNotNull(focusedFieldEditorInfo()) { "The field did not take text input" }
        assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
    }

    @Test
    fun inANormalTabTheKeyboardMayLearn() {
        show(incognito = false)

        val info = checkNotNull(focusedFieldEditorInfo()) { "The field did not take text input" }
        assertEquals(0, info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
    }

    // Helpers

    /** Opens the layer as a caller does: the view model first, in the same event. */
    private fun show(incognito: Boolean, initialText: String = "", selectAll: Boolean = false, browserBackHandler: Boolean = false) {
        rig.search.open(initialText, selectAll, incognito)
        composeRule.setSkimContent(incognito = incognito) {
            if (browserBackHandler) BackHandler { browserBacks++ }
            if (shown) {
                SearchOverlay(
                    search = rig.search,
                    incognito = incognito,
                    onOpenUrl = { opened += it },
                    onAskSkim = { asked += it },
                    onClose = {
                        closes++
                        shown = false
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun field() = composeRule.onNode(hasSetTextAction() and hasContentDescription("Search", substring = true))

    /** Types [text] into the field, then lets the view model's rest pass. */
    private fun type(text: String) {
        field().performTextInput(text)
        composeRule.pass(150)
    }

    /** Waits for [text] to show: rows of history and bookmarks come back from the database's own threads. */
    private fun awaitText(text: String) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun isSecure(attrs: WindowManager.LayoutParams) = attrs.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}
