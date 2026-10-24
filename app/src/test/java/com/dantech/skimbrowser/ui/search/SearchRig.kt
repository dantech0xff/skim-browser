package com.dantech.skimbrowser.ui.search

import android.app.Dialog
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.allViews
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.data.repo.HistoryRepository
import com.dantech.skimbrowser.data.settings.SearchEngine
import com.dantech.skimbrowser.testing.BOARD_STATUS_BAR_HEIGHT
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import java.io.Closeable
import java.time.Duration
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * A real [SearchViewModel] over an in-memory database, for the search layer's tests: [engine] is the
 * chosen engine, and the engine answers each text with what [suggested] holds for it. The view model
 * is made on the main thread and runs there, on the Robolectric clock the test moves with [pass].
 */
class SearchRig(val engine: SearchEngine = SearchEngine.BING) : Closeable {

    val db: SkimDatabase = inMemorySkimDatabase()
    val history = HistoryRepository(db.historyDao(), db)
    val bookmarks = BookmarkRepository(db.bookmarkDao())

    /** The engine's suggestions for each text, as the text was typed. */
    val suggested = mutableMapOf<String, List<String>>()

    private val store = ViewModelStore()

    val search: SearchViewModel = ViewModelProvider.create(
        store,
        viewModelFactory {
            initializer {
                SearchViewModel(
                    engine = flowOf(engine),
                    suggestions = { _, text -> suggested[text].orEmpty() },
                    history = history,
                    bookmarks = bookmarks,
                )
            }
        },
    )[SearchViewModel::class]

    /** Adds the history entry of 04b and others that do not match "heat pump". */
    fun seedHistory() = runBlocking {
        history.recordVisit(DAYLIGHT, "Why heat pumps are winning in cold climates", at = 1_000)
        history.recordVisit("https://tabi-note.jp/osaka", "Osaka night buses: a complete guide", at = 2_000)
    }

    override fun close() {
        store.clear()
        db.close()
    }

    companion object {
        const val DAYLIGHT = "https://daylightjournal.com/energy/heat-pumps-cold"

        /** What the engine suggests for "heat pump" on 04b, with the text itself first as engines do. */
        val HeatPumpSuggestions = listOf("heat pump", "heat pump vs furnace", "heat pump cost", "heat pump rebates 2026")
    }
}

/**
 * Lets [ms] pass on the main thread's clock, where the view model rests before a lookup, then
 * settles the UI. Robolectric's clock only moves when told to.
 *
 * The field's text reaches the view model through snapshot apply notifications: they are sent and
 * the main thread is idled first, so the rest starts before the clock moves, not after.
 */
fun ComposeContentTestRule.pass(ms: Long) {
    Snapshot.sendApplyNotifications()
    waitForIdle()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    waitForIdle()
}

/** The window of the dialog shown last: the search layer, or a dialog destination. */
fun latestDialog(): Dialog = checkNotNull(ShadowDialog.getLatestDialog()) { "No dialog is showing" }

/**
 * Gives the dialog shown last a status bar of [height], as a phone has: Robolectric reports no
 * insets, and a dialog window gets its own.
 */
fun ComposeContentTestRule.giveDialogAStatusBar(height: Dp = BOARD_STATUS_BAR_HEIGHT) {
    val decor = latestDialog().window!!.decorView
    val px = (height.value * decor.resources.displayMetrics.density).toInt()
    runOnIdle {
        val statusBar = Insets.of(0, px, 0, 0)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), statusBar)
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars(), statusBar)
            .setVisible(WindowInsetsCompat.Type.statusBars(), true)
            .build()
        decor.allViews.forEach { view -> ViewCompat.dispatchApplyWindowInsets(view, insets) }
    }
    waitForIdle()
}

/**
 * What the dialog shown last tells the keyboard about its focused text field, as Android asks when
 * the keyboard connects; null when no field of it takes text.
 */
fun focusedFieldEditorInfo(): EditorInfo? {
    val editor: View = latestDialog().window!!.decorView.allViews.firstOrNull { it.onCheckIsTextEditor() } ?: return null
    return EditorInfo().also { editor.onCreateInputConnection(it) }
}
