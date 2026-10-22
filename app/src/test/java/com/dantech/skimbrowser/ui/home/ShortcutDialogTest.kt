package com.dantech.skimbrowser.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.ShortcutEntity
import com.dantech.skimbrowser.data.repo.ShortcutRepository
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Add and Edit shortcut dialog (15) with its real view model over an in-memory database, in the
 * window the navigation's dialog scene opens for it. Saving runs on an app scope of the test's own.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ShortcutDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val db: SkimDatabase = inMemorySkimDatabase()
    private val shortcuts = ShortcutRepository(db.shortcutDao())
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val viewModels = ViewModelStore()
    private var closes = 0

    @After
    fun tearDown() {
        viewModels.clear()
        appScope.cancel()
        db.close()
    }

    @Test
    fun addStartsEmptyWithItsTitleTheUrlFocusedAndSaveOff() {
        show()

        composeRule.onNodeWithText("Add shortcut").assertExists()
        urlField().assertIsFocused()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun saveTurnsOnOnlyOnceTheUrlIsAWebAddress() {
        show()

        urlField().performTextReplacement("heat pump")
        composeRule.onNodeWithText("Save").assertIsNotEnabled()

        urlField().performTextReplacement("javascript:alert(1)")
        composeRule.onNodeWithText("Save").assertIsNotEnabled()

        urlField().performTextReplacement("daylightjournal.com")
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun saveAddsTheShortcutAndClosesTheDialog() {
        show()
        nameField().performTextReplacement("Daylight Journal")
        urlField().performTextReplacement("daylightjournal.com")

        composeRule.onNodeWithText("Save").performClick()

        awaitClose()
        assertEquals(1, closes)
        assertEquals(listOf("Daylight Journal" to "https://daylightjournal.com"), saved())
    }

    @Test
    fun theKeyboardsDoneOnTheUrlSavesToo() {
        show()
        urlField().performTextReplacement("tabi-note.jp")

        urlField().performImeAction()

        awaitClose()
        assertEquals(listOf("tabi-note.jp" to "https://tabi-note.jp"), saved())
    }

    @Test
    fun cancelClosesTheDialogAndSavesNothing() {
        show()
        urlField().performTextReplacement("daylightjournal.com")

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, closes)
        assertEquals(emptyList<Pair<String, String>>(), saved())
    }

    @Test
    fun editIsTitledEditShortcutAndFilledInFromTheShortcut() {
        val github = add("GitHub", "https://github.com")
        show(github.id)

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("https://github.com").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Edit shortcut").assertExists()
        composeRule.onAllNodesWithText("Add shortcut").assertCountEquals(0)
        composeRule.onNode(hasSetTextAction() and hasText("Name") and hasText("GitHub")).assertExists()
        urlField().assertIsFocused()
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun editSavesOverTheShortcut() {
        val github = add("GitHub", "https://github.com")
        show(github.id)
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("https://github.com").fetchSemanticsNodes().isNotEmpty() }

        urlField().performTextReplacement("github.com/mobile")
        composeRule.onNodeWithText("Save").performClick()

        awaitClose()
        assertEquals(listOf(ShortcutEntity(github.id, "GitHub", "https://github.com/mobile", github.position)), runBlocking { shortcuts.observe().first() })
    }

    @Test
    fun editingAShortcutThatIsGoneClosesTheDialog() {
        show(shortcutId = 999)

        awaitClose()
        assertEquals(1, closes)
    }

    private fun show(shortcutId: Long? = null) {
        val dialog = ViewModelProvider.create(
            viewModels,
            viewModelFactory { initializer { ShortcutDialogViewModel(shortcutId, shortcuts, appScope) } },
        )[ShortcutDialogViewModel::class]
        composeRule.setSkimContent {
            Dialog(onDismissRequest = {}, properties = ShortcutDialogProperties) {
                ShortcutDialog(dialog, onClose = { closes++ })
            }
        }
        composeRule.waitForIdle()
    }

    /** Waits for the dialog to close: the view model reports a save on the main thread, after the database. */
    private fun awaitClose() {
        composeRule.waitUntil(5_000) {
            composeRule.waitForIdle()
            closes > 0
        }
    }

    private fun nameField() = composeRule.onNode(hasSetTextAction() and hasText("Name"))

    private fun urlField() = composeRule.onNode(hasSetTextAction() and hasText("URL"))

    private fun add(name: String, url: String): ShortcutEntity = runBlocking {
        shortcuts.add(name, url)
        shortcuts.observe().first().single { it.url == url }
    }

    private fun saved(): List<Pair<String, String>> = runBlocking {
        withTimeout(5_000) { shortcuts.observe().first() }.map { it.name to it.url }
    }
}
