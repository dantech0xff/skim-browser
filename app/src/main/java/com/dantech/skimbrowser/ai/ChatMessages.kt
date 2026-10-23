package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.chat.MessageStatus
import com.dantech.skimbrowser.domain.chat.SavedMessage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal fun ChatMessage.toSaved(chatId: Long, createdAt: Long) = SavedMessage(
    id = id,
    chatId = chatId,
    role = role,
    kind = kind,
    text = text,
    citationsJson = citationsJson(citations),
    feedback = feedback,
    status = status,
    suggestedQuestionsJson = questionsJson(suggestedQuestions),
    createdAt = createdAt,
)

internal fun SavedMessage.toMessage() = ChatMessage(
    id = id,
    role = role,
    kind = kind,
    text = text,
    citations = citationsOf(citationsJson),
    feedback = feedback,
    status = status,
    suggestedQuestions = questionsOf(suggestedQuestionsJson),
)

/** The first summary line, without its bullet, for the Continue card. */
internal fun summarySnippet(text: String): String? {
    val line = text.lineSequence().firstOrNull()?.removePrefix("- ")?.trim().orEmpty()
    return line.take(AiConfig.SNIPPET_CHARS).ifBlank { null }
}

internal fun historyTurn(message: ChatMessage): HistoryTurn? = when (message.kind) {
    MessageKind.QUESTION -> HistoryTurn.Text(MessageRole.USER, Prompts.question(message.text))
    MessageKind.SUMMARY, MessageKind.ANSWER, MessageKind.TABS_OVERVIEW ->
        HistoryTurn.Text(MessageRole.ASSISTANT, message.text)
}

private fun citationsJson(indexes: List<Int>): String? =
    indexes.takeIf { it.isNotEmpty() }?.let { JSON.encodeToString(ListSerializer(Int.serializer()), it) }

private fun citationsOf(json: String?): List<Int> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        JSON.decodeFromString(ListSerializer(Int.serializer()), json)
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}

private fun questionsJson(questions: List<String>): String? =
    questions.takeIf { it.isNotEmpty() }?.let { JSON.encodeToString(ListSerializer(String.serializer()), it) }

private fun questionsOf(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        JSON.decodeFromString(ListSerializer(String.serializer()), json)
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}

private val JSON = Json { ignoreUnknownKeys = true }

internal fun stoppedMessage(session: ChatSession) = ChatMessage(
    id = 0,
    role = MessageRole.ASSISTANT,
    kind = session.draftKind,
    text = session.draft,
    citations = session.draftCitations,
    feedback = MessageFeedback.NONE,
    status = MessageStatus.STOPPED,
)
