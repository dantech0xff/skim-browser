package com.dantech.skimbrowser.ui.bookmarks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.fake.FakeBookmarkRepository
import com.dantech.skimbrowser.domain.usecase.DeleteBookmark
import com.dantech.skimbrowser.domain.usecase.RestoreBookmark
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bookmarks lists folders and recent rows, searches every folder, and undoes a delete. */
@RunWith(AndroidJUnit4::class)
class BookmarksViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun theRootListsFoldersAndRecentlyAddedAndSearchCrossesFolders() = runTest {
        val bookmarks = bookmarks()
        bookmarks.addToMobile("Why heat pumps are winning", "https://daylightjournal.com/energy")
        val imported = bookmarks.addImported("bookmarks.html", "Osaka night buses", "https://tabi-note.jp/osaka")
        val model = model(null, bookmarks)
        val root = model.state.first { it.folders.size == 2 && it.bookmarks.isNotEmpty() }

        assertNull(root.title)
        assertEquals(FolderLabel.Mobile, root.folders[0].label)
        assertEquals(1, root.folders[0].bookmarkCount)
        assertEquals(FolderLabel.Imported, root.folders[1].label)
        assertEquals("bookmarks.html", root.folders[1].sourceFileName)
        assertEquals(BookmarkListHeading.RecentlyAdded, root.heading)

        val opened = model(imported, bookmarks)
        val folder = opened.state.first { it.title == FolderLabel.Imported && it.bookmarks.size == 1 }
        assertEquals("tabi-note.jp", folder.bookmarks.single().host)
        assertTrue(folder.folders.isEmpty())

        model.setQuery("osaka")
        val found = model.state.first { it.searching && it.bookmarks.size == 1 }
        assertEquals("https://tabi-note.jp/osaka", found.bookmarks.single().url)
        assertTrue(found.folders.isEmpty())
    }

    @Test
    fun anEmptyRootShowsNothingToList() = runTest {
        val model = model(null, bookmarks())
        val state = model.state.first { it.loaded }
        assertTrue(state.empty)
        assertTrue(state.folders.isEmpty())
    }

    @Test
    fun deletingOffersUndoThatRestoresTheBookmark() = runTest {
        val bookmarks = bookmarks()
        val id = bookmarks.addToMobile("Kotlin", "https://kotlinlang.org/")
        val undo = UndoRegistry(backgroundScope)
        val snackbar = SnackbarController(undo)
        val model = model(null, bookmarks, snackbar, undo)
        model.state.first { it.bookmarks.isNotEmpty() }

        model.delete(id)
        model.state.first { it.empty }
        assertEquals(UiText.Res(R.string.snackbar_bookmark_deleted), snackbar.current.value?.text)
        val token = (snackbar.current.value?.action as SnackbarAction.Undo).token
        undo.undo(token)
        model.state.first { it.bookmarks.singleOrNull()?.url == "https://kotlinlang.org/" }
    }

    @Test
    fun aMissingFolderIsReported() = runTest {
        val model = model(404, bookmarks())
        val state = model.state.first { it.loaded }
        assertTrue(state.missing)
    }

    private fun bookmarks(): FakeBookmarkRepository = FakeBookmarkRepository()

    private fun model(
        folderId: Long?,
        bookmarks: BookmarkRepository,
        snackbar: SnackbarController = SnackbarController(UndoRegistry(kotlinx.coroutines.test.TestScope().backgroundScope)),
        undo: UndoRegistry = UndoRegistry(kotlinx.coroutines.test.TestScope().backgroundScope),
    ) = BookmarksViewModel(folderId, bookmarks, DeleteBookmark(bookmarks), RestoreBookmark(bookmarks), snackbar, undo)
}
