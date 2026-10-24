package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.fake.FakeSitePermissionRepository
import com.dantech.skimbrowser.domain.page.HighlightRequest
import com.dantech.skimbrowser.domain.page.PageBlock
import com.dantech.skimbrowser.domain.page.PageContent
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.tab.CloseToken
import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.domain.tab.GroupingSnapshot
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.TabsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The assistant use cases forward one action, and open-tab collection keeps the send rules. */
class AiUseCasesTest {

    @Test
    fun eachAssistantUseCaseForwardsToTheMatchingCall() {
        val assistant = RecordingAssistant()
        val session = UnusedConversation()
        val pages = emptyList<TabContext>()
        assertSame(assistant.replies, SummarizePage(assistant)(session))
        assertSame(assistant.replies, AskPage(assistant)(session, "Key numbers"))
        assertSame(assistant.replies, RetryAnswer(assistant)(session))
        assertSame(assistant.replies, OverviewTabs(assistant)(session, pages, "Summarize"))
        assertSame(assistant.replies, AskTabs(assistant)(session, "What changed?"))
        assertEquals(
            listOf(
                "summarize",
                "ask:Key numbers",
                "retry",
                "overview:Summarize",
                "askTabs:What changed?",
            ),
            assistant.calls,
        )
        assertEquals(listOf(session, session, session, session, session), assistant.sessions)
    }

    @Test
    fun openTabsLeaveOutIncognitoAskSitesAndPagesThatAreNotWeb() = runTest {
        val web = tab("web", "https://daylight.example/a", lastAccessedAt = 2)
        val older = tab("older", "https://notes.example/b", lastAccessedAt = 1)
        val hidden = tab("hidden", "https://secret.example/c", incognito = true, lastAccessedAt = 9)
        val blank = tab("blank", "about:blank", lastAccessedAt = 8)
        val grouped = tab("grouped", "https://grouped.example/d", groupId = 4, lastAccessedAt = 7)
        val ask = tab("ask", "https://ask.example/e", lastAccessedAt = 6)
        val sites = FakeSitePermissionRepository()
        sites.setSkimAi("ask.example", SkimAiState.ASK)
        val live = page("https://daylight.example/a", "Cold models.")
        val tabs = ScriptedTabs(
            listOf(web, older, hidden, blank, grouped, ask),
            livePages = mapOf(web.id to live),
        )

        val read = CollectOpenTabs(tabs, sites).invoke(extractLive = true)
        assertEquals(listOf("grouped", "web", "older"), read.map { it.tab.id })
        assertEquals(listOf("1", "2", "3"), read.map { it.ref })
        assertNull(read[0].text)
        assertEquals("Cold models.", read[1].text)
        assertEquals("daylight.example", read[1].host)
        assertNull(read[2].text)

        val titlesOnly = CollectOpenTabs(tabs, sites).invoke(extractLive = false, ungroupedOnly = true)
        assertEquals(listOf("web", "older"), titlesOnly.map { it.tab.id })
        assertNull(titlesOnly.first().text)
        assertEquals(3, tabs.liveReads)
    }

    @Test
    fun openTabsKeepAtMostTheLimitMostRecentlyUsed() = runTest {
        val tabs = (0..OpenTabLimits.MAX_TABS).map { index ->
            tab("t$index", "https://example.com/$index", lastAccessedAt = index.toLong())
        }
        val collected = CollectOpenTabs(ScriptedTabs(tabs), FakeSitePermissionRepository())
            .invoke(extractLive = false)
        assertEquals(OpenTabLimits.MAX_TABS, collected.size)
        assertEquals("t${OpenTabLimits.MAX_TABS}", collected.first().tab.id)
        assertEquals("t1", collected.last().tab.id)
    }

    private class RecordingAssistant : AiAssistant {
        val calls = mutableListOf<String>()
        val sessions = mutableListOf<PageConversation>()
        val replies: Flow<AiReply> = flowOf(AiReply.Finished)

        override fun summarize(session: PageConversation): Flow<AiReply> = record("summarize", session)

        override fun ask(session: PageConversation, question: String): Flow<AiReply> = record("ask:$question", session)

