package com.dantech.skimbrowser.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Asks the model. The implementation builds the SDK request and is the only type that holds it.
 * A reply never includes an SDK type.
 */
interface AiAssistant {
    fun summarize(session: PageConversation): Flow<AiReply>

    /**
     * Summarizes a saved article. The reply is not written into a Skim chat. [host] is the site
     * the text was saved from, and it must already be allowed for Skim AI.
     */
    fun summarizeSaved(
        host: String,
        url: String,
        title: String,
        paragraphs: List<String>,
        languageTag: String,
    ): Flow<AiReply>

    fun ask(session: PageConversation, question: String): Flow<AiReply>

    fun retry(session: PageConversation): Flow<AiReply>

    fun overviewTabs(session: PageConversation, context: List<TabContext>, prompt: String): Flow<AiReply>

    fun askTabs(session: PageConversation, question: String): Flow<AiReply>
}
