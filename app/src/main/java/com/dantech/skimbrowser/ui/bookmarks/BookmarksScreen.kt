package com.dantech.skimbrowser.ui.bookmarks

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.ui.components.FaviconLetterTile
import com.dantech.skimbrowser.ui.components.IconTile
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.navigation.BookmarksKey
import com.dantech.skimbrowser.ui.navigation.EditBookmarkKey
import com.dantech.skimbrowser.ui.navigation.MoveToFolderKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Bookmarks (14), at the root or in one folder. Search looks through every folder. */
@Composable
fun BookmarksRoute(key: BookmarksKey, navigator: SkimNavigator) {
    val model = hiltViewModel<BookmarksViewModel, BookmarksViewModel.Factory>(
        key = "bookmarks-${key.folderId}",
    ) { factory ->
        factory.create(key.folderId)
    }
    val state by model.state.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var menuBookmarkId by remember { mutableStateOf<Long?>(null) }
    val query = rememberTextFieldState()
    LaunchedEffect(searching, query) {
        if (!searching) return@LaunchedEffect
        snapshotFlow { query.text.toString() }
            .map { it.trim() }
            .distinctUntilChanged()
            .collect(model::setQuery)
    }
    LaunchedEffect(state.missing) { if (state.missing) navigator.back() }
    LaunchedEffect(state.bookmarks, menuBookmarkId) {
        val open = menuBookmarkId
        if (open != null && state.bookmarks.none { it.id == open }) menuBookmarkId = null
    }
    val title = state.title?.let { folderLabelText(it) } ?: stringResource(R.string.bookmarks_title)
    BookmarksScreen(
        title = title,
        state = state,
        searchState = if (searching) query else null,
        menuBookmarkId = menuBookmarkId,
        onBack = navigator::back,
        onSearch = { searching = true },
        onCloseSearch = {
            searching = false
            query.clearText()
            model.setQuery("")
        },
        onOpenFolder = { navigator.openBookmarks(it) },
        onOpenBookmark = { url ->
            model.open(url)
            navigator.showBrowser()
        },
        onOpenMenu = { menuBookmarkId = it },
        onDismissMenu = { menuBookmarkId = null },
        onEdit = { navigator.push(EditBookmarkKey(it)) },
        onMove = { navigator.push(MoveToFolderKey(it)) },
        onDelete = { id ->
            menuBookmarkId = null
            model.delete(id)
        },
    )
}

