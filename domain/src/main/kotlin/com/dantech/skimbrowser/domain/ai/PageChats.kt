package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.page.PageContent
import com.dantech.skimbrowser.domain.tab.Tab

/** The process-wide page and across-tabs chats the sheets open and record. */
interface PageChats {
    fun sessionFor(tabId: String): PageConversation?

    fun sessionForChat(chatId: Long): PageConversation?

    suspend fun openForTab(tab: Tab, content: PageContent, approvedHost: String): PageConversation

    suspend fun openSaved(chatId: Long, tab: Tab, content: PageContent): PageConversation?

    suspend fun openTabs(title: String, tabCount: Int, pages: List<TabContext>): PageConversation

    suspend fun openSavedTabs(chatId: Long): PageConversation?

    suspend fun record(session: PageConversation, message: ChatMessage)

    suspend fun setFeedback(session: PageConversation, messageId: Long, feedback: MessageFeedback)

    /**
     * Records the proxy id and revisions for [messageId]. [sentRevision] is the newest revision
     * the proxy accepted. A memory-only chat keeps this on the session and not in the database.
     */
    suspend fun stampFeedback(
        session: PageConversation,
        messageId: Long,
        answerId: String,
        revision: Int,
        sentRevision: Int,
    )
}
