package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A button of a dialog: its [label], what pressing it does, and whether it can be pressed now. */
data class DialogAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

/**
 * A dialog as drawn on 06d and 15: 344 dp wide, radius 28, surfaceContainerHigh, 24 dp of padding,
 * the title in headlineSmall, then [text] in bodyMedium and onSurfaceVariant, then text buttons on
 * the right, [dismiss] before [confirm], 8 dp apart. With an [icon] above it the title is centered.
 * The screen behind dims to the 32% scrim.
 *
 * Only the confirm button of a destructive action that cannot be undone is drawn in error (Delete
 * all AI data): pass [destructiveConfirm]. Clear on Clear browsing data and Clear Skim history stays
 * primary, as drawn.
 *
 * Buttons keep a 48 dp touch target around their 40 dp shape; the spacing around them leaves 24 dp
 * between the drawn buttons, the text and the bottom edge.
 *
 * @param onDismissRequest Back or a tap outside the dialog; a [dismiss] button has its own action.
 * @param icon drawn above the title in [iconTint]: `ic_delete` in error (Delete all AI data),
 *   `ic_location_on` in primary (site permission), or [SunnyMark] at 56 dp (06d).
 * @param text the content under the title: the body text, or the fields and choices of the dialog.
 *   Paragraphs of a body sit 12 dp apart (06d); fields use [SkimTextField]. It scrolls when the
 *   screen is too short for it.
 */
@Composable
fun SkimAlertDialog(
    title: String,
    confirm: DialogAction,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismiss: DialogAction? = null,
    destructiveConfirm: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
    text: (@Composable () -> Unit)? = null,
) {
    DialogFrame(
        title = title,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = icon,
        iconTint = iconTint,
        body = alertBody(text),
    ) {
        if (dismiss != null) DialogButton(dismiss)
        DialogButton(confirm, destructive = destructiveConfirm)
    }
}

/**
 * [SkimAlertDialog] for a dialog destination, drawn in the window the navigation's dialog scene
 * opens for it: that window must not use the platform's dialog width
 * (`DialogProperties(usePlatformDefaultWidth = false)`), and Back or a tap outside the dialog go
 * back through the scene. This draws the same 344 dp surface and the 32% scrim, without a second
 * window of its own.
 */
@Composable
fun SkimDialogContent(
    title: String,
    confirm: DialogAction,
    modifier: Modifier = Modifier,
    dismiss: DialogAction? = null,
    destructiveConfirm: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
    text: (@Composable () -> Unit)? = null,
) {
    DialogSurface(
        title = title,
        modifier = modifier,
        icon = icon,
        iconTint = iconTint,
        body = alertBody(text),
    ) {
        if (dismiss != null) DialogButton(dismiss)
        DialogButton(confirm, destructive = destructiveConfirm)
    }
}

/** The [text] of an alert dialog, inset like the title, in bodyMedium and onSurfaceVariant. */
private fun alertBody(text: (@Composable () -> Unit)?): (@Composable ColumnScope.() -> Unit)? {
    if (text == null) return null
    return {
        Box(Modifier.padding(horizontal = DialogPadding)) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph)) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    content = text,
                )
            }
        }
    }
}

/**
 * A dialog that picks one of [options] (Search engine, Theme, Move to folder; 15): a radio row per
 * option, 48 dp high, and a Cancel button only. The whole row selects its option and reads as a
 * radio button with its [label]. Choosing calls [onSelect] with the option, also when it is the
 * selected one; the caller saves it and closes the dialog. Cancel, Back and a tap outside call
 * [onDismiss].
 *
 * @param selected the option checked now, or null for none.
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: ImmutableList<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DialogFrame(
        title = title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = null,
        iconTint = Color.Unspecified,
        body = {
            // The rows reach 12 dp past the dialog padding on each side, as drawn.
            Column(Modifier.padding(horizontal = DialogPadding - ChoiceRowOutset).selectableGroup()) {
                options.forEach { option ->
                    ChoiceRow(label(option), selected = option == selected, onClick = { onSelect(option) })
                }
            }
        },
    ) {
        DialogButton(DialogAction(stringResource(R.string.action_cancel), onDismiss))
    }
}

/**
 * [ChoiceDialog] drawn in the window a dialog destination already opened. The navigation scene goes
 * back on Back and on a tap outside; Cancel calls [onDismiss].
 */
@Composable
fun <T> ChoiceDialogContent(
    title: String,
    options: ImmutableList<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DialogSurface(
        title = title,
        modifier = modifier,
        icon = null,
        iconTint = Color.Unspecified,
        body = {
            Column(Modifier.padding(horizontal = DialogPadding - ChoiceRowOutset).selectableGroup()) {
                options.forEach { option ->
                    ChoiceRow(label(option), selected = option == selected, onClick = { onSelect(option) })
                }
            }
        },
    ) {
        DialogButton(DialogAction(stringResource(R.string.action_cancel), onDismiss))
    }
}

/**
 * An outlined text field of a dialog (Add shortcut, Rename group, Edit bookmark; 15): a 56 dp box
 * with radius 4, an outline border (2 dp primary while focused), [label] in the gap of the top
 * border, and the text on one line in bodyLarge.
 *
 * material3 lays out an outlined field 8 dp below its top so the label fits above the border. The
 * drawn fields have no such space: here the label reaches into the space above, and the field lays
 * out as its 56 dp box, so dialog spacing is measured box to box.
 *
 * @param focusRequester lets the dialog focus the field as it opens (Rename group).
 */
