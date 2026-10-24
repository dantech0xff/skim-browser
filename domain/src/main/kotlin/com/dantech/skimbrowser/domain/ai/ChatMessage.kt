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
)
