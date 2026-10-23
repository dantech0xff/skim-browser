package com.dantech.skimbrowser.ui.bookmarks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.data.repo.ImportedNode
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
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

    private val databases = mutableListOf<SkimDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun theRootListsFoldersAndRecentlyAddedAndSearchCrossesFolders() = runTest {
        val bookmarks = bookmarks()
        bookmarks.addToMobile("Why heat pumps are winning", "https://daylightjournal.com/energy")
        val imported = bookmarks.importTree(
            "bookmarks.html",
            listOf(ImportedNode("Osaka night buses", "https://tabi-note.jp/osaka")),
        )
        val model = BookmarksViewModel(null, bookmarks, SnackbarController(UndoRegistry(backgroundScope)), UndoRegistry(backgroundScope))
        val root = model.state.first { it.folders.size == 2 && it.bookmarks.isNotEmpty() }

        assertNull(root.title)
        assertEquals(FolderLabel.Mobile, root.folders[0].label)
        assertEquals(1, root.folders[0].bookmarkCount)
        assertEquals(FolderLabel.Imported, root.folders[1].label)
        assertEquals("bookmarks.html", root.folders[1].sourceFileName)
        assertEquals(BookmarkListHeading.RecentlyAdded, root.heading)

        val opened = BookmarksViewModel(
            imported!!.folderId,
            bookmarks,
            SnackbarController(UndoRegistry(backgroundScope)),
            UndoRegistry(backgroundScope),
        )
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
        val model = BookmarksViewModel(null, bookmarks(), SnackbarController(UndoRegistry(backgroundScope)), UndoRegistry(backgroundScope))
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
        val model = BookmarksViewModel(null, bookmarks, snackbar, undo)
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
        val model = BookmarksViewModel(404, bookmarks(), SnackbarController(UndoRegistry(backgroundScope)), UndoRegistry(backgroundScope))
        val state = model.state.first { it.loaded }
        assertTrue(state.missing)
    }

    private fun bookmarks(): BookmarkRepository {
        val db = inMemorySkimDatabase().also { databases += it }
        return BookmarkRepository(db.bookmarkDao())
    }
}
