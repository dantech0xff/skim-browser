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

    private val jobs = ConcurrentHashMap<String, Job>()

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
        val message = session.messages.value.firstOrNull { it.id == messageId } ?: return
        val answerId = message.feedbackAnswerId ?: ids()
        val revision = message.feedbackRevision + 1
        sessions.stampFeedback(session, messageId, answerId, revision, message.feedbackSentRevision)
        enqueue(session, messageId, answerId, surface)
    }

    private fun enqueue(session: PageConversation, messageId: Long, answerId: String, surface: FeedbackSurface) {
        jobs.remove(answerId)?.cancel()
        jobs[answerId] = scope.launch {
            repeat(ATTEMPTS) { attempt ->
                if (!consent()) {
                    retire(session, messageId, answerId)
                    return@launch
                }
                val latest = session.messages.value.firstOrNull { it.id == messageId } ?: return@launch
                if (latest.feedbackAnswerId != answerId || latest.feedbackSentRevision >= latest.feedbackRevision) return@launch
                val report = report(latest.feedbackAnswerId ?: return@launch, latest.feedbackRevision, latest.feedback, surface)
                when (val result = sender.send(report)) {
                    is FeedbackDelivery.Accepted -> {
                        coroutineContext.ensureActive()
                        if (result.storedRevision >= report.revision) {
                            ack(session, messageId, answerId, report.revision)
                        }
                        return@launch
                    }
                    FeedbackDelivery.Rejected -> return@launch
                    FeedbackDelivery.Failed -> {
                        if (!consent()) {
                            retire(session, messageId, answerId)
                            return@launch
                        }
                        if (attempt < ATTEMPTS - 1) delay(PAUSE_MS * (attempt + 1))
                    }
                }
            }
        }
    }

    private suspend fun flushSaved() {
        if (!consent()) return
        for (message in chats.unsentFeedback()) {
            val answerId = message.feedbackAnswerId ?: continue
            val surface = if (message.kind == MessageKind.TABS_OVERVIEW) FeedbackSurface.TABS else FeedbackSurface.PAGE
            jobs.remove(answerId)?.cancel()
            jobs[answerId] = scope.launch {
                if (!consent()) return@launch
                val report = report(answerId, message.feedbackRevision, message.feedback, surface)
                val result = sender.send(report)
                coroutineContext.ensureActive()
                if (result is FeedbackDelivery.Accepted && result.storedRevision >= report.revision && consent()) {
                    chats.ackFeedback(message.id, answerId, report.revision, report.rating)
                } else if (!consent()) {
                    chats.ackFeedback(message.id, answerId, report.revision, report.rating)
                }
            }
        }
    }

    /** Marks [report]'s revision sent only while that revision is still the live one. */
    private suspend fun ack(session: PageConversation, messageId: Long, answerId: String, revision: Int) {
        val current = session.messages.value.firstOrNull { it.id == messageId } ?: return
        if (current.feedbackAnswerId != answerId || current.feedbackRevision != revision) return
        if (current.feedbackSentRevision >= revision) return
        sessions.stampFeedback(session, messageId, answerId, revision, revision)
    }

    /** Stops a send the person no longer allows, without changing the rating or a newer revision. */
    private suspend fun retire(session: PageConversation, messageId: Long, answerId: String) {
        val current = session.messages.value.firstOrNull { it.id == messageId } ?: return
        if (current.feedbackAnswerId != answerId || current.feedbackSentRevision >= current.feedbackRevision) return
        sessions.stampFeedback(session, messageId, answerId, current.feedbackRevision, current.feedbackRevision)
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
