package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.ai.ChatMessage

/**
 * The message after applying a rating stamp, or null when the stamp would rewind a newer revision
 * or a newer acknowledgement. The rating itself stays whatever [live] already has.
 */
internal fun mergeStamp(live: ChatMessage, answerId: String, revision: Int, sentRevision: Int): ChatMessage? {
    if (revision < live.feedbackRevision) return null
    if (live.feedbackAnswerId != null && live.feedbackAnswerId != answerId && revision == live.feedbackRevision) {
        return null
    }
    val sent = if (revision > live.feedbackRevision) {
        sentRevision.coerceIn(0, revision)
    } else {
        maxOf(live.feedbackSentRevision, sentRevision).coerceAtMost(revision)
    }
    if (sent < live.feedbackSentRevision && revision == live.feedbackRevision) return null
    if (live.feedbackAnswerId == answerId && live.feedbackRevision == revision && live.feedbackSentRevision == sent) {
        return null
    }
    return live.copy(
        feedbackAnswerId = answerId,
        feedbackRevision = revision,
        feedbackSentRevision = sent,
    )
}