@Composable
fun BookmarksScreen(
    title: String,
    state: BookmarksUiState,
    searchState: TextFieldState?,
    menuBookmarkId: Long?,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenBookmark: (String) -> Unit,
    onOpenMenu: (Long) -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: (Long) -> Unit,
    onMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (searchState == null) {
                IconButton(onClick = onSearch) {
                    Icon(painterResource(R.drawable.ic_search), stringResource(R.string.cd_search_bookmarks))
                }
            }
        },
        searchState = searchState,
        searchPlaceholder = stringResource(R.string.cd_search_bookmarks),
        onCloseSearch = onCloseSearch,
    ) {
        if (!state.loaded || state.missing) return@LargeTitleScaffold
        if (state.searching) {
            if (state.bookmarks.isEmpty()) {
                item { EmptyBookmarks(Modifier.fillParentMaxHeight()) }
            } else {
                item {
                    BookmarkRows(
                        state.bookmarks,
                        menuBookmarkId,
                        onOpenBookmark,
                        onOpenMenu,
                        onDismissMenu,
                        onEdit,
                        onMove,
                        onDelete,
                        Modifier.padding(top = 8.dp),
                    )
                }
            }
            return@LargeTitleScaffold
        }
        if (state.empty) {
            item { EmptyBookmarks(Modifier.fillParentMaxHeight()) }
            return@LargeTitleScaffold
        }
        if (state.folders.isNotEmpty()) {
            section(R.string.bookmarks_section_folders, first = true) {
                state.folders.forEachIndexed { index, folder ->
                    SkimListItem(
                        headline = folderLabelText(folder.label),
                        supporting = folderSummary(folder),
                        index = index,
                        count = state.folders.size,
                        onClick = { onOpenFolder(folder.id) },
                        leading = { FolderTile() },
                    )
                }
            }
        }
        if (state.bookmarks.isNotEmpty()) {
            if (state.heading == BookmarkListHeading.RecentlyAdded) {
                section(R.string.bookmarks_section_recently_added, first = state.folders.isEmpty()) {
                    BookmarkItems(state, menuBookmarkId, onOpenBookmark, onOpenMenu, onDismissMenu, onEdit, onMove, onDelete)
                }
            } else {
                item {
                    BookmarkRows(
                        state.bookmarks,
                        menuBookmarkId,
                        onOpenBookmark,
                        onOpenMenu,
                        onDismissMenu,
                        onEdit,
                        onMove,
                        onDelete,
                        Modifier.padding(top = if (state.folders.isEmpty()) 8.dp else 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.BookmarkItems(
    state: BookmarksUiState,
    menuBookmarkId: Long?,
    onOpenBookmark: (String) -> Unit,
    onOpenMenu: (Long) -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: (Long) -> Unit,
    onMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    state.bookmarks.forEachIndexed { index, bookmark ->
        SkimListItem(
            headline = bookmark.title,
            supporting = bookmark.host,
            index = index,
            count = state.bookmarks.size,
            onClick = { onOpenBookmark(bookmark.url) },
            singleLine = true,
            leading = { FaviconLetterTile(bookmark.letter, content = colors.onSurfaceVariant) },
            trailing = {
                BookmarkOptions(menuBookmarkId == bookmark.id, onOpenMenu, onDismissMenu, onEdit, onMove, onDelete, bookmark.id)
            },
            trailingIsIconButton = true,
        )
    }
}

@Composable
private fun BookmarkRows(
    bookmarks: ImmutableList<BookmarkRow>,
    menuBookmarkId: Long?,
    onOpenBookmark: (String) -> Unit,
    onOpenMenu: (Long) -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: (Long) -> Unit,
    onMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedColumn(modifier) {
        BookmarkItems(
            BookmarksUiState(loaded = true, bookmarks = bookmarks),
            menuBookmarkId,
            onOpenBookmark,
            onOpenMenu,
            onDismissMenu,
            onEdit,
            onMove,
            onDelete,
        )
    }
}

@Composable
private fun BookmarkOptions(
    expanded: Boolean,
    onOpenMenu: (Long) -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: (Long) -> Unit,
    onMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    bookmarkId: Long,
) {
    Box {
        IconButton(onClick = { onOpenMenu(bookmarkId) }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.cd_bookmark_options))
        }
        BookmarkMenu(
            expanded = expanded,
            onEdit = { onEdit(bookmarkId) },
            onMove = { onMove(bookmarkId) },
            onDelete = { onDelete(bookmarkId) },
            onDismiss = onDismissMenu,
        )
    }
}

@Composable
private fun FolderTile() {
    val colors = MaterialTheme.colorScheme
    IconTile(
        icon = R.drawable.ic_folder_filled,
        container = colors.secondaryContainer,
        content = colors.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun folderSummary(folder: BookmarkFolderRow): String = when (folder.kind) {
    FolderKind.IMPORTED -> pluralStringResource(
        R.plurals.bookmarks_imported_count_from,
        folder.bookmarkCount,
        folder.bookmarkCount,
        folder.sourceFileName.orEmpty(),
    )
    FolderKind.MOBILE, FolderKind.FOLDER -> pluralStringResource(
        R.plurals.bookmarks_count,
        folder.bookmarkCount,
        folder.bookmarkCount,
    )
}

@Composable
private fun EmptyBookmarks(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.bookmarks_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun LazyListScope.section(
    @StringRes label: Int,
    first: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    item { SectionLabel(stringResource(label), first) }
    item { SegmentedColumn(content = content) }
}
