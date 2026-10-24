package com.dantech.skimbrowser.ui.skimhistory

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.dantech.skimbrowser.di.WallClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.chat.ChatKind
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.ChatSummary
import com.dantech.skimbrowser.domain.usecase.RemoveSkimChat
import com.dantech.skimbrowser.domain.usecase.RestoreSkimChat
import com.dantech.skimbrowser.ui.components.siteLetter
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.DateBucket
import com.dantech.skimbrowser.ui.text.UiText
import com.dantech.skimbrowser.ui.text.bucketOf
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One saved chat in Skim history (04c). */
@Immutable
data class SkimHistoryRow(
    val chatId: Long,
    val kind: ChatKind,
    val title: String,
    val subtitle: UiText,
    val letter: String,
)

/** One day section, newest first. */
@Immutable
data class SkimHistorySection(val bucket: DateBucket, val rows: List<SkimHistoryRow>)

/** What Skim history shows. [empty] is true once the list has been read and holds nothing. */
@Immutable
data class SkimHistoryUiState(
    val loaded: Boolean = false,
    val searching: Boolean = false,
    val sections: List<SkimHistorySection> = emptyList(),
) {
    val empty: Boolean get() = loaded && sections.isEmpty()
}

/**
 * Skim history (04c). Removing a row offers Undo through [undo], which still runs after this screen
 * is gone. Clearing all history cancels that Undo first, so it cannot put a chat back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SkimHistoryViewModel @Inject constructor(
    private val chats: ChatRepository,
    private val removeSkimChat: RemoveSkimChat,
    private val restoreSkimChat: RestoreSkimChat,
    private val snackbar: SnackbarController,
    private val undo: UndoRegistry,
    @WallClock private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<SkimHistoryUiState> = query.flatMapLatest { raw ->
        val text = raw.trim()
        chats.observeAll(text.ifEmpty { null }).map { items ->
            SkimHistoryUiState(
                loaded = true,
                searching = text.isNotEmpty(),
                sections = sectionsOf(items, clock(), zone),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SkimHistoryUiState())

    /** Filters the list in place. Blank text shows every chat again. */
    fun setQuery(value: String) {
        query.value = value
    }

    /**
     * Removes chat [id] and offers Undo. Finishes even if this screen leaves while the delete is
     * running. Nothing happens when the chat is already gone.
     */
    fun remove(id: Long) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val deleted = removeSkimChat(id) ?: return@withContext
                val token = undo.register(UndoKind.SKIM_CHAT, undo = { restoreSkimChat(deleted) })
                snackbar.show(UiText.Res(R.string.snackbar_removed_from_history), SnackbarAction.Undo(token))
            }
        }
    }
}

/** Groups [items] into the day sections, dropping empty ones. [items] is newest first. */
internal fun sectionsOf(items: List<ChatSummary>, now: Long, zone: ZoneId): List<SkimHistorySection> {
    if (items.isEmpty()) return emptyList()
    val grouped = items.groupBy { bucketOf(it.chat.updatedAt, now, zone) }
    return DateBucket.entries.mapNotNull { bucket ->
        grouped[bucket]?.let { rows -> SkimHistorySection(bucket, rows.map(::rowOf)) }
    }
}

private fun rowOf(item: ChatSummary): SkimHistoryRow {
    val chat = item.chat
    val subtitle = when (chat.kind) {
        ChatKind.PAGE -> if (item.questionCount > 0) {
            UiText.Plural(
                R.plurals.skim_history_questions_about,
                item.questionCount,
                listOf(item.questionCount, chat.host.orEmpty()),
            )
        } else {
            UiText.Res(R.string.skim_history_summary_of, listOf(chat.host.orEmpty()))
        }
        ChatKind.TABS -> UiText.Plural(
            R.plurals.skim_history_answers_across_tabs,
            item.answerCount,
            listOf(item.answerCount, chat.tabCount),
        )
    }
    return SkimHistoryRow(
        chatId = chat.id,
        kind = chat.kind,
        title = chat.title,
        subtitle = subtitle,
        letter = siteLetter(chat.host, chat.title),
    )
}
