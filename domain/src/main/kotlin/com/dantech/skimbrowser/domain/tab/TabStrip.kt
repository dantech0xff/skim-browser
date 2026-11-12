package com.dantech.skimbrowser.domain.tab

/**
 * The tab a horizontal swipe on the browser bar would open. [title] is the page title, empty on
 * the New tab page. [groupId] is set only when this tab is in a different group from the current
 * one, including null when the swipe leaves a group for Other.
 */
data class TabNeighbor(
    val id: String,
    val title: String,
    val groupId: Long?,
    val changedGroup: Boolean,
)

/**
 * The next tab in the strip, or null at the end. The strip is the current tab's kind only
 * (normal or incognito), in the order [TabsState.tabs] already shows: groups, then Other.
 * [forward] moves toward the end of that strip.
 */
fun TabsState.adjacentTab(forward: Boolean): TabNeighbor? {
    val current = currentTab ?: return null
    val strip = tabs.filter { it.incognito == current.incognito }
    val index = strip.indexOfFirst { it.id == current.id }
    if (index < 0) return null
    val neighbor = strip.getOrNull(index + if (forward) 1 else -1) ?: return null
    val changedGroup = neighbor.groupId != current.groupId
    return TabNeighbor(
        id = neighbor.id,
        title = neighbor.title,
        groupId = if (changedGroup) neighbor.groupId else null,
        changedGroup = changedGroup,
    )
}
