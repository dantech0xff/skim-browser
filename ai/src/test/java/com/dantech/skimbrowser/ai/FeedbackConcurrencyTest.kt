package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.ai.ChatMessage
import com.dantech.skimbrowser.domain.ai.FeedbackDelivery
import com.dantech.skimbrowser.domain.ai.FeedbackReport
import com.dantech.skimbrowser.domain.ai.FeedbackSender
import com.dantech.skimbrowser.domain.ai.FeedbackSurface
import com.dantech.skimbrowser.domain.ai.PageChats
import com.dantech.skimbrowser.domain.ai.PageConversation
import com.dantech.skimbrowser.domain.chat.ChatKind
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.chat.MessageStatus
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.chat.SavedMessage
import com.dantech.skimbrowser.domain.fake.FakeChatRepository
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.page.PageContent
import com.dantech.skimbrowser.domain.settings.SkimSettings
import com.dantech.skimbrowser.domain.tab.Tab
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Premise under test: a check-then-write of a previously read message, and cancelling whichever
 * send is in the map, stays correct when the main thread and the app scope both change that
 * message. It does not. The rating fields are applied to the row held inside one lock, and a
 * startup send does not cancel a send that a newer thumb already started.
 */
class FeedbackConcurrencyTest {

    @Test
    fun anAckWaitsForTheLockSoATapKeepsItsRatingAndText() {
        val session = session()
        val seenDuringAck = AtomicReference<MessageFeedback>()
        val ackEntered = CountDownLatch(1)
        val releaseAck = CountDownLatch(1)
        val ack = Thread {
            session.editMessage(7) { current ->
                ackEntered.countDown()
                releaseAck.await(5, TimeUnit.SECONDS)
                seenDuringAck.set(current.feedback)
                mergeStamp(current, "answer-1", current.feedbackRevision, 1)
            }
        }
        ack.start()
        assertTrue(ackEntered.await(5, TimeUnit.SECONDS))
        val tap = Thread {
            session.editMessage(7) { it.copy(feedback = MessageFeedback.POOR) }
        }
        tap.start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (tap.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield()
        assertEquals(Thread.State.BLOCKED, tap.state)
        releaseAck.countDown()
        ack.join(5_000)
        tap.join(5_000)
        val row = session.messages.value.single()
        assertEquals(MessageFeedback.GOOD, seenDuringAck.get())
        assertEquals(MessageFeedback.POOR, row.feedback)
        assertEquals("Current summary", row.text)
        assertEquals(1, row.feedbackRevision)
        assertEquals(1, row.feedbackSentRevision)
    }

    @Test
    fun startupFlushDoesNotCancelAThumbThatStartsWhileTheSnapshotIsHeld() = runTest {
        val saved = FakeChatRepository()
        val chatId = saved.createChat(
            SavedChat(
                kind = ChatKind.PAGE,
                url = "https://example.com",
                host = "example.com",
                title = "Example",
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        val messageId = saved.addMessage(
            SavedMessage(
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                kind = MessageKind.SUMMARY,
                text = "Current summary",
                feedback = MessageFeedback.GOOD,
                status = MessageStatus.COMPLETE,
                createdAt = 1,
                feedbackAnswerId = "answer-1",
                feedbackRevision = 1,
                feedbackSentRevision = 0,
            ),
        )
        val session = session(messageId)
        val sender = RecordingSender()
        val relayBox = arrayOfNulls<FeedbackRelay>(1)
        saved.beforeUnsent = {
            session.editMessage(messageId) { it.copy(feedback = MessageFeedback.POOR) }
            relayBox[0]?.afterLocalChange(session, messageId, FeedbackSurface.PAGE)
        }
        relayBox[0] = FeedbackRelay(
            sessions = IdlePages,
            chats = saved,
            settings = FakeSettingsRepository(SkimSettings(sendRatings = true)),
            sender = sender,
            appVersion = "1.0",
            scope = this,
        )
        testScheduler.advanceUntilIdle()
        val row = session.messages.value.single()
        assertEquals(MessageFeedback.POOR, row.feedback)
        assertEquals("Current summary", row.text)
        assertEquals(2, row.feedbackRevision)
        assertTrue(sender.reports.any { it.revision == 2 && it.rating == MessageFeedback.POOR })
        assertTrue(sender.reports.none { it.revision == 1 && it.rating == MessageFeedback.GOOD })
    }

    private fun session(id: Long = 7): ChatSession {
        val session = ChatSession(
            chatId = 4,
            tabId = "tab",
            pageUrl = "https://example.com",
            approvedHost = "example.com",
            sources = emptyList(),
            document = articlePage(),
            languageTag = "en",
        )
        session.replaceMessages(
            listOf(
                ChatMessage(
                    id = id,
                    role = MessageRole.ASSISTANT,
                    kind = MessageKind.SUMMARY,
                    text = "Current summary",
                    feedback = MessageFeedback.GOOD,
                    status = MessageStatus.COMPLETE,
                    feedbackAnswerId = "answer-1",
                    feedbackRevision = 1,
                    feedbackSentRevision = 0,
                ),
            ),
        )
        return session
    }
}

private class RecordingSender : FeedbackSender {
    val reports = mutableListOf<FeedbackReport>()

    override suspend fun send(report: FeedbackReport): FeedbackDelivery {
        reports += report
        return FeedbackDelivery.Accepted(report.revision)
    }
}

private object IdlePages : PageChats {
    override fun sessionFor(tabId: String): PageConversation? = null
    override fun sessionForChat(chatId: Long): PageConversation? = null
    override suspend fun openForTab(tab: Tab, content: PageContent, approvedHost: String) = error("unused")
    override suspend fun openSaved(chatId: Long, tab: Tab, content: PageContent) = null
    override suspend fun openTabs(title: String, tabCount: Int, pages: List<com.dantech.skimbrowser.domain.ai.TabContext>) = error("unused")
    override suspend fun openSavedTabs(chatId: Long) = null
    override suspend fun record(session: PageConversation, message: ChatMessage) = Unit
    override suspend fun setFeedback(session: PageConversation, messageId: Long, feedback: MessageFeedback) = Unit
    override suspend fun stampFeedback(session: PageConversation, messageId: Long, answerId: String, revision: Int, sentRevision: Int) = Unit
}
