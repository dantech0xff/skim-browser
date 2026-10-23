package com.dantech.skimbrowser.ui.tabs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.domain.tab.GroupingBannerState
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabGroup
import com.dantech.skimbrowser.domain.tab.TabGrouper
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.TabsState
import com.dantech.skimbrowser.domain.tab.applyLayout
import com.dantech.skimbrowser.domain.tab.layout
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.ui.components.siteLetter
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** One tab card. [letter] is the site's letter; [title] is the page title, or blank on a new tab. */
@Immutable
data class TabCardState(
    val id: String,
    val title: String,
    val letter: String,
    val isCurrent: Boolean,
    val hasSkimChat: Boolean,
)

/**
 * A section of the grid. [group] null is Other when [showHeader] is true, and the only unlabelled
 * grid when the mode has no groups (or is incognito).
 */
@Immutable
data class TabSection(
    val group: TabGroup?,
    val tabs: List<TabCardState>,
    val showHeader: Boolean,
)

/** What Tabs (09) or Incognito tabs (09b) shows. [count] is every tab of the mode, search included. */
@Immutable
data class TabsUiState(
    val incognito: Boolean = false,
    val count: Int = 0,
    val sections: List<TabSection> = emptyList(),
    val query: String? = null,
    val grouping: GroupingBannerState? = null,
    val aiEnabled: Boolean = true,
) {
    val empty: Boolean get() = count == 0
}

/**
 * Tabs (09) and Incognito tabs (09b). Sections follow the tab manager: named groups in order, then
 * Other. Search keeps a tab whose title or URL contains the text and drops a section that would be
 * empty. Closing offers Undo through [undo], which still runs after this screen is gone; closing
 * every incognito tab ends that session at once and cannot be undone.
 *
 * [grouping] is the banner shown after tabs were grouped. This screen only draws it.
 * [grouper] runs while the normal tabs screen is open. Rename and incognito do not pass one.
 */
