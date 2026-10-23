package com.dantech.skimbrowser.ui.home

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.fake.FakeShortcutRepository
import com.dantech.skimbrowser.domain.shortcut.Shortcut
import com.dantech.skimbrowser.domain.usecase.AddShortcut
import com.dantech.skimbrowser.domain.usecase.UpdateShortcut
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Add and Edit shortcut dialog over an in-memory database. The view model runs on the test's
 * main dispatcher, saving on an app scope of the test's own; typing goes into the fields as the
 * input field would put it there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShortcutDialogViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var shortcuts: FakeShortcutRepository
    private val appJob = SupervisorJob()

    /** Every view model of the test, cleared after it so none of its work runs on into the next test. */
    private val viewModels = ViewModelStore()
    private var viewModelCount = 0

    @Before
    fun setUp() {
        shortcuts = FakeShortcutRepository()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        appJob.cancel()
    }

    // Add shortcut

    @Test
    fun addStartsEmptyWithSaveOff() = runTest {
        val dialog = dialog()
        settle()

        assertEquals(ShortcutDialogUiState(editing = false, loaded = true, canSave = false, done = false), dialog.state.value)
        assertEquals("", dialog.name.text.toString())
        assertEquals("", dialog.url.text.toString())
    }

    @Test
    fun saveIsOnOnlyWhileTheUrlIsAWebAddress() = runTest {
        val dialog = dialog()

        type(dialog, url = "daylightjournal.com")
        assertTrue(dialog.state.value.canSave)

        type(dialog, url = "   ")
        assertFalse(dialog.state.value.canSave)

        type(dialog, url = "heat pump")
        assertFalse("text the field would search for", dialog.state.value.canSave)

        type(dialog, url = " http://192.168.1.1/admin ")
        assertTrue(dialog.state.value.canSave)

        type(dialog, name = "Daylight Journal", url = "")
        assertFalse("a name alone is not enough", dialog.state.value.canSave)
    }

    @Test
    fun saveAddsTheShortcutWithTheUrlTheSearchFieldWouldOpen() = runTest {
        val dialog = dialog()
        type(dialog, name = "Daylight Journal", url = "daylightjournal.com")

        dialog.save()

        assertTrue(dialog.state.first { it.done }.done)
        assertEquals(listOf("Daylight Journal" to "https://daylightjournal.com"), saved())
    }

    @Test
    fun anHttpUrlIsSavedAsTyped() = runTest {
        val dialog = dialog()
        type(dialog, name = "Router", url = " http://192.168.1.1/admin ")

        dialog.save()

        dialog.state.first { it.done }
        assertEquals(listOf("Router" to "http://192.168.1.1/admin"), saved())
    }

    @Test
    fun anEmptyNameTakesTheSitesHost() = runTest {
        val first = dialog()
        type(first, name = "", url = "https://www.daylightjournal.com/energy/heat-pumps")
        first.save()
        first.state.first { it.done }

        val second = dialog()
        type(second, name = "   ", url = "tabi-note.jp")
        second.save()
        second.state.first { it.done }

        assertEquals(
            listOf("daylightjournal.com" to "https://www.daylightjournal.com/energy/heat-pumps", "tabi-note.jp" to "https://tabi-note.jp"),
            saved(),
        )
    }

    @Test
    fun textThatIsNoWebAddressLeavesSaveOffAndIsNeverSaved() = runTest {
        listOf(
            "javascript:alert(1)",
            "JAVASCRIPT:alert(document.cookie)",
            "file:///sdcard/Download",
            "content://media/external/images/1",
            "intent://scan#Intent;scheme=zxing;end",
            "data:text/html,<script>alert(1)</script>",
            "about:blank",
            "heat pump",
            "exa\u202Emple.com",
        ).forEach { url ->
            val dialog = dialog()
            type(dialog, name = "Nope", url = url)
            assertFalse(url, dialog.state.value.canSave)

            // Save is drawn off, but a call that comes anyway must not save either.
            dialog.save()
            settle()
            appJob.children.toList().joinAll()

            assertEquals(url, ShortcutDialogUiState(editing = false, loaded = true, canSave = false, done = false), dialog.state.value)
        }
        assertEquals(emptyList<Pair<String, String>>(), saved())
    }

    @Test
    fun fixingTheUrlTurnsSaveOn() = runTest {
        val dialog = dialog()
        type(dialog, url = "javascript:alert(1)")
        assertFalse(dialog.state.value.canSave)

        type(dialog, url = "example.com")

        assertTrue(dialog.state.value.canSave)
        dialog.save()
        dialog.state.first { it.done }
        assertEquals(listOf("example.com" to "https://example.com"), saved())
    }

    @Test
    fun aSecondTapOnSaveAddsOneShortcut() = runTest {
        val dialog = dialog()
        type(dialog, name = "Wikipedia", url = "wikipedia.org")

        dialog.save()
        settle()
        assertFalse("Save is off while saving", dialog.state.value.canSave)
        dialog.save()
        dialog.state.first { it.done }
        dialog.save()
        appJob.children.toList().joinAll()

        assertEquals(listOf("Wikipedia" to "https://wikipedia.org"), saved())
    }

    @Test
    fun aShortcutBeingSavedIsKeptWhenTheDialogGoesAway() = runTest {
        val store = ViewModelStore()
        val dialog = ViewModelProvider.create(store, viewModelFactory { initializer { newDialog() } })[ShortcutDialogViewModel::class]
        type(dialog, name = "Wikipedia", url = "wikipedia.org")

        dialog.save()
        store.clear()
        appJob.children.toList().joinAll()

        assertEquals(listOf("Wikipedia" to "https://wikipedia.org"), saved())
    }

    // Edit shortcut

    @Test
    fun editFillsInTheShortcutThenSavesOverItInPlace() = runTest {
        shortcuts.add("Wikipedia", "https://wikipedia.org")
        shortcuts.add("GitHub", "https://github.com")
        val (wikipedia, github) = shortcuts.observe().first()

        val dialog = dialog(github.id)
        dialog.state.first { it.loaded }
        settle()

        assertEquals(ShortcutDialogUiState(editing = true, loaded = true, canSave = true, done = false), dialog.state.value)
        assertEquals("GitHub", dialog.name.text.toString())
        assertEquals("https://github.com", dialog.url.text.toString())

        type(dialog, name = "GitHub Mobile", url = "github.com/mobile")
        dialog.save()
        dialog.state.first { it.done }

        assertEquals(listOf(wikipedia, Shortcut(github.id, "GitHub Mobile", "https://github.com/mobile", github.position)), shortcuts.observe().first())
    }

    @Test
    fun saveWaitsForTheShortcutBeingEditedToLoad() = runTest {
        shortcuts.add("Wikipedia", "https://wikipedia.org")
        val wikipedia = shortcuts.observe().first().single()
        val dialog = dialog(wikipedia.id)

        assertFalse(dialog.state.value.loaded)
        assertFalse(dialog.state.value.canSave)
        dialog.url.setTextAndPlaceCursorAtEnd("example.com")
        dialog.save()
        appJob.children.toList().joinAll()

        assertEquals("nothing was saved before the shortcut loaded", listOf(wikipedia), shortcuts.observe().first())
    }

    @Test
    fun saveStaysOffUntilTheShortcutBeingEditedIsRead() = runTest {
        val reads = MutableSharedFlow<List<Shortcut>>()
        val held = FakeShortcutRepository(reads)
        val dialog = kept {
            ShortcutDialogViewModel(
                7,
                held,
                AddShortcut(held),
                UpdateShortcut(held),
                CoroutineScope(appJob + StandardTestDispatcher(testScheduler)),
            )
        }

        type(dialog, url = "example.com")
        assertFalse(dialog.state.value.loaded)
        assertFalse("a web address typed before the shortcut is read", dialog.state.value.canSave)

        reads.emit(listOf(Shortcut(7, "Wikipedia", "https://www.wikipedia.org", 0)))
        settle()

        assertTrue(dialog.state.value.loaded)
        assertTrue(dialog.state.value.canSave)
        assertEquals("https://www.wikipedia.org", dialog.url.text.toString())
    }

    @Test
    fun editingAShortcutThatIsGoneClosesTheDialog() = runTest {
        val dialog = dialog(shortcutId = 999)

        val state = dialog.state.first { it.done }

        assertTrue(state.editing)
        assertFalse(state.canSave)
        assertTrue(saved().isEmpty())
    }

    // Helpers

    private fun TestScope.dialog(shortcutId: Long? = null): ShortcutDialogViewModel = kept { newDialog(shortcutId) }

    /** The dialog's view model, for a store of the caller's. */
    private fun TestScope.newDialog(shortcutId: Long? = null) =
        ShortcutDialogViewModel(
            shortcutId,
            shortcuts,
            AddShortcut(shortcuts),
            UpdateShortcut(shortcuts),
            CoroutineScope(appJob + StandardTestDispatcher(testScheduler)),
        )

    private inline fun <reified T : ViewModel> kept(noinline make: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { make() } })["view-model-${viewModelCount++}", T::class]

    /** Types into the dialog's fields as the user would, then lets the dialog see it. */
    private fun TestScope.type(dialog: ShortcutDialogViewModel, name: String? = null, url: String? = null) {
        name?.let(dialog.name::setTextAndPlaceCursorAtEnd)
        url?.let(dialog.url::setTextAndPlaceCursorAtEnd)
        settle()
    }

    /** What a frame does: field changes reach their observers, and work ready to run runs. */
    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private suspend fun saved(): List<Pair<String, String>> = shortcuts.observe().first().map { it.name to it.url }

}
