package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.MessageStatus
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** A chat stays with its page, and a memory-only chat is never written down. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChatSessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val fixtures = mutableListOf<TabManagerFixture>()
    private val appJobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        appJobs.forEach { it.cancel() }
        kotlinx.coroutines.runBlocking { fixtures.forEach { it.close() } }
    }

    @Test
    fun aSameDocumentNavigationKeepsTheChatAndANewPageDropsIt() = runTest(main.testDispatcher) {
        val open = open(this)
        val session = open.store.openForTab(open.tab(), articlePage(), PAGE_HOST)
        advanceUntilIdle()
        open.fixture.settle()
        assertEquals(session.chatId, open.tab().chatId)

        open.fixture.engine.navigateInDocument(open.id, "$PAGE_URL#section")
        advanceUntilIdle()
        assertEquals(session, open.store.sessionFor(open.id))
        assertEquals(session.chatId, open.tab().chatId)

        val stream = Job().also { appJobs += it }
        session.beginStream(stream, MessageKind.SUMMARY)
        session.noteText("partial summary")
        open.fixture.engine.openPage(open.id, "https://example.com/other", "Other")
        advanceUntilIdle()
        open.fixture.settle()

        assertNull(open.store.sessionFor(open.id))
        assertNull(open.tab().chatId)
        val saved = open.chats.messages(session.chatId)
        assertEquals(MessageStatus.STOPPED, saved.single().status)
        assertEquals("partial summary", saved.single().text)
    }

    @Test
    fun turningHistoryOffKeepsTheChatInMemoryOnly() = runTest(main.testDispatcher) {
        val open = open(this)
        open.fixture.settings.setKeepChatHistory(false)
        val session = open.store.openForTab(open.tab(), articlePage(), PAGE_HOST)
        open.store.record(session, message("A point."))
        advanceUntilIdle()
        open.fixture.settle()

        assertTrue(session.chatId < 0)
        assertEquals(0, open.chats.observeCount().first())
        assertNull(open.fixture.openRows().single().chatId)
        assertEquals("A point.", session.messages.value.single().text)
    }

    @Test
    fun recordingAMessageDoesNotRetieTheTab() = runTest(main.testDispatcher) {
        val open = open(this)
        val session = open.store.openForTab(open.tab(), articlePage(), PAGE_HOST)
        advanceUntilIdle()
        val tied = open.tab().chatId
        open.store.record(session, message("- The first point is the one shown on Home."))
        advanceUntilIdle()
        open.fixture.settle()

        assertEquals(tied, open.tab().chatId)
        assertEquals(
            "The first point is the one shown on Home.",
            open.chats.load(session.chatId)?.snippet,
        )
    }

    @Test
    fun deletingTheChatDropsTheSession() = runTest(main.testDispatcher) {
        val open = open(this)
        val session = open.store.openForTab(open.tab(), articlePage(), PAGE_HOST)
        advanceUntilIdle()
        open.fixture.settle()
        open.chats.delete(session.chatId)
        advanceUntilIdle()
        open.fixture.settle()

        assertNull(open.store.sessionFor(open.id))
        assertNull(open.tab().chatId)
    }

    private fun open(scope: TestScope): Open {
        val fixture = TabManagerFixture(scope, folder.newFolder()).also { fixtures += it }
        fixture.engines.attach(fixture.engine)
        val chats = ChatRepository(fixture.db.skimChatDao(), fixture.db)
        val appJob = SupervisorJob().also { appJobs += it }
        val store = chatSessionsForTest(
            chats = chats,
            tabs = fixture.manager,
            settings = fixture.settings,
            appScope = CoroutineScope(appJob + StandardTestDispatcher(scope.testScheduler)),
            mainDispatcher = fixture.main,
        )
        val id = fixture.manager.newTab(incognito = false, url = PAGE_URL)
        fixture.engine.openPage(id, PAGE_URL, "Why heat pumps are winning in cold climates")
        return Open(fixture, chats, store, id)
    }

    private class Open(
        val fixture: TabManagerFixture,
        val chats: ChatRepository,
        val store: ChatSessionStore,
        val id: String,
    ) {
        fun tab() = fixture.tab(id)!!
    }

    private fun message(text: String) = ChatMessage(
        id = 0,
        role = MessageRole.ASSISTANT,
        kind = MessageKind.SUMMARY,
        text = text,
        status = MessageStatus.COMPLETE,
    )
}
