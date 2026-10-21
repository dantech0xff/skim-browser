package com.dantech.skimbrowser.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ConnectedToggleGroupTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selections = mutableListOf<Int>()

    @Test
    fun theSelectedOptionIsTheCheckedRadioButton() {
        setGroup(selected = 1)

        composeRule.onNodeWithText(INCOGNITO)
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithText(OPEN)
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertHasClickAction()
    }

    @Test
    fun tappingAnotherOptionSelectsItsIndex() {
        setGroup(selected = 0)

        composeRule.onNodeWithText(INCOGNITO).performClick()

        assertEquals(listOf(1), selections)
    }

    @Test
    fun tappingTheSelectedOptionChangesNothing() {
        setGroup(selected = 0)

        composeRule.onNodeWithText(OPEN).performClick()

        assertEquals(emptyList<Int>(), selections)
        composeRule.onNodeWithText(OPEN).assertIsOn()
    }

    @Test
    fun theSelectionFollowsTheCallersState() {
        composeRule.setSkimContent {
            var selected by remember { mutableIntStateOf(0) }
            ConnectedToggleGroup(
                options = listOf(OPEN, INCOGNITO),
                selected = selected,
                onSelect = { selected = it },
                contentDescription = GROUP,
            )
        }

        composeRule.onNodeWithText(INCOGNITO).performClick()

        composeRule.onNodeWithText(INCOGNITO).assertIsOn()
        composeRule.onNodeWithText(OPEN).assertIsOff()
    }

    @Test
    fun theGroupIsALabelledSelectableGroup() {
        setGroup(selected = 0)

        composeRule.onNodeWithContentDescription(GROUP)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
    }

    @Test
    fun aMiddleOptionIsSelectableToo() {
        composeRule.setSkimContent {
            ConnectedToggleGroup(
                options = listOf("A", "B", "C"),
                selected = 0,
                onSelect = { selections += it },
                contentDescription = GROUP,
            )
        }

        composeRule.onNodeWithText("B").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        composeRule.onNodeWithText("B").performClick()

        assertEquals(listOf(1), selections)
    }

    private fun setGroup(selected: Int) {
        composeRule.setSkimContent {
            ConnectedToggleGroup(
                options = listOf(OPEN, INCOGNITO),
                selected = selected,
                onSelect = { selections += it },
                contentDescription = GROUP,
            )
        }
    }

    private companion object {
        const val OPEN = "Open"
        const val INCOGNITO = "Incognito"
        const val GROUP = "Tab list"
    }
}
