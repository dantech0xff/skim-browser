package com.dantech.skimbrowser.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
class CitationChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    @Test
    fun anEnabledCitationIsAButtonNamingItsParagraph() {
        setChip(enabled = true)

        composeRule.onNodeWithText(LABEL)
            .assertHasClickAction()
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun aDisabledCitationHasNoClickAction() {
        setChip(enabled = false)

        composeRule.onNodeWithText(LABEL)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performTouchInput { click() }

        assertEquals(0, clicks)
    }

    private fun setChip(enabled: Boolean) {
        composeRule.setSkimContent {
            CitationChip(paragraph = 6, enabled = enabled, onClick = { clicks++ })
        }
    }

    private companion object {
        const val LABEL = "From paragraph 6"
    }
}
