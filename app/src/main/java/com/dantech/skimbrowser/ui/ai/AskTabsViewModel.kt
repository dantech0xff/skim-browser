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
import com.dantech.skimbrowser.domain.ai.AskTabs
import com.dantech.skimbrowser.domain.ai.ChatMessage
import com.dantech.skimbrowser.domain.ai.CollectOpenTabs
import com.dantech.skimbrowser.domain.ai.NetworkAvailability
import com.dantech.skimbrowser.domain.ai.OverviewTabs
import com.dantech.skimbrowser.domain.ai.PageChats
import com.dantech.skimbrowser.domain.ai.PageConversation
import com.dantech.skimbrowser.domain.ai.TabContext
import com.dantech.skimbrowser.domain.ai.TabsOverview
import com.dantech.skimbrowser.domain.ai.decodeStoredOverview
import com.dantech.skimbrowser.domain.ai.encodeOverview
import com.dantech.skimbrowser.domain.ai.overviewFromModel
import com.dantech.skimbrowser.domain.ai.withRealTabIds
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.chat.MessageStatus
import com.dantech.skimbrowser.ui.navigation.AskTabsKey
import kotlin.coroutines.coroutineContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One topic in an across-tabs answer. [firstTabId] is the tab the group link scrolls to. */
@Immutable
data class OverviewGroupUi(val name: String, val summary: String, val firstTabId: String?)

/** What the across-tabs card is showing. */
@Immutable
sealed interface TabsOverviewState {
    /** The sheet is open and nothing has been asked. */
    data object Idle : TabsOverviewState

    /** The overview has not produced JSON yet. */
    data object Generating : TabsOverviewState

    /** A finished overview. [groups] use real tab ids. */
    data class Ready(
        val intro: String,
        val groups: ImmutableList<OverviewGroupUi>,
        val feedback: MessageFeedback,
        val messageId: Long,
        val followUps: ImmutableList<String>,
    ) : TabsOverviewState

    data object Offline : TabsOverviewState

    data object ServerError : TabsOverviewState

    data object Refused : TabsOverviewState
}

/** A follow-up question and, once it exists, the answer under it. */
@Immutable
data class TabsAnswer(val question: String, val answer: String?, val stopped: Boolean)

/**
 * Ask Skim about the open tabs. [tabCount] is how many tabs the answer covers. The subtitle is
 * hidden when it is 0. [prompt] is the first question, drawn as the user's bubble.
 */
@Immutable
data class AskTabsUiState(
    val tabCount: Int = 0,
    val prompt: String? = null,
    val overview: TabsOverviewState = TabsOverviewState.Idle,
    val turns: ImmutableList<TabsAnswer> = persistentListOf(),
    val generating: Boolean = false,
    val inputEnabled: Boolean = true,
    val answerError: AnswerError? = null,
) {
    val showSubtitle: Boolean get() = tabCount > 0
}

/**
 * Ask Skim about the open tabs. A prompt on the key is sent once. Opening again, or coming back
 * after process death, shows what was already saved and sends nothing until the person asks.
 */
