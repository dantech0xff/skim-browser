package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.isAskCandidate
import com.dantech.skimbrowser.domain.url.hostOf

/**
 * The normal web tabs an across-tabs action may send, most recently used first, at most
 * [OpenTabLimits.MAX_TABS]. Incognito tabs, the New tab page, and sites set to ask again are left
 * out. [ungroupedOnly] keeps tabs that are not in a group, which is what grouping looks at.
 *
 * When [extractLive] is true, page text is read only from a tab that already has a live WebView.
 */
class CollectOpenTabs(
    private val tabs: TabsController,
    private val sites: SitePermissionRepository,
) {
    suspend operator fun invoke(extractLive: Boolean, ungroupedOnly: Boolean = false): List<TabContext> {
        val candidates = tabs.state.value.tabs
            .asSequence()
            .filter { tab -> tab.isAskCandidate() }
            .filter { tab -> !ungroupedOnly || tab.groupId == null }
            .sortedByDescending { it.lastAccessedAt }
            .toList()
        val hosts = candidates.mapNotNull { hostOf(it.url.orEmpty()) }.toSet()
        val states = sites.skimAiStates(hosts)
        val eligible = candidates
            .filter { tab ->
                val host = hostOf(tab.url.orEmpty()) ?: return@filter false
                states[host] != SkimAiState.ASK
            }
            .take(OpenTabLimits.MAX_TABS)
        return eligible.mapIndexed { index, tab ->
            val page = if (extractLive) tabs.extractLivePage(tab.id) else null
            val text = page?.blocks
                ?.joinToString("\n") { it.text }
                ?.take(OpenTabLimits.TEXT_CHARS)
                ?.ifBlank { null }
            TabContext(
                ref = (index + 1).toString(),
                tab = tab,
                title = page?.title?.ifBlank { null } ?: tab.title,
                host = hostOf(tab.url.orEmpty()).orEmpty(),
                text = text,
            )
        }
    }
}
