package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.chat.MessageStatus

/** One message as the sheet shows it and, when chat history is on, as it is saved. */
data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val kind: MessageKind,
    val text: String,
    val citations: List<Int> = emptyList(),
    val feedback: MessageFeedback = MessageFeedback.NONE,
    val status: MessageStatus,
    /** Follow-up questions offered with an across-tabs overview. */
    val suggestedQuestions: List<String> = emptyList(),
    /** Random id for a rating sent to the proxy. Null until the person agrees to send one. */
    val feedbackAnswerId: String? = null,
    /** How many times this answer's rating changed. Retries reuse the same number. */
    val feedbackRevision: Int = 0,
    /**
     * The newest revision that will not be sent again. It advances when the proxy accepts that
     * revision, or when the person withdraws consent while a send of it is still being tried.
     */
    val feedbackSentRevision: Int = 0,
)
