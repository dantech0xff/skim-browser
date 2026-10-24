package com.dantech.skimbrowser.ui.home

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.dantech.skimbrowser.di.WallClock
import com.dantech.skimbrowser.di.WallZone
import com.dantech.skimbrowser.di.VoiceAvailable
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.pagesEligibleForAsk
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.shortcut.Shortcut
import com.dantech.skimbrowser.domain.shortcut.ShortcutRepository
import com.dantech.skimbrowser.ui.components.siteLetter
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import com.dantech.skimbrowser.ui.text.relativeTimeText
import java.time.ZoneId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A Continue with Skim card on Home (04): one of the newest page chats. */
@Immutable
data class ContinueCard(
    val chatId: Long,
    /** The page's site, as the card's meta row shows it. */
    val host: String,
    /** The letter of the site's favicon tile. */
    val letter: String,
    /** When the chat last changed: "2 h ago", "Yesterday" or a date. */
    val timeText: UiText,
    val title: String,
    /** The short summary line; empty when the chat has none. */
    val snippet: String,
)

/**
 * What Home (04) shows. The first Home, before anything is browsed, needs no flag of its own: the
 * hero card hides while no tab shows a page ([showsHero]), Continue with Skim hides while there is no
 * page chat ([showsContinue]), and Shortcuts shows only its Add tile while [shortcuts] is empty.
 */
@Immutable
data class HomeUiState(
    /** The N of "Summarize my N open tabs": normal tabs showing a web page; New tab pages and incognito tabs don't count. */
    val eligibleTabCount: Int = 0,
    /** In tile order. */
    val shortcuts: ImmutableList<Shortcut> = persistentListOf(),
    /** The newest page chats, newest first, at most two. */
    val continueChats: ImmutableList<ContinueCard> = persistentListOf(),
    /** False hides the mic: no app takes speech input. */
    val voiceAvailable: Boolean = false,
) {
    val showsHero: Boolean get() = eligibleTabCount > 0

    val showsContinue: Boolean get() = continueChats.isNotEmpty()
}

/**
 * Home, the New tab page of a normal tab (04). Commands are called on the main thread.
 *
 * Removing a shortcut runs on [viewModelScope] and calls [ShortcutRepository]. This view model stays
 * with the browser screen, so leaving the New tab page does not cancel the delete. Undo runs
 * through [undo], which keeps the restore on its own scope after this view model is gone.
 *
 * @param voiceAvailable whether an app takes speech input, from `VoiceInput.isAvailable`.
 * @param clock the time the Continue cards count from.
 * @param zone the time zone their calendar days are counted in.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tabs: TabsController,
    private val shortcuts: ShortcutRepository,
    chats: ChatRepository,
    private val snackbar: SnackbarController,
    private val undo: UndoRegistry,
    @VoiceAvailable voiceAvailable: Boolean,
    @WallClock private val clock: () -> Long = System::currentTimeMillis,
    @WallZone private val zone: @JvmSuppressWildcards () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        tabs.state.map { pagesEligibleForAsk(it) }.distinctUntilChanged(),
        shortcuts.observe().map { it.toImmutableList() },
        chats.observeRecentPageChats(CONTINUE_CARDS).map { recent -> recent.map(::continueCard).toImmutableList() },
    ) { tabCount, shortcutList, cards ->
        HomeUiState(tabCount, shortcutList, cards, voiceAvailable)
    }
        .catch { e -> Log.w(TAG, "Home stopped updating: ${e.javaClass.simpleName}") }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            HomeUiState(eligibleTabCount = pagesEligibleForAsk(tabs.state.value), voiceAvailable = voiceAvailable),
        )

    /**
     * Opens a shortcut's [url] in the tab Home belongs to: the current tab, while it is a normal tab
     * on its New tab page. Once that tab shows a page (a second tap of the same frame), nothing more
     * happens.
     */
    @MainThread
    fun openShortcut(url: String) {
        val tab = tabs.state.value.currentTab ?: return
        if (tab.incognito || tab.url != null) return
        tabs.load(tab.id, url)
    }

    /** Remove in the shortcut menu: deletes the shortcut at once and shows "Shortcut removed" with Undo. */
    fun removeShortcut(id: Long) {
        // The registry keeps the Undo block after this view model is gone, so the block captures the repository.
        val repository = shortcuts
        viewModelScope.launch {
            val removed = repository.remove(id) ?: return@launch
            val token = undo.register(UndoKind.SHORTCUT, undo = { repository.restore(removed) })
            snackbar.show(UiText.Res(R.string.snackbar_shortcut_removed), SnackbarAction.Undo(token))
        }
    }

    private fun continueCard(chat: SavedChat): ContinueCard {
        val host = chat.host.orEmpty()
        return ContinueCard(
            chatId = chat.id,
            host = host,
            letter = siteLetter(host, chat.title),
            timeText = relativeTimeText(chat.updatedAt, clock(), zone()),
            title = chat.title,
            snippet = chat.snippet.orEmpty(),
        )
    }

    internal companion object {
        /** Home shows the two newest page chats. */
        const val CONTINUE_CARDS = 2

        /** Keeps reading for a moment after Home leaves the screen, so coming straight back costs nothing. */
        private const val STOP_TIMEOUT_MS = 5_000L

        private const val TAG = "HomeViewModel"
    }
}
