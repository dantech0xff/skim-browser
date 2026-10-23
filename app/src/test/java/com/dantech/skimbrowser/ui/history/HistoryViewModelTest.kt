package com.dantech.skimbrowser.ui.history

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.fake.FakeHistoryRepository
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.usecase.RemoveHistoryEntry
import com.dantech.skimbrowser.domain.usecase.RestoreHistoryEntry
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.DateBucket
import com.dantech.skimbrowser.ui.text.UiText
import java.time.ZoneId
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

/** History groups visits by day, and Undo survives the screen, a later visit, and a full clear. */
@RunWith(AndroidJUnit4::class)
class HistoryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val stores = mutableListOf<ViewModelStore>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
    }

    @Test
    fun visitsGroupIntoTodayAndYesterdayAndSearchKeepsMatches() = runTest {
        val history = history()
        history.recordVisit(TODAY, "Why heat pumps are winning", NOW)
        history.recordVisit(YESTERDAY, "Osaka night buses", NOW - DAY)
        val model = model(history)

        val grouped = model.state.first { it.sections.size == 2 }
        assertEquals(DateBucket.Today, grouped.sections[0].bucket)
        assertEquals(TODAY, grouped.sections[0].entries.single().url)
        assertEquals(DateBucket.Yesterday, grouped.sections[1].bucket)

        model.setQuery("osaka")
        val found = model.state.first { it.searching && it.sections.size == 1 }
        assertEquals(YESTERDAY, found.sections.single().entries.single().url)
    }

    @Test
    fun undoRestoresAfterTheScreenIsGone() = runTest {
        val history = history()
        history.recordVisit(TODAY, "Why heat pumps are winning", NOW)
        val undo = UndoRegistry(backgroundScope)
        val snackbar = SnackbarController(undo)
        val model = model(history, undo, snackbar)
        val id = model.state.first { it.sections.isNotEmpty() }.sections.single().entries.single().id

        model.remove(id)
        history.observe(null).first { it.isEmpty() }
        stores.forEach { it.clear() }

        val token = (snackbar.current.value?.action as SnackbarAction.Undo).token
        undo.undo(token)
        val restored = history.observe(null).first { it.isNotEmpty() }
        assertEquals(TODAY, restored.single().url)
    }

    @Test
    fun undoAfterTheUrlIsVisitedAgainKeepsTheNewerVisit() = runTest {
        val history = history()
        history.recordVisit(TODAY, "Why heat pumps are winning", NOW)
        val undo = UndoRegistry(backgroundScope)
        val snackbar = SnackbarController(undo)
        val model = model(history, undo, snackbar)
        val id = model.state.first { it.sections.isNotEmpty() }.sections.single().entries.single().id

        model.remove(id)
        history.observe(null).first { it.isEmpty() }
        history.recordVisit(TODAY, "Updated title", NOW + 5_000)
        val token = (snackbar.current.value?.action as SnackbarAction.Undo).token
        undo.undo(token)

        val rows = history.observe(null).first { it.singleOrNull()?.visitedAt == NOW + 5_000 }
        assertEquals(1, rows.size)
        assertEquals("Updated title", rows.single().title)
    }

    @Test
    fun undoAfterHistoryIsClearedDoesNothing() = runTest {
        val history = history()
        history.recordVisit(TODAY, "Why heat pumps are winning", NOW)
        val undo = UndoRegistry(backgroundScope)
        val snackbar = SnackbarController(undo)
        val model = model(history, undo, snackbar)
        val id = model.state.first { it.sections.isNotEmpty() }.sections.single().entries.single().id

        model.remove(id)
        history.observe(null).first { it.isEmpty() }
        val message = snackbar.current.value
        assertEquals(UiText.Res(R.string.snackbar_removed_from_history), message?.text)
        undo.cancelAll(UndoKind.HISTORY)
        snackbar.dismissFor(UndoKind.HISTORY)
        assertNull(snackbar.current.value)

        val token = (message?.action as SnackbarAction.Undo).token
        undo.undo(token)
        assertTrue(history.observe(null).first().isEmpty())
    }

    private fun history(): HistoryRepository = FakeHistoryRepository()

    private fun model(
        history: HistoryRepository,
        undo: UndoRegistry = UndoRegistry(kotlinx.coroutines.test.TestScope().backgroundScope),
        snackbar: SnackbarController = SnackbarController(undo),
    ): HistoryViewModel {
        val store = ViewModelStore().also { stores += it }
        return ViewModelProvider(
            store,
            viewModelFactory {
                initializer {
                    HistoryViewModel(
                        history,
                        RemoveHistoryEntry(history),
                        RestoreHistoryEntry(history),
                        snackbar,
                        undo,
                        clock = { NOW },
                        zone = ZoneId.of("UTC"),
                    )
                }
            },
        )[HistoryViewModel::class.java]
    }

    private companion object {
        const val TODAY = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val YESTERDAY = "https://tabi-note.jp/osaka-buses"
        const val NOW = 1_761_228_000_000L // 2026-10-23 15:00 UTC
        const val DAY = 24L * 60 * 60 * 1000
    }
}
