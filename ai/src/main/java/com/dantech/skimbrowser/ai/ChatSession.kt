package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.dantech.skimbrowser.domain.ai.PageConversation
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.page.PageContent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

typealias ChatMessage = com.dantech.skimbrowser.domain.ai.ChatMessage

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
 * the page was actually read from. The model request itself stays on this class.
 */
class ChatSession(
    override val chatId: Long,
    override val tabId: String,
    override val pageUrl: String,
    val approvedHost: String,
    sources: List<AiSource>,
    override val document: PageContent,
    val languageTag: String,
    /** How many tabs an across-tabs chat covers. A page chat leaves this at 0. */
    override val tabCount: Int = 0,
) : PageConversation {
    private val sourcesRef = AtomicReference(sources)
    private val openingRef = AtomicReference<List<ContentBlockParam>?>(null)
    private val pendingReply = AtomicReference<Message?>(null)
    private val turns = mutableListOf<HistoryTurn>()
    private val turnLock = Any()
    private val messagesState = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val stopSaved = AtomicBoolean(false)

    @Volatile
    override var streaming: Boolean = false

    @Volatile
    override var draft: String = ""

    @Volatile
    override var draftKind: MessageKind = MessageKind.SUMMARY

    @Volatile
    override var draftCitations: List<Int> = emptyList()

    /** The last request this chat built. Null until a summary or a question has been built. */
    @Volatile
    internal var lastParams: MessageCreateParams? = null

    private var streamJob: Job? = null

    override val messages: StateFlow<List<ChatMessage>> = messagesState.asStateFlow()

    override val canRetry: Boolean get() = lastParams != null

    /** The pages this chat may send. An across-tabs chat replaces them when the open tabs change. */
    val sources: List<AiSource> get() = sourcesRef.get()

    override val hasSources: Boolean get() = sources.isNotEmpty()

    fun replaceSources(next: List<AiSource>) {
        sourcesRef.set(next)
    }

    override fun replacePages(pages: List<TabContext>) {
        replaceSources(pages.toSources())
    }

    /** The first user message, once a summary or a first question has built it. */
    val opening: List<ContentBlockParam>? get() = openingRef.get()

    override val hasOpening: Boolean get() = opening != null

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

    override fun ensureOverviewOpening(pages: List<TabContext>, prompt: String) {
        if (opening != null || pages.isEmpty()) return
        val question = messages.value.firstOrNull { it.kind == MessageKind.QUESTION }?.text ?: prompt
        freezeOpening(overviewBlocks(pages, question))
    }

    val history: List<HistoryTurn> get() = synchronized(turnLock) { turns.toList() }

    /** The document plus the summary instruction. Later calls return the same blocks. */
    fun summaryOpening(): List<ContentBlockParam> = freezeOpening(
        listOf(
            ContentBlockParam.ofDocument(pageDocument(document)),
            ContentBlockParam.ofText(Prompts.summaryInstruction(languageTag)),
        ),
    )

    /** The document plus [question], used when the page was never summarized. */
    fun questionOpening(question: String): List<ContentBlockParam> = freezeOpening(
        listOf(
            ContentBlockParam.ofDocument(pageDocument(document)),
            ContentBlockParam.ofText(question),
        ),
    )

    fun addTurn(turn: HistoryTurn) {
        synchronized(turnLock) { turns += turn }
    }

    /** Holds the model's message until the sheet decides the turn should be kept. */
    internal fun stashReply(message: Message) {
        pendingReply.set(message)
    }

    override fun commitPendingReply(keep: Boolean) {
        val message = pendingReply.getAndSet(null)
        if (keep && message != null) addTurn(HistoryTurn.Live(message))
    }

    override fun rememberAssistantText(text: String) {
        addTurn(HistoryTurn.Text(MessageRole.ASSISTANT, text))
    }

    override fun stoppedMessage(): ChatMessage = com.dantech.skimbrowser.ai.stoppedMessage(this)

    fun replaceMessages(messages: List<ChatMessage>) {
        messagesState.value = messages
    }

    override fun beginStream(job: Job, kind: MessageKind) {
        stopSaved.set(false)
        draft = ""
        draftCitations = emptyList()
        draftKind = kind
        streaming = true
        streamJob = job
    }

    override fun noteText(delta: String) {
        draft += delta
    }

    override fun noteCite(start: Int) {
        if (start !in draftCitations) draftCitations = draftCitations + start
    }

    override fun cancelStream() {
        streamJob?.cancel()
        streamJob = null
    }

    /** True the first time a stop is saved, so a close and a navigation do not save it twice. */
    override fun takeStop(): Boolean = stopSaved.compareAndSet(false, true)

    override fun endStream() {
        streaming = false
        streamJob = null
    }
}