@Composable
fun SkimTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    val labelLineHeight = MaterialTheme.typography.bodySmall.lineHeight
    val labelOverhang = with(LocalDensity.current) {
        if (labelLineHeight.isSp) labelLineHeight.toDp() / 2 else DefaultLabelOverhang
    }
    OutlinedTextField(
        state = state,
        modifier = modifier
            .labelOverhang(labelOverhang)
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

/** Lays out the field without the space material3 keeps above it for the label: [overhang] high. */
private fun Modifier.labelOverhang(overhang: Dp): Modifier = layout { measurable, constraints ->
    val top = overhang.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = top))
    layout(placeable.width, (placeable.height - top).coerceAtLeast(0)) { placeable.place(0, -top) }
}

/** The dialog window of the dialogs above, holding their [DialogSurface]. */
@Composable
private fun DialogFrame(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    icon: (@Composable () -> Unit)?,
    iconTint: Color,
    body: (@Composable ColumnScope.() -> Unit)?,
    buttons: @Composable FlowRowScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogSurface(title, modifier, icon, iconTint, body, buttons)
    }
}

/**
 * The surface shared by the dialogs above, in the dialog window it is composed in: the title, the
 * [body] 16 dp under it, then the [buttons] row, over the 32% scrim.
 */
@Composable
private fun DialogSurface(
    title: String,
    modifier: Modifier,
    icon: (@Composable () -> Unit)?,
    iconTint: Color,
    body: (@Composable ColumnScope.() -> Unit)?,
    buttons: @Composable FlowRowScope.() -> Unit,
) {
    ScrimBehindDialog()
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .width(DialogWidth)
            .semantics { paneTitle = title },
        shape = DialogShape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.padding(top = DialogPadding, bottom = DialogPadding - ButtonTargetInset)) {
            if (icon != null) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = TitleGap)) {
                    CompositionLocalProvider(LocalContentColor provides iconTint, content = icon)
                }
            }
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = DialogPadding),
                // Balanced lines, as the boards draw a two-line title (site permission).
                style = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Heading),
                textAlign = if (icon != null) TextAlign.Center else TextAlign.Start,
            )
            if (body != null) {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(top = TitleGap),
                    content = body,
                )
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DialogPadding, top = DialogPadding - ButtonTargetInset, end = DialogPadding),
                horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.End),
                content = buttons,
            )
        }
    }
}

/**
 * A dialog window dims the screen by the platform's dim amount, 60% on current releases; the drawn
 * scrim behind dialogs is black at 32%.
 */
@Composable
private fun ScrimBehindDialog() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(ScrimAlpha) }
}

/** A text button of a dialog: labelLarge in primary, or in error for a [destructive] confirm. */
@Composable
private fun DialogButton(action: DialogAction, destructive: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick = action.onClick,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
        enabled = action.enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) colors.error else colors.primary,
            disabledContentColor = colors.onSurface.copy(alpha = DisabledContentAlpha),
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
        Text(
            action.label,
            style = ButtonDefaults.textStyleFor(ButtonDefaults.MinHeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A 48 dp radio row: the 20 dp radio in a 40 dp box 2 dp from the start, the text 4 dp after it. */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ChoiceRowHeight)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(start = 2.dp, end = ChoiceRowOutset),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(RadioBoxSize), contentAlignment = Alignment.Center) {
            RadioButton(selected = selected, onClick = null)
        }
        Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
    }
}

private val DialogWidth = 344.dp
private val DialogShape = RoundedCornerShape(28.dp)
private val DialogPadding = 24.dp
private val TitleGap = 16.dp
private val ButtonGap = 8.dp

/** A 40 dp dialog button lays out 48 dp high: 4 dp above and below its shape. */
private val ButtonTargetInset = 4.dp
private val ChoiceRowHeight = 48.dp
private val ChoiceRowOutset = 12.dp
private val RadioBoxSize = 40.dp
private val DefaultLabelOverhang = 8.dp

@PreviewLightDark
@Composable
private fun SkimAlertDialogPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        SkimAlertDialog(
            title = stringResource(R.string.delete_ai_data_title),
            confirm = DialogAction(stringResource(R.string.action_delete), onClick = {}),
            onDismissRequest = {},
            dismiss = DialogAction(stringResource(R.string.action_cancel), onClick = {}),
            destructiveConfirm = true,
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            iconTint = MaterialTheme.colorScheme.error,
            text = { Text(stringResource(R.string.delete_ai_data_body)) },
        )
    }
}

@PreviewLightDark
@Composable
private fun TextFieldDialogPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        val name = rememberTextFieldState("Heat pump research")
        SkimAlertDialog(
            title = stringResource(R.string.rename_group),
            confirm = DialogAction(stringResource(R.string.action_save), onClick = {}, enabled = name.text.isNotBlank()),
            onDismissRequest = {},
            dismiss = DialogAction(stringResource(R.string.action_cancel), onClick = {}),
            text = { SkimTextField(name, stringResource(R.string.field_group_name)) },
        )
    }
}

@PreviewLightDark
@Composable
private fun ChoiceDialogPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        val labels = mapOf(
            ThemeSetting.System to stringResource(R.string.theme_system_default),
            ThemeSetting.Light to stringResource(R.string.theme_light),
            ThemeSetting.Dark to stringResource(R.string.theme_dark),
        )
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeSetting.entries.toImmutableList(),
            selected = ThemeSetting.System,
            label = { labels.getValue(it) },
            onSelect = {},
            onDismiss = {},
        )
    }
}
