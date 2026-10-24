package com.dantech.skimbrowser.ui.search

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.SunnyMark
import kotlinx.coroutines.flow.map

/**
 * The search field's view model of the browser destination, the one Home, the incognito New tab
 * page and the address of a page all open [SearchOverlay] with. Call it from that destination's
 * content, whose view model store keeps it while the browser is in the back stack.
 */
@Composable
fun searchViewModel(): SearchViewModel = hiltViewModel(key = SEARCH_VIEW_MODEL_KEY)

/**
 * Search & suggestions (04b): a full-screen layer over the screen that opened it, shown while it is
 * composed. The caller opens it right after [SearchViewModel.open] (with the tab's [incognito]), in
 * the event that asks for it, and removes it from the composition in [onClose] and after
 * [onOpenUrl] or [onAskSkim].
 *
 * The field edits [SearchViewModel.query] with the keyboard up. Enter opens what
 * [SearchViewModel.submitTarget] makes of the text, an engine suggestion opens the engine's results
 * for it, its north west arrow copies it into the field, and a history or bookmark row opens its
 * page. Clear text empties the field and keeps the layer and the keyboard. Close search and system
 * Back call [onClose]: the layer is a window of its own, so Back reaches it before the browser's.
 *
 * The layer is a dialog window drawn edge to edge on surfaceContainerLow. In an incognito tab the
 * window is always secure, whatever the window under it is at that moment, and the keyboard is asked
 * not to learn from what is typed.
 *
 * @param onAskSkim the Ask Skim row, with the text without its outer white space.
 */
@Composable
fun SearchOverlay(
    search: SearchViewModel,
    incognito: Boolean,
    onOpenUrl: (String) -> Unit,
    onAskSkim: (String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            securePolicy = if (incognito) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
        ),
    ) {
        EdgeToEdgeLayerWindow()
        NoKeyboardLearning(enabled = incognito) {
            SearchLayer(search, incognito, onOpenUrl, onAskSkim, onClose)
        }
    }
}

@Composable
private fun SearchLayer(
    search: SearchViewModel,
    incognito: Boolean,
    onOpenUrl: (String) -> Unit,
    onAskSkim: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state by search.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val fade = remember { Animatable(0f) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(Unit) { fade.animateTo(1f, fadeSpec) }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(colors.surfaceContainerLow),
    ) {
        SearchField(
            query = search.query,
            hint = stringResource(if (incognito) R.string.incognito_search_hint else R.string.search_hint),
            focusRequester = focus,
            onSubmit = { text -> search.submitTarget(text)?.let(onOpenUrl) },
            onClear = search::clear,
            onClose = onClose,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.askSkim || state.engineSuggestions.isNotEmpty() || state.pageMatches.isNotEmpty()) {
            Suggestions(
                query = search.query,
                state = state,
                onAskSkim = onAskSkim,
                onSuggestion = { suggestion -> search.suggestionTarget(suggestion)?.let(onOpenUrl) },
                onEditSuggestion = search::editSuggestion,
                onPage = { page -> onOpenUrl(page.url) },
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(
                        WindowInsets.ime.union(WindowInsets.navigationBars)
                            .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
            )
        }
    }
}

/**
 * The bar of 04b: 56 dp, full radius, surfaceContainerHigh with 4 dp inside; Close search, the
 * field in bodyLarge with [hint] while it is empty (and as its label), and Clear text once there is
 * text. Enter is Go, on a keyboard for addresses without autocorrect.
 */
@Composable
private fun SearchField(
    query: TextFieldState,
    hint: String,
    focusRequester: FocusRequester,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Surface(modifier = modifier.fillMaxWidth().height(BarHeight), shape = CircleShape, color = colors.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurface)) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_close_search))
            }
            BasicTextField(
                state = query,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = hint },
                textStyle = typography.bodyLarge.copy(color = colors.onSurface),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                onKeyboardAction = { onSubmit(query.text.toString()) },
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(colors.primary),
                decorator = { field ->
                    Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.text.isEmpty()) {
                            Text(hint, color = colors.onSurfaceVariant, style = typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        field()
                    }
                },
            )
            if (query.text.isNotEmpty()) {
                IconButton(onClick = onClear, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurfaceVariant)) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_clear_text))
                }
            }
        }
    }
}

