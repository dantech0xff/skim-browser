package com.dantech.skimbrowser.domain.tab

import com.dantech.skimbrowser.domain.url.isWebUrl

/**
 * Incognito tabs are never restored. A saved row has no incognito flag; this still drops a tab
 * that was marked incognito, so a restored session cannot bring one back.
 */
fun tabsToRestore(tabs: List<Tab>): List<Tab> = tabs.filterNot { it.incognito }

/**
 * Splits a close that is being undone. Incognito tabs come back only while their session is still
 * open ([sessionOpen]). A session that is ending (its last tab's Undo is still offered) is open.
 * Once the session is gone, those tabs are [UndoCloseSplit.lost] and are not shown again.
 */
fun partitionUndoClose(tabs: List<Tab>, sessionOpen: Boolean): UndoCloseSplit {
    val (restorable, lost) = tabs.partition { tab -> !tab.incognito || sessionOpen }
    return UndoCloseSplit(restorable, lost)
}

/** Tabs that come back, and incognito tabs whose session has already ended. */
data class UndoCloseSplit(val restorable: List<Tab>, val lost: List<Tab>)

/** True when [closing] includes an incognito tab and [remaining] has none left. */
fun closesLastIncognitoTab(closing: List<Tab>, remaining: List<Tab>): Boolean =
    closing.any { it.incognito } && remaining.none { it.incognito }

/**
 * Puts [tabs] back into [existingGroupIds]. A tab whose group is gone goes to the end of Other,
 * starting at [firstOtherPosition] and counting up in list order.
 */
fun placeRestoredTabs(tabs: List<Tab>, existingGroupIds: Set<Long>, firstOtherPosition: Int): List<Tab> {
    var otherPosition = firstOtherPosition
    return tabs.map { tab ->
        if (tab.groupId == null || tab.groupId in existingGroupIds) {
            tab
        } else {
            tab.copy(groupId = null, position = otherPosition++)
        }
    }
}

/**
 * Which tab is current after Undo. The tab that was current comes back when it was restored and
 * nobody has selected another tab since the close ([currentTabId] is still [replacementId]).
 * When nothing is current, the restored tab used most recently becomes current.
 */
fun currentAfterUndo(
    restoredIds: Set<String>,
    previousCurrentId: String?,
    currentTabId: String?,
    replacementId: String?,
    restored: List<Tab>,
): String? = when {
    previousCurrentId in restoredIds && currentTabId == replacementId -> previousCurrentId
    currentTabId == null -> restored.maxByOrNull { it.lastAccessedAt }?.id
    else -> currentTabId
}

/**
 * A normal tab showing a web page. The New tab page has no URL, and an incognito tab is never
 * offered to Ask about open tabs or to Home's summarize action.
 */
fun Tab.isAskCandidate(): Boolean = !incognito && url?.let(::isWebUrl) == true

/** How many open tabs [isAskCandidate] accepts. */
fun pagesEligibleForAsk(state: TabsState): Int = state.tabs.count { it.isAskCandidate() }
