package com.dantech.skimbrowser.ui.history

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.FaviconLetterTile
import com.dantech.skimbrowser.ui.components.IconTile
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.siteLetter
import com.dantech.skimbrowser.ui.navigation.ClearBrowsingDataKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** History (13). A row opens in the current tab. Search filters the list in place. */
@Composable
fun HistoryRoute(navigator: SkimNavigator) {
    val model = hiltViewModel<HistoryViewModel>()
    val state by model.state.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    val query = rememberTextFieldState()
    LaunchedEffect(searching, query) {
        if (!searching) return@LaunchedEffect
        snapshotFlow { query.text.toString() }
            .map { it.trim() }
            .distinctUntilChanged()
            .collect(model::setQuery)
    }
    HistoryScreen(
        state = state,
        searchState = if (searching) query else null,
        onBack = navigator::back,
        onSearch = { searching = true },
        onCloseSearch = {
            searching = false
            query.clearText()
            model.setQuery("")
        },
        onClearData = { navigator.push(ClearBrowsingDataKey) },
        onOpen = { url ->
            model.open(url)
            navigator.showBrowser()
        },
        onRemove = model::remove,
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    searchState: TextFieldState?,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onClearData: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(
        title = stringResource(R.string.history_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (searchState == null) {
                IconButton(onClick = onSearch) {
                    Icon(painterResource(R.drawable.ic_search), stringResource(R.string.cd_search_history))
                }
            }
        },
        searchState = searchState,
        searchPlaceholder = stringResource(R.string.cd_search_history),
        onCloseSearch = onCloseSearch,
    ) {
        if (!state.loaded) return@LargeTitleScaffold
        if (state.empty) {
            item {
                Column(Modifier.fillParentMaxHeight()) {
                    if (searchState == null) ClearDataRow(onClearData)
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.history_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            return@LargeTitleScaffold
        }
        if (searchState == null) {
            item { ClearDataRow(onClearData) }
        }
        state.sections.forEach { section ->
            section(section.bucket.title) {
                section.entries.forEachIndexed { index, entry ->
                    SkimListItem(
                        headline = entry.title.ifBlank { entry.host },
                        supporting = entry.host,
                        index = index,
                        count = section.entries.size,
                        onClick = { onOpen(entry.url) },
                        singleLine = true,
                        leading = { FaviconLetterTile(siteLetter(entry.host, entry.title)) },
                        trailing = { RemoveButton { onRemove(entry.id) } },
                        trailingIsIconButton = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClearDataRow(onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SegmentedColumn(Modifier.padding(top = 8.dp)) {
        SkimListItem(
            headline = stringResource(R.string.clear_browsing_data),
            index = 0,
            count = 1,
            onClick = onClear,
            leading = { IconTile(R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer) },
        )
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.cd_remove_from_history),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun LazyListScope.section(@StringRes label: Int, content: @Composable ColumnScope.() -> Unit) {
    item { SectionLabel(stringResource(label), first = false) }
    item { SegmentedColumn(content = content) }
}
