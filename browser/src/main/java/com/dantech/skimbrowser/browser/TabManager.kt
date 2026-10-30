package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.domain.tab.HitTarget
import com.dantech.skimbrowser.domain.tab.PageEvent
import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.domain.tab.RestoreResult

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.dantech.skimbrowser.browser.page.HighlightRequest
import com.dantech.skimbrowser.browser.page.PageContent
import com.dantech.skimbrowser.browser.page.PageScriptSource
import com.dantech.skimbrowser.browser.page.highlightArgsJson
import com.dantech.skimbrowser.browser.page.parsePageContent
import com.dantech.skimbrowser.domain.url.isWebUrl
import com.dantech.skimbrowser.domain.url.withoutFragment
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.GroupingSnapshot
import com.dantech.skimbrowser.domain.tab.PersistedTab
import com.dantech.skimbrowser.domain.tab.PersistedTabGroup
import com.dantech.skimbrowser.domain.tab.TabPlacement
import com.dantech.skimbrowser.domain.tab.TabRepository
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.closesLastIncognitoTab
import com.dantech.skimbrowser.domain.tab.currentAfterUndo
import com.dantech.skimbrowser.domain.tab.partitionUndoClose
import com.dantech.skimbrowser.domain.tab.placeRestoredTabs
import com.dantech.skimbrowser.domain.tab.tabsToRestore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The tabs of the process: their state, the commands screens give them and what their WebViews
 * report. One instance lives in the app container and outlives every Activity; the Activity's
 * WebView pool [attach]es as the [TabEngine] while it exists and reports through [events].
 *
 * Commands are called on the main thread. Normal tabs are written to Room through one queue on
 * `ioDispatcher`, in the order of the commands. Incognito tabs never reach Room, history, the last
 * tab setting or any file. A closed tab is never written again (a write still queued for it is
 * dropped), so it cannot come back after process death unless Undo brings it back.
 */
