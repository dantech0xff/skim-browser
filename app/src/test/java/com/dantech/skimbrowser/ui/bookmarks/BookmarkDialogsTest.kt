package com.dantech.skimbrowser.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.domain.usecase.MoveBookmark
import com.dantech.skimbrowser.domain.usecase.UpdateBookmark
import com.dantech.skimbrowser.data.repo.ImportedNode
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Edit bookmark and Move to folder as board 15 draws them. That board is a collage, so the goldens
 * are whole-screen captures of each dialog on its own.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class BookmarkDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var db: SkimDatabase? = null
    private val jobs = SupervisorJob()

    @After
    fun tearDown() {
        jobs.cancel()
        db?.close()
    }

    @Test
    fun editBookmarkMatchesTheBoardAndTheFolderFieldOpensMove() {
        val dialogs = seed()
        var moved = false
        composeRule.setSkimContent {
            ScreenBehind()
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                EditBookmarkDialog(dialogs.edit, onClose = {}, onMove = { moved = true })
            }
        }
        composeRule.waitUntil(5_000) { dialogs.edit.state.value.loaded }
        composeRule.waitForIdle()

        capture("dialog-edit-bookmark-15")
        composeRule.onNodeWithText("Why heat pumps are winning in cold climates").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Folder, Mobile bookmarks").performClick()
        assertTrue(moved)
    }

    @Test
    fun moveToFolderListsMobileAndImported() {
        val dialogs = seed()
        composeRule.setSkimContent {
            ScreenBehind()
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                MoveToFolderDialog(dialogs.move, onClose = {})
            }
        }
        composeRule.waitUntil(5_000) { dialogs.move.state.value.loaded }
        composeRule.waitForIdle()

        capture("dialog-move-to-folder-15")
        composeRule.onNodeWithText("Mobile bookmarks").assertIsDisplayed()
        composeRule.onNodeWithText("Imported").assertIsDisplayed()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        captureScreenRoboImage("$COMPONENT_GOLDENS/$name.png")
        composeRule.assertA11y()
    }

    private fun seed(): Dialogs {
        val database = inMemorySkimDatabase().also { db = it }
        val bookmarks = BookmarkRepository(database.bookmarkDao())
        val id = runBlocking {
            bookmarks.importTree("bookmarks.html", listOf(ImportedNode("Guide", "https://energysaver.example/guide")))
            bookmarks.addToMobile(TITLE, URL)
        }
        val scope = CoroutineScope(jobs + Dispatchers.Main.immediate)
        return Dialogs(
            EditBookmarkViewModel(id, bookmarks, UpdateBookmark(bookmarks), scope),
            MoveToFolderViewModel(id, bookmarks, MoveBookmark(bookmarks), scope),
        )
    }

    private class Dialogs(val edit: EditBookmarkViewModel, val move: MoveToFolderViewModel)

    private companion object {
        const val COMPONENT_GOLDENS = "src/test/screenshots/components"
        const val TITLE = "Why heat pumps are winning in cold climates"
        const val URL = "daylightjournal.com/energy/heat-pumps-cold"
    }
}

@androidx.compose.runtime.Composable
private fun ScreenBehind() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
}
