package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.ai.ChatMessage
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.chat.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A stamp never rewinds a revision or replaces the rating that is already on the message. */
class FeedbackStampTest {

    @Test
    fun anOlderRevisionIsIgnored() {
        val live = message(feedback = MessageFeedback.POOR, revision = 2, sent = 0)
        assertNull(mergeStamp(live, "answer-1", revision = 1, sentRevision = 1))
    }

    @Test
    fun anAckMovesTheSentRevisionAndKeepsTheRating() {
        val live = message(feedback = MessageFeedback.GOOD, revision = 2, sent = 0)
        val merged = mergeStamp(live, "answer-1", revision = 2, sentRevision = 2)
        assertEquals(MessageFeedback.GOOD, merged?.feedback)
        assertEquals(2, merged?.feedbackRevision)
        assertEquals(2, merged?.feedbackSentRevision)
    }

    @Test
    fun aNewerRevisionKeepsTheRatingAlreadyStored() {
        val live = message(feedback = MessageFeedback.POOR, revision = 1, sent = 0)
        val merged = mergeStamp(live, "answer-1", revision = 2, sentRevision = 0)
        assertEquals(MessageFeedback.POOR, merged?.feedback)
        assertEquals(2, merged?.feedbackRevision)
        assertEquals(0, merged?.feedbackSentRevision)
    }

    private fun message(feedback: MessageFeedback, revision: Int, sent: Int) = ChatMessage(
        id = 7,
        role = MessageRole.ASSISTANT,
        kind = MessageKind.SUMMARY,
        text = "A summary",
        feedback = feedback,
        status = MessageStatus.COMPLETE,
        feedbackAnswerId = "answer-1",
        feedbackRevision = revision,
        feedbackSentRevision = sent,
    )
}