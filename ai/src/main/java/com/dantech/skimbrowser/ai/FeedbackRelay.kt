package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.ai.FeedbackDelivery
import com.dantech.skimbrowser.domain.ai.FeedbackFollowUp
import com.dantech.skimbrowser.domain.ai.FeedbackReport
import com.dantech.skimbrowser.domain.ai.FeedbackSender
import com.dantech.skimbrowser.domain.ai.FeedbackSurface
import com.dantech.skimbrowser.domain.ai.MessageFeedbackSync
import com.dantech.skimbrowser.domain.ai.PageChats
import com.dantech.skimbrowser.domain.ai.PageConversation
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Sends a rating only after the person has agreed, and never for an incognito source.
 * The local rating is already stored before this runs. A failed send stays unsent.
 */
@Singleton
class FeedbackRelay @Inject constructor(
    private val sessions: PageChats,
    private val chats: ChatRepository,
    private val settings: SettingsRepository,
    private val sender: FeedbackSender,
    @FeedbackVersion private val appVersion: String,
    @AppScope private val scope: CoroutineScope,
) : MessageFeedbackSync {

    internal var ids: () -> String = { UUID.randomUUID().toString() }

    private val flights = ConcurrentHashMap<String, Flight>()

    init {
        scope.launch { flushSaved() }
    }

    override suspend fun afterLocalChange(
        session: PageConversation,
        messageId: Long,
        surface: FeedbackSurface,
    ): FeedbackFollowUp {
        if (!shares(session)) return FeedbackFollowUp.LOCAL_ONLY
        return when (settings.settings.first().sendRatings) {
            null -> FeedbackFollowUp.NEEDS_CONSENT
            false -> FeedbackFollowUp.LOCAL_ONLY
            true -> {
                armAndSend(session, messageId, surface)
                FeedbackFollowUp.QUEUED
            }
        }
    }

    override suspend fun rememberConsent(allow: Boolean) {
        settings.setSendRatings(allow)
    }

    override suspend fun sendAfterConsent(session: PageConversation, messageId: Long, surface: FeedbackSurface) {
        if (!shares(session)) return
        if (settings.settings.first().sendRatings != true) return
        armAndSend(session, messageId, surface)
    }

    private suspend fun armAndSend(session: PageConversation, messageId: Long, surface: FeedbackSurface) {
        val chat = session as? ChatSession ?: return
        val armed = chat.editMessage(messageId) { current ->
            val answerId = current.feedbackAnswerId ?: ids()
            mergeStamp(current, answerId, current.feedbackRevision + 1, current.feedbackSentRevision)
        } ?: return
        val answerId = armed.feedbackAnswerId ?: return
        if (chat.chatId > 0) {
            chats.advanceFeedbackRevision(messageId, answerId, armed.feedbackRevision, armed.feedbackSentRevision)
        }
        begin(answerId, supersede = true) { ticket ->
            deliver(chat, messageId, answerId, surface, ticket)
        }
    }

    /**
     * One in-flight send per answer. [supersede] is a newer thumb: it cancels the older send.
     * Startup work passes false and leaves a send that is already running.
     */
    private fun begin(answerId: String, supersede: Boolean, block: suspend (Long) -> Unit) {
        val flight = flights.getOrPut(answerId) { Flight() }
        synchronized(flight) {
            if (!supersede && flight.job?.isActive == true) return
            val ticket = ++flight.ticket
            flight.job?.cancel()
            flight.job = scope.launch {
                if (!isCurrent(answerId, ticket)) return@launch
                block(ticket)
            }
        }
    }

    private fun isCurrent(answerId: String, ticket: Long): Boolean {
        val flight = flights[answerId] ?: return false
        synchronized(flight) { return flight.ticket == ticket }
    }

    private suspend fun deliver(
        chat: ChatSession,
        messageId: Long,
        answerId: String,
        surface: FeedbackSurface,
        ticket: Long,
    ) {
        repeat(ATTEMPTS) { attempt ->
            if (!isCurrent(answerId, ticket)) return
            if (!consent()) {
                retire(chat, messageId, answerId, ticket)
                return
            }
            val latest = chat.messages.value.firstOrNull { it.id == messageId } ?: return
            if (latest.feedbackAnswerId != answerId || latest.feedbackSentRevision >= latest.feedbackRevision) return
            val report = report(answerId, latest.feedbackRevision, latest.feedback, surface)
            when (val result = sender.send(report)) {
                is FeedbackDelivery.Accepted -> {
                    coroutineContext.ensureActive()
                    if (!isCurrent(answerId, ticket)) return
                    if (result.storedRevision >= report.revision) ack(chat, messageId, answerId, report.revision)
                    return
                }
                FeedbackDelivery.Rejected -> return
                FeedbackDelivery.Failed -> {
                    if (!isCurrent(answerId, ticket)) return
                    if (!consent()) {
                        retire(chat, messageId, answerId, ticket)
                        return
                    }
                    if (attempt < ATTEMPTS - 1) delay(PAUSE_MS * (attempt + 1))
                }
            }
        }
    }

    private suspend fun flushSaved() {
        if (!consent()) return
        for (message in chats.unsentFeedback()) {
            val answerId = message.feedbackAnswerId ?: continue
            val surface = if (message.kind == MessageKind.TABS_OVERVIEW) FeedbackSurface.TABS else FeedbackSurface.PAGE
            begin(answerId, supersede = false) { ticket ->
                if (!isCurrent(answerId, ticket) || !consent()) return@begin
                val fresh = chats.messages(message.chatId).firstOrNull { it.id == message.id } ?: return@begin
                if (fresh.feedbackAnswerId != answerId || fresh.feedbackRevision != message.feedbackRevision) return@begin
                if (fresh.feedback != message.feedback || fresh.feedbackSentRevision >= fresh.feedbackRevision) return@begin
                val report = report(answerId, fresh.feedbackRevision, fresh.feedback, surface)
                val result = sender.send(report)
                coroutineContext.ensureActive()
                if (!isCurrent(answerId, ticket)) return@begin
                if (result is FeedbackDelivery.Accepted && result.storedRevision >= report.revision && consent()) {
                    chats.ackFeedback(message.id, answerId, report.revision, report.rating)
                } else if (!consent()) {
                    chats.ackFeedback(message.id, answerId, report.revision, report.rating)
                }
            }
        }
    }

    private suspend fun ack(chat: ChatSession, messageId: Long, answerId: String, revision: Int) {
        val installed = chat.editMessage(messageId) { current ->
            if (current.feedbackAnswerId != answerId || current.feedbackRevision != revision) null
            else mergeStamp(current, answerId, revision, revision)
        } ?: return
        if (chat.chatId > 0) chats.ackFeedback(messageId, answerId, revision, installed.feedback)
    }

    private suspend fun retire(chat: ChatSession, messageId: Long, answerId: String, ticket: Long) {
        if (!isCurrent(answerId, ticket)) return
        val revision = chat.messages.value.firstOrNull { it.id == messageId }?.feedbackRevision ?: return
        ack(chat, messageId, answerId, revision)
    }

    private class Flight {
        var ticket: Long = 0
        var job: Job? = null
    }

    private suspend fun consent(): Boolean = settings.settings.first().sendRatings == true

    private fun report(answerId: String, revision: Int, rating: com.dantech.skimbrowser.domain.chat.MessageFeedback, surface: FeedbackSurface) =
        FeedbackReport(answerId, revision, rating, surface, appVersion.take(32).ifBlank { "0" })

    private fun shares(session: PageConversation): Boolean {
        val chat = session as? ChatSession ?: return false
        return chat.sources.none { it.incognito }
    }

    private companion object {
        const val ATTEMPTS = 3
        const val PAUSE_MS = 400L
    }
}
