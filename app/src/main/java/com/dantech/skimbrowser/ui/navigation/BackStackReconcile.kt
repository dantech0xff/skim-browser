package com.dantech.skimbrowser.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.dantech.skimbrowser.browser.TabsState

/**
 * [keys] as they can still be shown with the open [tabs], for a back stack restored after the
 * Activity or the process went away. A key of a tab that is no longer open is dropped (incognito
 * tabs never come back after process death; closed tabs may have been purged meanwhile), Incognito
 * tabs (09b) become Tabs (09) once no incognito tab is open, and a Tabs screen forgets a focus tab
 * that is gone. Everything else keeps its order. The result is never empty.
 */
fun reconcileBackStack(keys: List<NavKey>, tabs: TabsState): List<NavKey> {
    val open = tabs.tabs.mapTo(HashSet()) { it.id }
    val anyIncognito = tabs.tabs.any { it.incognito }
    val kept = keys.mapNotNull { key ->
        when {
            key !is SkimKey -> null
            key.tabId?.let { it !in open } == true -> null
            key is TabsKey -> key.copy(
                incognito = key.incognito && anyIncognito,
                focusTabId = key.focusTabId?.takeIf { it in open },
            )
            else -> key
        }
    }
    return kept.ifEmpty { listOf(BrowserKey) }
}
