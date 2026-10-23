package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * The only builder of model requests, and the only place a request is allowed to open a connection.
 * Incognito pages, a site that has not been allowed, and a page that moved to another host are
 * refused here, before the client is asked for.
 */
class SkimAiService(
    private val clients: ClaudeClientProvider,
    private val sites: SitePermissionRepository,
    private val settings: SettingsRepository,
) {
    fun summarize(session: ChatSession): Flow<AiEvent> {
        val params = base()
            .addUserMessageOfBlockParams(session.summaryOpening())
            .build()
        session.lastParams = params
        return send(params, session.sources, Consent.SITE_APPROVED, session.approvedHost)
    }

    /**
     * Asks [question]. The first question on a page that was not summarized becomes the opening
     * message. A later question is added after the turns already sent, and remembered so the
     * request after it repeats them.
     */
    fun ask(session: ChatSession, question: String): Flow<AiEvent> {
        val wrapped = Prompts.question(question)
        val params = if (session.opening == null) {
            base().addUserMessageOfBlockParams(session.questionOpening(wrapped)).build()
        } else {
            val turns = session.history
            val already = turns.lastOrNull().let { it is HistoryTurn.Text && it.role == MessageRole.USER && it.text == wrapped }
            val builder = base().addUserMessageOfBlockParams(session.opening!!)
            replay(builder, if (already) turns.dropLast(1) else turns)
            builder.addUserMessage(wrapped)
            if (!already) session.addTurn(HistoryTurn.Text(MessageRole.USER, wrapped))
            builder.build()
        }
        session.lastParams = params
        return send(params, session.sources, Consent.SITE_APPROVED, session.approvedHost)
    }

    /** Sends the last summary or question again, without adding a turn. */
    fun retry(session: ChatSession): Flow<AiEvent> {
        val params = session.lastParams ?: return flow { }
        return send(params, session.sources, Consent.SITE_APPROVED, session.approvedHost)
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
        val denied = sources.any { source ->
            val state = states[source.host] ?: SkimAiState.UNSET
            when (consent) {
                Consent.SITE_APPROVED -> state == SkimAiState.ASK || (state == SkimAiState.UNSET && askBefore)
                Consent.TABS_ACTION -> state == SkimAiState.ASK
            }
        }
        if (denied) throw AiDenied(AiFailure.NotAllowed(hostChanged = false))
        emitAll(clients.client().streamEvents(params))
    }

    private fun base(): MessageCreateParams.Builder =
        MessageCreateParams.builder()
            .model(AiConfig.MODEL_ID)
            .maxTokens(AiConfig.MAX_TOKENS)
            .outputConfig(OutputConfig.builder().effort(AiConfig.EFFORT_PAGE).build())
            .system(Prompts.SYSTEM)

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
