package com.dantech.skimbrowser.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class LargeTitleScaffoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var backs = 0
    private var closes = 0
    private var searches = 0
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Test
    fun theTitleIsAHeadingUnderBackAndTheActions() {
        setScaffold(search = null)

        composeRule.onNodeWithText(TITLE).assert(isHeading())
        composeRule.onNodeWithText(ITEM).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SEARCH_ACTION).performClick()
        composeRule.onNodeWithContentDescription(BACK).performClick()

        assertEquals(1, searches)
        assertEquals(1, backs)
    }

    @Test
    fun aSearchFieldReplacesTheTitleAndTakesFocus() {
        setScaffold(search = TextFieldState())

        composeRule.onNodeWithText(TITLE).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(SEARCH_ACTION).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(BACK).assertDoesNotExist()
        composeRule.onNode(hasSetTextAction())
            .assertIsFocused()
            .assert(hasText(PLACEHOLDER))
        composeRule.onNodeWithContentDescription(CLEAR_TEXT).assertDoesNotExist()
        composeRule.onNodeWithText(ITEM).assertIsDisplayed()
    }

    @Test
    fun typingFillsTheCallersStateAndOffersClearText() {
        val state = TextFieldState()
        setScaffold(search = state)

        composeRule.onNode(hasSetTextAction()).performTextInput("heat")

        assertEquals("heat", state.text.toString())
        composeRule.onNodeWithContentDescription(CLEAR_TEXT).assertIsDisplayed()
    }

    @Test
    fun clearTextEmptiesTheFieldAndKeepsTheKeyboard() {
        val state = TextFieldState("heat pump")
        setScaffold(search = state)

        composeRule.onNodeWithContentDescription(CLEAR_TEXT).performClick()

        assertEquals("", state.text.toString())
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.onNodeWithContentDescription(CLEAR_TEXT).assertDoesNotExist()
    }

    @Test
    fun closeSearchAndSystemBackLeaveTheSearchNotTheScreen() {
        setScaffold(search = TextFieldState("heat"))

        composeRule.onNodeWithContentDescription(CLOSE_SEARCH).performClick()
        composeRule.runOnIdle { backDispatcher.onBackPressed() }

        assertEquals(2, closes)
        assertEquals(0, backs)
    }

    private fun setScaffold(search: TextFieldState?) {
        composeRule.setSkimContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            LargeTitleScaffold(
                title = TITLE,
                onBack = { backs++ },
                actions = {
                    IconButton(onClick = { searches++ }) {
                        Icon(painterResource(R.drawable.ic_search), SEARCH_ACTION)
                    }
                },
                searchState = search,
                searchPlaceholder = PLACEHOLDER,
                onCloseSearch = { closes++ },
            ) {
                item { Text(ITEM) }
            }
        }
    }

    private companion object {
        const val TITLE = "History"
        const val ITEM = "Why heat pumps are winning in cold climates"
        const val BACK = "Back"
        const val SEARCH_ACTION = "Search history"
        const val PLACEHOLDER = "Search history"
        const val CLOSE_SEARCH = "Close search"
        const val CLEAR_TEXT = "Clear text"
    }
}
