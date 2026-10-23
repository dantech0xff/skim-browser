package com.dantech.skimbrowser.ui.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.ai.ChatSessionStore
import com.dantech.skimbrowser.ai.chatSessionsForTest
import com.dantech.skimbrowser.ai.FakeClaudeServer
import com.dantech.skimbrowser.ai.PAGE_HOST
import com.dantech.skimbrowser.ai.PAGE_URL
import com.dantech.skimbrowser.ai.SkimAiService
import com.dantech.skimbrowser.ai.articlePage
import com.dantech.skimbrowser.ai.provider
import com.dantech.skimbrowser.browser.FakePageScripts
import com.dantech.skimbrowser.browser.FakeTabEngine.Call
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.browser.page.HighlightArgs
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.MessageStatus
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.chat.SavedMessage
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.domain.usecase.LoadSavedChat
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.navigation.AskPageKey
import com.dantech.skimbrowser.ui.reader.ReaderHighlights
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Ask about this page sends a summary only when the person just opened a page that can be read. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AskPageViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val server = FakeClaudeServer()
    private val stores = mutableListOf<ViewModelStore>()
    private val fixtures = mutableListOf<TabManagerFixture>()
    private val appJobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        appJobs.forEach { it.cancel() }
        server.close()
        kotlinx.coroutines.runBlocking { fixtures.forEach { it.close() } }
    }

    @Test
    fun aShortPageIsNotSummarized() = runTest(main.testDispatcher) {
        server.enqueueStatus(500)
        val open = ready(this, pageJson("Too short."))
        open.allow()
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST))

        val shown = model.state.first { it.summary is SummaryState.NotEnoughText }
        assertTrue(shown.inputEnabled)
        assertFalse(shown.showChips)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun openingOfflineSendsNothing() = runTest(main.testDispatcher) {
        server.enqueueStatus(500)
        val open = ready(this, longPageJson())
        open.fixture.network.goOffline()
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST))

        val shown = model.state.first { it.summary is SummaryState.Offline }
        assertFalse(shown.inputEnabled)
        assertEquals(0, server.requestCount)
        assertNoExtract(open)
    }

    @Test
    fun aRestoredSheetSendsNothing() = runTest(main.testDispatcher) {
        server.enqueueStatus(500)
        val open = ready(this, longPageJson())
        open.allow()
        val saved = SavedStateHandle(mapOf("openHandled" to true))
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST), saved)

        val shown = model.state.first { it.summary is SummaryState.Interrupted }
        assertEquals("", (shown.summary as SummaryState.Interrupted).text)
        assertEquals(0, server.requestCount)
        assertNoExtract(open)
    }

    @Test
    fun closingMidStreamKeepsThePartialSummary() = runTest(main.testDispatcher) {
        val slow = server.serveSlowly("slow-stream.sse", pauseMs = 15_000)
        val open = ready(this, longPageJson(), baseUrl = slow.baseUrl)
        open.allow()
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST))
        try {
            model.state.first { (it.summary as? SummaryState.Streaming)?.text?.contains("First.") == true }
            stores.last().clear()
            advanceUntilIdle()
            open.fixture.settle()

            val saved = open.chats.messages(open.fixture.tab(open.id)!!.chatId!!).single()
            assertEquals(MessageStatus.STOPPED, saved.status)
            assertEquals(MessageKind.SUMMARY, saved.kind)
            assertTrue(saved.text.contains("First."))
            assertFalse(saved.text.contains("STILL_GOING"))
        } finally {
            slow.close()
        }
    }

    @Test
    fun stopKeepsTheTextThatArrived() = runTest(main.testDispatcher) {
        val slow = server.serveSlowly("slow-stream.sse", pauseMs = 15_000)
        val open = ready(this, longPageJson(), baseUrl = slow.baseUrl)
        open.allow()
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST))
        try {
            model.state.first { (it.summary as? SummaryState.Streaming)?.text?.contains("First.") == true }
            model.stop()
            val stopped = model.state.first { it.summary is SummaryState.Interrupted }
            val text = (stopped.summary as SummaryState.Interrupted).text
            assertTrue(text.contains("First."))
            assertFalse(text.contains("STILL_GOING"))
            assertFalse(stopped.generating)
        } finally {
            slow.close()
        }
    }

    @Test
    fun aServerErrorShowsTheErrorCard() = runTest(main.testDispatcher) {
        server.enqueueStatus(500)
        val open = ready(this, longPageJson())
        open.allow()
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST))

        val shown = model.state.first { it.summary is SummaryState.ServerError }
        assertTrue(shown.inputEnabled)
        assertFalse(shown.generating)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aRefusalKeepsNoText() = runTest(main.testDispatcher) {
        server.enqueue("refusal.sse")
        val open = ready(this, longPageJson())
        open.allow()
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST))

        val shown = model.state.first { it.summary is SummaryState.Refused }
        assertTrue(shown.summary is SummaryState.Refused)
        advanceUntilIdle()
        open.fixture.settle()
        val saved = open.chats.messages(open.fixture.tab(open.id)!!.chatId!!).single()
        assertEquals(MessageStatus.REFUSED, saved.status)
        assertEquals("", saved.text)
    }

    @Test
    fun aChipSendsItsOwnLabel() = runTest(main.testDispatcher) {
        server.enqueue("summary-with-citations.sse")
        server.enqueue("answer-plain.sse")
        val open = ready(this, longPageJson())
        open.allow()
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST))

        model.state.first { it.summary is SummaryState.Done }
        model.ask("Key numbers")
        val asked = model.state.first { it.turns.any { turn -> turn.question == "Key numbers" && turn.answer != null } }
        assertEquals("Key numbers", asked.turns.single().question)
        advanceUntilIdle()
        open.fixture.settle()
        val question = open.chats.messages(open.fixture.tab(open.id)!!.chatId!!)
            .single { it.kind == MessageKind.QUESTION }
        assertEquals("Key numbers", question.text)
    }

    @Test
    fun aCitationIsPassedAsJsonAndANewLoadTurnsTheChipOff() = runTest(main.testDispatcher) {
        server.enqueue("citation-injection.sse")
        val open = ready(this, longPageJson())
        open.allow()
        val model = open.model(AskPageKey(tabId = open.id, approvedHost = PAGE_HOST))

        val ready = model.state.first { it.summary is SummaryState.Done }
        assertTrue(ready.citationsEnabled)
        model.highlight(1)
        advanceUntilIdle()

        val script = open.fixture.engine.callsOf<Call.Evaluate>().last { it.script.startsWith("skimHighlight(") }.script
        assertTrue(script.endsWith(")"))
        val argsJson = script.removePrefix("skimHighlight(").removeSuffix(")")
        val args = Json.decodeFromString(HighlightArgs.serializer(), argsJson)
        assertEquals("\"]'),fetch(`\${x}`)", args.cited)
        assertTrue(argsJson.contains("\\\""))
        assertTrue(argsJson.contains("]'),fetch("))
        assertTrue(argsJson.contains("\${x}"))

        open.fixture.engine.openPage(open.id, PAGE_URL)
        val disabled = model.state.first { !it.citationsEnabled }
        assertFalse(disabled.citationsEnabled)
    }

    @Test
    fun aSavedChatOnAnIncognitoTabOpensANormalTabAndDoesNotSummarize() = runTest(main.testDispatcher) {
        server.enqueueStatus(500)
        val open = shell(this)
        val privateId = open.fixture.manager.newTab(incognito = true, url = "https://example.com/private")
        val chatId = open.savedChat()
        val model = open.model(AskPageKey(savedChatId = chatId))

        val shown = model.state.first { it.summary is SummaryState.Done }
        assertEquals(listOf("Saved point."), (shown.summary as SummaryState.Done).bullets)
        assertEquals(0, server.requestCount)
        val opened = open.fixture.manager.state.value.tabs.single { !it.incognito }
        assertEquals(PAGE_URL, opened.url)
        assertEquals(chatId, opened.chatId)
        assertNull(open.fixture.tab(privateId)?.chatId)
    }

    @Test
    fun aSavedChatLoadsOnTheCurrentNormalTab() = runTest(main.testDispatcher) {
        server.enqueueStatus(500)
        val open = shell(this)
        val id = open.fixture.manager.newTab(incognito = false, url = "https://example.com/other")
        open.fixture.engine.openPage(id, "https://example.com/other", "Other")
        val chatId = open.savedChat()
        val model = open.model(AskPageKey(savedChatId = chatId))

        val shown = model.state.first { it.summary is SummaryState.Done }
        assertEquals(listOf("Saved point."), (shown.summary as SummaryState.Done).bullets)
        assertEquals(0, server.requestCount)
        assertEquals(Call.Load(id, PAGE_URL), open.fixture.engine.callsOf<Call.Load>().single())
        assertEquals(listOf(id), open.fixture.tabIds())
        assertEquals(chatId, open.fixture.tab(id)?.chatId)
    }

    /** The readable-page check evaluates too. A summary reads the page with the extract script. */
    private fun assertNoExtract(open: Open) {
        val extracts = open.fixture.engine.callsOf<Call.Evaluate>().filter { it.script == FakePageScripts.EXTRACT }
        assertTrue(extracts.isEmpty())
    }

    private fun ready(scope: TestScope, json: String, baseUrl: String = server.baseUrl): Open {
        val open = shell(scope, baseUrl)
        open.fixture.engine.evaluator = { _, script -> if (script == FakePageScripts.EXTRACT) json else null }
        val id = open.fixture.manager.newTab(incognito = false, url = PAGE_URL)
        open.fixture.engine.openPage(id, PAGE_URL, "Why heat pumps are winning in cold climates")
        return open.copy(id = id)
    }

    private fun shell(scope: TestScope, baseUrl: String = server.baseUrl): Open {
        val fixture = TabManagerFixture(scope, folder.newFolder()).also { fixtures += it }
        fixture.manager.attach(fixture.engine)
        fixture.engine.onWebViewCreated = onCreated@{ tabId ->
            val url = fixture.tab(tabId)?.url ?: return@onCreated
            fixture.engine.openPage(tabId, url, "Saved page")
        }
        val chats = ChatRepository(fixture.db.skimChatDao(), fixture.db)
        val appJob = SupervisorJob().also { appJobs += it }
        val appScope = CoroutineScope(appJob + StandardTestDispatcher(scope.testScheduler))
        val sessions = chatSessionsForTest(chats, fixture.manager, fixture.settings, appScope, fixture.main)
        val sites = SitePermissionRepository(fixture.db.sitePermissionDao(), fixture.db)
        val ai = SkimAiService(provider(baseUrl), sites, fixture.settings)
        return Open(fixture, chats, sessions, ai, appScope, sites, id = "")
    }

    private suspend fun Open.allow() {
        fixture.settings.setAskBeforeSending(false)
        sites.setSkimAi(PAGE_HOST, SkimAiState.ALLOWED)
    }

    private suspend fun Open.savedChat(): Long {
        val chatId = chats.createChat(
            SavedChat(
                kind = ChatKind.PAGE,
                url = PAGE_URL,
                host = PAGE_HOST,
                title = "Why heat pumps are winning in cold climates",
                snippet = "Saved point.",
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        chats.addMessage(
            SavedMessage(
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                kind = MessageKind.SUMMARY,
                text = "- Saved point.",
                status = MessageStatus.COMPLETE,
                createdAt = 1,
            ),
        )
        return chatId
    }

    private fun Open.model(key: AskPageKey, saved: SavedStateHandle = SavedStateHandle()): AskPageViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = viewModelFactory {
            initializer {
                AskPageViewModel(
                    key = key,
                    tabs = fixture.manager,
                    sessions = sessions,
                    ai = ai,
                    network = fixture.network,
                    readerHighlights = ReaderHighlights(),
                    loadSavedChat = LoadSavedChat(chats),
                    savedState = saved,
                    appScope = appScope,
                )
            }
        }
        return ViewModelProvider(store, factory)[AskPageViewModel::class.java]
    }

    private data class Open(
        val fixture: TabManagerFixture,
        val chats: ChatRepository,
        val sessions: ChatSessionStore,
        val ai: SkimAiService,
        val appScope: CoroutineScope,
        val sites: SitePermissionRepository,
        val id: String,
    )

    private fun pageJson(vararg paragraphs: String): String {
        val blocks = paragraphs.mapIndexed { index, text ->
            """{"id":"$index","tag":"p","text":${Json.encodeToString(text)}}"""
        }
        val title = Json.encodeToString("Why heat pumps are winning in cold climates")
        return """{"title":$title,"readerable":true,"blocks":[${blocks.joinToString(",")}]}"""
    }

    private fun longPageJson(): String = pageJson(*articlePage().blocks.map { it.text }.toTypedArray())
}
