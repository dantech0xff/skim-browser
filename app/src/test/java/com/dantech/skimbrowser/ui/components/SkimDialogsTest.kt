package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SkimDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    // Alert dialog

    @Test
    fun eachButtonCallsItsOwnAction() {
        setAlertDialog()

        composeRule.onNodeWithText(CONFIRM).performClick()
        composeRule.onNodeWithText(DISMISS).performClick()

        assertEquals(listOf("confirm", "dismiss"), calls)
    }

    @Test
    fun backAsksTheCallerToDismiss() {
        setAlertDialog()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(listOf("dismissRequest"), calls)
    }

    @Test
    fun aDisabledConfirmCannotBePressed() {
        setAlertDialog(confirmEnabled = false)

        composeRule.onNodeWithText(CONFIRM).assertIsNotEnabled().performClick()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun onlyADestructiveConfirmIsDrawnInError() {
        var error = Color.Unspecified
        var primary = Color.Unspecified
        composeRule.setSkimContent {
            error = MaterialTheme.colorScheme.error
            primary = MaterialTheme.colorScheme.primary
            SkimAlertDialog(
                title = TITLE,
                confirm = DialogAction(CONFIRM, onClick = {}),
                onDismissRequest = {},
                dismiss = DialogAction(DISMISS, onClick = {}),
                destructiveConfirm = true,
            )
        }

        val confirm = composeRule.onNodeWithText(CONFIRM).captureToImage()
        val dismiss = composeRule.onNodeWithText(DISMISS).captureToImage()
        assertTrue("Delete is drawn in error", confirm.hasPixel(error))
        assertFalse("Delete is not drawn in primary", confirm.hasPixel(primary))
        assertTrue("Cancel is drawn in primary", dismiss.hasPixel(primary))
        assertFalse("Cancel is not drawn in error", dismiss.hasPixel(error))
    }

    @Test
    fun aConfirmIsPrimaryUnlessItIsDestructive() {
        var error = Color.Unspecified
        var primary = Color.Unspecified
        composeRule.setSkimContent {
            error = MaterialTheme.colorScheme.error
            primary = MaterialTheme.colorScheme.primary
            SkimAlertDialog(title = TITLE, confirm = DialogAction(CONFIRM, onClick = {}), onDismissRequest = {})
        }

        val confirm = composeRule.onNodeWithText(CONFIRM).captureToImage()
        assertTrue(confirm.hasPixel(primary))
        assertFalse(confirm.hasPixel(error))
    }

    @Test
    fun theDialogIs344WideAndAnnouncedByItsTitle() {
        composeRule.setSkimContent {
            SkimAlertDialog(
                title = TITLE,
                confirm = DialogAction(CONFIRM, onClick = {}),
                onDismissRequest = {},
                modifier = Modifier.testTag(DIALOG),
                text = { Text(BODY) },
            )
        }

        composeRule.onNodeWithTag(DIALOG)
            .assertWidthIsEqualTo(344.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, TITLE))
    }

    // Choice dialog

    @Test
    fun theSelectedOptionIsTheCheckedRadioButton() {
        setChoiceDialog(selected = BING)

        composeRule.onNodeWithText(BING)
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        listOf(GOOGLE, DUCKDUCKGO).forEach { composeRule.onNodeWithText(it).assertIsNotSelected() }
    }

    @Test
    fun choosingAnOptionCallsOnSelectOnceWithIt() {
        setChoiceDialog(selected = GOOGLE)

        composeRule.onNodeWithText(DUCKDUCKGO).performClick()

        assertEquals(listOf("select $DUCKDUCKGO"), calls)
    }

    @Test
    fun choosingTheSelectedOptionStillCallsOnSelect() {
        setChoiceDialog(selected = GOOGLE)

        composeRule.onNodeWithText(GOOGLE).performClick()

        assertEquals(listOf("select $GOOGLE"), calls)
    }

    @Test
    fun cancelIsTheOnlyButton() {
        setChoiceDialog(selected = GOOGLE)

        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        composeRule.onAllNodes(button).assertCountEquals(1)
        composeRule.onNode(button and hasText(CANCEL)).performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun eachOptionRowIs48High() {
        setChoiceDialog(selected = GOOGLE)

        listOf(GOOGLE, BING, DUCKDUCKGO).forEach { composeRule.onNodeWithText(it).assertHeightIsEqualTo(48.dp) }
    }

    // Text field

    @Test
    fun typingFillsTheCallersState() {
        val state = TextFieldState()
        composeRule.setSkimContent { SkimTextField(state, LABEL) }

        composeRule.onNodeWithText(LABEL).performTextInput("Daylight Journal")

        assertEquals("Daylight Journal", state.text.toString())
    }

    @Test
    fun theFocusRequesterFocusesTheField() {
        composeRule.setSkimContent {
            val focus = remember { FocusRequester() }
            SkimTextField(remember { TextFieldState("Heat pump research") }, LABEL, focusRequester = focus)
            LaunchedEffect(Unit) { focus.requestFocus() }
        }

        composeRule.onNodeWithText(LABEL).assertIsFocused()
    }

    @Test
    fun aFieldLaysOutAsItsDrawnBox() {
        composeRule.setSkimContent {
            Column {
                SkimTextField(remember { TextFieldState() }, LABEL, Modifier.testTag(FIELD))
                Box(Modifier.size(1.dp).testTag(BELOW))
            }
        }

        // material3 keeps 8 dp above the border for the label: here the label reaches above the
        // field instead, so what follows the field starts right under its 56 dp box.
        val field = composeRule.onNodeWithTag(FIELD).getUnclippedBoundsInRoot()
        val below = composeRule.onNodeWithTag(BELOW).getUnclippedBoundsInRoot()
        assertEquals(56f, field.bottom.value, 0.5f)
        assertEquals(56f, below.top.value, 0.5f)
    }

    private fun setAlertDialog(confirmEnabled: Boolean = true) {
        composeRule.setSkimContent {
            SkimAlertDialog(
                title = TITLE,
                confirm = DialogAction(CONFIRM, onClick = { calls += "confirm" }, enabled = confirmEnabled),
                onDismissRequest = { calls += "dismissRequest" },
                dismiss = DialogAction(DISMISS, onClick = { calls += "dismiss" }),
                text = { Text(BODY) },
            )
        }
    }

    private fun setChoiceDialog(selected: String) {
        composeRule.setSkimContent {
            ChoiceDialog(
                title = ENGINE,
                options = persistentListOf(GOOGLE, BING, DUCKDUCKGO),
                selected = selected,
                label = { it },
                onSelect = { calls += "select $it" },
                onDismiss = { calls += "dismiss" },
            )
        }
    }

    private companion object {
        const val TITLE = "Delete all AI data?"
        const val BODY = "Your Skim history is deleted and every site asks again before Skim reads it."
        const val CONFIRM = "Delete"
        const val DISMISS = "Cancel"
        const val CANCEL = "Cancel"
        const val ENGINE = "Search engine"
        const val GOOGLE = "Google"
        const val BING = "Bing"
        const val DUCKDUCKGO = "DuckDuckGo"
        const val LABEL = "Group name"
        const val DIALOG = "dialog"
        const val FIELD = "field"
        const val BELOW = "below"
    }
}
