package com.dantech.skimbrowser.ui.search

import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.browser.toVisibleText
import com.dantech.skimbrowser.data.db.entity.BookmarkEntity
import com.dantech.skimbrowser.data.db.entity.HistoryEntryEntity
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.data.repo.HistoryRepository
import com.dantech.skimbrowser.data.settings.SearchEngine
import com.dantech.skimbrowser.search.omniboxAddress
import com.dantech.skimbrowser.search.resolveOmnibox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A history or bookmark row of the search field (04b): a page whose title or URL holds the text. */
data class PageMatch(
    /** The page's title as plain visible text, or its URL when it has none. */
    val title: String,
    val host: String,
    val url: String,
    /** A bookmark (drawn with the bookmarks icon) rather than a history entry. */
    val isBookmark: Boolean,
)

/** The rows under the search field (04b); the text itself is [SearchViewModel.query]. */
data class SearchUiState(
    /** The field belongs to an incognito tab: dark, no Ask Skim row, no history or bookmark rows. */
    val incognito: Boolean = true,
    /** The Ask Skim row, with the text as the question: shown while there is text, never in incognito. */
    val askSkim: Boolean = false,
    /** The engine's suggestions for the text, at most three; none while it fails or has none. */
    val engineSuggestions: List<String> = emptyList(),
    /** History then bookmarks holding the text, one row per URL, at most three; always none in incognito. */
    val pageMatches: List<PageMatch> = emptyList(),
)

/**
 * The search field and its suggestions (04b), for every place it opens from: Home and the incognito
 * New tab page (04, 09c), and the address of a page (05, 11). Commands are called on the main thread.
 *
 * The overlay's input field edits [query] directly. A change of its text cancels the lookup still
 * running for the text before (the engine request with it), and once the text has stayed the same
 * for 150 ms the engine's suggestions and the matching history and bookmarks are looked up side by
 * side, each shown as soon as it arrives. Blank text sends nothing and clears every row at once.
 *
 * Each opening starts with [open], which says whether the field belongs to an incognito tab; until
 * the first one the field is treated as incognito. In incognito, the engine still suggests (the
 * text goes only to the engine), but history and bookmarks are never read and Ask Skim is not offered.
 *
 * @param engine the user's search engine, from the settings.
 * @param suggestions the engine's suggestions for a text (`SuggestionService.suggestions`).
 */
