package com.dantech.skimbrowser.ui.bookmarks

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolder
import com.dantech.skimbrowser.domain.fake.FakeBookmarkRepository
import com.dantech.skimbrowser.domain.usecase.MoveBookmark
import com.dantech.skimbrowser.domain.usecase.UpdateBookmark
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Edit bookmark and Move to folder over an in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BookmarkDialogViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var bookmarks: FakeBookmarkRepository
    private val viewModels = ViewModelStore()
    private var viewModelCount = 0
    private val appJob = SupervisorJob()

    @After
    fun tearDown() {
        viewModels.clear()
        appJob.cancel()
    }

    @Test
    fun editFillsTheBookmarkAndSaveWritesTheNameAndUrl() = runTest {
        open()
        val id = bookmarks.addToMobile("Heat pumps", "https://daylightjournal.com/energy")
        val edit = edit(id)
        await { edit.state.value.loaded }
        settle()

        assertEquals("Heat pumps", edit.name.text.toString())
        assertEquals("https://daylightjournal.com/energy", edit.url.text.toString())
        assertEquals(FolderLabel.Mobile, edit.state.value.folder)
        assertTrue(edit.state.value.canSave)

        edit.name.setTextAndPlaceCursorAtEnd("Cold climates")
        edit.url.setTextAndPlaceCursorAtEnd("daylightjournal.com/cold")
        settle()
        assertTrue(edit.state.value.canSave)
        edit.save()
        await { edit.state.value.done }

        val saved = bookmarks.observe(id).first()
        assertEquals("Cold climates", saved?.title)
        assertEquals("https://daylightjournal.com/cold", saved?.url)
        assertEquals(BookmarkFolder.MOBILE_ID, saved?.folderId)
    }

    @Test
    fun saveStaysOffForTextThatIsNotAWebAddress() = runTest {
        open()
        val id = bookmarks.addToMobile("Notes", "https://example.com")
        val edit = edit(id)
        await { edit.state.value.loaded }
        edit.url.setTextAndPlaceCursorAtEnd("javascript:alert(1)")
        settle()
        assertFalse(edit.state.value.canSave)
    }

    @Test
    fun aMissingBookmarkClosesTheDialog() = runTest {
        open()
        val edit = edit(404)
        await { edit.state.value.done }
        assertFalse(edit.state.value.loaded)
    }

    @Test
    fun moveToFolderMovesAtOnceAndNamesImportedFolders() = runTest {
        open()
        val id = bookmarks.addToMobile("Heat pumps", "https://daylightjournal.com/energy")
        val imported = bookmarks.addImported("bookmarks.html", "Guide", "https://energysaver.example/guide")
        val move = move(id)
        await { move.state.value.loaded }

        assertEquals(id.let { bookmarks.observe(it).first()!!.folderId }, move.state.value.selectedId)
        assertEquals(listOf(FolderLabel.Mobile, FolderLabel.Imported), move.state.value.options.map { it.label })
        move.moveTo(imported)
        await { move.state.value.done }
        assertEquals(imported, bookmarks.observe(id).first()?.folderId)
    }

    private fun TestScope.open() {
        bookmarks = FakeBookmarkRepository()
    }

    private fun TestScope.edit(id: Long): EditBookmarkViewModel {
        val dialog = kept { EditBookmarkViewModel(id, bookmarks, UpdateBookmark(bookmarks), appScope()) }
        return dialog
    }

    private fun TestScope.move(id: Long): MoveToFolderViewModel =
        kept { MoveToFolderViewModel(id, bookmarks, MoveBookmark(bookmarks), appScope()) }

    private fun TestScope.appScope(): CoroutineScope = CoroutineScope(appJob + StandardTestDispatcher(testScheduler))

    private suspend fun TestScope.await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            runCurrent()
            if (System.nanoTime() > deadline) throw AssertionError("The dialog did not update")
            Thread.sleep(15)
        }
    }

    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private inline fun <reified T : ViewModel> kept(noinline make: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { make() } })["vm-${viewModelCount++}", T::class]
}
