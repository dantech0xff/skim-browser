package com.dantech.skimbrowser.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * A list screen with a large title (04c, 10, 12, 13, 14) on surfaceContainerLow: a 64 dp row with
 * Back and up to two [actions] 4 dp apart, the title below it in headlineLargeEmphasized (16 dp in,
 * 12 dp above the list), and [content] in a lazy column that ends 24 dp above the navigation bar.
 * Scrolling the list collapses the title into the 64 dp row, where it reads in titleLarge; the
 * collapsed state is not drawn.
 *
 * While [searchState] is set, a search field replaces the title in the 64 dp row and the keyboard
 * comes up: Close search (or system Back) calls [onCloseSearch], Clear text empties the field, and
 * the caller filters [content] in place as the text changes. The boards do not draw this state; it
 * reuses the Close search and Clear text controls of 04b.
 *
 * The scaffold places no snackbar host: the app shell owns the only one.
 *
 * @param searchPlaceholder hint and accessibility label of the search field, the label of the
 *   action that opened it ("Search history"); required while [searchState] is set.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    searchState: TextFieldState? = null,
    searchPlaceholder: String = "",
    onCloseSearch: () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    require(searchState == null || searchPlaceholder.isNotBlank()) { "A search field needs a placeholder." }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = largeTitleBarColors()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (searchState == null) {
            LargeTitleBar(title, onBack, actions, colors, scrollBehavior)
        } else {
            SearchTitleBar(searchState, searchPlaceholder, onCloseSearch, colors)
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (searchState == null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
            contentPadding = PaddingValues(bottom = navigationBar.calculateBottomPadding() + ListBottomPadding),
            content = content,
        )
    }
}

@Composable
private fun LargeTitleBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    colors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // TwoRowsTopAppBar is the layout of LargeFlexibleTopAppBar with a title for each row, so the
    // expanded title takes the drawn style and padding while the collapsed one keeps titleLarge.
    TwoRowsTopAppBar(
        title = { expanded ->
            if (expanded) {
                Text(
                    text = title,
                    modifier = Modifier.padding(bottom = TitleBottomPadding).semantics { heading() },
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back))
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ActionGap),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        },
        collapsedHeight = BarRowHeight,
        expandedHeight = BarRowHeight + TitleLineHeight + TitleBottomPadding,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SearchTitleBar(
    state: TextFieldState,
    placeholder: String,
    onCloseSearch: () -> Unit,
    colors: TopAppBarColors,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(onBack = onCloseSearch)
    LaunchedEffect(state) { focusRequester.requestFocus() }
    TopAppBar(
        title = {
            val typography = MaterialTheme.typography
            val textColors = MaterialTheme.colorScheme
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                textStyle = typography.bodyLarge.copy(color = textColors.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The list filters as the text changes, so Search only puts the keyboard away.
                onKeyboardAction = { keyboard?.hide() },
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(textColors.primary),
                decorator = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = textColors.onSurfaceVariant,
                                style = typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        field()
                    }
                },
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_close_search))
            }
        },
        actions = {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_clear_text))
                }
            }
        },
        expandedHeight = BarRowHeight,
        colors = colors,
    )
}

/** The bar keeps the screen's surfaceContainerLow when content scrolls under it. */
@Composable
private fun largeTitleBarColors(): TopAppBarColors {
    val colors = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = colors.surfaceContainerLow,
        scrolledContainerColor = colors.surfaceContainerLow,
        navigationIconContentColor = colors.onSurface,
        titleContentColor = colors.onSurface,
        actionIconContentColor = colors.onSurfaceVariant,
    )
}

private val BarRowHeight = 64.dp
private val TitleLineHeight = 40.dp
private val TitleBottomPadding = 12.dp
private val ActionGap = 4.dp
private val ListBottomPadding = 24.dp

@PreviewLightDark
@Composable
private fun LargeTitleScaffoldPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        LargeTitleScaffold(
            title = stringResource(R.string.history_title),
            onBack = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(painterResource(R.drawable.ic_search), stringResource(R.string.cd_search_history))
                }
            },
        ) {
            item { SectionLabel(stringResource(R.string.date_today), first = true) }
            item {
                SegmentedColumn {
                    listOf("daylightjournal.com", "tabi-note.jp").forEachIndexed { index, host ->
                        SkimListItem(
                            headline = host,
                            supporting = host,
                            index = index,
                            count = 2,
                            onClick = {},
                            singleLine = true,
                            leading = { FaviconLetterTile(siteLetter(host, title = null)) },
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LargeTitleScaffoldSearchPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        LargeTitleScaffold(
            title = stringResource(R.string.history_title),
            onBack = {},
            searchState = rememberTextFieldState("heat"),
            searchPlaceholder = stringResource(R.string.cd_search_history),
        ) {
            item { SectionLabel(stringResource(R.string.date_today), first = true) }
        }
    }
}
