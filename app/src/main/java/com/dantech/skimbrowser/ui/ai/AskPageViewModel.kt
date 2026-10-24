package com.dantech.skimbrowser.ui.ai

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.assisted.Assisted
import com.dantech.skimbrowser.di.AppScope
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.domain.ai.AiFailure
import com.dantech.skimbrowser.domain.ai.AiReply
import com.dantech.skimbrowser.domain.ai.AskPage
import com.dantech.skimbrowser.domain.ai.ChatMessage
import com.dantech.skimbrowser.domain.ai.NetworkAvailability
import com.dantech.skimbrowser.domain.ai.PageChats
import com.dantech.skimbrowser.domain.ai.PageConversation
import com.dantech.skimbrowser.domain.ai.RetryAnswer
import com.dantech.skimbrowser.domain.ai.SummarizePage
import com.dantech.skimbrowser.domain.page.HighlightRequest
import com.dantech.skimbrowser.domain.page.PageContent
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.newTab
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.chat.MessageStatus
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.withoutFragment
import com.dantech.skimbrowser.domain.usecase.LoadSavedChat
import com.dantech.skimbrowser.ui.navigation.AskPageKey
import com.dantech.skimbrowser.ui.reader.ReaderHighlights
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the summary card is showing. */
@Immutable
sealed interface SummaryState {
    /** The page is being read, or the summary has not produced text yet. */
    data object Generating : SummaryState

    /** Summary text received so far. */
    data class Streaming(val text: String) : SummaryState

    /** A finished summary. [bullets] are the lines without their leading "- ". */
    data class Done(
        val text: String,
        val bullets: List<String>,
        val feedback: MessageFeedback,
        val messageId: Long,
    ) : SummaryState

    data object NotEnoughText : SummaryState

    data object Offline : SummaryState

    data object ServerError : SummaryState

    data object Refused : SummaryState

    /** A summary that was stopped. [text] is the part that arrived. */
    data class Interrupted(val text: String) : SummaryState
}

/** Why the latest question has no answer. */
enum class AnswerError { Server, Offline, Refused }

/** One question and, once it exists, the answer under it. */
@Immutable
data class QaTurn(
    val question: String,
    val answer: String?,
    val paragraphs: List<Int>,
    val stopped: Boolean,
)

/** Ask about this page, as the sheet draws it. */
@Immutable
data class AskPageUiState(
    val title: String = "",
    val host: String = "",
    val summary: SummaryState = SummaryState.Generating,
    val turns: List<QaTurn> = emptyList(),
    val showChips: Boolean = false,
    val inputEnabled: Boolean = false,
    val generating: Boolean = false,
    val waitingForAnswer: Boolean = false,
    val citationsEnabled: Boolean = false,
    val answerError: AnswerError? = null,
)

/** The sheet asks the route to replace itself with the permission dialog. */
sealed interface AskPageEffect {
    data class NeedsPermission(val tabId: String, val fromReader: Boolean) : AskPageEffect
}

/**
 * Ask about this page. A summary is requested only the first time this sheet is opened for a page
 * that has enough text and is online. Opening again, or coming back after process death, shows
 * what was already saved and sends nothing until the person asks.
 */
