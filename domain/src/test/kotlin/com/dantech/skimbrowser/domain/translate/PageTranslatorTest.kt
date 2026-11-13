package com.dantech.skimbrowser.domain.translate

import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.AiFailure
import com.dantech.skimbrowser.domain.ai.AiReply
import com.dantech.skimbrowser.domain.ai.NetworkAvailability
import com.dantech.skimbrowser.domain.ai.PageConversation
import com.dantech.skimbrowser.domain.ai.TabContext
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.fake.FakeSitePermissionRepository
import com.dantech.skimbrowser.domain.page.HighlightRequest
import com.dantech.skimbrowser.domain.page.PageContent
import com.dantech.skimbrowser.domain.tab.CloseToken
import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.domain.tab.GroupingSnapshot
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.TabsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageTranslatorTest {

    @Test
    fun anIncognitoPageIsNotSent() = runTest {
        val assistant = RecordingAssistant { flowOfText("1\tBonjour") }
        val translator = translator(tab(incognito = true), assistant, online = true, scope = this)
        translator.translate("tab", "fr-FR")
        advanceUntilIdle()

        val state = translator.sessions.value.getValue("tab")
        assertTrue(state is PageTranslation.Failed && state.reason == PageTranslation.Note.Incognito)
        assertEquals(0, assistant.sent.size)
    }

    @Test
    fun offlineDoesNotAskAndDoesNotChangeThePage() = runTest {
        val pages = pages(node(1, "Hello", true))
        val assistant = RecordingAssistant { flowOfText("1\tBonjour") }
        val translator = translator(tab(), assistant, online = false, pages = pages, scope = this)
        translator.translate("tab", "en-US")
        advanceUntilIdle()

        val state = translator.sessions.value.getValue("tab")
        assertTrue(state is PageTranslation.Failed && state.reason == PageTranslation.Note.Offline)
        assertEquals(0, assistant.sent.size)
        assertTrue(pages.replaced.isEmpty())
    }

    @Test
    fun theViewportChunkIsSentBeforeTheRestAndWrittenBack() = runTest {
        val seen = "a".repeat(1000)
        val later = "b".repeat(1000)
        val pages = pages(node(7, later, false), node(3, seen, true))
        val assistant = RecordingAssistant { nodes ->
            flowOfText(nodes.joinToString("\n") { "${it.id}\tX${it.id}" })
        }
        val translator = translator(tab(), assistant, online = true, pages = pages, scope = this)
        translator.translate("tab", "en-US")
        advanceUntilIdle()

        assertEquals(listOf(listOf(3), listOf(7)), assistant.sent.map { chunk -> chunk.map { it.id } })
        assertEquals(listOf(listOf(3), listOf(7)), pages.replaced.map { rows -> rows.map { it.id } })
        val ready = translator.sessions.value.getValue("tab")
        assertTrue(ready is PageTranslation.Ready && !ready.showingOriginal && ready.note == null)
    }

    @Test
    fun aLimitAfterOneChunkKeepsWhatWasTranslated() = runTest {
        val pages = pages(node(1, "a".repeat(1000), true), node(2, "b".repeat(1000), false))
        var calls = 0
        val assistant = RecordingAssistant {
            calls += 1
            if (calls == 1) flowOfText("1\tUn") else flow { emit(AiReply.Failed(AiFailure.DailyLimit)) }
        }
        val translator = translator(tab(), assistant, online = true, pages = pages, scope = this)
        translator.translate("tab", "fr")
        advanceUntilIdle()

        assertEquals(1, pages.replaced.size)
        val ready = translator.sessions.value.getValue("tab")
        assertTrue(ready is PageTranslation.Ready && ready.note == PageTranslation.Note.Limit)
    }

    @Test
    fun aSiteThatIsNotAllowedChangesNoText() = runTest {
        val pages = pages(node(1, "Hello", true))
        val assistant = RecordingAssistant { flow { emit(AiReply.Failed(AiFailure.NotAllowed(false))) } }
        val translator = translator(tab(), assistant, online = true, pages = pages, scope = this)
        translator.translate("tab", "en")
        advanceUntilIdle()

        val state = translator.sessions.value.getValue("tab")
        assertTrue(state is PageTranslation.Failed && state.reason == PageTranslation.Note.NotAllowed)
        assertEquals("example.com", (state as PageTranslation.Failed).host)
        assertTrue(pages.replaced.isEmpty())
    }

    @Test
    fun aNewDocumentDropsTheTranslation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val pages = pages(node(1, "Hello", true))
        val assistant = RecordingAssistant {
            flow {
                gate.await()
                emit(AiReply.Text("1\tBonjour"))
                emit(AiReply.Finished)
            }
        }
        val translator = translator(tab(), assistant, online = true, pages = pages, scope = this)
        translator.translate("tab", "fr")
        advanceUntilIdle()
        assertEquals(1, assistant.sent.size)
        pages.runtime.value = pages.runtime.value.copy(loadNonce = 2)
        translator.onDocument("tab", 2)
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(translator.sessions.value["tab"] == null)
        assertTrue(pages.replaced.isEmpty())
    }

    @Test
    fun showOriginalAfterDismissDoesNotBringTheBarBack() = runTest {
        val pages = pages(node(1, "Hello", true))
        val gate = CompletableDeferred<Unit>()
        pages.showGate = gate
        val translator = translator(tab(), RecordingAssistant { flowOfText("1\tBonjour") }, online = true, pages = pages, scope = this)
        translator.translate("tab", "fr")
        advanceUntilIdle()
        translator.showOriginal("tab", true)
        translator.dismiss("tab")
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(translator.sessions.value["tab"] == null)
    }

    @Test
    fun showOriginalUpdatesTheReadyBar() = runTest {
        val pages = pages(node(1, "Hello", true))
        val translator = translator(tab(), RecordingAssistant { flowOfText("1\tBonjour") }, online = true, pages = pages, scope = this)
        translator.translate("tab", "en")
        advanceUntilIdle()
        translator.showOriginal("tab", true)
        advanceUntilIdle()

        val ready = translator.sessions.value.getValue("tab")
        assertTrue(ready is PageTranslation.Ready && ready.showingOriginal)
    }

    private fun translator(
        tab: Tab,
        assistant: RecordingAssistant,
        online: Boolean,
        pages: ScriptTabs = pages(),
        scope: CoroutineScope,
    ): PageTranslator {
        pages.tabsState.value = TabsState(tabs = listOf(tab), currentTabId = tab.id)
        return PageTranslator(
            pages,
            assistant,
            FakeSettingsRepository(),
            object : NetworkAvailability {
                override val hasActiveNetwork = online
                override val validated = MutableStateFlow(online)
            },
            FakeSitePermissionRepository(),
            scope,
        )
    }
}