class SearchViewModel(
    engine: Flow<SearchEngine>,
    private val suggestions: suspend (SearchEngine, String) -> List<String>,
    private val history: HistoryRepository,
    private val bookmarks: BookmarkRepository,
) : ViewModel() {

    private data class Session(val id: Int, val incognito: Boolean)

    private data class Lookup(val text: String, val session: Session)

    /** The text of the search field; the overlay's input field reads and edits it. */
    val query = TextFieldState()

    // Snapshot state like the field, so a new opening and its first text reach the lookups together.
    private var session by mutableStateOf(Session(id = 0, incognito = true))

    private val _state = MutableStateFlow(SearchUiState())

    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val currentEngine: StateFlow<SearchEngine?> = engine.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            snapshotFlow { Lookup(query.text.toString(), session) }.collectLatest { lookup ->
                if (lookup.text.isBlank()) {
                    showNothing()
                    return@collectLatest
                }
                _state.update { it.copy(askSkim = !lookup.session.incognito) }
                // The text must rest this long before anything is looked up or sent.
                delay(DEBOUNCE_MS)
                lookUp(lookup)
            }
        }
    }

    /**
     * Starts an opening of the field with [initialText]: all of it selected when [selectAll] (the
     * page's address from 05 or 11), the cursor after it otherwise (empty from Home, the spoken
     * text from voice search). Rows of an earlier opening go at once.
     */
    @MainThread
    fun open(initialText: String = "", selectAll: Boolean = false, incognito: Boolean) {
        session = Session(session.id + 1, incognito)
        if (selectAll) query.setTextAndSelectAll(initialText) else query.setTextAndPlaceCursorAtEnd(initialText)
        _state.value = SearchUiState(incognito = incognito, askSkim = !incognito && initialText.isNotBlank())
    }

    /** Clear text: empties the field and removes every row at once. */
    @MainThread
    fun clear() {
        query.clearText()
        showNothing()
    }

    /** The north west arrow of a suggestion: puts [suggestion] in the field, the cursor at its end. */
    @MainThread
    fun editSuggestion(suggestion: String) {
        query.setTextAndPlaceCursorAtEnd(suggestion)
        _state.update { it.copy(askSkim = !session.incognito && suggestion.isNotBlank()) }
    }

    /**
     * What Enter opens for [text] (see [resolveOmnibox]): a web address as such, other text as the
     * engine's results; null for blank text. Until the engine setting is read, only addresses open.
     */
    fun submitTarget(text: String): String? {
        val engine = currentEngine.value ?: return omniboxAddress(text)
        return resolveOmnibox(text, engine)
    }

    /** What a tap on an engine suggestion opens: the engine's results for it, never an address. */
    fun suggestionTarget(suggestion: String): String? =
        suggestion.trim().takeIf { it.isNotEmpty() }?.let { text -> currentEngine.value?.searchUrl(text) }

    private fun showNothing() {
        _state.update { it.copy(askSkim = false, engineSuggestions = emptyList(), pageMatches = emptyList()) }
    }

    private suspend fun lookUp(lookup: Lookup) = coroutineScope {
        launch {
            val found = engineSuggestionsFor(lookup.text)
            publish(lookup) { it.copy(engineSuggestions = found) }
        }
        if (!lookup.session.incognito) {
            launch {
                val found = pageMatchesFor(lookup.text)
                publish(lookup) { it.copy(pageMatches = found) }
            }
        }
    }

    /**
     * Shows what [lookup] found while it still describes the field. A new text cancels its lookup,
     * but the field changes (Clear text, a new opening) a moment before the lookups hear of it, and a
     * result arriving in between must not show under another text or in another opening.
     */
    private fun publish(lookup: Lookup, change: (SearchUiState) -> SearchUiState) {
        if (lookup.session == session && lookup.text == query.text.toString()) _state.update(change)
    }

    /** At most three, without the text itself: the field and its Ask Skim row show that already. */
    private suspend fun engineSuggestionsFor(text: String): List<String> {
        val engine = currentEngine.filterNotNull().first()
        val typed = text.trim()
        return orNothing { suggestions(engine, text) }
            .filterNot { it.equals(typed, ignoreCase = true) }
            .take(MAX_ROWS)
    }

    /** History first, then bookmarks, one row per URL, at most three in all. */
    private suspend fun pageMatchesFor(text: String): List<PageMatch> {
        val visited = orNothing { history.matches(text, MAX_ROWS) }.map { pageMatch(it) }
        val bookmarked = orNothing { bookmarks.matches(text, MAX_ROWS) }.map { pageMatch(it) }
        return (visited + bookmarked).distinctBy { it.url }.take(MAX_ROWS)
    }

    /** What [read] returns, or nothing when it fails: a failed lookup only hides its rows. */
    private suspend fun <T> orNothing(read: suspend () -> List<T>): List<T> = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "A lookup failed: ${e.javaClass.simpleName}")
        emptyList()
    }

    private companion object {
        const val TAG = "SearchViewModel"
        const val DEBOUNCE_MS = 150L

        /** Rows of each group: engine suggestions, and history with bookmarks. */
        const val MAX_ROWS = 3

        fun pageMatch(entry: HistoryEntryEntity) = PageMatch(titleOf(entry.title, entry.url), entry.host, entry.url, isBookmark = false)

        fun pageMatch(bookmark: BookmarkEntity) = PageMatch(titleOf(bookmark.title, bookmark.url), bookmark.host, bookmark.url, isBookmark = true)

        fun titleOf(title: String, url: String): String = title.toVisibleText().ifEmpty { url.toVisibleText() }
    }
}