@HiltViewModel(assistedFactory = AskPageViewModel.Factory::class)
class AskPageViewModel @AssistedInject constructor(
    @Assisted private val key: AskPageKey,
    private val tabs: TabsController,
    private val sessions: PageChats,
    private val summarizePage: SummarizePage,
    private val askPage: AskPage,
    private val retryAnswer: RetryAnswer,
    private val network: NetworkAvailability,
    private val readerHighlights: ReaderHighlights,
    private val loadSavedChat: LoadSavedChat,
    private val savedState: SavedStateHandle,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: AskPageKey): AskPageViewModel
    }

    private val _state = MutableStateFlow(AskPageUiState())
    val state: StateFlow<AskPageUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AskPageEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<AskPageEffect> = _effects.asSharedFlow()

    private var session: PageConversation? = null
    private var job: Job? = null
    private var runtimeJob: Job? = null
    private val citedText = mutableMapOf<Int, String>()

    init {
        viewModelScope.launch { network.validated.collect { online -> onNetwork(online) } }
        job = viewModelScope.launch { open() }
    }

    /** Sends [question], the typed text or a chip's label. */
    fun ask(question: String) {
        val text = question.trim()
        val current = session ?: return
        if (text.isEmpty() || !_state.value.inputEnabled || _state.value.generating) return
        launchRequest {
            sessions.record(
                current,
                ChatMessage(
                    id = 0,
                    role = MessageRole.USER,
                    kind = MessageKind.QUESTION,
                    text = text,
                    status = MessageStatus.COMPLETE,
                ),
            )
            publish(current, generating = true, waiting = true, streamingKind = MessageKind.ANSWER)
            collect(current, askPage(current, text), MessageKind.ANSWER)
        }
    }

    /** Stops the request and keeps the text that has arrived. */
    fun stop() {
        val current = session
        val wasStreaming = current?.streaming == true
        current?.cancelStream()
        job?.cancel()
        if (current != null && wasStreaming && current.takeStop()) {
            val message = current.stoppedMessage()
            current.endStream()
            _state.value = _state.value.copy(
                summary = if (message.kind == MessageKind.SUMMARY) SummaryState.Interrupted(message.text) else _state.value.summary,
                generating = false,
                waitingForAnswer = false,
                inputEnabled = network.hasActiveNetwork,
                showChips = false,
            )
            viewModelScope.launch {
                sessions.record(current, message)
                publish(current)
            }
        } else {
            _state.value = _state.value.copy(
                generating = false,
                waitingForAnswer = false,
                inputEnabled = network.hasActiveNetwork && _state.value.summary !is SummaryState.Offline,
            )
        }
    }

    /** Sends the last summary or question again. With no session yet, reads the page and summarizes. */
    fun retry() {
        if (!network.hasActiveNetwork) {
            _state.value = _state.value.copy(
                summary = SummaryState.Offline,
                generating = false,
                inputEnabled = false,
                showChips = false,
                answerError = null,
            )
            return
        }
        val current = session
        if (current == null) {
            job?.cancel()
            job = viewModelScope.launch { openExplicit() }
            return
        }
        val unanswered = current.messages.value.lastOrNull()?.kind == MessageKind.QUESTION
        val kind = if (current.canRetry && unanswered) MessageKind.ANSWER else MessageKind.SUMMARY
        if (kind == MessageKind.SUMMARY && !current.canRetry && !enoughText(current.document)) return
        launchRequest {
            publish(
                current,
                generating = true,
                waiting = kind == MessageKind.ANSWER,
                streamingKind = kind,
                summaryOverride = SummaryState.Generating.takeIf { kind == MessageKind.SUMMARY },
            )
            val flow = if (current.canRetry) retryAnswer(current) else summarizePage(current)
            collect(current, flow, kind)
        }
    }

    /** Good and poor exclude each other. Tapping the selected one clears it. Nothing is sent. */
    fun feedback(good: Boolean) {
        val done = _state.value.summary as? SummaryState.Done ?: return
        val current = session ?: return
        val next = when {
            good && done.feedback == MessageFeedback.GOOD -> MessageFeedback.NONE
            !good && done.feedback == MessageFeedback.POOR -> MessageFeedback.NONE
            good -> MessageFeedback.GOOD
            else -> MessageFeedback.POOR
        }
        viewModelScope.launch {
            sessions.setFeedback(current, done.messageId, next)
            publish(current)
        }
    }

    /**
     * Highlights paragraph [paragraph] (1-based) when the page is still the one that was read.
     * From the reader, the reader scrolls to that block instead.
     */
    fun highlight(paragraph: Int) {
        val current = session ?: return
        val runtime = tabs.runtime(current.tabId).value
        if (!sameDocument(current, runtime)) return
        val index = paragraph - 1
        val block = current.document.blocks.getOrNull(index) ?: return
        val request = HighlightRequest(
            expectedUrl = current.document.url,
            expectedNonce = current.document.loadNonce,
            blockId = block.id,
            citedText = citedText[index],
        )
        viewModelScope.launch {
            if (key.fromReader) readerHighlights.request(current.tabId, index)
            else tabs.highlightBlock(current.tabId, request)
        }
    }

    override fun onCleared() {
        val current = session ?: return
        current.cancelStream()
        if (current.streaming && current.takeStop()) {
            val message = current.stoppedMessage()
            appScope.launch { sessions.record(current, message) }
        }
    }

    private suspend fun open() {
        val savedChatId = key.savedChatId
        if (savedChatId != null) {
            savedState[OPEN_HANDLED] = true
            openSaved(savedChatId)
            return
        }
        val tabId = key.tabId ?: return
        val restored = savedState.get<Boolean>(OPEN_HANDLED) == true
        if (!restored) savedState[OPEN_HANDLED] = true
        val existing = sessions.sessionFor(tabId)
        if (existing != null) {
            adopt(existing, summarize = false)
            return
        }
        if (restored) {
            restore(tabId)
            return
        }
        openExplicit()
    }

    private suspend fun openExplicit() {
        val tabId = key.tabId ?: return
        val tab = tabs.state.value.tab(tabId) ?: return
        showHeader(tab, SummaryState.Generating, generating = true)
        if (!network.hasActiveNetwork) {
            _state.value = _state.value.copy(summary = SummaryState.Offline, generating = false, inputEnabled = false, showChips = false)
            return
        }
        val content = tabs.extractPage(tabId) ?: run {
            _state.value = _state.value.copy(summary = SummaryState.ServerError, generating = false, inputEnabled = true, showChips = false)
            return
        }
        val host = key.approvedHost ?: hostOf(content.url).orEmpty()
        adopt(sessions.openForTab(tab, content, host), summarize = true)
    }

    private suspend fun openSaved(chatId: Long) {
        val chat = loadSavedChat(chatId) ?: return
        val url = chat.url ?: return
        val current = tabs.state.value.currentTabId?.let { tabs.state.value.tab(it) }
        val tabId = if (current != null && !current.incognito) {
            if (withoutFragment(current.url.orEmpty()) != withoutFragment(url)) tabs.load(current.id, url)
            current.id
        } else {
            tabs.newTab(incognito = false, url = url)
        }
        val tab = tabs.state.value.tab(tabId) ?: return
        showHeader(tab, SummaryState.Generating, generating = false)
        val content = tabs.extractPage(tabId) ?: placeholder(url, chat.title)
        val opened = sessions.openSaved(chatId, tab, content) ?: return
        adopt(opened, summarize = false)
    }

    private suspend fun restore(tabId: String) {
        val tab = tabs.state.value.tab(tabId)
        val chatId = tab?.chatId
        if (tab != null && chatId != null && chatId > 0) {
            val content = tabs.extractPage(tabId)
            if (content != null) {
                val opened = sessions.openSaved(chatId, tab, content)
                if (opened != null) {
                    adopt(opened, summarize = false)
                    return
                }
            }
        }
        showHeader(tab, SummaryState.Interrupted(""), generating = false)
    }

    private suspend fun adopt(opened: PageConversation, summarize: Boolean) {
        session = opened
        watch(opened.tabId)
        val already = opened.messages.value.any { it.kind == MessageKind.SUMMARY }
        if (!summarize || already) {
            publish(opened)
            return
        }
        if (!enoughText(opened.document)) {
            publish(opened, summaryOverride = SummaryState.NotEnoughText)
            return
        }
        publish(opened, generating = true, streamingKind = MessageKind.SUMMARY, summaryOverride = SummaryState.Generating)
        collect(opened, summarizePage(opened), MessageKind.SUMMARY)
    }

    private suspend fun collect(opened: PageConversation, events: Flow<AiReply>, kind: MessageKind) {
        val stream = coroutineContext[Job] ?: return
        var refused = false
        opened.beginStream(stream, kind)
        try {
            events.collect { event ->
                when (event) {
                    is AiReply.Text -> {
                        opened.noteText(event.delta)
                        publish(opened, generating = true, streamingKind = kind)
                    }
                    is AiReply.Cite -> {
                        opened.noteCite(event.start)
                        if (event.start !in citedText) citedText[event.start] = event.citedText
                        publish(opened, generating = true, streamingKind = kind)
                    }
                    is AiReply.Done -> if (event.stopReason.equals("refusal", ignoreCase = true)) {
                        refused = true
                        opened.draft = ""
                        opened.draftCitations = emptyList()
                    }
                    is AiReply.Finished -> {
                        opened.commitPendingReply(!refused)
                        sessions.record(opened, finishedMessage(opened, kind, refused))
                        opened.endStream()
                        publish(opened, summaryOverride = SummaryState.Refused.takeIf { refused && kind == MessageKind.SUMMARY })
                    }
                    is AiReply.Failed -> {
                        opened.endStream()
                        fail(opened, event.failure, kind)
                    }
                }
            }
            if (opened.streaming) {
                opened.endStream()
                publish(opened)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun fail(opened: PageConversation, failure: AiFailure, kind: MessageKind) {
        when (failure) {
            AiFailure.Offline -> publish(
                opened,
                summaryOverride = SummaryState.Offline.takeIf { kind == MessageKind.SUMMARY },
                answerError = AnswerError.Offline.takeIf { kind == MessageKind.ANSWER },
            )
            AiFailure.Server -> if (kind == MessageKind.SUMMARY) {
                publish(opened, summaryOverride = SummaryState.ServerError)
            } else {
                publish(opened, answerError = AnswerError.Server)
            }
            AiFailure.Refused -> if (kind == MessageKind.SUMMARY) {
                publish(opened, summaryOverride = SummaryState.Refused)
            } else {
                publish(opened, answerError = AnswerError.Refused)
            }
            is AiFailure.NotAllowed -> {
                if (failure.hostChanged) _effects.tryEmit(AskPageEffect.NeedsPermission(opened.tabId, key.fromReader))
                publish(opened)
            }
        }
    }

    private fun launchRequest(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun watch(tabId: String) {
        if (runtimeJob != null) return
        runtimeJob = viewModelScope.launch {
            tabs.runtime(tabId).collect { runtime ->
                val current = session ?: return@collect
                val enabled = sameDocument(current, runtime)
                if (_state.value.citationsEnabled != enabled) {
                    _state.value = _state.value.copy(citationsEnabled = enabled)
                }
            }
        }
    }

    private fun onNetwork(online: Boolean) {
        if (_state.value.summary is SummaryState.Offline) {
            _state.value = _state.value.copy(inputEnabled = online)
        }
    }

    private fun publish(
        opened: PageConversation,
        generating: Boolean = false,
        waiting: Boolean = false,
        streamingKind: MessageKind? = null,
        summaryOverride: SummaryState? = null,
        answerError: AnswerError? = null,
    ) {
        val messages = opened.messages.value
        val storedSummary = messages.lastOrNull { it.kind == MessageKind.SUMMARY }?.let(::summaryOf)
        val summary = when {
            summaryOverride != null -> summaryOverride
            generating && streamingKind == MessageKind.SUMMARY -> streamingSummary(opened)
            storedSummary != null -> storedSummary
            else -> _state.value.summary
        }
        val streamingAnswer = opened.draft.takeIf { generating && streamingKind == MessageKind.ANSWER }
        val offline = summary is SummaryState.Offline
        _state.value = AskPageUiState(
            title = opened.document.title.ifBlank { _state.value.title },
            host = hostOf(opened.document.url) ?: _state.value.host,
            summary = summary,
            turns = turnsOf(messages, streamingAnswer),
            showChips = summary is SummaryState.Done && !generating && !offline,
            inputEnabled = network.hasActiveNetwork && !offline,
            generating = generating,
            waitingForAnswer = waiting || (generating && streamingKind == MessageKind.ANSWER && opened.draft.isEmpty()),
            citationsEnabled = sameDocument(opened, tabs.runtime(opened.tabId).value),
            answerError = if (generating) null else answerError,
        )
    }

    private fun streamingSummary(opened: PageConversation): SummaryState =
        if (opened.draft.isEmpty()) SummaryState.Generating else SummaryState.Streaming(opened.draft)

    private fun showHeader(tab: Tab?, summary: SummaryState, generating: Boolean) {
        _state.value = AskPageUiState(
            title = tab?.title.orEmpty().ifBlank { _state.value.title },
            host = hostOf(tab?.url.orEmpty()) ?: key.approvedHost.orEmpty(),
            summary = summary,
            generating = generating,
            inputEnabled = network.hasActiveNetwork && summary !is SummaryState.Offline,
        )
    }

    private fun summaryOf(message: ChatMessage): SummaryState = when (message.status) {
        MessageStatus.COMPLETE -> SummaryState.Done(message.text, summaryBullets(message.text), message.feedback, message.id)
        MessageStatus.STOPPED -> SummaryState.Interrupted(message.text)
        MessageStatus.REFUSED -> SummaryState.Refused
        MessageStatus.ERROR -> SummaryState.ServerError
    }

    private fun turnsOf(messages: List<ChatMessage>, streamingAnswer: String?): List<QaTurn> {
        val turns = mutableListOf<QaTurn>()
        var question: String? = null
        messages.forEach { message ->
            when (message.kind) {
                MessageKind.QUESTION -> {
                    question?.let { turns += QaTurn(it, null, emptyList(), stopped = false) }
                    question = message.text
                }
                MessageKind.ANSWER -> {
                    val asked = question ?: return@forEach
                    turns += QaTurn(
                        question = asked,
                        answer = message.text,
                        paragraphs = message.citations.map { it + 1 },
                        stopped = message.status == MessageStatus.STOPPED,
                    )
                    question = null
                }
                else -> Unit
            }
        }
        question?.let { asked ->
            turns += QaTurn(
                question = asked,
                answer = streamingAnswer?.takeIf { it.isNotEmpty() },
                paragraphs = streamingAnswer?.let { session?.draftCitations.orEmpty().map { index -> index + 1 } }.orEmpty(),
                stopped = false,
            )
        }
        return turns
    }

    private fun finishedMessage(opened: PageConversation, kind: MessageKind, refused: Boolean) = ChatMessage(
        id = 0,
        role = MessageRole.ASSISTANT,
        kind = kind,
        text = if (refused) "" else opened.draft,
        citations = if (refused) emptyList() else opened.draftCitations,
        status = if (refused) MessageStatus.REFUSED else MessageStatus.COMPLETE,
    )

    private fun sameDocument(opened: PageConversation, runtime: TabRuntime): Boolean =
        runtime.loadNonce == opened.document.loadNonce &&
            withoutFragment(runtime.committedUrl.orEmpty()) == withoutFragment(opened.document.url)

    private fun placeholder(url: String, title: String) = PageContent(
        url = url,
        loadNonce = 0,
        title = title,
        byline = null,
        siteName = null,
        lang = null,
        readerable = false,
        blocks = emptyList(),
    )

    private companion object {
        const val OPEN_HANDLED = "openHandled"
    }
}

/** The summary lines without a leading "- ". */
internal fun summaryBullets(text: String): List<String> = text.lineSequence()
    .map { it.trim().removePrefix("- ").trim() }
    .filter { it.isNotEmpty() }
    .toList()

private fun enoughText(content: PageContent): Boolean = content.blocks.sumOf { it.text.length } >= NOT_ENOUGH_TEXT

private const val NOT_ENOUGH_TEXT = 300
