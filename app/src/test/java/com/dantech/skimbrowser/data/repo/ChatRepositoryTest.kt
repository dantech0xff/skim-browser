package com.dantech.skimbrowser.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.MessageStatus
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.SkimMessageEntity
import com.dantech.skimbrowser.testing.assertThrowsSuspending
import com.dantech.skimbrowser.testing.awaitItemMatching
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.longQuery
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChatRepositoryTest {

    private lateinit var db: SkimDatabase
    private lateinit var chats: ChatRepository

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        chats = ChatRepository(db.skimChatDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteReturnsTheChatWithItsMessagesAndSignalsItsId() = runTest {
        val id = savedChatWithMessages("Why heat pumps are winning")
        val chat = checkNotNull(chats.chat(id))
        val messages = chats.messages(id)

        chats.removals.test {
            val deleted = chats.delete(id)
            assertEquals(id, awaitItem())
            assertEquals(DeletedChat(chat.toDomain(), messages.map { it.toDomain() }), deleted)
            expectNoEvents()
        }
        assertNull(chats.chat(id))
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM skim_messages"))
    }

    @Test
    fun undoOfADeletedChatBringsBackTheSameIdsInOrder() = runTest {
        savedChatWithMessages("Older", updatedAt = 10)
        val id = savedChatWithMessages("Why heat pumps are winning", updatedAt = 20)
        savedChatWithMessages("Newer", updatedAt = 30)
        val listed = chats.observeAll(null).first()
        val messages = chats.messages(id)

        val deleted = checkNotNull(chats.delete(id))
        chats.restore(deleted)

        assertEquals(listed, chats.observeAll(null).first())
        assertEquals(messages, chats.messages(id))
    }

    @Test
    fun undoOfAChatThatIsStillSavedChangesNothing() = runTest {
        val id = savedChatWithMessages("Kept")
        val chat = checkNotNull(chats.chat(id))
        val messages = chats.messages(id)

        chats.restore(
            DeletedChat(
                chat.toDomain().copy(title = "Changed"),
                messages.map { it.toDomain().copy(text = "Changed") },
            ),
        )

        assertEquals(chat, chats.chat(id))
        assertEquals(messages, chats.messages(id))
    }

    @Test
    fun deleteAllSignalsNullEvenWithNothingSaved() = runTest {
        chats.removals.test {
            chats.deleteAll()
            assertNull(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun deleteAllRemovesEveryChatAndMessage() = runTest {
        savedChatWithMessages("A")
        savedChatWithMessages("B")

        chats.removals.test {
            chats.deleteAll()
            assertNull(awaitItem())
        }
        assertEquals(0, chats.observeCount().first())
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM skim_messages"))
    }

    @Test
    fun deletingAChatThatDoesNotExistSignalsNothing() = runTest {
        chats.removals.test {
            assertNull(chats.delete(404))
            assertNull(chats.delete(-5))
            expectNoEvents()
        }
    }

    @Test
    fun deletingWithNobodyListeningDoesNotWait() = runTest(timeout = 30.seconds) {
        val ids = (1..80).map { savedChatWithMessages("Chat $it") }

        ids.forEach { chats.delete(it) }
        chats.deleteAll()

        assertEquals(0, chats.observeCount().first())
    }

    @Test
    fun aBusyCollectorDoesNotHoldUpDeletionsAndStillGetsEverySignalInOrder() = runTest(timeout = 30.seconds) {
        val ids = (1..10).map { savedChatWithMessages("Chat $it") }
        val busy = CompletableDeferred<Unit>()
        val received = mutableListOf<Long?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            chats.removals.collect {
                received += it
                busy.await()
            }
        }

        // The collector is stuck on the first signal; the next ones must wait in the buffer.
        ids.forEach { chats.delete(it) }
        chats.deleteAll()
        busy.complete(Unit)
        advanceUntilIdle()

        assertEquals(ids + listOf<Long?>(null), received)
        collector.cancel()
    }

    @Test
    fun aNewChatAlwaysGetsAFreshPositiveId() = runTest {
        val id = chats.createChat(chat("In memory first", updatedAt = 1).copy(id = -7))

        assertTrue(id > 0)
        assertNull(chats.chat(-7))
        assertEquals("In memory first", chats.chat(id)?.title)
    }

    @Test
    fun messagesBelongToASavedChatAndGetFreshIds() = runTest {
        val chatId = chats.createChat(chat("Kotlin", updatedAt = 1))

        val id = chats.addMessage(message(chatId, MessageKind.QUESTION, MessageRole.USER).copy(id = -3))

        assertTrue(id > 0)
        assertEquals(listOf(id), chats.messages(chatId).map { it.id })
        assertThrowsSuspending<IllegalArgumentException> { chats.addMessage(message(-1, MessageKind.QUESTION, MessageRole.USER)) }
    }

    @Test
    fun updatesRewriteTheChatAndTheMessage() = runTest {
        val chatId = chats.createChat(chat("Kotlin", updatedAt = 1))
        val messageId = chats.addMessage(message(chatId, MessageKind.ANSWER, MessageRole.ASSISTANT).copy(status = MessageStatus.STOPPED))

        chats.updateChat(checkNotNull(chats.chat(chatId)).copy(snippet = "Kotlin 2.2 adds guard conditions", updatedAt = 9))
        chats.updateMessage(chats.messages(chatId).single().copy(text = "Full answer", status = MessageStatus.COMPLETE))

        assertEquals("Kotlin 2.2 adds guard conditions", chats.chat(chatId)?.snippet)
        val updated = chats.messages(chatId).single()
        assertEquals(messageId, updated.id)
        assertEquals("Full answer", updated.text)
        assertEquals(MessageStatus.COMPLETE, updated.status)
    }

    @Test
    fun skimHistoryRowsCanBeFilteredAndCounted() = runTest {
        savedChatWithMessages("Kotlin 2.2 release notes", host = "kotlinlang.org", updatedAt = 2)
        savedChatWithMessages("Why heat pumps are winning", host = "daylightjournal.com", updatedAt = 1)

        chats.observeCount().test {
            awaitItemMatching { it == 2 }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("Kotlin 2.2 release notes"), chats.observeAll(" kotlinlang ").first().map { it.chat.title })
        assertEquals(2, chats.observeAll("  ").first().size)
        val item = chats.observeAll("heat").first().single()
        assertEquals(1, item.questionCount)
        assertTrue(item.hasSummary)
        assertEquals(1, item.answerCount)
    }

    @Test
    fun recentPageChatsLeaveOutTabsChats() = runTest {
        chats.createChat(chat("Page", updatedAt = 1))
        chats.createChat(chat("Summarize my 7 open tabs", updatedAt = 2).copy(kind = ChatKind.TABS, url = null, host = null, tabCount = 7))

        assertEquals(listOf("Page"), chats.observeRecentPageChats(limit = 5).first().map { it.title })
        assertEquals(emptyList<SkimChatEntity>(), chats.observeRecentPageChats(limit = -1).first())
    }

    private suspend fun savedChatWithMessages(title: String, host: String = "daylightjournal.com", updatedAt: Long = 1): Long {
        val id = chats.createChat(chat(title, host = host, updatedAt = updatedAt))
        chats.addMessage(message(id, MessageKind.SUMMARY, MessageRole.ASSISTANT))
        chats.addMessage(message(id, MessageKind.QUESTION, MessageRole.USER))
        chats.addMessage(message(id, MessageKind.ANSWER, MessageRole.ASSISTANT))
        return id
    }

    private fun chat(title: String, host: String = "daylightjournal.com", updatedAt: Long) = SkimChatEntity(
        kind = ChatKind.PAGE,
        url = "https://$host/",
        host = host,
        title = title,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun message(chatId: Long, kind: MessageKind, role: MessageRole) =
        SkimMessageEntity(chatId = chatId, role = role, kind = kind, text = "$kind text", status = MessageStatus.COMPLETE, createdAt = 1)
}
