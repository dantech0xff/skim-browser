package com.dantech.skimbrowser.domain.tab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A tab, normal or incognito. Normal tabs are kept in Room and come back after process death;
 * incognito tabs exist only in memory.
 */
data class Tab(
    val id: String,
    val incognito: Boolean,
    /** The page the tab shows; null while it shows the New tab page. */
    val url: String?,
    /** The title the page set, at most [MAX_TITLE_LENGTH] characters; empty until the page sets one. */
    val title: String,
    /** The tab's group, or null outside every group ("Other"). Incognito tabs are never grouped. */
    val groupId: Long?,
    /** The order inside the tab's group, or inside Other; numbers may have gaps. */
    val position: Int,
    val desktopSite: Boolean = false,
    /** Opened by another app's link: Back on the first page closes the tab. */
    val openedByExternalApp: Boolean = false,
    /** Back on the first page closes the tab and returns to the screen that opened it. */
    val closeOnBack: Boolean = false,
    /** Started from the New tab page: Back on the first page returns to it. */
    val fromNewTabPage: Boolean = false,
    /** The tab's Skim chat: positive once saved, negative while it lives only in memory. */
    val chatId: Long? = null,
    /** The tab whose page opened this one as a popup; not kept across process death. */
    val openerTabId: String? = null,
    val createdAt: Long,
    val lastAccessedAt: Long,
) {
    companion object {
        /** The longest title kept; a page sets its own title and can make it as long as it likes. */
        const val MAX_TITLE_LENGTH = 1_024
    }
}

/** A group of normal tabs in Tabs (09). [createdBySkim] marks a group made by Skim's grouping. */
data class TabGroup(
    val id: Long,
    val title: String,
    val position: Int,
    val createdBySkim: Boolean = false,
)

/**
 * Every open tab and group.
 *
 * [tabs] lists normal tabs first, by group (in [groups] order) then Other, each by position, and
 * incognito tabs last, by position. Tabs closed while their Undo is still offered are not listed.
 * A group whose tabs all wait for Undo stays in [groups] without a tab until the close is final;
 * screens show only groups that have tabs. [layoutVersion] grows with every applied layout.
 */
data class TabsState(
    val tabs: List<Tab> = emptyList(),
    val groups: List<TabGroup> = emptyList(),
    val currentTabId: String? = null,
    val layoutVersion: Long = 0,
) {
    val currentTab: Tab? get() = currentTabId?.let(::tab)

    fun tab(id: String): Tab? = tabs.firstOrNull { it.id == id }
}

/**
 * The live state of a tab's page, reported by its WebView. Nothing here is kept across process
 * death or after the WebView is destroyed, except the navigation state and the last error.
 */
data class TabRuntime(
    /** Load progress from 0 to 1 while a page loads; null when nothing is loading. */
    val progress: Float? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** True when the committed page was loaded over https. */
    val isSecure: Boolean = false,
    val loadError: LoadError? = null,
    /** True when the page looks like an article Reader view can show. */
    val readerable: Boolean = false,
    /** Find in page, while its bar is open. */
    val find: FindState? = null,
    /** True once the current document finished loading in a live WebView. */
    val pageLoaded: Boolean = false,
    /** The URL of the document the WebView committed last, same-document navigations included. */
    val committedUrl: String? = null,
    /** Identifies the current document: a new one on every document load, unchanged by SPA navigation. */
    val loadNonce: Long = 0,
)

/** Why a main-frame load failed: the states of Page failed to load (11). */
sealed interface LoadError {
    val url: String

    /** The Chromium error name shown under the message, such as `ERR_NAME_NOT_RESOLVED`. */
    val codeName: String

    data class Unreachable(override val url: String, override val codeName: String) : LoadError

    data class NoInternet(override val url: String, override val codeName: String) : LoadError

    data class TimedOut(override val url: String, override val codeName: String) : LoadError

    /** The page's renderer crashed more often than the tab may recover from on its own. */
    data class Crashed(override val url: String, override val codeName: String) : LoadError
}

/** Find in page (05c). [activeMatchIndex] counts from 0; "0/0" shows when [matchCount] is 0. */
data class FindState(val query: String, val activeMatchIndex: Int = 0, val matchCount: Int = 0)

/** What a long-press on a page hit, for Link and Image options (05d). */
@Serializable
sealed interface HitTarget {
    @Serializable
    @SerialName("link")
    data class Link(val url: String, val text: String?) : HitTarget

    @Serializable
    @SerialName("image")
    data class Image(val src: String, val alt: String?, val linkUrl: String?) : HitTarget
}

/** Main-frame milestones of a tab's page, in the order the WebView reports them. */
sealed interface PageEvent {
    val tabId: String

    /** A new document committed; [loadNonce] identifies it until the next one. */
    data class PageStarted(override val tabId: String, val url: String, val loadNonce: Long) : PageEvent

    /**
     * A navigation committed and entered the history. [isSameDocument] is true when no new
     * document started since the previous one, as with `history.pushState` or a fragment change.
     */
    data class Navigated(override val tabId: String, val url: String, val isSameDocument: Boolean) : PageEvent

    data class PageFinished(override val tabId: String, val url: String, val loadNonce: Long) : PageEvent
}

/** A close that can still be undone, returned by [TabsController.close]. */
@JvmInline
value class CloseToken(val id: Long)

/**
 * A layout of the normal tabs for [TabsController.applyLayout]: [groups] in display order, each listing
 * its tabs in order, then the tabs outside every group ([ungrouped]) in order.
 */
@Serializable
data class GroupLayout(val groups: List<Group>, val ungrouped: List<String>) {
    /** A group; an [id] that is null, or that is not a current group, creates a new group. */
    @Serializable
    data class Group(
        val id: Long?,
        val title: String,
        val tabIds: List<String>,
        val createdBySkim: Boolean = false,
    )
}

/** The current layout of the normal tabs, groups without an open tab left out. */
fun TabsState.layout(): GroupLayout {
    val normal = tabs.filterNot { it.incognito }
    val byGroup = normal.groupBy { it.groupId }
    return GroupLayout(
        groups = groups.mapNotNull { group ->
            byGroup[group.id]?.let { members ->
                GroupLayout.Group(group.id, group.title, members.map { it.id }, group.createdBySkim)
            }
        },
        ungrouped = normal.filter { tab -> tab.groupId == null || groups.none { it.id == tab.groupId } }.map { it.id },
    )
}

/** The [layout] of [state]. Named so the browser package can re-export [TabsState.layout] without calling itself. */
fun layoutOf(state: TabsState): GroupLayout = state.layout()

/** The outcome of restoring saved tabs. */
sealed interface RestoreResult {
    /** The saved normal tabs are back; [tabCount] may be 0 on a fresh install. */
    data class Restored(val tabCount: Int) : RestoreResult

    /**
     * The saved tabs could not be read ([timedOut] tells why): the session started on a New tab
     * and its tabs are not saved, so the next start still finds the earlier ones.
     */
    data class Fallback(val timedOut: Boolean) : RestoreResult
}
