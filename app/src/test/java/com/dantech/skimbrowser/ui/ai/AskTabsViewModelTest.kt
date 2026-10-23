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
import com.dantech.skimbrowser.ai.OverviewGroup
import com.dantech.skimbrowser.ai.SkimAiService
import com.dantech.skimbrowser.ai.TabsOverview
import com.dantech.skimbrowser.ai.encodeOverview
import com.dantech.skimbrowser.ai.provider
import com.dantech.skimbrowser.browser.FakeTabEngine.Call
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.MessageFeedback
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.MessageStatus
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.chat.SavedMessage
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.navigation.AskTabsKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Ask Skim about the open tabs sends one overview, then plain follow-ups, and does not ask again when restored. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AskTabsViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val stores = mutableListOf<ViewModelStore>()
    private val fixtures = mutableListOf<TabManagerFixture>()
    private val appJobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        appJobs.forEach { it.cancel() }
        kotlinx.coroutines.runBlocking { fixtures.forEach { it.close() } }
    }

    @Test
    fun aPromptSendsTheOverviewAndShowsTheGroups() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueue("tabs-overview.sse")
            val open = shell(this, server.baseUrl)
            open.tabs(7)
            val before = server.requestCount
            val model = open.model(AskTabsKey(prompt = "Summarize my 7 open tabs"))

            val shown = model.state.first { it.overview is TabsOverviewState.Ready }
            val ready = shown.overview as TabsOverviewState.Ready
            assertEquals(7, shown.tabCount)
            assertTrue(shown.showSubtitle)
            assertEquals("Summarize my 7 open tabs", shown.prompt)
            assertEquals("Your tabs cover three topics.", ready.intro)
            assertEquals(listOf("Heat pump research", "Osaka trip", "Other"), ready.groups.map { it.name })
            assertEquals(listOf("Which installer looked best?", "What is left to book in Osaka?"), ready.followUps)
            assertEquals(before + 1, server.requestCount)
            assertTrue(ready.groups.all { it.firstTabId != null })
        }
    }

    @Test
    fun anEmptySheetSendsNothing() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueueStatus(500)
            val open = shell(this, server.baseUrl)
            open.tabs(2)
            val before = server.requestCount
            val model = open.model(AskTabsKey())

            val shown = model.state.first { it.tabCount == 2 }
            assertTrue(shown.overview is TabsOverviewState.Idle)
            assertTrue(shown.prompt == null)
            assertEquals(before, server.requestCount)
        }
    }

    @Test
    fun noOpenTabsHidesTheSubtitleAndSendsNothing() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueueStatus(500)
            val open = shell(this, server.baseUrl)
            val before = server.requestCount
            val model = open.model(AskTabsKey(prompt = "Summarize my 0 open tabs"))

            val shown = model.state.first { it.prompt == "Summarize my 0 open tabs" }
            assertEquals(0, shown.tabCount)
            assertFalse(shown.showSubtitle)
            assertTrue(shown.overview is TabsOverviewState.Idle)
            assertEquals(before, server.requestCount)
        }
    }

    @Test
    fun openingOfflineSendsNothing() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueueStatus(500)
            val open = shell(this, server.baseUrl)
            open.tabs(3)
            open.fixture.network.goOffline()
            val before = server.requestCount
            val model = open.model(AskTabsKey(prompt = "Summarize my 3 open tabs"))

            val shown = model.state.first { it.overview is TabsOverviewState.Offline }
            assertEquals(3, shown.tabCount)
            assertFalse(shown.inputEnabled)
            assertEquals(before, server.requestCount)
        }
    }

    @Test
    fun aSavedChatSendsNothingAndKeepsItsTabCount() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueueStatus(500)
            val open = shell(this, server.baseUrl)
            val chatId = open.savedTabsChat()
            val before = server.requestCount
            val model = open.model(AskTabsKey(savedChatId = chatId))

            val shown = model.state.first { it.overview is TabsOverviewState.Ready }
            assertEquals(7, shown.tabCount)
            assertEquals("Saved intro.", (shown.overview as TabsOverviewState.Ready).intro)
            assertEquals(before, server.requestCount)
        }
    }

    @Test
    fun openingAgainDoesNotAskASecondTime() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueue("tabs-overview.sse")
            server.enqueueStatus(500)
            val open = shell(this, server.baseUrl)
            open.tabs(7)
            val saved = SavedStateHandle()
            val first = open.model(AskTabsKey(prompt = "Summarize my 7 open tabs"), saved)
            first.state.first { it.overview is TabsOverviewState.Ready }
            val sent = server.requestCount
            stores.last().clear()

            val second = open.model(AskTabsKey(prompt = "Summarize my 7 open tabs"), saved)
            val shown = second.state.first { it.overview is TabsOverviewState.Ready }
            assertEquals(7, shown.tabCount)
            assertEquals(sent, server.requestCount)
        }
    }

    @Test
    fun aFollowUpIsPlainText() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueue("tabs-overview.sse")
            server.enqueue("answer-plain.sse")
            val open = shell(this, server.baseUrl)
            open.tabs(7)
            val model = open.model(AskTabsKey(prompt = "Summarize my 7 open tabs"))
            model.state.first { it.overview is TabsOverviewState.Ready }
            server.take()

            model.ask("What changed?")
            val asked = model.state.first { it.turns.any { turn -> turn.answer?.contains("300 to 900") == true } }
            assertEquals("What changed?", asked.turns.single().question)
            val followUp = server.take().body.readUtf8()
            assertFalse(followUp.contains("json_schema"))
            assertTrue(followUp.contains("What changed?"))
        }
    }

    @Test
    fun unsafeNamesAndFollowUpsAreDropped() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueue("tabs-overview-injection.sse")
            val open = shell(this, server.baseUrl)
            open.tabs(6)
            val model = open.model(AskTabsKey(prompt = "Summarize my 6 open tabs"))

            val ready = model.state.first { it.overview is TabsOverviewState.Ready }.overview as TabsOverviewState.Ready
            assertEquals(listOf("Other", "Kept"), ready.groups.map { it.name })
            assertEquals(listOf("What changed?"), ready.followUps)
        }
    }

    @Test
    fun stopDropsAnOverviewThatHasNotFinished() = runTest(main.testDispatcher) {
        val server = FakeClaudeServer()
        val slow = server.serveSlowly("tabs-overview.sse", pauseAfter = "text_delta", pauseMs = 15_000)
        try {
            val open = shell(this, slow.baseUrl)
            open.tabs(4)
            val model = open.model(AskTabsKey(prompt = "Summarize my 4 open tabs"))
            model.state.first { it.generating }
            val started = System.currentTimeMillis()
            while (slow.written() == 0 && System.currentTimeMillis() - started < 8_000) {
                advanceUntilIdle()
                Thread.sleep(20)
            }
            assertTrue(slow.written() > 0)
            model.stop()
            val stopped = model.state.first { !it.generating }
            assertFalse(stopped.overview is TabsOverviewState.Ready)
            advanceUntilIdle()
            open.fixture.settle()
            val chatId = open.chats.observeAll(null).first().firstOrNull()?.chat?.id
            if (chatId != null) {
                assertTrue(open.chats.messages(chatId).none { it.kind == MessageKind.TABS_OVERVIEW })
            }
        } finally {
            slow.close()
            server.close()
        }
    }

    @Test
    fun feedbackTogglesOnTheOverview() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueue("tabs-overview.sse")
            val open = shell(this, server.baseUrl)
            open.tabs(7)
            val model = open.model(AskTabsKey(prompt = "Summarize my 7 open tabs"))
            model.state.first { it.overview is TabsOverviewState.Ready }

            model.feedback(good = true)
            val good = model.state.first { (it.overview as? TabsOverviewState.Ready)?.feedback == MessageFeedback.GOOD }
            assertEquals(MessageFeedback.GOOD, (good.overview as TabsOverviewState.Ready).feedback)
            model.feedback(good = true)
            val cleared = model.state.first { (it.overview as? TabsOverviewState.Ready)?.feedback == MessageFeedback.NONE }
            assertEquals(MessageFeedback.NONE, (cleared.overview as TabsOverviewState.Ready).feedback)
        }
    }

    @Test
    fun tabsWithoutALivePageAreNotGivenAWebView() = runTest(main.testDispatcher) {
        FakeClaudeServer().use { server ->
            server.enqueue("tabs-overview.sse")
            val open = shell(this, server.baseUrl)
            repeat(7) { index ->
                open.scope.testScheduler.advanceTimeBy(1)
                open.fixture.manager.newTab(incognito = false, url = "https://site$index.example/p")
            }
            val model = open.model(AskTabsKey(prompt = "Summarize my 7 open tabs"))
            model.state.first { it.overview is TabsOverviewState.Ready }
            assertTrue(open.fixture.engine.callsOf<Call.EnsureWebView>().isEmpty())
        }
    }

    private fun shell(scope: TestScope, baseUrl: String): Open {
        val fixture = TabManagerFixture(scope, folder.newFolder()).also { fixtures += it }
        fixture.manager.attach(fixture.engine)
        val chats = ChatRepository(fixture.db.skimChatDao(), fixture.db)
        val appJob = SupervisorJob().also { appJobs += it }
        val appScope = CoroutineScope(appJob + StandardTestDispatcher(scope.testScheduler))
        val sessions = chatSessionsForTest(chats, fixture.manager, fixture.settings, appScope, fixture.main)
        val sites = SitePermissionRepository(fixture.db.sitePermissionDao(), fixture.db)
        val ai = SkimAiService(provider(baseUrl), sites, fixture.settings)
        return Open(fixture, chats, sessions, ai, sites, appScope, scope)
    }

    private fun Open.tabs(count: Int) {
        repeat(count) { index ->
            scope.testScheduler.advanceTimeBy(1)
            val id = fixture.manager.newTab(incognito = false, url = "https://site$index.example/p")
            fixture.engine.openPage(id, "https://site$index.example/p", "Tab ${index + 1}")
        }
    }

    private suspend fun Open.savedTabsChat(): Long {
        val chatId = chats.createChat(
            SavedChat(
                kind = ChatKind.TABS,
                url = null,
                host = null,
                title = "Summarize my 7 open tabs",
                tabCount = 7,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        chats.addMessage(
            SavedMessage(
                chatId = chatId,
                role = MessageRole.USER,
                kind = MessageKind.QUESTION,
                text = "Summarize my 7 open tabs",
                status = MessageStatus.COMPLETE,
                createdAt = 1,
            ),
        )
        chats.addMessage(
            SavedMessage(
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                kind = MessageKind.TABS_OVERVIEW,
                text = encodeOverview(
                    TabsOverview(
                        intro = "Saved intro.",
                        groups = listOf(OverviewGroup("Heat pump research", "Sizing.", listOf("tab-1"))),
                        followUps = listOf("Which installer looked best?"),
                    ),
                ),
                status = MessageStatus.COMPLETE,
                createdAt = 2,
            ),
        )
        return chatId
    }

    private fun Open.model(key: AskTabsKey, saved: SavedStateHandle = SavedStateHandle()): AskTabsViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = viewModelFactory {
            initializer {
                AskTabsViewModel(
                    key = key,
                    ai = ai,
                    sessions = sessions,
                    tabs = fixture.manager,
                    sites = sites,
                    network = fixture.network,
                    savedState = saved,
                    appScope = appScope,
                )
            }
        }
        return ViewModelProvider(store, factory)[AskTabsViewModel::class.java]
    }

    private data class Open(
        val fixture: TabManagerFixture,
        val chats: ChatRepository,
        val sessions: ChatSessionStore,
        val ai: SkimAiService,
        val sites: SitePermissionRepository,
        val appScope: CoroutineScope,
        val scope: TestScope,
    )
}
