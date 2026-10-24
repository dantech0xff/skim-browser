package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/** Behind sheets and dialogs the screen dims with the scrim role, black, at 32% (tokens.md). */
internal const val ScrimAlpha = 0.32f

/** The outline of a sheet: 28 dp top corners. The reader's text settings panel (08b) uses it too. */
val SheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * The state of a [SkimBottomSheet]. Ask about this page (06) opens at half height and drags to full
 * height; tapping a citation lowers it again so the highlighted paragraph shows above it. [expand]
 * and [partialExpand] make those moves. Every other sheet has its full height only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class SkimSheetState internal constructor(
    internal val sheetState: SheetState,
    private val skipPartiallyExpanded: Boolean,
) {
    /** Animates the sheet to its full height; suspends until it is there or a drag interrupts it. */
    suspend fun expand() {
        sheetState.expand()
    }

    /**
     * Animates the sheet down to half height; suspends until it is there or a drag interrupts it.
     *
     * A state made with `skipPartiallyExpanded = true` has no half height, and the sheet then stays
     * where it is. material3 throws for that call, but a citation can be tapped in any sheet that
     * shows one, so lowering a full-height sheet is a no-op rather than a crash.
     */
    suspend fun partialExpand() {
        if (!skipPartiallyExpanded) sheetState.partialExpand()
    }
}

/**
 * Remembers the state of a [SkimBottomSheet]; it survives recreation and process death. With
 * [skipPartiallyExpanded] (the default) the sheet opens at full height, as 05b, 05d and 06b do;
 * without it the sheet opens at half height and can be dragged to full height (06).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSkimSheetState(skipPartiallyExpanded: Boolean = true): SkimSheetState {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = if (skipPartiallyExpanded) FullHeightOnly else HalfAndFullHeight,
    )
    return remember(sheetState, skipPartiallyExpanded) { SkimSheetState(sheetState, skipPartiallyExpanded) }
}

/**
 * A modal sheet as drawn on 05b, 05d, 06, 06b, 06c and the site information sheet: 28 dp top
 * corners on surfaceContainerLow, the 32 by 4 handle in a 36 dp row on top, and the screen behind
 * under the scrim at 32%. Swiping it down, tapping the scrim and Back close it; [onDismiss] runs
 * once it has slid away, and the caller then removes it.
 *
 * TalkBack announces [paneTitle] ("Ask about this page", "More options") when the sheet opens. The
 * handle is material3's drag handle in the drawn size, so TalkBack can still expand, lower or
 * dismiss the sheet from it.
 *
 * The sheet pads its content for the system bars and the keyboard, so content adds no imePadding.
 * It has no snackbar host: snackbars show in the main window, and the one sheet that needs its own
 * ("Copied to clipboard" in 06 and 06b) keeps a local host in its content.
 *
 * @param state the state the content also uses to move the sheet, from [rememberSkimSheetState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkimBottomSheet(
    onDismiss: () -> Unit,
    paneTitle: String,
    modifier: Modifier = Modifier,
    state: SkimSheetState = rememberSkimSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Set outside material3's own pane title ("Bottom Sheet"), so this one is announced.
        modifier = modifier.semantics { this.paneTitle = paneTitle },
        sheetState = state.sheetState,
        shape = SheetShape,
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        scrimColor = colors.scrim.copy(alpha = ScrimAlpha),
        dragHandle = { SheetDragHandle() },
        content = content,
    )
}

/**
 * The handle on top of a sheet: a 32 by 4 bar with radius 2 in onSurfaceVariant at 40%, centered
 * in a 36 dp row. It is decoration, as drawn; the reader's text settings panel (08b), a panel rather
 * than a modal sheet, draws it on top with [SheetShape].
 */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HandleRowHeight)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(HandleWidth, HandleHeight).background(handleColor(), HandleShape))
    }
}

/**
 * material3's drag handle, drawn like [SheetHandle]. It keeps material3's label ("Drag handle") and
 * its expand, collapse and dismiss actions. The default handle keeps 22 dp above and below its bar;
 * the drawn row is 36 dp, so the handle reports that height with its bar in the middle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDragHandle() {
    BottomSheetDefaults.DragHandle(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0))
            val height = HandleRowHeight.roundToPx()
            layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
        },
        width = HandleWidth,
        height = HandleHeight,
        shape = HandleShape,
        color = handleColor(),
    )
}

@Composable
@ReadOnlyComposable
private fun handleColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HandleAlpha)

@OptIn(ExperimentalMaterial3Api::class)
private val FullHeightOnly = setOf(SheetValue.Hidden, SheetValue.Expanded)

@OptIn(ExperimentalMaterial3Api::class)
private val HalfAndFullHeight = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)

private val HandleRowHeight = 36.dp
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private val HandleShape = RoundedCornerShape(2.dp)
private const val HandleAlpha = 0.4f

@PreviewLightDark
@Composable
private fun SkimBottomSheetPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        SkimBottomSheet(onDismiss = {}, paneTitle = stringResource(R.string.cd_more_options)) {
            Text(
                stringResource(R.string.ai_ask_about_page),
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SheetHandlePreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, SheetShape)
                .padding(bottom = 24.dp),
        ) {
            SheetHandle()
            Text(
                stringResource(R.string.reader_text),
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}
