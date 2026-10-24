package com.dantech.skimbrowser.ui.bookmarks

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.assisted.Assisted
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolderSummary
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.tab.OpenUrlInCurrentTab
import com.dantech.skimbrowser.ui.components.siteLetter
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A folder row of Bookmarks (14). [label] is resolved by the screen. */
@Immutable
data class BookmarkFolderRow(
    val id: Long,
    val label: FolderLabel,
    val kind: FolderKind,
    val sourceFileName: String?,
    val bookmarkCount: Int,
)

/** A bookmark row. [letter] is the tile's letter. */
@Immutable
data class BookmarkRow(
    val id: Long,
    val title: String,
    val url: String,
    val host: String,
    val letter: String,
)

/** Which heading, if any, sits above [BookmarksUiState.bookmarks]. */
enum class BookmarkListHeading {
    /** The root's "Recently added". */
    RecentlyAdded,

    /** A folder's own bookmarks, or search results: no heading. */
    None,
}

/**
 * What Bookmarks shows. [title] is null at the root (the screen says "Bookmarks") and the open
 * folder's name otherwise. [missing] means the open folder is gone. [empty] means there is nothing
 * to list once the data has been read.
 */
@Immutable
data class BookmarksUiState(
    val loaded: Boolean = false,
    val title: FolderLabel? = null,
    val missing: Boolean = false,
    val searching: Boolean = false,
    val folders: ImmutableList<BookmarkFolderRow> = persistentListOf(),
    val bookmarks: ImmutableList<BookmarkRow> = persistentListOf(),
    val heading: BookmarkListHeading = BookmarkListHeading.None,
) {
    val empty: Boolean get() = loaded && !missing && !searching && folders.isEmpty() && bookmarks.isEmpty()
}

/**
 * Bookmarks (14), at the root when [folderId] is null and inside that folder otherwise. Search
 * looks through every folder. Deleting a bookmark offers Undo through [undo], which still runs
 * after this screen is gone.
 */
@HiltViewModel(assistedFactory = BookmarksViewModel.Factory::class)
class BookmarksViewModel @AssistedInject constructor(
    @Assisted private val folderId: Long?,
    private val bookmarks: BookmarkRepository,
    private val snackbar: SnackbarController,
    private val undo: UndoRegistry,
    private val openUrl: OpenUrlInCurrentTab,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(folderId: Long?): BookmarksViewModel
    }

    private val query = MutableStateFlow("")

    val state: StateFlow<BookmarksUiState> = query.flatMapLatest { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            bookmarks.search(text).map { matches ->
                BookmarksUiState(loaded = true, searching = true, bookmarks = matches.map(::bookmarkRow).toImmutableList())
            }
        } else if (folderId == null) {
            bookmarks.observeRoot().map { root -> rootState(root.folders, root.recentlyAdded) }
        } else {
            bookmarks.observeFolder(folderId).map { content ->
                val folder = content.folder
                if (folder == null) {
                    BookmarksUiState(loaded = true, missing = true)
                } else {
                    BookmarksUiState(
                        loaded = true,
                        title = folderLabel(folder),
                        folders = content.subfolders.map(::folderRow).toImmutableList(),
                        bookmarks = content.bookmarks.map(::bookmarkRow).toImmutableList(),
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BookmarksUiState())

    /** Filters every folder. Blank text shows the root or the open folder again. */
    fun setQuery(value: String) {
        query.value = value
    }

    /** Opens [url] in the current tab, or a new normal tab when none is current. */
    fun open(url: String) {
        openUrl(url)
    }

    /**
     * Deletes bookmark [id] and offers Undo. Finishes even if this screen leaves while the delete
     * is running. Nothing happens when the bookmark is already gone.
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val removed = bookmarks.delete(id) ?: return@withContext
                val token = undo.register(UndoKind.BOOKMARK, undo = { bookmarks.restore(removed) })
                snackbar.show(UiText.Res(R.string.snackbar_bookmark_deleted), SnackbarAction.Undo(token))
            }
        }
    }

    private fun rootState(folders: List<BookmarkFolderSummary>, recent: List<Bookmark>): BookmarksUiState {
        val any = folders.any { it.bookmarkCount > 0 }
        if (!any) return BookmarksUiState(loaded = true)
        return BookmarksUiState(
            loaded = true,
            folders = folders.map(::folderRow).toImmutableList(),
            bookmarks = recent.map(::bookmarkRow).toImmutableList(),
            heading = if (recent.isEmpty()) BookmarkListHeading.None else BookmarkListHeading.RecentlyAdded,
        )
    }
}

private fun folderRow(summary: BookmarkFolderSummary) = BookmarkFolderRow(
    id = summary.folder.id,
    label = folderLabel(summary.folder),
    kind = summary.folder.kind,
    sourceFileName = summary.folder.sourceFileName,
    bookmarkCount = summary.bookmarkCount,
)

private fun bookmarkRow(bookmark: Bookmark) = BookmarkRow(
    id = bookmark.id,
    title = bookmark.title.ifBlank { bookmark.host },
    url = bookmark.url,
    host = bookmark.host,
    letter = siteLetter(bookmark.host, bookmark.title),
)