private class RecordingAssistant(
    private val reply: (List<PageTextNode>) -> Flow<AiReply>,
) : AiAssistant {
    val sent = mutableListOf<List<PageTextNode>>()

    override fun translateNodes(
        host: String,
        languageTag: String,
        nodes: List<PageTextNode>,
        incognito: Boolean,
    ): Flow<AiReply> {
        sent += nodes
        return reply(nodes)
    }

    override fun summarize(session: PageConversation): Flow<AiReply> = error("unused")
    override fun summarizeSaved(host: String, url: String, title: String, paragraphs: List<String>, languageTag: String) = error("unused")
    override fun ask(session: PageConversation, question: String) = error("unused")
    override fun retry(session: PageConversation) = error("unused")
    override fun overviewTabs(session: PageConversation, context: List<TabContext>, prompt: String) = error("unused")
    override fun askTabs(session: PageConversation, question: String) = error("unused")
}

private class ScriptTabs(private val collected: List<PageTextNode>) : TabsController {
    val tabsState = MutableStateFlow(TabsState())
    val runtime = MutableStateFlow(TabRuntime(pageLoaded = true, committedUrl = "https://example.com/", loadNonce = 1))
    val replaced = mutableListOf<List<TranslatedText>>()
    var showGate: CompletableDeferred<Unit>? = null
    var showResult: Boolean = true

    override fun runtime(tabId: String): StateFlow<TabRuntime> = runtime
    override suspend fun collectPageText(tabId: String) = collected
    override suspend fun replacePageText(tabId: String, rows: List<TranslatedText>): Boolean {
        replaced += rows
        return true
    }

    override suspend fun showOriginalPageText(tabId: String, original: Boolean): Boolean {
        showGate?.await()
        return showResult
    }
    override val state: StateFlow<TabsState> get() = tabsState
    override fun openNewTab(incognito: Boolean, url: String?, background: Boolean, openedByExternalApp: Boolean, closeOnBack: Boolean, openerTabId: String?) = error("unused")
    override fun openBackgroundTab(url: String, title: String) = error("unused")
    override fun select(tabId: String) = error("unused")
    override fun load(tabId: String, url: String) = error("unused")
    override fun returnToNewTabPage(tabId: String) = error("unused")
    override fun goBack(tabId: String) = error("unused")
    override fun goForward(tabId: String) = error("unused")
    override fun reload(tabId: String) = error("unused")
    override fun setDesktopSite(tabId: String, on: Boolean) = error("unused")
    override fun find(tabId: String, query: String) = error("unused")
    override fun findNext(tabId: String, forward: Boolean) = error("unused")
    override fun clearFind(tabId: String) = error("unused")
    override fun close(tabIds: List<String>): CloseToken = error("unused")
    override fun undoClose(token: CloseToken) = error("unused")
    override fun finalizeClose(token: CloseToken) = error("unused")
    override fun replaceLayout(layout: GroupLayout, expectedVersion: Long?, snapshot: GroupingSnapshot?) = error("unused")
    override suspend fun extractPage(tabId: String): PageContent? = error("unused")
    override suspend fun extractLivePage(tabId: String): PageContent? = error("unused")
    override suspend fun highlightBlock(tabId: String, request: HighlightRequest) = error("unused")
}

private fun pages(vararg nodes: PageTextNode) = ScriptTabs(nodes.toList())

private fun node(id: Int, text: String, inViewport: Boolean) = PageTextNode(id, text, inViewport)

private fun flowOfText(text: String): Flow<AiReply> = flow {
    emit(AiReply.Text(text))
    emit(AiReply.Finished)
}

private fun tab(incognito: Boolean = false) = Tab(
    id = "tab",
    incognito = incognito,
    url = "https://example.com/story",
    title = "Story",
    groupId = null,
    position = 0,
    createdAt = 0,
    lastAccessedAt = 0,
)
