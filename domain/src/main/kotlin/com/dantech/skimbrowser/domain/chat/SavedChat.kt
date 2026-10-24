package com.dantech.skimbrowser.domain.chat

/** A saved Skim chat. Only chats of normal tabs are stored, and only while Keep chat history is on. */
data class SavedChat(
    val id: Long = 0,
    val kind: ChatKind,
    val url: String?,
    val host: String?,
    val title: String,
    val tabCount: Int = 0,
    val snippet: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One message of a saved chat. JSON columns stay strings. */
data class SavedMessage(
    val id: Long = 0,
    val chatId: Long,
    val role: MessageRole,
    val kind: MessageKind,
    val text: String,
    val citationsJson: String? = null,
    val feedback: MessageFeedback = MessageFeedback.NONE,
    val status: MessageStatus,
    val suggestedQuestionsJson: String? = null,
    val createdAt: Long,
)

/** A row of Skim history: the chat and the counts its subtitle needs. */
data class ChatSummary(
    val chat: SavedChat,
    val questionCount: Int,
    val hasSummary: Boolean,
    val answerCount: Int,
)

/** A chat removed from Skim history, kept whole so Undo can put it back with the same ids. */
data class DeletedChat(val chat: SavedChat, val messages: List<SavedMessage>)
