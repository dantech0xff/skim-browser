package com.dantech.skimbrowser.ai

import androidx.compose.runtime.Immutable
import com.dantech.skimbrowser.browser.GroupLayout
import com.dantech.skimbrowser.browser.NetworkMonitor
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TabsState
import com.dantech.skimbrowser.browser.layout
import com.dantech.skimbrowser.domain.tab.GroupingSnapshot
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.tab.TabRepository
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The "Skim grouped your tabs" banner. Null hides it. Undo stays enabled only while that layout is current. */
@Immutable
data class GroupingBannerState(val undoEnabled: Boolean)

/**
 * Groups the ungrouped normal tabs once per set of tabs, and offers Undo while the tabs screen is
 * open. Leaving that screen cancels a request that is still running and hides the banner. The set
 * of tabs is remembered, so the same tabs are not grouped again.
 */
class TabGrouping(
    private val ai: SkimAiService,
    private val tabs: TabManager,
    private val tabRepo: TabRepository,
    private val sites: SitePermissionRepository,
    private val network: NetworkMonitor,
    private val appScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val shown = MutableStateFlow<Long?>(null)
    private val undoSnapshot = AtomicReference<GroupingSnapshot?>(null)
    private val knownKey = AtomicReference<String?>(null)

    /** The banner, or null when it is hidden. Undo is enabled only while the grouped layout is still current. */
    val banner: Flow<GroupingBannerState?> = combine(tabs.state, shown) { state, version ->
        version?.let { GroupingBannerState(undoEnabled = state.layoutVersion == it) }
    }

    init {
        appScope.launch {
            val snapshot = runCatching { tabRepo.snapshot() }.getOrNull() ?: return@launch
            knownKey.compareAndSet(null, snapshot.tabSetKey)
            if (snapshot.bannerVisible && undoSnapshot.compareAndSet(null, snapshot)) {
                shown.update { current -> current ?: snapshot.appliedLayoutVersion }
            }
        }
    }

    /**
     * Groups the open tabs when at least four ungrouped ones can be sent and this set has not been
     * grouped before. No network means nothing is sent, and a later open can try again. The result
     * is dropped when the tabs change while the model is answering.
     */
    fun start(): Job = appScope.launch {
        if (!network.hasActiveNetwork) return@launch
        val context = collectTabsContext(tabs, sites, extractLive = false, ungroupedOnly = true)
        if (context.size < MIN_UNGROUPED) return@launch
        val key = tabSetKey(context.map { it.tab.id })
        if (knownKey.get() == key || tabRepo.snapshot()?.tabSetKey == key) {
            knownKey.compareAndSet(null, key)
            return@launch
        }
        val attemptedVersion = tabs.state.value.layoutVersion
        knownKey.set(key)
        val result = try {
            ai.groupTabs(context)
        } catch (e: CancellationException) {
            knownKey.compareAndSet(key, null)
            throw e
        } catch (_: Exception) {
            knownKey.compareAndSet(key, null)
            return@launch
        }
        if (result == null) return@launch
        val state = tabs.state.value
        val nowKey = tabSetKey(
            collectTabsContext(tabs, sites, extractLive = false, ungroupedOnly = true).map { it.tab.id },
        )
        if (state.layoutVersion != attemptedVersion || nowKey != key) return@launch
        val layout = layoutAfterGrouping(state, result, context) ?: return@launch
        val snapshot = GroupingSnapshot(
            previousLayoutJson = LAYOUT_JSON.encodeToString(GroupLayout.serializer(), state.layout()),
            tabSetKey = key,
            appliedLayoutVersion = attemptedVersion + 1,
            bannerVisible = true,
            createdAt = clock(),
        )
        val applied = withContext(mainDispatcher) {
            tabs.applyLayout(layout, expectedVersion = attemptedVersion, snapshot = snapshot)
        }
        if (!applied) return@launch
        val version = tabs.state.value.layoutVersion
        undoSnapshot.set(snapshot.copy(appliedLayoutVersion = version))
        shown.value = version
    }

    /** Puts the tabs back as they were before this grouping, when that layout is still current. */
    suspend fun undo() {
        val snapshot = undoSnapshot.get() ?: return
        if (tabs.state.value.layoutVersion != snapshot.appliedLayoutVersion) return
        val previous = LAYOUT_JSON.decodeFromString(GroupLayout.serializer(), snapshot.previousLayoutJson)
        val applied = withContext(mainDispatcher) {
            tabs.applyLayout(
                previous,
                expectedVersion = snapshot.appliedLayoutVersion,
                snapshot = snapshot.copy(bannerVisible = false),
            )
        }
        if (!applied) return
        shown.value = null
        undoSnapshot.set(null)
    }

    /** Hides the banner and keeps the set of tabs, so opening the screen again does not group them again. */
    suspend fun hideBanner() {
        shown.value = null
        undoSnapshot.set(null)
        tabRepo.setSnapshotBanner(false)
    }

    private companion object {
        const val MIN_UNGROUPED = 4
        val LAYOUT_JSON = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The layout after [result] is applied to [state]. New groups are appended. A tab that moves leaves
 * its old group, and a group of one that was already there stays. Null when no new group of two
 * open tabs remains, so the layout is left alone.
 */
internal fun layoutAfterGrouping(
    state: TabsState,
    result: GroupingResult,
    context: List<TabContext>,
): GroupLayout? {
    val refs = context.associate { it.ref to it.tab.id }
    val open = state.tabs.filterNot { it.incognito }.map { it.id }.toSet()
    val moved = HashSet<String>()
    val created = result.groups.mapNotNull { group ->
        val ids = group.tabIds.mapNotNull { refs[it] }.filter { it in open && moved.add(it) }
        if (ids.size < 2) null else GroupLayout.Group(id = null, title = group.name, tabIds = ids, createdBySkim = true)
    }
    if (created.isEmpty()) return null
    val current = state.layout()
    val kept = current.groups.mapNotNull { group ->
        val remaining = group.tabIds.filter { it !in moved }
        if (remaining.isEmpty()) null else group.copy(tabIds = remaining)
    }
    return GroupLayout(groups = kept + created, ungrouped = current.ungrouped.filter { it !in moved })
}
