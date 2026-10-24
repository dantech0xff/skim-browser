package com.dantech.skimbrowser.ui.history

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.dantech.skimbrowser.di.WallClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.history.HistoryEntry
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.tab.OpenUrlInCurrentTab
import com.dantech.skimbrowser.domain.usecase.RemoveHistoryEntry
import com.dantech.skimbrowser.domain.usecase.RestoreHistoryEntry
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.DateBucket
import com.dantech.skimbrowser.ui.text.UiText
import com.dantech.skimbrowser.ui.text.bucketOf
import java.time.ZoneId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One day section of History (13), newest section first and newest visit first inside it. */
@Immutable
data class HistorySection(val bucket: DateBucket, val entries: List<HistoryEntry>)

/** What History shows. [empty] is true once the list has been read and holds nothing. */
@Immutable
data class HistoryUiState(
    val loaded: Boolean = false,
    val searching: Boolean = false,
    val sections: List<HistorySection> = emptyList(),
) {
    val empty: Boolean get() = loaded && sections.isEmpty()
}

/**
 * History (13). The list is the newest [HistoryRepository.LIST_LIMIT] visits, or every visit whose
 * title or URL contains the search text. Removing a row offers Undo through [undo], which still
 * runs after this screen is gone. A visit recorded again for the same URL while Undo is waiting
 * keeps the newer row: [HistoryRepository.restore] does not replace it.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val removeHistoryEntry: RemoveHistoryEntry,
    private val restoreHistoryEntry: RestoreHistoryEntry,
    private val snackbar: SnackbarController,
    private val undo: UndoRegistry,
    private val openUrl: OpenUrlInCurrentTab,
    @WallClock private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<HistoryUiState> = query.flatMapLatest { raw ->
        val text = raw.trim()
        history.observe(text.ifEmpty { null }).map { entries ->
            HistoryUiState(
                loaded = true,
                searching = text.isNotEmpty(),
                sections = sectionsOf(entries, clock(), zone),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

    /** Filters the list in place. Blank text shows the newest visits again. */
    fun setQuery(value: String) {
        query.value = value
    }

    /** Opens [url] in the current tab, or a new normal tab when none is current. */
    fun open(url: String) {
        openUrl(url)
    }

    /**
     * Removes visit [id] and offers Undo. Finishes even if this screen leaves while the delete is
     * running. Nothing happens when the visit is already gone.
     */
    fun remove(id: Long) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val entry = removeHistoryEntry(id) ?: return@withContext
                val token = undo.register(UndoKind.HISTORY, undo = { restoreHistoryEntry(entry) })
                snackbar.show(UiText.Res(R.string.snackbar_removed_from_history), SnackbarAction.Undo(token))
            }
        }
    }
}

/** Groups [entries] into the day sections, dropping empty ones. [entries] is newest first. */
internal fun sectionsOf(entries: List<HistoryEntry>, now: Long, zone: ZoneId): List<HistorySection> {
    if (entries.isEmpty()) return emptyList()
    val grouped = entries.groupBy { bucketOf(it.visitedAt, now, zone) }
    return DateBucket.entries.mapNotNull { bucket -> grouped[bucket]?.let { HistorySection(bucket, it) } }
}