        override fun retry(session: PageConversation): Flow<AiReply> = record("retry", session)

        override fun overviewTabs(session: PageConversation, context: List<TabContext>, prompt: String): Flow<AiReply> =
            record("overview:$prompt", session)

        override fun askTabs(session: PageConversation, question: String): Flow<AiReply> =
            record("askTabs:$question", session)

        private fun record(name: String, session: PageConversation): Flow<AiReply> {
            calls += name
            sessions += session
            return replies
        }
    }

    private class ScriptedTabs(
        tabs: List<Tab>,
        private val livePages: Map<String, PageContent> = emptyMap(),
    ) : TabsController {
        var liveReads = 0
        override val state: StateFlow<TabsState> = MutableStateFlow(TabsState(tabs = tabs))
        private val idle = MutableStateFlow(TabRuntime())

        override fun runtime(tabId: String): StateFlow<TabRuntime> = idle

        override fun openNewTab(
            incognito: Boolean,
            url: String?,
            background: Boolean,
            openedByExternalApp: Boolean,
            closeOnBack: Boolean,
            openerTabId: String?,
        ): String = error("unused")

        override fun select(tabId: String) = error("unused")
        override fun load(tabId: String, url: String) = error("unused")
        override fun returnToNewTabPage(tabId: String) = error("unused")
        override fun goBack(tabId: String): Boolean = error("unused")
        override fun goForward(tabId: String) = error("unused")
        override fun reload(tabId: String) = error("unused")
        override fun setDesktopSite(tabId: String, on: Boolean) = error("unused")
        override fun find(tabId: String, query: String) = error("unused")
        override fun findNext(tabId: String, forward: Boolean) = error("unused")
        override fun clearFind(tabId: String) = error("unused")
        override fun close(tabIds: List<String>): CloseToken = error("unused")
        override fun undoClose(token: CloseToken) = error("unused")
        override fun finalizeClose(token: CloseToken) = error("unused")
        override fun replaceLayout(layout: GroupLayout, expectedVersion: Long?, snapshot: GroupingSnapshot?): Boolean =
            error("unused")

        override suspend fun extractPage(tabId: String): PageContent? = error("unused")

        override suspend fun extractLivePage(tabId: String): PageContent? {
            liveReads += 1
            return livePages[tabId]
        }

        override suspend fun highlightBlock(tabId: String, request: HighlightRequest): Boolean = error("unused")
    }
}

private fun tab(
    id: String,
    url: String?,
    incognito: Boolean = false,
    groupId: Long? = null,
    lastAccessedAt: Long = 0,
) = Tab(
    id = id,
    incognito = incognito,
    url = url,
    title = id,
    groupId = groupId,
    position = 0,
    createdAt = 0,
    lastAccessedAt = lastAccessedAt,
)

private fun page(url: String, text: String) = PageContent(
    url = url,
    loadNonce = 1,
    title = "Title",
    byline = null,
    siteName = null,
    lang = null,
    readerable = false,
    blocks = listOf(PageBlock(id = "0", tag = "p", text = text)),
)

private class UnusedConversation : PageConversation {
    override val chatId = 0L
    override val tabId = ""
    override val pageUrl = ""
    override val document = page("", "")
    override val tabCount = 0
    override val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override var streaming = false
    override var draft = ""
    override var draftKind = MessageKind.SUMMARY
    override var draftCitations = emptyList<Int>()
    override val canRetry = false
    override val hasOpening = false
    override val hasSources = false
    override fun cancelStream() = Unit
    override fun takeStop() = false
    override fun beginStream(job: Job, kind: MessageKind) = Unit
    override fun noteText(delta: String) = Unit
    override fun noteCite(start: Int) = Unit
    override fun endStream() = Unit
    override fun commitPendingReply(keep: Boolean) = Unit
    override fun replacePages(pages: List<TabContext>) = Unit
    override fun ensureOverviewOpening(pages: List<TabContext>, prompt: String) = Unit
    override fun rememberAssistantText(text: String) = Unit
    override fun stoppedMessage(): ChatMessage = error("unused")
}
