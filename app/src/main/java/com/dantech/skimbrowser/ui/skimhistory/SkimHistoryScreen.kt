package com.dantech.skimbrowser.ui.skimhistory

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import com.dantech.skimbrowser.domain.chat.ChatKind
import com.dantech.skimbrowser.ui.components.FaviconLetterTile
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.navigation.ClearSkimHistoryKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.text.asString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Skim history (04c). A page row reopens that chat. A tabs row reopens the across-tabs answer. */
@Composable
fun SkimHistoryRoute(navigator: SkimNavigator) {
    val model = hiltViewModel<SkimHistoryViewModel>()
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
    SkimHistoryScreen(
        state = state,
        searchState = if (searching) query else null,
        onBack = navigator::back,
        onSearch = { searching = true },
        onCloseSearch = {
            searching = false
            query.clearText()
            model.setQuery("")
        },
        onDeleteAll = { navigator.push(ClearSkimHistoryKey) },
        onOpen = { row ->
            if (row.kind == ChatKind.TABS) navigator.openSavedTabsChat(row.chatId)
            else navigator.openSavedChat(row.chatId)
        },
        onRemove = model::remove,
    )
}

@Composable
fun SkimHistoryScreen(
    state: SkimHistoryUiState,
    searchState: TextFieldState?,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpen: (SkimHistoryRow) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(
        title = stringResource(R.string.skim_history_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (searchState == null) {
                IconButton(onClick = onSearch) {
                    Icon(painterResource(R.drawable.ic_search), stringResource(R.string.cd_search_skim_history))
                }
            }
            IconButton(onClick = onDeleteAll) {
                Icon(painterResource(R.drawable.ic_delete_sweep), stringResource(R.string.cd_delete_all_skim_history))
            }
        },
        searchState = searchState,
        searchPlaceholder = stringResource(R.string.cd_search_skim_history),
        onCloseSearch = onCloseSearch,
    ) {
        if (!state.loaded) return@LargeTitleScaffold
        if (state.empty) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxHeight().fillMaxWidth().padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.skim_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            return@LargeTitleScaffold
        }
        state.sections.forEach { section ->
            historySection(section.bucket.title) {
                section.rows.forEachIndexed { index, row ->
                    SkimListItem(
                        headline = row.title,
                        supporting = row.subtitle.asString(),
                        index = index,
                        count = section.rows.size,
                        onClick = { onOpen(row) },
                        singleLine = true,
                        leading = { HistoryMark(row) },
                        trailing = { RemoveButton { onRemove(row.chatId) } },
                        trailingIsIconButton = true,
                    )
                }
            }
        }
        item { HistoryFooter() }
    }
}

@Composable
private fun HistoryMark(row: SkimHistoryRow) {
    if (row.kind == ChatKind.TABS) {
        SunnyMark(size = 40.dp, iconSize = 20.dp)
    } else {
        FaviconLetterTile(row.letter)
    }
}

@Composable
private fun HistoryFooter() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painterResource(R.drawable.ic_verified_user),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.skim_history_footer),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.cd_remove_from_skim_history),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun LazyListScope.historySection(@StringRes label: Int, content: @Composable ColumnScope.() -> Unit) {
    item { SectionLabel(stringResource(label), first = false) }
    item { SegmentedColumn(content = content) }
}
