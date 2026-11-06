package com.dantech.skimbrowser.ai

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
import kotlinx.serialization.json.JsonObject

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
        val params = request(AiConfig.EFFORT_PAGE, AiConfig.MAX_TOKENS, format = null, chat.summaryOpening())
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
            request(AiConfig.EFFORT_PAGE, AiConfig.MAX_TOKENS, format = null, chat.questionOpening(wrapped))
        } else {
            followUp(AiConfig.EFFORT_PAGE, AiConfig.MAX_TOKENS, format = null, chat.opening!!, chat, wrapped)
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
        val params = request(AiConfig.EFFORT_TABS, AiConfig.MAX_TOKENS, TABS_OVERVIEW_FORMAT, opening)
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
        val params = followUp(
            AiConfig.EFFORT_TABS,
            AiConfig.MAX_TOKENS,
            format = null,
            opening,
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
        val params = request(
            AiConfig.EFFORT_GROUPING,
            AiConfig.GROUPING_MAX_TOKENS,
            GROUPING_FORMAT,
            groupingBlocks(context),
        )
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
                        session.stashReply(event.text)
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
        params: SkimRequest,
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

    private fun request(
        effort: String,
        maxTokens: Long,
        format: JsonObject?,
        opening: List<WireBlock>,
        turns: List<WireMessage> = emptyList(),
    ): SkimRequest = SkimRequest.build(
        maxTokens = maxTokens,
        effort = effort,
        format = format,
        messages = listOf(WireMessage("user", WireContent.Blocks(opening))) + turns,
    )

    /** Adds [wrapped] after the turns already sent, and remembers it unless that turn is already last. */
    private fun followUp(
        effort: String,
        maxTokens: Long,
        format: JsonObject?,
        opening: List<WireBlock>,
        chat: ChatSession,
        wrapped: String,
    ): SkimRequest {
        val turns = chat.history
        val already = turns.lastOrNull().let { it is HistoryTurn.Text && it.role == MessageRole.USER && it.text == wrapped }
        val replay = (if (already) turns.dropLast(1) else turns).map { turn ->
            when (turn) {
                is HistoryTurn.Text -> WireMessage(
                    role = if (turn.role == MessageRole.USER) "user" else "assistant",
                    content = WireContent.Text(turn.text),
                )
            }
        }
        if (!already) chat.addTurn(HistoryTurn.Text(MessageRole.USER, wrapped))
        return request(
            effort,
            maxTokens,
            format,
            opening,
            replay + WireMessage("user", WireContent.Text(wrapped)),
        )
    }
}
