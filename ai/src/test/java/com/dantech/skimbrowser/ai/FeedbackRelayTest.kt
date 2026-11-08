package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.browser.page.PageContent
import com.dantech.skimbrowser.domain.ai.ChatMessage
import com.dantech.skimbrowser.domain.ai.FeedbackDelivery
import com.dantech.skimbrowser.domain.ai.FeedbackFollowUp
import com.dantech.skimbrowser.domain.ai.FeedbackReport
import com.dantech.skimbrowser.domain.ai.FeedbackSender
import com.dantech.skimbrowser.domain.ai.FeedbackSurface
import com.dantech.skimbrowser.domain.ai.PageChats
import com.dantech.skimbrowser.domain.ai.PageConversation
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.chat.MessageStatus
import com.dantech.skimbrowser.domain.fake.FakeChatRepository
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.settings.SkimSettings
import com.dantech.skimbrowser.domain.tab.Tab
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A rating stays local until the person agrees, and a failed send is not treated as saved. */
class FeedbackRelayTest {

    @Test
    fun theFirstThumbAsksAndDoesNotCallTheProxy() = runTest {
        val harness = harness(this, sendRatings = null)
        harness.rate(MessageFeedback.GOOD)
        assertEquals(FeedbackFollowUp.NEEDS_CONSENT, harness.relay.afterLocalChange(harness.session, 7, FeedbackSurface.PAGE))
        assertTrue(harness.sender.reports.isEmpty())
        assertEquals(MessageFeedback.GOOD, harness.message().feedback)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun aDeclinedChoiceAndAnIncognitoPageStayOnThePhone() = runTest {
        val declined = harness(this, sendRatings = false)
        assertEquals(FeedbackFollowUp.LOCAL_ONLY, declined.relay.afterLocalChange(declined.session, 7, FeedbackSurface.PAGE))
        val hidden = harness(this, sendRatings = true, incognito = true)
        assertEquals(FeedbackFollowUp.LOCAL_ONLY, hidden.relay.afterLocalChange(hidden.session, 7, FeedbackSurface.PAGE))
        assertTrue(declined.sender.reports.isEmpty())
        assertTrue(hidden.sender.reports.isEmpty())
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun agreeingSendsOnlyTheNewRatingAndALaterClearUsesTheNextRevision() = runTest {
        val harness = harness(this, sendRatings = null)
        harness.rate(MessageFeedback.GOOD)
        harness.relay.rememberConsent(true)
        harness.relay.sendAfterConsent(harness.session, 7, FeedbackSurface.PAGE)
        testScheduler.advanceUntilIdle()
        val first = harness.sender.reports.single()
        assertEquals("answer-1", first.answerId)
        assertEquals(1, first.revision)
        assertEquals(MessageFeedback.GOOD, first.rating)
        assertEquals("1.0", first.appVersion)
        assertEquals(1, harness.message().feedbackSentRevision)

        harness.rate(MessageFeedback.NONE)
        harness.relay.afterLocalChange(harness.session, 7, FeedbackSurface.PAGE)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(1, 2), harness.sender.reports.map { it.revision })
        assertEquals(MessageFeedback.NONE, harness.sender.reports.last().rating)
    }

    @Test
    fun aFailedSendLeavesTheRevisionUnackedAndTheNextTrySendsItOnce() = runTest {
        val harness = harness(this, sendRatings = true, failTimes = 1)
        harness.rate(MessageFeedback.POOR)
        harness.relay.afterLocalChange(harness.session, 7, FeedbackSurface.PAGE)
        testScheduler.advanceUntilIdle()
        assertEquals(2, harness.sender.reports.size)
        assertEquals(1, harness.message().feedbackSentRevision)
        assertEquals(MessageFeedback.POOR, harness.message().feedback)
    }

    @Test
    fun anOlderAcceptanceDoesNotEraseANewerRevision() = runTest {
        val hold = CompletableDeferred<Unit>()
        val harness = harness(this, sendRatings = true, holdFirst = hold)
        harness.rate(MessageFeedback.GOOD)
        harness.relay.afterLocalChange(harness.session, 7, FeedbackSurface.PAGE)
        testScheduler.advanceUntilIdle()
        harness.rate(MessageFeedback.POOR)
        harness.relay.afterLocalChange(harness.session, 7, FeedbackSurface.PAGE)
        hold.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(2, harness.message().feedbackRevision)
        assertEquals(2, harness.message().feedbackSentRevision)
        assertEquals(MessageFeedback.POOR, harness.message().feedback)
    }

    @Test
    fun aStaleAckKeepsTheNewerRevisionAndRating() = runTest {
        val harness = harness(this, sendRatings = true)
        harness.chats.beforeApply = { _, revision, sent ->
            if (sent == revision) {
                harness.chats.beforeApply = null
                val current = harness.message()
                harness.session.replaceMessages(
                    listOf(
                        current.copy(
                            feedback = MessageFeedback.POOR,
                            feedbackAnswerId = current.feedbackAnswerId,
                            feedbackRevision = revision + 1,
                            feedbackSentRevision = 0,
                        ),
                    ),
                )
            }
        }
        harness.rate(MessageFeedback.GOOD)
        harness.relay.afterLocalChange(harness.session, 7, FeedbackSurface.PAGE)
        testScheduler.advanceUntilIdle()
        assertEquals(MessageFeedback.POOR, harness.message().feedback)
        assertEquals(2, harness.message().feedbackRevision)
        assertEquals(0, harness.message().feedbackSentRevision)
        assertEquals(MessageFeedback.GOOD, harness.sender.reports.single().rating)
    }

    @Test
    fun aStartupFlushDoesNotReplaceANewerRating() = runTest {
        val saved = FakeChatRepository()
        val chatId = saved.createChat(
            com.dantech.skimbrowser.domain.chat.SavedChat(
                kind = com.dantech.skimbrowser.domain.chat.ChatKind.PAGE,
                url = "https://example.com",
                host = "example.com",
                title = "Example",
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        saved.addMessage(
            com.dantech.skimbrowser.domain.chat.SavedMessage(
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                kind = MessageKind.SUMMARY,
                text = "A summary",
                feedback = MessageFeedback.GOOD,
                status = MessageStatus.COMPLETE,
                createdAt = 1,
                feedbackAnswerId = "answer-1",
                feedbackRevision = 1,
                feedbackSentRevision = 0,
            ),
        )
        val hold = CompletableDeferred<Unit>()
        val sender = ScriptSender(failTimes = 0, holdFirst = hold)
        val settings = FakeSettingsRepository(SkimSettings(sendRatings = true))
        FeedbackRelay(
            sessions = MemoryChats(ChatSession(
                chatId = chatId,
                tabId = "tab",
                pageUrl = "https://example.com",
                approvedHost = "example.com",
                sources = emptyList(),
                document = articlePage(),
                languageTag = "en",
            )),
            chats = saved,
            settings = settings,
            sender = sender,
            appVersion = "1.0",
            scope = this,
        )
        testScheduler.advanceUntilIdle()
        val newer = saved.messages(chatId).single().copy(
            feedback = MessageFeedback.POOR,
            feedbackRevision = 2,
            feedbackSentRevision = 0,
            text = "A newer summary",
        )
        saved.updateMessage(newer)
        hold.complete(Unit)
        testScheduler.advanceUntilIdle()
        val row = saved.messages(chatId).single()
        assertEquals(MessageFeedback.POOR, row.feedback)
        assertEquals(2, row.feedbackRevision)
        assertEquals(0, row.feedbackSentRevision)
        assertEquals("A newer summary", row.text)
        assertEquals(1, sender.reports.single().revision)
        assertEquals(MessageFeedback.GOOD, sender.reports.single().rating)
    }

    @Test
    fun revokingConsentStopsTheRetryAndKeepsTheLocalRating() = runTest {
        val hold = CompletableDeferred<Unit>()
        val harness = harness(this, sendRatings = true, holdFirst = hold, failAfterHold = true)
        harness.rate(MessageFeedback.POOR)
        harness.relay.afterLocalChange(harness.session, 7, FeedbackSurface.PAGE)
        testScheduler.advanceUntilIdle()
        harness.settings.setSendRatings(false)
        hold.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(1, harness.sender.reports.size)
        assertEquals(MessageFeedback.POOR, harness.message().feedback)
        assertEquals(1, harness.message().feedbackRevision)
        assertEquals(1, harness.message().feedbackSentRevision)
    }

    private fun harness(
        scope: CoroutineScope,
        sendRatings: Boolean?,
        incognito: Boolean = false,
        failTimes: Int = 0,
        holdFirst: CompletableDeferred<Unit>? = null,
        failAfterHold: Boolean = false,
    ): Harness {
        val session = ChatSession(
            chatId = 4,
            tabId = "tab",
            pageUrl = "https://example.com",
            approvedHost = "example.com",
            sources = listOf(AiSource("tab", incognito, "example.com")),
            document = articlePage(),
            languageTag = "en",
        )
        session.replaceMessages(
            listOf(
                ChatMessage(
                    id = 7,
                    role = MessageRole.ASSISTANT,
                    kind = MessageKind.SUMMARY,
                    text = "A summary",
                    status = MessageStatus.COMPLETE,
                ),
            ),
        )
        val chats = MemoryChats(session)
        val sender = ScriptSender(failTimes, holdFirst, failAfterHold)
        val settings = FakeSettingsRepository(SkimSettings(sendRatings = sendRatings))
        val relay = FeedbackRelay(
            sessions = chats,
            chats = FakeChatRepository(),
            settings = settings,
            sender = sender,
            appVersion = "1.0",
            scope = scope,
        )
        relay.ids = { "answer-1" }
        return Harness(relay, session, chats, sender, settings)
    }

    private class Harness(
        val relay: FeedbackRelay,
        val session: ChatSession,
        val chats: MemoryChats,
        val sender: ScriptSender,
        val settings: FakeSettingsRepository,
    ) {
        fun rate(feedback: MessageFeedback) {
            chats.setLocal(feedback)
        }

        fun message(): ChatMessage = session.messages.value.single()
    }
}

private class ScriptSender(
    private var failTimes: Int,
    private val holdFirst: CompletableDeferred<Unit>?,
    private val failAfterHold: Boolean = false,
) : FeedbackSender {
    val reports = CopyOnWriteArrayList<FeedbackReport>()
    val started = CompletableDeferred<Unit>()
    val finished = CompletableDeferred<Unit>()

    override suspend fun send(report: FeedbackReport): FeedbackDelivery {
        reports += report
        if (reports.size == 1) {
            started.complete(Unit)
            holdFirst?.await()
        }
        if (failAfterHold && holdFirst != null && reports.size == 1) return FeedbackDelivery.Failed
        if (failTimes > 0) {
            failTimes -= 1
            return FeedbackDelivery.Failed
        }
        val result = FeedbackDelivery.Accepted(report.revision)
        if (holdFirst != null && report.revision >= 2) finished.complete(Unit)
        return result
    }
}

private class MemoryChats(private val session: ChatSession) : PageChats {
    var beforeApply: (suspend (answerId: String, revision: Int, sentRevision: Int) -> Unit)? = null

    fun setLocal(feedback: MessageFeedback) {
        session.replaceMessages(session.messages.value.map { it.copy(feedback = feedback) })
    }

    override fun sessionFor(tabId: String) = session
    override fun sessionForChat(chatId: Long) = session
    override suspend fun openForTab(tab: Tab, content: PageContent, approvedHost: String) = session
    override suspend fun openSaved(chatId: Long, tab: Tab, content: PageContent) = session
    override suspend fun openTabs(title: String, tabCount: Int, pages: List<TabContext>) = session
    override suspend fun openSavedTabs(chatId: Long) = session
    override suspend fun record(session: PageConversation, message: ChatMessage) = Unit
    override suspend fun setFeedback(session: PageConversation, messageId: Long, feedback: MessageFeedback) = Unit
    override suspend fun stampFeedback(session: PageConversation, messageId: Long, answerId: String, revision: Int, sentRevision: Int) {
        beforeApply?.invoke(answerId, revision, sentRevision)
        val live = this.session.messages.value.first { it.id == messageId }
        val merged = mergeStamp(live, answerId, revision, sentRevision) ?: return
        this.session.replaceMessages(this.session.messages.value.map { if (it.id == messageId) merged else it })
    }
}
