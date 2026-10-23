package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.dantech.skimbrowser.browser.page.PageContent
import com.dantech.skimbrowser.data.db.entity.MessageFeedback
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.MessageStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** A turn already sent, replayed on the next request. A live turn keeps the model's own message. */
sealed interface HistoryTurn {
    data class Live(val message: Message) : HistoryTurn

    data class Text(val role: MessageRole, val text: String) : HistoryTurn
}

/**
 * The conversation about one page, held for the life of the process.
 *
 * [chatId] is positive when the chat is saved and negative when it lives only in memory.
 * [opening] is the first user message, frozen the first time it is built, so a later request
 * repeats those bytes. [approvedHost] is the host the person allowed; [sources] carry the host
 * the page was actually read from.
 */
class ChatSession(
    val chatId: Long,
    val tabId: String,
    val pageUrl: String,
    val approvedHost: String,
    sources: List<AiSource>,
    val document: PageContent,
    val languageTag: String,
    /** How many tabs an across-tabs chat covers. A page chat leaves this at 0. */
    val tabCount: Int = 0,
) {
    private val sourcesRef = AtomicReference(sources)
    private val openingRef = AtomicReference<List<ContentBlockParam>?>(null)
    private val turns = mutableListOf<HistoryTurn>()
    private val turnLock = Any()
    private val messagesState = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val stopSaved = AtomicBoolean(false)

    @Volatile var streaming: Boolean = false

    @Volatile var draft: String = ""

    @Volatile var draftKind: MessageKind = MessageKind.SUMMARY

    @Volatile var draftCitations: List<Int> = emptyList()

    @Volatile var lastParams: MessageCreateParams? = null

    private var streamJob: Job? = null

    val messages: StateFlow<List<ChatMessage>> = messagesState.asStateFlow()

    /** The pages this chat may send. An across-tabs chat replaces them when the open tabs change. */
    val sources: List<AiSource> get() = sourcesRef.get()

    fun replaceSources(next: List<AiSource>) {
        sourcesRef.set(next)
    }

    /** The first user message, once a summary or a first question has built it. */
    val opening: List<ContentBlockParam>? get() = openingRef.get()

    /**
     * Keeps [blocks] as the first user message when none is set yet, and returns the message that
     * stays. A later call does not replace it, so a follow-up repeats the same documents.
     */
    fun freezeOpening(blocks: List<ContentBlockParam>): List<ContentBlockParam> {
        openingRef.compareAndSet(null, blocks)
        return openingRef.get()!!
    }

    /** Replaces the first user message. An across-tabs overview does this, because that message is the request. */
    fun setOpening(blocks: List<ContentBlockParam>): List<ContentBlockParam> {
        openingRef.set(blocks)
        return blocks
    }

    val history: List<HistoryTurn> get() = synchronized(turnLock) { turns.toList() }

    /** The document plus the summary instruction. Later calls return the same blocks. */
    fun summaryOpening(): List<ContentBlockParam> {
        val blocks = listOf(
            ContentBlockParam.ofDocument(pageDocument(document)),
            ContentBlockParam.ofText(Prompts.summaryInstruction(languageTag)),
        )
        openingRef.compareAndSet(null, blocks)
        return openingRef.get()!!
    }

    /** The document plus [question], used when the page was never summarized. */
    fun questionOpening(question: String): List<ContentBlockParam> {
        val blocks = listOf(
            ContentBlockParam.ofDocument(pageDocument(document)),
            ContentBlockParam.ofText(question),
        )
        openingRef.compareAndSet(null, blocks)
        return openingRef.get()!!
    }

    fun addTurn(turn: HistoryTurn) {
        synchronized(turnLock) { turns += turn }
    }

    fun replaceMessages(messages: List<ChatMessage>) {
        messagesState.value = messages
    }

    fun beginStream(job: Job, kind: MessageKind) {
        stopSaved.set(false)
        draft = ""
        draftCitations = emptyList()
        draftKind = kind
        streaming = true
        streamJob = job
    }

    fun noteText(delta: String) {
        draft += delta
    }

    fun noteCite(start: Int) {
        if (start !in draftCitations) draftCitations = draftCitations + start
    }

    fun cancelStream() {
        streamJob?.cancel()
        streamJob = null
    }

    /** True the first time a stop is saved, so a close and a navigation do not save it twice. */
    fun takeStop(): Boolean = stopSaved.compareAndSet(false, true)

    fun endStream() {
        streaming = false
        streamJob = null
    }
}
