package com.dantech.skimbrowser.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Asks the model. The implementation builds the SDK request and is the only type that holds it.
 * A reply never includes an SDK type.
 */
interface AiAssistant {
    fun summarize(session: PageConversation): Flow<AiReply>

    fun ask(session: PageConversation, question: String): Flow<AiReply>

    fun retry(session: PageConversation): Flow<AiReply>

    fun overviewTabs(session: PageConversation, context: List<TabContext>, prompt: String): Flow<AiReply>

    fun askTabs(session: PageConversation, question: String): Flow<AiReply>
}
