package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.chat.MessageFeedback

/** Which thumbs the rating came from. It is not the page and not the answer. */
enum class FeedbackSurface { PAGE, TABS }

/**
 * One rating for one answer. [answerId] is random and identifies the answer to the proxy only.
 * [revision] grows when the person changes the rating. A retry sends the same revision.
 */
data class FeedbackReport(
    val answerId: String,
    val revision: Int,
    val rating: MessageFeedback,
    val surface: FeedbackSurface,
    val appVersion: String,
)

/** What the proxy said about [FeedbackReport]. A failure is not a saved rating. */
sealed interface FeedbackDelivery {
    data class Accepted(val storedRevision: Int) : FeedbackDelivery
    data object Rejected : FeedbackDelivery
    data object Failed : FeedbackDelivery
}

/** Posts a rating. It does not decide consent, and it does not change the chat. */
interface FeedbackSender {
    suspend fun send(report: FeedbackReport): FeedbackDelivery
}

/** What the sheet should do after the rating is already stored on the phone. */
enum class FeedbackFollowUp { LOCAL_ONLY, NEEDS_CONSENT, QUEUED }

/**
 * Decides whether a rating that is already on the phone may be sent. Sending never blocks the
 * chat. A failure leaves the local rating as it is and does not report the proxy as saved.
 */
interface MessageFeedbackSync {
    suspend fun afterLocalChange(
        session: PageConversation,
        messageId: Long,
        surface: FeedbackSurface,
    ): FeedbackFollowUp

    /** Remembers the disclosure. Does not send ratings that were chosen before this. */
    suspend fun rememberConsent(allow: Boolean)

    /** The person just agreed. Sends the rating already stored for [messageId], not older ones. */
    suspend fun sendAfterConsent(session: PageConversation, messageId: Long, surface: FeedbackSurface)
}
