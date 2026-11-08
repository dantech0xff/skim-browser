package com.dantech.skimbrowser.ui.ai

import com.dantech.skimbrowser.domain.ai.FeedbackFollowUp
import com.dantech.skimbrowser.domain.ai.FeedbackSurface
import com.dantech.skimbrowser.domain.ai.MessageFeedbackSync
import com.dantech.skimbrowser.domain.ai.PageConversation

/** Ratings stay on the phone. Existing sheet tests do not open the proxy. */
internal object IdleFeedback : MessageFeedbackSync {
    override suspend fun afterLocalChange(
        session: PageConversation,
        messageId: Long,
        surface: FeedbackSurface,
    ) = FeedbackFollowUp.LOCAL_ONLY

    override suspend fun rememberConsent(allow: Boolean) = Unit

    override suspend fun sendAfterConsent(session: PageConversation, messageId: Long, surface: FeedbackSurface) = Unit
}

internal class RecordingFeedback : MessageFeedbackSync {
    var sent = false

    override suspend fun afterLocalChange(
        session: PageConversation,
        messageId: Long,
        surface: FeedbackSurface,
    ) = FeedbackFollowUp.NEEDS_CONSENT

    override suspend fun rememberConsent(allow: Boolean) = Unit

    override suspend fun sendAfterConsent(session: PageConversation, messageId: Long, surface: FeedbackSurface) {
        sent = true
    }
}