/**
 * The rows under the field, in segmented groups 12 dp apart: Ask Skim, the engine's suggestions,
 * then history and bookmarks. The region is labelled "Suggestions" for accessibility and scrolls
 * above the keyboard.
 */
@Composable
private fun Suggestions(
    query: TextFieldState,
    state: SearchUiState,
    onAskSkim: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onEditSuggestion: (String) -> Unit,
    onPage: (PageMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.cd_suggestions)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                isTraversalGroup = true
            }
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(GroupGap),
    ) {
        if (state.askSkim) {
            SegmentedColumn { AskSkimRow(query.text.toString(), onClick = { onAskSkim(query.text.toString().trim()) }) }
        }
        if (state.engineSuggestions.isNotEmpty()) {
            SegmentedColumn {
                state.engineSuggestions.forEachIndexed { index, suggestion ->
                    SuggestionRow(suggestion, index, state.engineSuggestions.size, onSuggestion, onEditSuggestion)
                }
            }
        }
        if (state.pageMatches.isNotEmpty()) {
            SegmentedColumn {
                state.pageMatches.forEachIndexed { index, page ->
                    SkimListItem(
                        headline = page.title,
                        supporting = page.host,
                        index = index,
                        count = state.pageMatches.size,
                        onClick = { onPage(page) },
                        singleLine = true,
                        leading = { RowIcon(if (page.isBookmark) R.drawable.ic_bookmarks else R.drawable.ic_history) },
                    )
                }
            }
        }
    }
}

/** The Ask Skim row: the text over "Ask Skim" in tertiary 500, by a 40 dp Sunny mark (AI). */
@Composable
private fun AskSkimRow(text: String, onClick: () -> Unit) {
    SkimListItem(
        headline = text,
        supporting = stringResource(R.string.ask_skim),
        index = 0,
        count = 1,
        onClick = onClick,
        singleLine = true,
        leading = { SunnyMark(size = 40.dp, iconSize = 20.dp) },
        supportingColor = MaterialTheme.colorScheme.tertiary,
        supportingWeight = FontWeight.Medium,
    )
}

/** An engine suggestion with its north west arrow, "Edit search: <suggestion>". */
@Composable
private fun SuggestionRow(
    suggestion: String,
    index: Int,
    count: Int,
    onSuggestion: (String) -> Unit,
    onEditSuggestion: (String) -> Unit,
) {
    SkimListItem(
        headline = suggestion,
        index = index,
        count = count,
        onClick = { onSuggestion(suggestion) },
        singleLine = true,
        leading = { RowIcon(R.drawable.ic_search) },
        trailing = {
            IconButton(onClick = { onEditSuggestion(suggestion) }) {
                Icon(painterResource(R.drawable.ic_north_west), stringResource(R.string.cd_edit_search, suggestion))
            }
        },
        trailingIsIconButton = true,
    )
}

/** A 24 dp icon centered in the 40 dp leading cell of a row, without a tile. */
@Composable
private fun RowIcon(@DrawableRes icon: Int) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null)
    }
}

/**
 * Sets up the dialog window as a full-screen layer: drawn behind the system bars with icons that
 * read on surfaceContainerLow, nothing dimmed behind it, and no window animation (the content fades
 * in instead).
 */
@Composable
private fun EdgeToEdgeLayerWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val lightBars = MaterialTheme.colorScheme.surfaceContainerLow.luminance() > 0.5f
    DisposableEffect(window, lightBars) {
        if (window != null) {
            WindowCompat.enableEdgeToEdge(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        onDispose { }
    }
}

/**
 * Asks the keyboard not to learn from the text fields in [content] while [enabled]: what is typed
 * in an incognito tab must not come back as a suggestion elsewhere.
 */
@Composable
internal fun NoKeyboardLearning(enabled: Boolean, content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            if (!enabled) {
                nextHandler.startInputMethod(request)
            } else {
                nextHandler.startInputMethod(
                    PlatformTextInputMethodRequest { outAttributes ->
                        request.createInputConnection(outAttributes).also {
                            outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }
                    },
                )
            }
        },
        content = content,
    )
}

/** The key of [searchViewModel] in the browser destination's store. */
internal const val SEARCH_VIEW_MODEL_KEY = "search"

private val BarHeight = 56.dp

/** Space between two groups of rows. */
private val GroupGap = 12.dp
