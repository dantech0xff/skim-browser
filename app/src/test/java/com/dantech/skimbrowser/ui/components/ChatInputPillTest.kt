package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ChatInputPillTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var sends = 0
    private var stops = 0
    private var generating by mutableStateOf(false)

    @Test
    fun typingFillsTheCallersState() {
        val state = TextFieldState()
        setPill(state)

        field().performTextInput(QUESTION)

        assertEquals(QUESTION, state.text.toString())
    }

    @Test
    fun theEmptyFieldIsNamedByItsPlaceholder() {
        setPill(TextFieldState())

        field().assert(hasText(PLACEHOLDER))
    }

    @Test
    fun sendLooksReadyButIgnoresAnEmptyField() {
        setPill(TextFieldState())

        composeRule.onNodeWithContentDescription(SEND).assertIsEnabled().performClick()

        assertEquals(0, sends)
    }

    @Test
    fun sendIgnoresBlankText() {
        setPill(TextFieldState("   "))

        composeRule.onNodeWithContentDescription(SEND).performClick()

        assertEquals(0, sends)
    }

    @Test
    fun sendSendsTypedText() {
        val state = TextFieldState()
        setPill(state)
        field().performTextInput(QUESTION)

        composeRule.onNodeWithContentDescription(SEND).performClick()

        assertEquals(1, sends)
    }

    @Test
    fun theKeyboardSendActionSendsToo() {
        setPill(TextFieldState(QUESTION))

        field().performImeAction()

        assertEquals(1, sends)
    }

    @Test
    fun anInputThatIsOffTakesNoTextAndSendsNothing() {
        val state = TextFieldState(QUESTION)
        setPill(state, enabled = false)

        field()
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.InsertTextAtCursor))
        assertThrows(AssertionError::class.java) { field().performTextInput("more") }
        composeRule.onNodeWithContentDescription(SEND).assertIsNotEnabled().performClick()

        assertEquals(QUESTION, state.text.toString())
        assertEquals(0, sends)
    }

    @Test
    fun whileSkimWritesStopReplacesSend() {
        generating = true
        val state = TextFieldState()
        setPill(state)

        composeRule.onNodeWithContentDescription(SEND).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(STOP).performClick()
        // The next question can be typed meanwhile.
        field().performTextInput(QUESTION)

        assertEquals(1, stops)
        assertEquals(0, sends)
        assertEquals(QUESTION, state.text.toString())
    }

    @Test
    fun theKeyboardSendActionWaitsWhileSkimWrites() {
        generating = true
        setPill(TextFieldState(QUESTION))

        field().performImeAction()

        assertEquals(0, sends)
    }

    @Test
    fun sendComesBackWhenSkimStopsWriting() {
        generating = true
        setPill(TextFieldState(QUESTION))

        generating = false
        composeRule.onNodeWithContentDescription(SEND).performClick()

        assertEquals(1, sends)
        composeRule.onNodeWithContentDescription(STOP).assertDoesNotExist()
    }

    private fun setPill(state: TextFieldState, enabled: Boolean = true) {
        composeRule.setSkimContent {
            ChatInputPill(
                state = state,
                placeholder = PLACEHOLDER,
                enabled = enabled,
                generating = generating,
                onSend = { sends++ },
                onStop = { stops++ },
            )
        }
    }

    private fun field() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))

    private companion object {
        const val PLACEHOLDER = "Ask about this page"
        const val QUESTION = "How much would I save per year?"
        const val SEND = "Send"
        const val STOP = "Stop"
    }
}