@Singleton
class TabManager(
    private val tabRepo: TabRepository,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val incognito: IncognitoSession,
    private val stateStore: TabStateStore,
    private val thumbnails: ThumbnailStore,
    private val network: NetworkMonitor,
    private val appScope: CoroutineScope,
    private val scripts: PageScriptSource,
    private val clock: () -> Long = System::currentTimeMillis,
    /** A monotonic clock for the renderer crash budget. */
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val newTabId: () -> String = { UUID.randomUUID().toString() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val restoreTimeoutMs: Long = RESTORE_TIMEOUT_MS,
) : TabsController {

    @Inject constructor(
        tabRepo: TabRepository,
        history: HistoryRepository,
        settings: SettingsRepository,
        incognito: IncognitoSession,
        stateStore: TabStateStore,
        thumbnails: ThumbnailStore,
        network: NetworkMonitor,
        @AppScope appScope: CoroutineScope,
        scripts: PageScriptSource,
    ) : this(
        tabRepo, history, settings, incognito, stateStore, thumbnails, network, appScope, scripts,
        restoreTimeoutMs = RESTORE_TIMEOUT_MS,
    )

    /** A close that can still be undone; [replacementId] is the tab the close made current. */
    private data class PendingClose(val tabs: List<Tab>, val previousCurrentId: String?, val replacementId: String?)

    private class Loaded(
        val tabs: List<PersistedTab>,
        val groups: List<PersistedTabGroup>,
        val lastTabId: String?,
        val snapshot: GroupingSnapshot?,
    )

    private val _state = MutableStateFlow(TabsState())

    /** Every open tab and group; see [TabsState]. */
    override val state: StateFlow<TabsState> = _state.asStateFlow()

    private val _pageEvents = MutableSharedFlow<PageEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Main-frame milestones of every tab, for collectors that subscribed before they happen. */
    val pageEvents: SharedFlow<PageEvent> = _pageEvents.asSharedFlow()

    private val _hitTargets = MutableSharedFlow<Pair<String, HitTarget>>(
        extraBufferCapacity = HIT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Long-presses on links and images, with the tab they happened in (05d). */
    val hitTargets: SharedFlow<Pair<String, HitTarget>> = _hitTargets.asSharedFlow()

    /** Where the WebView layer reports what happens in the tabs. */
    val events: TabEventSink = Events()

    private val engine = MutableStateFlow<TabEngine?>(null)
    private val writer = SerialWriter(appScope, ioDispatcher, TAG)
    private val crashBudget = RendererCrashBudget()
    private val runtimes = ConcurrentHashMap<String, MutableStateFlow<TabRuntime>>()

    // Read and written on the main thread only.
    private val pendingCloses = LinkedHashMap<Long, PendingClose>()
    private var lastCloseTokenId = 0L
    private var nextGroupId = 1L
    private var lastLoadNonce = 0L
    private val pageStartedSinceVisit = HashSet<String>()
    private val readerableChecked = HashMap<String, Long>()
    private val historyTitleUpdates = HashMap<String, Int>()
    private val titleWrites = HashMap<String, Job>()
    private val pendingLoads = LinkedHashMap<String, String>()
    private val pendingReloads = LinkedHashSet<String>()
    private var restoring: Deferred<RestoreResult>? = null

    // Written on the main thread, read by the queue.
    @Volatile
    private var persistTabs = true

    @Volatile
    private var lastNormalTabId: String? = null
    private val queuedUpserts: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val lastTabIdQueued = AtomicBoolean(false)

    init {
        appScope.launch(mainDispatcher) {
            var online = network.validated.value
            network.validated.collectLatest { now ->
                val cameBack = now && !online
                online = now
                if (cameBack) {
                    delay(RECONNECT_DEBOUNCE_MS)
                    reloadShownTabIfOffline()
                }
            }
        }
    }

    /** The live state of [tabId]'s page; a tab that is not open reads as idle. */
    override fun runtime(tabId: String): StateFlow<TabRuntime> =
        (runtimes[tabId] ?: MutableStateFlow(TabRuntime())).asStateFlow()

    /**
     * Brings back the normal tabs saved in Room, their groups and the last shown tab; incognito
     * tabs never come back. Room is read on `ioDispatcher` behind any queued write; the result is
     * applied on the main thread. When reading fails or takes longer than the restore timeout, the
     * session starts on a New tab and does not save its tabs, so the saved ones are still there at
     * the next start. Every call returns the result of the first.
     */
    @AnyThread
    suspend fun restore(): RestoreResult {
        val job = synchronized(this) { restoring ?: appScope.async(ioDispatcher) { restoreOnce() }.also { restoring = it } }
        return job.await()
    }

    /**
     * Opens a tab and returns its id. It shows [url] (http or https; anything else opens the New
     * tab page) or the New tab page, and becomes the current tab unless [background]. An incognito
     * tab joins the incognito session, which starts a new one if the last session is ending.
     *
     * @throws IllegalStateException for an incognito tab when incognito is not supported.
     */
    @MainThread
    fun newTab(
        incognito: Boolean,
        url: String? = null,
        background: Boolean = false,
        openedByExternalApp: Boolean = false,
        closeOnBack: Boolean = false,
        openerTabId: String? = null,
    ): String {
        if (incognito) {
            check(this.incognito.isSupported) { "Incognito tabs need WebView profiles" }
            this.incognito.profileForNewTab(finalizePending = ::finalizeClose)
        }
        val now = clock()
        val id = newTabId()
        check(isStorableTabId(id) && _state.value.tab(id) == null && id !in pendingCloseIds()) { "Tab ids must be new" }
        val tab = Tab(
            id = id,
            incognito = incognito,
            url = url?.trim()?.takeIf { isWebUrl(it) || it == ABOUT_BLANK },
            title = "",
            groupId = null,
            position = nextOtherPosition(incognito),
            openedByExternalApp = openedByExternalApp,
            closeOnBack = closeOnBack,
            openerTabId = openerTabId,
            createdAt = now,
            lastAccessedAt = now,
        )
        if (url != null && tab.url == null) Log.w(TAG, "A new tab got a URL it cannot open; it shows the New tab page")
        runtimes[id] = MutableStateFlow(TabRuntime())
        _state.update { it.copy(tabs = sortTabs(it.tabs + tab, it.groups)) }
        persistTab(id)
        if (!background || _state.value.currentTabId == null) select(id)
        return id
    }

    override fun openNewTab(
        incognito: Boolean,
        url: String?,
        background: Boolean,
        openedByExternalApp: Boolean,
        closeOnBack: Boolean,
        openerTabId: String?,
    ): String = newTab(incognito, url, background, openedByExternalApp, closeOnBack, openerTabId)

    /** Makes [tabId] the current tab. A tab offline at its last load reloads if the network is back. */
    @MainThread
    override fun select(tabId: String) {
        val tab = openTab(tabId) ?: return
        val now = clock()
        _state.update { s ->
            s.copy(tabs = s.tabs.map { if (it.id == tabId) it.copy(lastAccessedAt = now) else it }, currentTabId = tabId)
        }
        persistTab(tabId)
        if (!tab.incognito) rememberLastTab(tabId)
        if (runtimeOf(tabId).loadError is LoadError.NoInternet && network.validated.value) reload(tabId)
    }

    /**
     * Loads [url] (http or https; anything else is ignored) in [tabId]. A tab on the New tab page
     * then remembers that it started there. Without an attached engine the tab's WebView loads it
     * when it is next created.
     */
    @MainThread
    override fun load(tabId: String, url: String) {
        val tab = openTab(tabId) ?: return
        val target = url.trim()
        if (!isWebUrl(target)) {
            Log.w(TAG, "Only http and https URLs load in a tab")
            return
        }
        updateTab(tabId) { it.copy(url = target, fromNewTabPage = it.fromNewTabPage || tab.url == null) }
        updateRuntime(tabId) { it.copy(loadError = null, find = null) }
        persistTab(tabId)
        val current = engine.value
        if (current != null) {
            current.load(tabId, target)
        } else {
            pendingReloads.remove(tabId)
            pendingLoads[tabId] = target
        }
    }

    /** Back on the first page of a tab started from the New tab page: the tab shows it again. */
    @MainThread
    override fun returnToNewTabPage(tabId: String) {
        openTab(tabId) ?: return
        forgetPendingWork(tabId)
        // The URL goes first, so the state the WebView hands over while it is destroyed is not kept.
        updateTab(tabId) { it.copy(url = null, title = "", fromNewTabPage = false) }
        runtimes[tabId]?.value = TabRuntime()
        persistTab(tabId)
        engine.value?.saveAndDestroy(tabId)
        stateStore.delete(tabId)
        thumbnails.delete(tabId)
    }

    @MainThread
    override fun goBack(tabId: String): Boolean {
        openTab(tabId) ?: return false
        return engine.value?.goBack(tabId) ?: false
    }

    @MainThread
    override fun goForward(tabId: String) {
        openTab(tabId) ?: return
        engine.value?.goForward(tabId)
    }

    /** Reloads [tabId]; without an attached engine its WebView reloads when it is next created. */
    @MainThread
    override fun reload(tabId: String) {
        openTab(tabId) ?: return
        val current = engine.value
        if (current != null) {
            if (runtimeOf(tabId).loadError is LoadError.Crashed) updateRuntime(tabId) { it.copy(loadError = null) }
            current.reload(tabId)
        } else if (tabId !in pendingLoads) {
            pendingReloads += tabId
        }
    }

    @MainThread
    override fun setDesktopSite(tabId: String, on: Boolean) {
        val tab = openTab(tabId) ?: return
        if (tab.desktopSite == on) return
        updateTab(tabId) { it.copy(desktopSite = on) }
        persistTab(tabId)
        engine.value?.setDesktop(tabId, on)
    }

    /** Starts or updates Find in page; the counts arrive in [runtime]. */
    @MainThread
    override fun find(tabId: String, query: String) {
        openTab(tabId) ?: return
        updateRuntime(tabId) { it.copy(find = FindState(query)) }
        engine.value?.find(tabId, query)
    }

    @MainThread
    override fun findNext(tabId: String, forward: Boolean) {
        openTab(tabId) ?: return
        if (runtimeOf(tabId).find == null) return
        engine.value?.findNext(tabId, forward)
    }

    @MainThread
    override fun clearFind(tabId: String) {
        openTab(tabId) ?: return
        updateRuntime(tabId) { it.copy(find = null) }
        engine.value?.clearFind(tabId)
    }

    /**
     * Closes [tabIds] at once and returns the token that undoes it. The tabs leave [state] right
     * away; their WebView state is saved and the WebViews destroyed. If the current tab closes, its
     * opener or else the most recently used tab of the same kind becomes current (none: null).
     * Closing the last incognito tab makes the incognito session end once the close is final.
     */
    @MainThread
    override fun close(tabIds: List<String>): CloseToken {
        val token = CloseToken(++lastCloseTokenId)
        val before = _state.value
        val closing = tabIds.distinct().mapNotNull(before::tab)
        if (closing.isEmpty()) return token
        val closingIds = closing.map { it.id }.toSet()
        val remaining = before.tabs.filterNot { it.id in closingIds }
        val current = before.currentTab
        val replacement = if (current != null && current.id in closingIds) replacementFor(current, remaining) else current

        pendingCloses[token.id] = PendingClose(closing, before.currentTabId, replacement?.id)
        closing.forEach { forgetPendingWork(it.id) }
        _state.value = before.copy(tabs = remaining, currentTabId = replacement?.id)
        closing.forEach { runtimes[it.id]?.value = TabRuntime() }

        val normalIds = closing.filterNot { it.incognito }.map { it.id }
        if (normalIds.isNotEmpty() && persistTabs) {
            val at = clock()
            postTabWrite { tabRepo.markClosed(normalIds, at) }
        }
        if (replacement?.id != before.currentTabId || lastNormalTabId in closingIds) currentChanged(replacement)
        engine.value?.let { live -> closing.forEach { live.saveAndDestroy(it.id) } }
        if (closesLastIncognitoTab(closing, remaining)) incognito.markEnding(token)
        return token
    }

    /**
     * Undoes the close of [token]: the tabs come back at their positions and in their groups (a
     * group that no longer exists sends its tabs to the end of Other), and the tab that was current
     * is current again unless another one was chosen since. A token already final or undone, or
     * unknown, does nothing.
     */
    @MainThread
    override fun undoClose(token: CloseToken) {
        val pending = pendingCloses.remove(token.id) ?: return
        val before = _state.value
        val groupIds = before.groups.map { it.id }.toSet()
        // Incognito tabs only come back into the session they were closed in.
        val sessionOpen = incognito.state.value != IncognitoSession.State.None
        val (restorable, lost) = partitionUndoClose(pending.tabs, sessionOpen)
        finalizeTabs(lost)
        if (restorable.isEmpty()) return

        val restored = placeRestoredTabs(restorable, groupIds, nextOtherPosition(incognito = false))
        restored.forEach { runtimes.getOrPut(it.id) { MutableStateFlow(TabRuntime()) } }
        val restoredIds = restored.map { it.id }.toSet()
        val current = currentAfterUndo(
            restoredIds = restoredIds,
            previousCurrentId = pending.previousCurrentId,
            currentTabId = before.currentTabId,
            replacementId = pending.replacementId,
            restored = restored,
        )
        _state.value = before.copy(tabs = sortTabs(before.tabs + restored, before.groups), currentTabId = current)

        val normalIds = restored.filterNot { it.incognito }.map { it.id }
        if (normalIds.isNotEmpty() && persistTabs) postTabWrite { tabRepo.reopen(normalIds) }
        // Rewritten whole: a tab closed before its first write reached Room has no row to reopen.
        normalIds.forEach(::persistTab)
        if (restored.any { it.incognito }) incognito.cancelEnding()
        if (current != before.currentTabId) currentChanged(current?.let(_state.value::tab))
    }

    /**
     * Makes the close of [token] final: the tabs are deleted with their saved state and thumbnail.
     * When it closed the last incognito tab the incognito session ends, with every incognito close
     * still waiting for Undo. A token already final or undone, or unknown, does nothing.
     */
    @MainThread
    override fun finalizeClose(token: CloseToken) {
        val pending = pendingCloses.remove(token.id) ?: return
        finalizeTabs(pending.tabs)
        val session = incognito.state.value
        if (session is IncognitoSession.State.Ending && session.pendingToken == token) endIncognitoSession()
        dropEmptyGroups()
    }

    /** Makes every close still waiting for Undo final, when the Activity finishes for good. */
    @MainThread
    fun finalizePendingCloses() {
        pendingCloses.keys.toList().forEach { finalizeClose(CloseToken(it)) }
    }

    /** The tabs whose close can still be undone. */
    @MainThread
    fun pendingCloseIds(): Set<String> = pendingCloses.values.flatMapTo(HashSet()) { pending -> pending.tabs.map { it.id } }

    /**
     * Applies [layout] to the normal tabs, the only way groups and order change. Returns false and
     * changes nothing when [expectedVersion] is given and the layout version moved on since.
     *
     * Ids of tabs that are not open (closed, waiting for Undo, incognito, unknown) are skipped and
     * groups left without a tab are dropped. Open normal tabs the layout does not mention stay in
     * their group if it stays, else go to the end of Other. New groups get ids no group ever had.
     * The layout, and [snapshot] stamped with the new layout version, are written in one
     * transaction.
     *
     * @throws IllegalArgumentException when a tab or a group is listed twice.
     */
    override fun replaceLayout(layout: GroupLayout, expectedVersion: Long?, snapshot: GroupingSnapshot?): Boolean =
        applyLayout(layout, expectedVersion, snapshot)

    @MainThread
    fun applyLayout(layout: GroupLayout, expectedVersion: Long? = null, snapshot: GroupingSnapshot? = null): Boolean {
        val before = _state.value
        if (expectedVersion != null && expectedVersion != before.layoutVersion) return false
        val listedTabs = layout.groups.flatMap { it.tabIds } + layout.ungrouped
        require(listedTabs.size == listedTabs.toSet().size) { "A tab is placed more than once" }
        val listedGroups = layout.groups.mapNotNull { it.id }
        require(listedGroups.size == listedGroups.toSet().size) { "A group is listed more than once" }

        val open = before.tabs.filterNot { it.incognito }
        val openIds = open.map { it.id }.toSet()
        val existingGroups = before.groups.map { it.id }.toSet()
        val placed = layout.groups
            .map { group -> group to group.tabIds.filter { it in openIds } }
            .filter { (_, members) -> members.isNotEmpty() }
        val groups = placed.mapIndexed { index, (group, _) ->
            TabGroup(
                id = group.id?.takeIf { it in existingGroups } ?: nextGroupId++,
                title = group.title.trim().cutTo(Tab.MAX_TITLE_LENGTH),
                position = index,
                createdBySkim = group.createdBySkim,
            )
        }

        val placement = HashMap<String, Pair<Long?, Int>>()
        val groupSizes = HashMap<Long, Int>()
        placed.forEachIndexed { index, (_, members) ->
            val groupId = groups[index].id
            members.forEachIndexed { position, tabId -> placement[tabId] = groupId to position }
            groupSizes[groupId] = members.size
        }
        var otherPosition = 0
        layout.ungrouped.filter { it in openIds }.forEach { placement[it] = null to otherPosition++ }
        open.filter { it.id !in placement }.forEach { tab ->
            val stays = tab.groupId?.takeIf { id -> groupSizes.containsKey(id) }
            placement[tab.id] = if (stays != null) {
                val position = groupSizes.getValue(stays)
                groupSizes[stays] = position + 1
                stays to position
            } else {
                null to otherPosition++
            }
        }

        val version = before.layoutVersion + 1
        val tabs = before.tabs.map { tab ->
            placement[tab.id]?.let { (groupId, position) -> tab.copy(groupId = groupId, position = position) } ?: tab
        }
        _state.value = before.copy(tabs = sortTabs(tabs, groups), groups = groups, layoutVersion = version)

        if (persistTabs) {
            val entities = groups.map { PersistedTabGroup(it.id, it.title, it.position, it.createdBySkim) }
            val indexOfGroup = groups.withIndex().associate { it.value.id to it.index }
            val placements = open.map { tab ->
                val (groupId, position) = placement.getValue(tab.id)
                TabPlacement(tab.id, groupId?.let(indexOfGroup::getValue), position)
            }
            val stamped = snapshot?.copy(appliedLayoutVersion = version)
            postTabWrite { tabRepo.saveLayout(entities, placements, stamped) }
        }
        return true
    }

    /** Ties [tabId] to the Skim chat [chatId], or unties it with null. */
    @MainThread
    fun bindChat(tabId: String, chatId: Long?) {
        val tab = openTab(tabId) ?: return
        if (tab.chatId == chatId) return
        updateTab(tabId) { it.copy(chatId = chatId) }
        persistTab(tabId)
    }

    /**
     * Waits until [tabId] shows its page fully loaded in a live WebView, creating one off screen
     * when the tab has none. False when the tab is not open or shows the New tab page, when the load
     * fails, or after [timeoutMs].
     */
    @AnyThread
    suspend fun awaitPageReady(tabId: String, timeoutMs: Long = PAGE_READY_TIMEOUT_MS): Boolean =
        withContext(mainDispatcher) {
            withTimeoutOrNull(timeoutMs) {
                if (openTab(tabId)?.url == null) return@withTimeoutOrNull false
                val live = engine.filterNotNull().first()
                if (openTab(tabId)?.url == null) return@withTimeoutOrNull false
                live.ensureWebView(tabId, offscreen = true)
                val runtime = runtimes[tabId] ?: return@withTimeoutOrNull false
                val settled = runtime.first { it.pageLoaded || it.loadError != null }
                settled.pageLoaded && settled.loadError == null
            } ?: false
        }

    /**
     * Reads the page of [tabId] once it is ready ([awaitPageReady]), with the URL it committed and
     * its load nonce. Null when it never gets ready, or when it navigated away while being read.
     */
    @AnyThread
    override suspend fun extractPage(tabId: String): PageContent? = withContext(mainDispatcher) {
        if (!scripts.isSupported || !awaitPageReady(tabId)) return@withContext null
        val live = engine.value ?: return@withContext null
        val runtime = runtimes[tabId] ?: return@withContext null
        val before = runtime.value
        val url = before.committedUrl ?: return@withContext null
        val json = evaluateOrNull(live, tabId, scripts.extractScript()) ?: return@withContext null
        val after = runtime.value
        if (after.loadNonce != before.loadNonce || !samePage(after.committedUrl, url)) return@withContext null
        parsePageContent(json, committedUrl = url, loadNonce = before.loadNonce)
    }

    /**
     * Reads the page of a tab that already has a live WebView. Does not create one. Null when the
     * tab has no live page, the script cannot run, or the page moves while it is read.
     */
    @AnyThread
    override suspend fun extractLivePage(tabId: String): PageContent? = withContext(mainDispatcher) {
        if (!scripts.isSupported) return@withContext null
        val live = engine.value ?: return@withContext null
        val runtime = runtimes[tabId]?.value ?: return@withContext null
        if (!runtime.pageLoaded || runtime.loadError != null) return@withContext null
        val url = runtime.committedUrl ?: return@withContext null
        val json = evaluateOrNull(live, tabId, scripts.extractScript()) ?: return@withContext null
        val after = runtimes[tabId]?.value ?: return@withContext null
        if (after.loadNonce != runtime.loadNonce || !samePage(after.committedUrl, url)) return@withContext null
        parsePageContent(json, committedUrl = url, loadNonce = runtime.loadNonce)
    }

    /**
     * Highlights a cited block in [tabId]'s page. Nothing runs, and the answer is false, unless the
     * block id is digits only and the tab still shows the document the citation was read from: the
     * same committed URL (fragment aside) and the same load nonce. The arguments reach the script
     * as JSON, never as code.
     */
    @AnyThread
    override suspend fun highlightBlock(tabId: String, request: HighlightRequest): Boolean = withContext(mainDispatcher) {
        val args = highlightArgsJson(request) ?: return@withContext false
        if (openTab(tabId) == null) return@withContext false
        val live = engine.value ?: return@withContext false
        val runtime = runtimeOf(tabId)
        if (runtime.loadNonce != request.expectedNonce || !samePage(runtime.committedUrl, request.expectedUrl)) {
            return@withContext false
        }
        evaluateOrNull(live, tabId, scripts.highlightScript(args)) == "true"
    }

    /**
     * The Activity's WebView pool is ready: loads and reloads asked for meanwhile reach it now. A
     * newer Activity's pool may take over while an older one still lives (the older instance only
     * finishes after the newer one started); none of its WebViews is the newer pool's, so from now
     * on no tab's page counts as loaded until the newer pool loads it.
     */
    @MainThread
    fun attach(engine: TabEngine) {
        val replaced = this.engine.value
        if (replaced != null && replaced !== engine) forgetLivePages()
        this.engine.value = engine
        val loads = pendingLoads.toMap()
        val reloads = pendingReloads.toList()
        pendingLoads.clear()
        pendingReloads.clear()
        loads.forEach { (tabId, url) -> if (openTab(tabId) != null) engine.load(tabId, url) }
        reloads.forEach { tabId -> if (openTab(tabId) != null) engine.reload(tabId) }
        reloadShownTabIfOffline()
    }

    /**
     * The Activity of [engine] is gone, and every WebView with it. Nothing changes when another
     * engine is attached by now: a newer Activity that started before the old one was destroyed
     * keeps its pool.
     */
    @MainThread
    fun detach(engine: TabEngine) {
        if (this.engine.value !== engine) return
        this.engine.value = null
        forgetLivePages()
    }

    /** True while [engine] is the one attached. */
    @MainThread
    fun isAttached(engine: TabEngine): Boolean = this.engine.value === engine

    /** Returns once every write queued so far has reached Room and the settings. */
    suspend fun awaitWrites() = writer.awaitIdle()

    /** No WebView of the attached engine, if any, shows a page yet: none is loading, loaded or searched. */
    private fun forgetLivePages() {
        pageStartedSinceVisit.clear()
        runtimes.values.forEach { flow -> flow.update { it.copy(progress = null, pageLoaded = false, find = null) } }
    }

    private suspend fun restoreOnce(): RestoreResult {
        // The read is its own job: a database stuck in a blocking call cannot hold up the timeout.
        val reading = appScope.async(ioDispatcher) {
            writer.run {
                val (tabs, groups) = tabRepo.loadOpen()
                Loaded(tabs, groups, settings.settings.first().lastTabId, tabRepo.snapshot())
            }
        }
        var timedOut = false
        val loaded = try {
            withTimeoutOrNull(restoreTimeoutMs) { reading.await() }.also {
                if (it == null) {
                    timedOut = true
                    reading.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Saved tabs could not be read", e)
            null
        }
        return withContext(mainDispatcher) {
            if (loaded != null) applyRestored(loaded) else startWithoutSavedTabs(timedOut)
        }
    }

    private fun applyRestored(loaded: Loaded): RestoreResult {
        val restored = tabsToRestore(loaded.tabs.map { it.toTab() })
        val restoredIds = restored.map { it.id }.toSet()
        val restoredGroups = loaded.groups.map { TabGroup(it.id, it.title, it.position, it.createdBySkim) }
        val before = _state.value
        val groups = (restoredGroups + before.groups.filterNot { group -> restoredGroups.any { it.id == group.id } })
            .sortedWith(compareBy({ it.position }, { it.id }))
        val tabs = restored + before.tabs.filterNot { it.id in restoredIds }
        restored.forEach { runtimes.getOrPut(it.id) { MutableStateFlow(TabRuntime()) } }
        nextGroupId = maxOf(nextGroupId, (groups.maxOfOrNull { it.id } ?: 0L) + 1)
        val current = before.currentTabId
            ?: loaded.lastTabId?.takeIf { it in restoredIds }
            ?: restored.maxByOrNull { it.lastAccessedAt }?.id
        // A version above the snapshot's, so a snapshot from an earlier process never looks current.
        val version = maxOf(before.layoutVersion, (loaded.snapshot?.appliedLayoutVersion ?: -1L) + 1)
        _state.value = TabsState(sortTabs(tabs, groups), groups, current, version)
        lastNormalTabId = loaded.lastTabId
        currentChanged(current?.let(_state.value::tab))

        val keep = tabs.map { it.id }.toSet()
        stateStore.purgeExcept(keep)
        thumbnails.purgeExcept(keep)
        return RestoreResult.Restored(restored.size)
    }

    private fun startWithoutSavedTabs(timedOut: Boolean): RestoreResult {
        persistTabs = false
        Log.w(TAG, if (timedOut) "Restoring tabs timed out" else "Restoring tabs failed")
        if (_state.value.currentTabId == null) newTab(incognito = false)
        return RestoreResult.Fallback(timedOut)
    }

    private fun openTab(tabId: String): Tab? = _state.value.tab(tabId)

    private fun runtimeOf(tabId: String): TabRuntime = runtimes[tabId]?.value ?: TabRuntime()

    private fun updateRuntime(tabId: String, transform: (TabRuntime) -> TabRuntime): TabRuntime =
        runtimes[tabId]?.updateAndGet(transform) ?: TabRuntime()

    /** Changes [tabId] in place; its group and position stay, so the order does too. */
    private fun updateTab(tabId: String, transform: (Tab) -> Tab) {
        _state.update { s -> s.copy(tabs = s.tabs.map { if (it.id == tabId) transform(it) else it }) }
    }

    private fun nextOtherPosition(incognito: Boolean): Int =
        (_state.value.tabs.filter { it.incognito == incognito && it.groupId == null }.maxOfOrNull { it.position } ?: -1) + 1

    private fun replacementFor(closed: Tab, remaining: List<Tab>): Tab? =
        closed.openerTabId?.let { opener -> remaining.firstOrNull { it.id == opener } }
            ?: remaining.filter { it.incognito == closed.incognito }.maxByOrNull { it.lastAccessedAt }

    /** Keeps the last tab setting on the normal tab shown last, or the most recent one left. */
    private fun currentChanged(current: Tab?) {
        when {
            current != null && !current.incognito -> rememberLastTab(current.id)
            lastNormalTabId?.let(::openTab) == null ->
                rememberLastTab(_state.value.tabs.filterNot { it.incognito }.maxByOrNull { it.lastAccessedAt }?.id)
        }
    }

    private fun rememberLastTab(tabId: String?) {
        if (!persistTabs || lastNormalTabId == tabId) return
        lastNormalTabId = tabId
        if (lastTabIdQueued.compareAndSet(false, true)) {
            writer.post {
                lastTabIdQueued.set(false)
                settings.setLastTabId(lastNormalTabId)
            }
        }
    }

    /**
     * Queues a write of [tabId]'s row. At most one is queued per tab, and it writes the tab as it is
     * when the queue reaches it: a tab closed by then is not written, so a write can never reopen it.
     */
    private fun persistTab(tabId: String) {
        if (!persistTabs) return
        val tab = openTab(tabId) ?: return
        if (tab.incognito || !queuedUpserts.add(tabId)) return
        writer.post {
            queuedUpserts.remove(tabId)
            val latest = _state.value.tab(tabId)?.takeIf { !it.incognito } ?: return@post
            tabRepo.upsert(latest.toEntity())
        }
    }

    /**
     * Queues a write of tab rows that is not a plain [persistTab] (close, reopen, final delete,
     * layout). Its data is as the tabs were when it was queued, so it can undo what a tab write that
     * ran just before it stored. Every tab therefore queues a new write after it: the one that may
     * still be waiting is forgotten, and the next [persistTab] finds no write queued and adds one that
     * runs behind this one and reads the tab as it is by then.
     */
    private fun postTabWrite(block: suspend () -> Unit) {
        queuedUpserts.clear()
        writer.post(block)
    }

    /** A title change is written a moment later, so a page that animates its title is written once. */
    private fun persistTitleLater(tabId: String) {
        titleWrites.remove(tabId)?.cancel()
        titleWrites[tabId] = appScope.launch(mainDispatcher) {
            delay(TITLE_WRITE_DELAY_MS)
            titleWrites.remove(tabId)
            persistTab(tabId)
        }
    }

    /** Drops the work still pending for a tab that closes or leaves its page. */
    private fun forgetPendingWork(tabId: String) {
        titleWrites.remove(tabId)?.cancel()
        pendingLoads.remove(tabId)
        pendingReloads.remove(tabId)
        pageStartedSinceVisit.remove(tabId)
        readerableChecked.remove(tabId)
        historyTitleUpdates.remove(tabId)
    }

    private fun finalizeTabs(tabs: List<Tab>) {
        if (tabs.isEmpty()) return
        val normalIds = tabs.filterNot { it.incognito }.map { it.id }
        if (normalIds.isNotEmpty() && persistTabs) postTabWrite { tabRepo.deleteClosed(normalIds) }
        tabs.forEach { tab ->
            stateStore.delete(tab.id)
            thumbnails.delete(tab.id)
            crashBudget.forget(tab.id)
            runtimes.remove(tab.id)
        }
    }

    /** Ends the incognito session, with every incognito close still waiting for Undo. */
    private fun endIncognitoSession() {
        val iterator = pendingCloses.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val (incognitoTabs, normalTabs) = entry.value.tabs.partition { it.incognito }
            if (incognitoTabs.isEmpty()) continue
            finalizeTabs(incognitoTabs)
            if (normalTabs.isEmpty()) iterator.remove() else entry.setValue(entry.value.copy(tabs = normalTabs))
        }
        incognito.end(engine.value)
    }

    /** Forgets groups that lost their last tab for good; their ids are never given out again. */
    private fun dropEmptyGroups() {
        val waiting = pendingCloses.values.flatMap { pending -> pending.tabs.mapNotNull { it.groupId } }.toSet()
        _state.update { s ->
            val used = s.tabs.mapNotNull { it.groupId }.toSet() + waiting
            if (s.groups.all { it.id in used }) s else s.copy(groups = s.groups.filter { it.id in used })
        }
    }

    /** Reloads the current tab when its last load found no internet and the network is back. */
    private fun reloadShownTabIfOffline() {
        val current = _state.value.currentTabId ?: return
        if (engine.value == null || !network.validated.value) return
        if (runtimeOf(current).loadError is LoadError.NoInternet) reload(current)
    }

    private fun checkReaderable(tabId: String, nonce: Long) {
        // A WebView that cannot run the scripts reads no page: none is offered as readable.
        if (!scripts.isSupported || readerableChecked[tabId] == nonce) return
        val live = engine.value ?: return
        if (runtimeOf(tabId).committedUrl?.let(::isWebUrl) != true) return
        readerableChecked[tabId] = nonce
        appScope.launch(mainDispatcher) {
            val readerable = evaluateOrNull(live, tabId, scripts.readerableScript()) == "true"
            if (openTab(tabId) != null) updateRuntime(tabId) { if (it.loadNonce == nonce) it.copy(readerable = readerable) else it }
        }
    }

    private suspend fun evaluateOrNull(live: TabEngine, tabId: String, script: String): String? = try {
        live.evaluate(tabId, script)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "A page script failed", e)
        null
    }

    /** Main-frame reports of the WebView layer; ignored for tabs that are not open. */
    private inner class Events : TabEventSink {

        override fun onPageStarted(tabId: String, url: String): Long {
            val tab = openTab(tabId) ?: return NO_LOAD
            val nonce = ++lastLoadNonce
            pageStartedSinceVisit += tabId
            historyTitleUpdates.remove(tabId)
            updateRuntime(tabId) {
                it.copy(
                    loadError = null,
                    readerable = false,
                    find = null,
                    pageLoaded = false,
                    committedUrl = url,
                    loadNonce = nonce,
                    isSecure = isHttps(url),
                )
            }
            if (tab.url != url || tab.title.isNotEmpty()) {
                updateTab(tabId) { it.copy(url = url, title = "") }
                persistTab(tabId)
            }
            _pageEvents.tryEmit(PageEvent.PageStarted(tabId, url, nonce))
            return nonce
        }

        override fun onProgress(tabId: String, percent: Int) {
            openTab(tabId) ?: return
            val clamped = percent.coerceIn(0, 100)
            updateRuntime(tabId) { it.copy(progress = if (clamped == 100) null else clamped / 100f) }
        }

        override fun onTitle(tabId: String, title: String) {
            val tab = openTab(tabId) ?: return
            // A page's title is shown in the tab grid and kept in history: no control or bidi
            // characters, which could make it read differently from what it is.
            val clean = title.toVisibleText().cutTo(Tab.MAX_TITLE_LENGTH)
            if (clean == tab.title) return
            updateTab(tabId) { it.copy(title = clean) }
            if (tab.incognito) return
            persistTitleLater(tabId)
            val runtime = runtimeOf(tabId)
            val url = runtime.committedUrl ?: return
            val updates = historyTitleUpdates[tabId] ?: 0
            // History takes the first few titles of a visit, not every frame of a title animation.
            if (clean.isNotEmpty() && runtime.loadError == null && isRecordable(url) && updates < MAX_HISTORY_TITLE_UPDATES) {
                historyTitleUpdates[tabId] = updates + 1
                writer.post { history.updateTitle(url, clean) }
            }
        }

        override fun onVisitedHistory(tabId: String, url: String) {
            val tab = openTab(tabId) ?: return
            val sameDocument = !pageStartedSinceVisit.remove(tabId)
            // A failed load commits an error page for its URL; that is no visit.
            val failed = runtimeOf(tabId).loadError?.url == url
            updateRuntime(tabId) { it.copy(committedUrl = url, isSecure = isHttps(url)) }
            if (tab.url != url) {
                updateTab(tabId) { it.copy(url = url) }
                persistTab(tabId)
            }
            historyTitleUpdates.remove(tabId)
            if (!tab.incognito && !failed && isRecordable(url)) {
                // A new document has no title yet (it was cleared when it started); a same-document
                // navigation keeps the document's title until the page sets another.
                val title = openTab(tabId)?.title.orEmpty()
                val at = clock()
                writer.post { history.recordVisit(url, title, at) }
            }
            _pageEvents.tryEmit(PageEvent.Navigated(tabId, url, sameDocument))
        }

        override fun onPageFinished(tabId: String, url: String) {
            openTab(tabId) ?: return
            val runtime = updateRuntime(tabId) { it.copy(pageLoaded = true, progress = null) }
            _pageEvents.tryEmit(PageEvent.PageFinished(tabId, url, runtime.loadNonce))
            if (runtime.loadError == null) checkReaderable(tabId, runtime.loadNonce)
        }

        override fun onLoadError(tabId: String, errorCode: Int, description: String?, url: String) {
            openTab(tabId) ?: return
            val error = loadErrorOf(errorCode, description, url, network.hasActiveNetwork)
            updateRuntime(tabId) { it.copy(loadError = error, progress = null) }
        }

        override fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
            openTab(tabId) ?: return
            updateRuntime(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
        }

        override fun onFindResult(tabId: String, activeMatchIndex: Int, matchCount: Int) {
            openTab(tabId) ?: return
            updateRuntime(tabId) { runtime ->
                runtime.find?.let { find ->
                    runtime.copy(find = find.copy(activeMatchIndex = activeMatchIndex.coerceAtLeast(0), matchCount = matchCount.coerceAtLeast(0)))
                } ?: runtime
            }
        }

        override fun onRendererGone(tabId: String, didCrash: Boolean): RendererGoneAction {
            val tab = openTab(tabId) ?: return RendererGoneAction.RESTORE
            val recovers = crashBudget.allow(tabId, elapsedRealtime())
            pageStartedSinceVisit.remove(tabId)
            updateRuntime(tabId) { runtime ->
                val error = if (recovers) {
                    null
                } else {
                    LoadError.Crashed(runtime.committedUrl ?: tab.url.orEmpty(), if (didCrash) "RENDERER_CRASHED" else "RENDERER_KILLED")
                }
                // A tab that shows the error has no WebView, so no history to go back or forward in until Try again.
                runtime.copy(
                    progress = null,
                    pageLoaded = false,
                    find = null,
                    loadError = error,
                    canGoBack = runtime.canGoBack && recovers,
                    canGoForward = runtime.canGoForward && recovers,
                )
            }
            return if (recovers) RendererGoneAction.RESTORE else RendererGoneAction.SHOW_ERROR
        }

        override fun onHitTarget(tabId: String, target: HitTarget) {
            openTab(tabId) ?: return
            target.bounded()?.let { _hitTargets.tryEmit(tabId to it) }
        }

        override fun onPopupRequested(openerTabId: String): String? {
            val opener = openTab(openerTabId) ?: return null
            if (opener.incognito && !incognito.isSupported) return null
            return newTab(incognito = opener.incognito, url = ABOUT_BLANK, openerTabId = opener.id)
        }

        override fun onPopupClosed(popupTabId: String) {
            // Only a popup of this process may close itself; any other page calling window.close() stays.
            if (openTab(popupTabId)?.openerTabId == null) return
            finalizeClose(close(listOf(popupTabId)))
        }

        override fun onStateSaved(tabId: String, state: Bundle) {
            val tab = openTab(tabId) ?: pendingCloses.values.firstNotNullOfOrNull { pending -> pending.tabs.firstOrNull { it.id == tabId } }
            if (tab?.url == null) return
            stateStore.save(tabId, state, tab.incognito)
        }

        override fun onWebViewDestroyed(tabId: String) {
            openTab(tabId) ?: return
            pageStartedSinceVisit.remove(tabId)
            updateRuntime(tabId) { it.copy(progress = null, pageLoaded = false, readerable = false, find = null) }
        }
    }

    companion object {
        private const val TAG = "SkimTabs"

        /** How long [restore] may take before the session starts without the saved tabs. */
        const val RESTORE_TIMEOUT_MS = 5_000L

        /** How long [awaitPageReady] waits by default. */
        const val PAGE_READY_TIMEOUT_MS = 15_000L

        /** How long the network must stay back before an offline tab reloads by itself. */
        const val RECONNECT_DEBOUNCE_MS = 2_000L

        private const val TITLE_WRITE_DELAY_MS = 1_000L
        private const val MAX_HISTORY_TITLE_UPDATES = 5
        private const val EVENT_BUFFER = 64
        private const val HIT_BUFFER = 8
        private const val NO_LOAD = 0L
        private const val ABOUT_BLANK = "about:blank"

        /** The longest URL kept with a tab in Room; a page can make one of megabytes. */
        private const val MAX_STORED_URL_LENGTH = 8_192

        /** The longest URL or text a long-press target may carry into the saved navigation state. */
        private const val MAX_HIT_URL_LENGTH = 64 * 1024
        private const val MAX_HIT_TEXT_LENGTH = 1_024

        private fun isHttps(url: String) = url.trim().startsWith("https:", ignoreCase = true)

        private fun isRecordable(url: String) = isWebUrl(url) && url.length <= HistoryRepository.MAX_URL_LENGTH

        private fun samePage(committed: String?, expected: String): Boolean =
            committed != null && withoutFragment(committed) == withoutFragment(expected)

        /**
         * The URL stored with a tab: as it is when short enough, else without its fragment, else
         * without its query, else none (the tab reopens on the New tab page; its saved WebView
         * state still has the page).
         */
        private fun storableUrl(url: String?): String? = when {
            url == null || url.length <= MAX_STORED_URL_LENGTH -> url
            else -> withoutFragment(url).takeIf { it.length <= MAX_STORED_URL_LENGTH }
                ?: url.substringBefore('?').takeIf { it.length <= MAX_STORED_URL_LENGTH }
        }

        private fun Tab.toEntity() = PersistedTab(
            id = id,
            url = storableUrl(url),
            title = title,
            groupId = groupId,
            position = position,
            desktopSite = desktopSite,
            openedByExternalApp = openedByExternalApp,
            closeOnBack = closeOnBack,
            fromNewTabPage = fromNewTabPage,
            chatId = chatId,
            createdAt = createdAt,
            lastAccessedAt = lastAccessedAt,
        )

        private fun PersistedTab.toTab() = Tab(
            id = id,
            incognito = false,
            url = url,
            title = title.cutTo(Tab.MAX_TITLE_LENGTH),
            groupId = groupId,
            position = position,
            desktopSite = desktopSite,
            openedByExternalApp = openedByExternalApp,
            closeOnBack = closeOnBack,
            fromNewTabPage = fromNewTabPage,
            chatId = chatId,
            createdAt = createdAt,
            lastAccessedAt = lastAccessedAt,
        )

        /** Normal tabs by group order then Other, each by position; incognito tabs last. */
        private fun sortTabs(tabs: List<Tab>, groups: List<TabGroup>): List<Tab> {
            val groupOrder = groups.withIndex().associate { it.value.id to it.index }
            return tabs.sortedWith(
                compareBy<Tab>(
                    { it.incognito },
                    { tab -> tab.groupId?.let(groupOrder::get) ?: Int.MAX_VALUE },
                    { it.position },
                    { it.createdAt },
                    { it.id },
                ),
            )
        }

        /** [this] small enough to sit in saved navigation state, or null when its URL is too long. */
        private fun HitTarget.bounded(): HitTarget? = when (this) {
            is HitTarget.Link -> takeIf { url.length <= MAX_HIT_URL_LENGTH }
                ?.copy(text = text?.cutTo(MAX_HIT_TEXT_LENGTH))
            is HitTarget.Image -> takeIf { src.length <= MAX_HIT_URL_LENGTH && (linkUrl?.length ?: 0) <= MAX_HIT_URL_LENGTH }
                ?.copy(alt = alt?.cutTo(MAX_HIT_TEXT_LENGTH))
        }
    }
}
