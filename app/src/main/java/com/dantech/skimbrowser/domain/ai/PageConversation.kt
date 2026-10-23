package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.page.PageContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/**
 * A chat the sheets can drive. The assistant implementation keeps model-SDK state behind this.
 * [canRetry] is true once a summary or question has been built and can be sent again.
 */
interface PageConversation {
    val chatId: Long
    val tabId: String
    val pageUrl: String
    val document: PageContent
    val tabCount: Int
    val messages: StateFlow<List<ChatMessage>>
    var streaming: Boolean
    var draft: String
    var draftKind: MessageKind
    var draftCitations: List<Int>
    val canRetry: Boolean
    val hasOpening: Boolean
    val hasSources: Boolean

    fun cancelStream()
    fun takeStop(): Boolean
    fun beginStream(job: Job, kind: MessageKind)
    fun noteText(delta: String)
    fun noteCite(start: Int)
    fun endStream()

    /** Keeps the model's latest message for the next turn when [keep] is true, and drops it otherwise. */
    fun commitPendingReply(keep: Boolean)

    fun replacePages(pages: List<TabContext>)
    fun ensureOverviewOpening(pages: List<TabContext>, prompt: String)
    fun rememberAssistantText(text: String)
    fun stoppedMessage(): ChatMessage
}