@HiltViewModel(assistedFactory = AskTabsViewModel.Factory::class)
class AskTabsViewModel @AssistedInject constructor(
    @Assisted private val key: AskTabsKey,
    private val sessions: PageChats,
    private val overviewTabs: OverviewTabs,
    private val askTabs: AskTabs,
    private val collectOpenTabs: CollectOpenTabs,
    private val network: NetworkAvailability,
    private val savedState: SavedStateHandle,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: AskTabsKey): AskTabsViewModel
    }

    private val _state = MutableStateFlow(AskTabsUiState())
    val state: StateFlow<AskTabsUiState> = _state.asStateFlow()

    private var session: PageConversation? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            network.validated.collect { online ->
                if (_state.value.overview is TabsOverviewState.Offline) {
                    _state.value = _state.value.copy(inputEnabled = online)
                }
            }
        }
        job = viewModelScope.launch { open() }
    }

    /** Sends [question]. The first one asks for the overview. Later ones, and the chips, ask a follow-up. */
    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || !_state.value.inputEnabled || _state.value.generating) return
        if (hasOverview()) sendFollowUp(text) else sendOverview(text)
    }

    /** Asks for the overview again, or sends the unanswered follow-up again. Nothing is sent while offline. */
    fun retry() {
        if (!network.hasActiveNetwork) {
            if (hasOverview()) {
                _state.value = _state.value.copy(answerError = AnswerError.Offline, generating = false)
            } else {
                _state.value = _state.value.copy(
                    overview = TabsOverviewState.Offline,
                    generating = false,
                    inputEnabled = false,
                    answerError = null,
                )
            }
            return
        }
        val current = session
        val lastQuestion = current?.messages?.value?.lastOrNull()?.takeIf { it.kind == MessageKind.QUESTION }
        if (current != null && hasOverview() && lastQuestion != null) {
            sendFollowUp(lastQuestion.text, recordQuestion = false)
        } else {
            val prompt = _state.value.prompt ?: key.prompt?.trim().orEmpty()
            if (prompt.isNotEmpty()) sendOverview(prompt)
        }
    }

    /** Stops the request. An overview that has not finished is discarded. A follow-up keeps the text that arrived. */
    fun stop() {
        val current = session
        val saveAnswer = current?.streaming == true && current.draftKind == MessageKind.ANSWER && current.takeStop()
        current?.cancelStream()
        job?.cancel()
        if (current != null && saveAnswer) {
            val message = current.stoppedMessage()
            current.endStream()
            viewModelScope.launch {
                sessions.record(current, message)
                publish(current)
            }
        } else {
            current?.endStream()
            if (current != null) current.draft = ""
            if (current != null) publish(current) else _state.value = _state.value.copy(generating = false, inputEnabled = network.hasActiveNetwork)
        }
    }

    /** Good and poor exclude each other. Tapping the selected one clears it. Nothing is sent. */
    fun feedback(good: Boolean) {
        val ready = _state.value.overview as? TabsOverviewState.Ready ?: return
        val current = session ?: return
        val next = when {
            good && ready.feedback == MessageFeedback.GOOD -> MessageFeedback.NONE
            !good && ready.feedback == MessageFeedback.POOR -> MessageFeedback.NONE
            good -> MessageFeedback.GOOD
            else -> MessageFeedback.POOR
        }
        viewModelScope.launch {
            sessions.setFeedback(current, ready.messageId, next)
            publish(current)
        }
    }

    override fun onCleared() {
        val current = session ?: return
        val saveAnswer = current.streaming && current.draftKind == MessageKind.ANSWER && current.takeStop()
        current.cancelStream()
        if (!saveAnswer) {
            current.endStream()
            return
        }
        val message = current.stoppedMessage()
        current.endStream()
        appScope.launch { sessions.record(current, message) }
    }

    private suspend fun open() {
        val savedChatId = key.savedChatId
        if (savedChatId != null) {
            savedState[OPEN_HANDLED] = true
            openSaved(savedChatId)
            return
        }
        val restored = savedState.get<Boolean>(OPEN_HANDLED) == true
        if (!restored) savedState[OPEN_HANDLED] = true
        val savedId = savedState.get<Long>(CHAT_ID)
        if (savedId != null && savedId > 0) {
            sessions.sessionForChat(savedId)?.let { adopt(it); return }
            openSaved(savedId)
            return
        }
        showCount()
        if (restored) return
        val prompt = key.prompt?.trim().orEmpty()
        if (prompt.isNotEmpty()) sendOverview(prompt)
    }

    private fun sendOverview(prompt: String) {
        launchRequest {
            val context = collectOpenTabs(extractLive = true)
            if (context.isEmpty()) {
                _state.value = AskTabsUiState(tabCount = 0, prompt = prompt, inputEnabled = network.hasActiveNetwork)
                return@launchRequest
            }
            if (!network.hasActiveNetwork) {
                _state.value = AskTabsUiState(
                    tabCount = context.size,
                    prompt = prompt,
                    overview = TabsOverviewState.Offline,
                    inputEnabled = false,
                )
                return@launchRequest
            }
            _state.value = AskTabsUiState(
                tabCount = context.size,
                prompt = prompt,
                overview = TabsOverviewState.Generating,
                generating = true,
                inputEnabled = true,
            )
            val current = session ?: sessions.openTabs(prompt, context.size, context).also { opened ->
                session = opened
                if (opened.chatId > 0) savedState[CHAT_ID] = opened.chatId
            }
            session = current
            collectOverview(current, context, prompt)
        }
    }

    private fun sendFollowUp(question: String, recordQuestion: Boolean = true) {
        launchRequest {
            val current = session ?: return@launchRequest
            val context = collectOpenTabs(extractLive = true)
            if (context.isNotEmpty()) current.replacePages(context)
            current.ensureOverviewOpening(context, question)
            if (!current.hasOpening || !current.hasSources) return@launchRequest
            if (recordQuestion) {
                sessions.record(
                    current,
                    ChatMessage(
                        id = 0,
                        role = MessageRole.USER,
                        kind = MessageKind.QUESTION,
                        text = question,
                        status = MessageStatus.COMPLETE,
                    ),
                )
            }
            publish(current, generating = true, streamingAnswer = true)
            collectAnswer(current, question)
        }
    }

    private suspend fun collectOverview(current: PageConversation, context: List<TabContext>, prompt: String) {
        val stream = coroutineContext[Job] ?: return
        current.beginStream(stream, MessageKind.TABS_OVERVIEW)
        var stop = ""
        var failed: AiFailure? = null
        try {
            overviewTabs(current, context, prompt).collect { event ->
                when (event) {
                    is AiReply.Text -> current.noteText(event.delta)
                    is AiReply.Cite -> Unit
                    is AiReply.Done -> stop = event.stopReason
                    is AiReply.Finished -> current.commitPendingReply(false)
                    is AiReply.Failed -> failed = event.failure
                }
            }
            val failure = failed
            if (failure != null) {
                current.endStream()
                failOverview(current, failure)
                return
            }
            current.endStream()
            if (stop.equals(REFUSAL, ignoreCase = true)) {
                current.draft = ""
                publish(current, overviewOverride = TabsOverviewState.Refused)
                return
            }
            val parsed = overviewFromModel(stop, current.draft, context.map { it.ref }.toSet())
            if (parsed == null) {
                publish(current, overviewOverride = TabsOverviewState.ServerError)
                return
            }
            val stored = parsed.withRealTabIds(context)
            sessions.record(current, questionMessage(prompt))
            sessions.record(current, overviewMessage(stored))
            current.rememberAssistantText(encodeOverview(stored))
            publish(current, tabCount = context.size)
        } catch (e: CancellationException) {
            throw e
        }
    }

    private suspend fun collectAnswer(current: PageConversation, question: String) {
        val stream = coroutineContext[Job] ?: return
        current.beginStream(stream, MessageKind.ANSWER)
        var refused = false
        try {
            askTabs(current, question).collect { event ->
                when (event) {
                    is AiReply.Text -> {
                        current.noteText(event.delta)
                        publish(current, generating = true, streamingAnswer = true)
                    }
                    is AiReply.Cite -> Unit
                    is AiReply.Done -> if (event.stopReason.equals(REFUSAL, ignoreCase = true)) {
                        refused = true
                        current.draft = ""
                    }
                    is AiReply.Finished -> {
                        current.endStream()
                        if (refused || current.draft.isEmpty()) {
                            current.commitPendingReply(false)
                            publish(current, answerError = if (refused) AnswerError.Refused else AnswerError.Server)
                            return@collect
                        }
                        current.commitPendingReply(true)
                        sessions.record(
                            current,
                            ChatMessage(
                                id = 0,
                                role = MessageRole.ASSISTANT,
                                kind = MessageKind.ANSWER,
                                text = current.draft,
                                status = MessageStatus.COMPLETE,
                            ),
                        )
                        publish(current)
                    }
                    is AiReply.Failed -> {
                        current.endStream()
                        failAnswer(current, event.failure)
                    }
                }
            }
            if (current.streaming) {
                current.endStream()
                publish(current)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private suspend fun openSaved(chatId: Long) {
        val opened = sessions.openSavedTabs(chatId) ?: return
        adopt(opened)
    }

    private suspend fun showCount() {
        val count = collectOpenTabs(extractLive = false).size
        _state.value = AskTabsUiState(tabCount = count, inputEnabled = network.hasActiveNetwork)
    }

    private fun adopt(opened: PageConversation) {
        session = opened
        publish(opened, tabCount = opened.tabCount.takeIf { it > 0 })
    }

    private fun hasOverview(): Boolean =
        session?.messages?.value?.any { it.kind == MessageKind.TABS_OVERVIEW && it.status == MessageStatus.COMPLETE } == true

    private fun failOverview(current: PageConversation, failure: AiFailure) {
        val overview = when (failure) {
            AiFailure.Offline -> TabsOverviewState.Offline
            AiFailure.Refused -> TabsOverviewState.Refused
            AiFailure.Server, is AiFailure.NotAllowed -> TabsOverviewState.ServerError
        }
        publish(current, overviewOverride = overview)
    }

    private fun failAnswer(current: PageConversation, failure: AiFailure) {
        val answer = when (failure) {
            AiFailure.Offline -> AnswerError.Offline
            AiFailure.Refused -> AnswerError.Refused
            AiFailure.Server, is AiFailure.NotAllowed -> AnswerError.Server
        }
        publish(current, answerError = answer)
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

    private fun publish(
        opened: PageConversation,
        generating: Boolean = false,
        streamingAnswer: Boolean = false,
        overviewOverride: TabsOverviewState? = null,
        answerError: AnswerError? = null,
        tabCount: Int? = null,
    ) {
        val messages = opened.messages.value
        val prompt = messages.firstOrNull { it.kind == MessageKind.QUESTION }?.text ?: _state.value.prompt
        val stored = messages.lastOrNull { it.kind == MessageKind.TABS_OVERVIEW && it.status == MessageStatus.COMPLETE }
        val overview = when {
            overviewOverride != null -> overviewOverride
            generating && opened.draftKind == MessageKind.TABS_OVERVIEW -> TabsOverviewState.Generating
            stored != null -> overviewOf(stored)
            else -> TabsOverviewState.Idle
        }
        val offline = overview is TabsOverviewState.Offline
        val count = tabCount ?: opened.tabCount.takeIf { it > 0 } ?: _state.value.tabCount
        _state.value = AskTabsUiState(
            tabCount = count,
            prompt = prompt,
            overview = overview,
            turns = turnsOf(messages, opened.draft.takeIf { generating && streamingAnswer }),
            generating = generating,
            inputEnabled = network.hasActiveNetwork && !offline,
            answerError = if (generating) null else answerError,
        )
    }

    private companion object {
        const val OPEN_HANDLED = "openHandled"
        const val CHAT_ID = "chatId"
        const val REFUSAL = "refusal"
    }
}

/** The text Copy puts on the clipboard: the intro, then each group name and its summary. */
internal fun tabsAnswerText(state: AskTabsUiState): String {
    val ready = state.overview as? TabsOverviewState.Ready ?: return ""
    return buildString {
        append(ready.intro)
        ready.groups.forEach { group ->
            append("\n\n")
            append(group.name)
            append('\n')
            append(group.summary)
        }
    }
}

private fun overviewOf(message: ChatMessage): TabsOverviewState {
    val parsed = decodeStoredOverview(message.text) ?: return TabsOverviewState.ServerError
    return TabsOverviewState.Ready(
        intro = parsed.intro,
        groups = parsed.groups.map { group ->
            OverviewGroupUi(group.name, group.summary, group.tabIds.firstOrNull())
        }.toImmutableList(),
        feedback = message.feedback,
        messageId = message.id,
        followUps = message.suggestedQuestions.ifEmpty { parsed.followUps }.toImmutableList(),
    )
}

private fun overviewMessage(overview: TabsOverview) = ChatMessage(
    id = 0,
    role = MessageRole.ASSISTANT,
    kind = MessageKind.TABS_OVERVIEW,
    text = encodeOverview(overview),
    status = MessageStatus.COMPLETE,
    suggestedQuestions = overview.followUps,
)

private fun questionMessage(text: String) = ChatMessage(
    id = 0,
    role = MessageRole.USER,
    kind = MessageKind.QUESTION,
    text = text,
    status = MessageStatus.COMPLETE,
)

private fun turnsOf(messages: List<ChatMessage>, streamingAnswer: String?): ImmutableList<TabsAnswer> {
    val turns = mutableListOf<TabsAnswer>()
    var seenQuestion = false
    var pending: String? = null
    messages.forEach { message ->
        when (message.kind) {
            MessageKind.QUESTION -> {
                if (!seenQuestion) {
                    seenQuestion = true
                    return@forEach
                }
                pending?.let { turns += TabsAnswer(it, null, stopped = false) }
                pending = message.text
            }
            MessageKind.ANSWER -> {
                val asked = pending ?: return@forEach
                if (message.status == MessageStatus.REFUSED || message.status == MessageStatus.ERROR) {
                    pending = null
                    return@forEach
                }
                turns += TabsAnswer(asked, message.text, stopped = message.status == MessageStatus.STOPPED)
                pending = null
            }
            else -> Unit
        }
    }
    pending?.let { asked ->
        turns += TabsAnswer(asked, streamingAnswer?.takeIf { it.isNotEmpty() }, stopped = false)
    }
    return turns.toImmutableList()
}
