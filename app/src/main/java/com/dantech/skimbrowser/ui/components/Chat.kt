package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * A question the user asked (06, 06b), at the end of the line: bodyMedium in onSecondaryContainer on
 * secondaryContainer, at most 300 dp wide with 10 by 16 dp of padding, its corners 20 except the
 * bottom end, 4.
 */
@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .background(colors.secondaryContainer, BubbleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            color = colors.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Skim's answer to a question (06), as plain text: bodyMedium in onSurface, kept 24 dp from the end
 * so it reads apart from the questions. Its citation follows it 8 dp below.
 */
@Composable
fun AnswerText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(end = AnswerEndPadding),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The chat input at the bottom of 06, 06b and 06c: a 56 dp pill in surfaceContainerHigh with the
 * text in bodyLarge after 20 dp, the [placeholder] in onSurfaceVariant while it is empty, and Send
 * 8 dp from the end. Text input only: chat has no voice input.
 *
 * Send stays drawn enabled while the field is empty, as on 06, but only calls [onSend] when there is
 * text; the keyboard's Send action does the same. The caller reads and clears [state].
 *
 * While Skim writes ([generating], 06c), Stop replaces Send and calls [onStop]. Typing still works,
 * but the keyboard's Send action sends nothing until Send is back.
 *
 * When the input is off ([enabled] false, offline on 06c) the pill is onSurface at 10% (12% in dark)
 * behind 38% content, takes no input, and Send is disabled.
 */
@Composable
fun ChatInputPill(
    state: TextFieldState,
    placeholder: String,
    enabled: Boolean,
    generating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val textColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = DisabledContentAlpha)
    val send = { if (enabled && !generating && state.text.isNotBlank()) onSend() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PillHeight)
            .clip(CircleShape)
            .background(if (enabled) colors.surfaceContainerHigh else disabledContainerColor())
            // Send lays out 48 dp around its 40 dp circle: 4 dp more gives the drawn 8 dp.
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = state,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            textStyle = typography.bodyLarge.copy(color = textColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            onKeyboardAction = { send() },
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(colors.primary),
            decorator = { field ->
                // The field is 48 dp high, as drawn; the placeholder is read as its label.
                Box(Modifier.heightIn(min = FieldHeight), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = if (enabled) colors.onSurfaceVariant else textColor,
                            style = typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
            },
        )
        if (generating) {
            StopButton(onClick = onStop)
        } else {
            SendButton(enabled = enabled, onClick = send)
        }
    }
}

private val BubbleMaxWidth = 300.dp
private val BubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp)
private val AnswerEndPadding = 24.dp
private val PillHeight = 56.dp
private val FieldHeight = 48.dp

@PreviewLightDark
@Composable
private fun ChatPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UserBubble("How much would I save per year?")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnswerText("The article puts it at \$300 to \$900 a year for a home switching from oil heating.")
                CitationChip(paragraph = 6, enabled = true, onClick = {})
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ChatInputPillPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val placeholder = stringResource(R.string.ai_ask_about_page)
            ChatInputPill(rememberTextFieldState(), placeholder, enabled = true, generating = false, onSend = {}, onStop = {})
            ChatInputPill(rememberTextFieldState(), placeholder, enabled = true, generating = true, onSend = {}, onStop = {})
            ChatInputPill(rememberTextFieldState(), placeholder, enabled = false, generating = false, onSend = {}, onStop = {})
        }
    }
}