class TabsViewModel(
    private val incognito: Boolean,
    private val tabs: TabsController,
    private val snackbar: SnackbarController,
    private val undo: UndoRegistry,
    grouping: Flow<GroupingBannerState?> = flowOf(null),
    private val grouper: TabGrouper? = null,
    private val appScope: CoroutineScope? = null,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val query = MutableStateFlow<String?>(null)
    private var groupingJob: Job? = null
    private var groupingAttached = false

    val state: StateFlow<TabsUiState> = combine(tabs.state, query, grouping) { tabsState, rawQuery, banner ->
        val (count, sections) = sectionsOf(tabsState, incognito, rawQuery)
        TabsUiState(
            incognito = incognito,
            count = count,
            sections = sections,
            query = rawQuery,
            grouping = if (incognito) null else banner,
            aiEnabled = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TabsUiState(incognito = incognito))

    /** Filters the grid in place. Blank text shows every tab of this mode again. */
    fun setQuery(value: String?) {
        query.value = value?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Starts grouping for the normal tabs screen. A second call while it is already running does
     * nothing, so the screen can attach once per visit.
     */
    fun attachGrouping() {
        if (incognito || grouper == null || groupingAttached) return
        groupingAttached = true
        groupingJob = grouper.start()
    }

    /** Cancels a grouping request that is still running and hides the banner. */
    fun detachGrouping() {
        if (!groupingAttached) return
        groupingAttached = false
        val job = groupingJob
        groupingJob = null
        job?.cancel()
        val active = grouper ?: return
        appScope?.launch { active.hideBanner() }
    }

    /** Puts the tabs back as they were before this grouping, when that layout is still current. */
    fun undoGrouping() {
        val active = grouper ?: return
        appScope?.launch { active.undo() }
    }

    override fun onCleared() {
        detachGrouping()
        super.onCleared()
    }

    /** Closes one tab and offers Undo. Nothing happens when it is already gone. */
    fun close(tabId: String) {
        closeIds(listOf(tabId))
    }

    /** Closes every tab of this mode. Incognito ends the session and offers no Undo. */
    fun closeAll() {
        val ids = tabs.state.value.tabs.filter { it.incognito == incognito }.map { it.id }
        if (incognito) {
            if (ids.isEmpty()) return
            tabs.finalizeClose(tabs.close(ids))
        } else {
            closeIds(ids)
        }
    }

    /** Closes every tab of [groupId] and offers one Undo for the group. */
    fun closeGroup(groupId: Long) {
        val ids = tabs.state.value.tabs.filter { it.groupId == groupId && !it.incognito }.map { it.id }
        closeIds(ids)
    }

    /** Moves the group's tabs to the end of Other. */
    fun ungroup(groupId: Long) {
        val state = tabs.state.value
        val layout = state.layout()
        val group = layout.groups.firstOrNull { it.id == groupId } ?: return
        tabs.applyLayout(
            layout.copy(
                groups = layout.groups.filterNot { it.id == groupId },
                ungrouped = layout.ungrouped + group.tabIds,
            ),
            expectedVersion = state.layoutVersion,
        )
    }

    /** Renames [groupId]. A blank name is ignored, so Save stays off until there is one. */
    fun rename(groupId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val state = tabs.state.value
        val layout = state.layout()
        if (layout.groups.none { it.id == groupId }) return
        tabs.applyLayout(
            layout.copy(groups = layout.groups.map { group ->
                if (group.id == groupId) group.copy(title = trimmed) else group
            }),
            expectedVersion = state.layoutVersion,
        )
    }

    /**
     * Moves [tabId] to [toIndexInGroup] inside its own group (or inside Other). An incognito tab
     * has no saved layout, so it stays where it is.
     */
    fun move(tabId: String, toIndexInGroup: Int) {
        val state = tabs.state.value
        val tab = state.tab(tabId) ?: return
        if (tab.incognito) return
        val layout = state.layout()
        val next = reordered(layout, tab, toIndexInGroup) ?: return
        tabs.applyLayout(next, expectedVersion = state.layoutVersion)
    }

    private fun closeIds(ids: List<String>) {
        if (ids.isEmpty()) return
        val open = tabs.state.value.tabs.map { it.id }.toSet()
        val closing = ids.filter { it in open }
        if (closing.isEmpty()) return
        val token = tabs.close(closing)
        val undoToken = undo.register(
            kind = UndoKind.TAB,
            undo = { withContext(mainDispatcher) { tabs.undoClose(token) } },
            finalize = { withContext(mainDispatcher) { tabs.finalizeClose(token) } },
        )
        val text = if (closing.size == 1) {
            UiText.Res(R.string.snackbar_tab_closed)
        } else {
            UiText.Plural(R.plurals.snackbar_tabs_closed, closing.size)
        }
        snackbar.show(text, SnackbarAction.Undo(undoToken))
    }
}

/** [count] of the mode, and the sections search left. A section with no visible tab is omitted. */
internal fun sectionsOf(state: TabsState, incognito: Boolean, query: String?): Pair<Int, List<TabSection>> {
    val mine = state.tabs.filter { it.incognito == incognito }
    val shown = if (query.isNullOrBlank()) {
        mine
    } else {
        mine.filter { tab ->
            tab.title.contains(query, ignoreCase = true) || tab.url?.contains(query, ignoreCase = true) == true
        }
    }
    val grouped = !incognito && state.groups.isNotEmpty()
    if (!grouped) {
        val cards = shown.sortedBy { it.position }.map { it.toCard(state) }
        val sections = if (cards.isEmpty()) emptyList() else listOf(TabSection(null, cards, showHeader = false))
        return mine.size to sections
    }
    val sections = buildList {
        state.groups.sortedBy { it.position }.forEach { group ->
            val members = shown.filter { it.groupId == group.id }.sortedBy { it.position }
            if (members.isNotEmpty()) add(TabSection(group, members.map { it.toCard(state) }, showHeader = true))
        }
        val other = shown.filter { tab -> tab.groupId == null || state.groups.none { it.id == tab.groupId } }
            .sortedBy { it.position }
        if (other.isNotEmpty()) add(TabSection(null, other.map { it.toCard(state) }, showHeader = true))
    }
    return mine.size to sections
}

private fun Tab.toCard(state: TabsState) = TabCardState(
    id = id,
    title = title,
    letter = siteLetter(url?.let(::hostOf), title),
    isCurrent = id == state.currentTabId,
    hasSkimChat = chatId != null && !incognito,
)

/** [tab] moved to [toIndex] inside its group. Null when it would stay, or it is not in the layout. */
private fun reordered(layout: GroupLayout, tab: Tab, toIndex: Int): GroupLayout? {
    if (tab.groupId == null) {
        val ids = layout.ungrouped.toMutableList()
        return moved(ids, tab.id, toIndex)?.let { layout.copy(ungrouped = it) }
    }
    var changed = false
    val groups = layout.groups.map { group ->
        if (group.id != tab.groupId) {
            group
        } else {
            val ids = moved(group.tabIds.toMutableList(), tab.id, toIndex) ?: return null
            changed = true
            group.copy(tabIds = ids)
        }
    }
    return if (changed) layout.copy(groups = groups) else null
}

/** [id] taken out and inserted at [toIndex]. Null when it is missing or already there. */
private fun moved(ids: MutableList<String>, id: String, toIndex: Int): List<String>? {
    val from = ids.indexOf(id)
    if (from < 0) return null
    val dest = toIndex.coerceIn(0, ids.lastIndex)
    if (from == dest) return null
    ids.removeAt(from)
    ids.add(dest, id)
    return ids
}
