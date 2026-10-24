package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.isAskCandidate
import com.dantech.skimbrowser.domain.url.hostOf
import kotlinx.coroutines.flow.Flow

/** Asks for a summary of the page the chat was opened on. */
class SummarizePage(private val assistant: AiAssistant) {
    operator fun invoke(session: PageConversation): Flow<AiReply> = assistant.summarize(session)
}

/** Asks [question] about the page the chat was opened on. */
class AskPage(private val assistant: AiAssistant) {
    operator fun invoke(session: PageConversation, question: String): Flow<AiReply> = assistant.ask(session, question)
}

/** Sends the last summary or question again, without adding a turn. */
class RetryAnswer(private val assistant: AiAssistant) {
    operator fun invoke(session: PageConversation): Flow<AiReply> = assistant.retry(session)
}

/** Asks for one answer across the open tabs. */
class OverviewTabs(private val assistant: AiAssistant) {
    operator fun invoke(session: PageConversation, context: List<TabContext>, prompt: String): Flow<AiReply> =
        assistant.overviewTabs(session, context, prompt)
}

/** Asks [question] about the tabs already sent. */
class AskTabs(private val assistant: AiAssistant) {
    operator fun invoke(session: PageConversation, question: String): Flow<AiReply> = assistant.askTabs(session, question)
}

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
