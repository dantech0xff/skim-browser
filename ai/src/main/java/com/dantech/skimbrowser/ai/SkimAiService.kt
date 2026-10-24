package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.AiFailure
import com.dantech.skimbrowser.domain.ai.AiReply
import com.dantech.skimbrowser.domain.ai.PageConversation
import com.dantech.skimbrowser.domain.chat.MessageRole
import com.dantech.skimbrowser.domain.permission.ResolveSkimAiConsent
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiAudience
import com.dantech.skimbrowser.domain.permission.SkimAiConsent
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * The only builder of model requests, and the only place a request is allowed to open a connection.
 * Incognito pages, a site that has not been allowed, and a page that moved to another host are
 * refused here, before the client is asked for.
 */
@Singleton
class SkimAiService(
    private val clients: ClaudeClientProvider,
    private val sites: SitePermissionRepository,
    private val settings: SettingsRepository,
    private val resolveConsent: ResolveSkimAiConsent = ResolveSkimAiConsent(),
) : AiAssistant {

    @Inject constructor(
        clients: ClaudeClientProvider,
        sites: SitePermissionRepository,
        settings: SettingsRepository,
    ) : this(clients, sites, settings, resolveConsent = ResolveSkimAiConsent())

    override fun summarize(session: PageConversation): Flow<AiReply> {
        val chat = session as ChatSession
        val params = base()
            .addUserMessageOfBlockParams(chat.summaryOpening())
            .build()
        chat.lastParams = params
        return send(params, chat.sources, Consent.SITE_APPROVED, chat.approvedHost).toReplies(chat)
    }

    /**
     * Asks [question]. The first question on a page that was not summarized becomes the opening
     * message. A later question is added after the turns already sent, and remembered so the
     * request after it repeats them.
     */
    override fun ask(session: PageConversation, question: String): Flow<AiReply> {
        val chat = session as ChatSession
        val wrapped = Prompts.question(question)
        val params = if (chat.opening == null) {
            base().addUserMessageOfBlockParams(chat.questionOpening(wrapped)).build()
        } else {
            followUpParams(base().addUserMessageOfBlockParams(chat.opening!!), chat, wrapped)
        }
        chat.lastParams = params
        return send(params, chat.sources, Consent.SITE_APPROVED, chat.approvedHost).toReplies(chat)
    }

    /**
     * Asks for one answer across [context]. The first user message is one document per tab, then
     * [prompt]. A later call replaces that message, so a failed overview can be asked again.
     */
    override fun overviewTabs(session: PageConversation, context: List<TabContext>, prompt: String): Flow<AiReply> {
        val chat = session as ChatSession
        val opening = chat.setOpening(overviewBlocks(context, prompt))
        chat.replaceSources(context.toSources())
        val params = configured(AiConfig.EFFORT_TABS, AiConfig.MAX_TOKENS, TABS_OVERVIEW_FORMAT)
            .addUserMessageOfBlockParams(opening)
            .build()
        chat.lastParams = params
        return send(params, chat.sources, Consent.TABS_ACTION, chat.approvedHost).toReplies(chat)
    }

    /**
     * Asks [question] about the tabs already sent. The documents stay on the first user message.
     * The answer is plain text: no JSON schema and no citations. Nothing is sent when that first
     * message has not been built.
     */
    override fun askTabs(session: PageConversation, question: String): Flow<AiReply> {
        val chat = session as ChatSession
        val opening = chat.opening ?: return flow { }
        val params = followUpParams(
            configured(AiConfig.EFFORT_TABS, AiConfig.MAX_TOKENS, format = null)
                .addUserMessageOfBlockParams(opening),
            chat,
            Prompts.question(question),
        )
        chat.lastParams = params
        return send(params, chat.sources, Consent.TABS_ACTION, chat.approvedHost).toReplies(chat)
    }

    /**
     * Groups [context] from titles and hosts. Null when the model does not finish an answer that
     * parses into at least one group of two tabs. A refusal and a reply cut off at the token limit
     * are null. [CancellationException] is rethrown; any other failure is thrown for the caller to
     * ignore, so a grouping request that is not allowed never becomes a layout.
     */
    suspend fun groupTabs(context: List<TabContext>): GroupingResult? {
        val params = configured(AiConfig.EFFORT_GROUPING, AiConfig.GROUPING_MAX_TOKENS, GROUPING_FORMAT)
            .addUserMessageOfBlockParams(groupingBlocks(context))
            .build()
        val events = send(params, context.toSources(), Consent.TABS_ACTION, approvedHost = "").toList()
        val stop = events.filterIsInstance<AiEvent.Done>().lastOrNull()?.stopReason.orEmpty()
        val text = events.filterIsInstance<AiEvent.Text>().joinToString("") { it.delta }
        return groupingFromModel(stop, text, context.map { it.ref }.toSet())
    }

    /** Sends the last summary or question again, without adding a turn. */
    override fun retry(session: PageConversation): Flow<AiReply> {
        val chat = session as ChatSession
        val params = chat.lastParams ?: return flow { }
        return send(params, chat.sources, Consent.SITE_APPROVED, chat.approvedHost).toReplies(chat)
    }

    private fun Flow<AiEvent>.toReplies(session: ChatSession): Flow<AiReply> = flow {
        try {
            collect { event ->
                when (event) {
                    is AiEvent.Text -> emit(AiReply.Text(event.delta))
                    is AiEvent.Cite -> emit(AiReply.Cite(event.start, event.end, event.citedText))
                    is AiEvent.Done -> emit(AiReply.Done(event.stopReason))
                    is AiEvent.Final -> {
                        session.stashReply(event.message)
                        emit(AiReply.Finished)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(AiReply.Failed(e.toAiFailure()))
        }
    }

    internal fun send(
        params: MessageCreateParams,
        sources: List<AiSource>,
        consent: Consent,
        approvedHost: String,
    ): Flow<AiEvent> = flow {
        require(sources.none { it.incognito }) { "Incognito pages are never sent" }
        if (consent == Consent.SITE_APPROVED) {
            if (sources.isEmpty() || sources.any { it.host != approvedHost }) {
                throw AiDenied(AiFailure.NotAllowed(hostChanged = true))
            }
        } else if (sources.isEmpty()) {
            throw AiDenied(AiFailure.NotAllowed(hostChanged = false))
        }
        val askBefore = settings.settings.first().askBeforeSending
        val states = sites.skimAiStates(sources.map { it.host }.toSet())
        val audience = if (consent == Consent.SITE_APPROVED) SkimAiAudience.PAGE else SkimAiAudience.TABS
        val denied = sources.any { source ->
            val state = states[source.host] ?: SkimAiState.UNSET
            resolveConsent(state, askBefore, audience) == SkimAiConsent.NeedsPermission
        }
        if (denied) throw AiDenied(AiFailure.NotAllowed(hostChanged = false))
        emitAll(clients.client().streamEvents(params))
    }

    private fun base(): MessageCreateParams.Builder =
        configured(AiConfig.EFFORT_PAGE, AiConfig.MAX_TOKENS, format = null)

    private fun configured(
        effort: OutputConfig.Effort,
        maxTokens: Long,
        format: JsonOutputFormat?,
    ): MessageCreateParams.Builder {
        val output = OutputConfig.builder().effort(effort)
        if (format != null) output.format(format)
        return MessageCreateParams.builder()
            .model(AiConfig.MODEL_ID)
            .maxTokens(maxTokens)
            .outputConfig(output.build())
            .system(Prompts.SYSTEM)
    }

    /** Adds [wrapped] after the turns already sent, and remembers it unless that turn is already last. */
    private fun followUpParams(
        builder: MessageCreateParams.Builder,
        chat: ChatSession,
        wrapped: String,
    ): MessageCreateParams {
        val turns = chat.history
        val already = turns.lastOrNull().let { it is HistoryTurn.Text && it.role == MessageRole.USER && it.text == wrapped }
        replay(builder, if (already) turns.dropLast(1) else turns)
        builder.addUserMessage(wrapped)
        if (!already) chat.addTurn(HistoryTurn.Text(MessageRole.USER, wrapped))
        return builder.build()
    }

    private fun replay(builder: MessageCreateParams.Builder, turns: List<HistoryTurn>) {
        for (turn in turns) {
            when (turn) {
                is HistoryTurn.Live -> builder.addMessage(turn.message)
                is HistoryTurn.Text -> when (turn.role) {
                    MessageRole.USER -> builder.addUserMessage(turn.text)
                    MessageRole.ASSISTANT -> builder.addAssistantMessage(turn.text)
                }
            }
        }
    }
}
