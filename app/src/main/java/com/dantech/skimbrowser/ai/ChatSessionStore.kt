package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.browser.PageEvent
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.page.PageContent
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.MessageFeedback
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.hostOf
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.data.withoutFragment
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The process-wide chats. A chat is saved only while Keep chat history is on, and a memory-only
 * chat never gets an id written onto its tab. Leaving the page, or deleting the chat, drops the
 * session here; [record] never changes which chat a tab points at.
 */
class ChatSessionStore(
    private val chats: ChatRepository,
    private val tabs: TabManager,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val clock: () -> Long = System::currentTimeMillis,
    private val languageTag: () -> String = { Locale.getDefault().toLanguageTag() },
) {
    private val byTab = ConcurrentHashMap<String, ChatSession>()
    private val nextMemoryId = AtomicLong(-1)

    init {
        appScope.launch {
            tabs.pageEvents.collect { event ->
                if (event is PageEvent.Navigated) onNavigated(event)
            }
        }
        appScope.launch {
            chats.removals.collect { onRemoval(it) }
        }
    }

    fun sessionFor(tabId: String): ChatSession? = byTab[tabId]

    /** Starts the chat for [tab] about [content]. [approvedHost] is the host the person allowed. */
    suspend fun openForTab(tab: Tab, content: PageContent, approvedHost: String): ChatSession {
        val url = withoutFragment(content.url)
        val keep = settings.settings.first().keepChatHistory && !tab.incognito
        val id = if (keep) {
            val now = clock()
            chats.createChat(
                SkimChatEntity(
                    kind = ChatKind.PAGE,
                    url = url,
                    host = hostOf(content.url),
                    title = content.title.ifBlank { tab.title.ifBlank { approvedHost } },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            nextMemoryId.getAndDecrement()
        }
        if (id > 0) withContext(mainDispatcher) { tabs.bindChat(tab.id, id) }
        return session(id, tab, content, approvedHost, url).also { byTab[tab.id] = it }
    }

    /**
     * Attaches the saved chat [chatId] to [tab] and fills it with the messages already stored.
     * Nothing is sent. Null when the chat is gone.
     */
    suspend fun openSaved(chatId: Long, tab: Tab, content: PageContent): ChatSession? {
        val chat = chats.chat(chatId) ?: return null
        val saved = chats.messages(chatId).map { it.toMessage() }
        val session = session(chat.id, tab, content, hostOf(content.url).orEmpty(), withoutFragment(chat.url ?: content.url))
        restoreTurns(session, saved)
        session.replaceMessages(saved)
        withContext(mainDispatcher) { tabs.bindChat(tab.id, chat.id) }
        byTab[tab.id] = session
        return session
    }

    /** Appends [message]. A memory-only chat is not written, and the tab's chat id is left alone. */
    suspend fun record(session: ChatSession, message: ChatMessage) {
        val stored = if (session.chatId <= 0) {
            message.copy(id = message.id.takeIf { it != 0L } ?: nextMemoryId.getAndDecrement())
        } else {
            val now = clock()
            val id = chats.addMessage(message.toEntity(session.chatId, now))
            val chat = chats.chat(session.chatId)
            if (chat != null && message.kind == MessageKind.SUMMARY) {
                chats.updateChat(
                    chat.copy(snippet = summarySnippet(message.text) ?: chat.snippet, updatedAt = now),
                )
            }
            message.copy(id = id)
        }
        session.replaceMessages(session.messages.value + stored)
    }

    suspend fun setFeedback(session: ChatSession, messageId: Long, feedback: MessageFeedback) {
        val updated = session.messages.value.map { if (it.id == messageId) it.copy(feedback = feedback) else it }
        session.replaceMessages(updated)
        if (session.chatId <= 0) return
        val entity = chats.messages(session.chatId).firstOrNull { it.id == messageId } ?: return
        chats.updateMessage(entity.copy(feedback = feedback))
    }

    private suspend fun onNavigated(event: PageEvent.Navigated) {
        if (event.isSameDocument) return
        val session = byTab[event.tabId] ?: return
        if (withoutFragment(event.url) == session.pageUrl) return
        session.cancelStream()
        if (session.streaming && session.takeStop()) record(session, stoppedMessage(session))
        session.endStream()
        detach(session)
    }

    private suspend fun onRemoval(id: Long?) {
        val gone = if (id == null) byTab.values.toList() else byTab.values.filter { it.chatId == id }
        gone.forEach { session ->
            session.cancelStream()
            session.endStream()
            detach(session)
        }
    }

    private suspend fun detach(session: ChatSession) {
        if (session.chatId > 0) withContext(mainDispatcher) { tabs.bindChat(session.tabId, null) }
        byTab.remove(session.tabId)
    }

    private fun session(id: Long, tab: Tab, content: PageContent, approvedHost: String, pageUrl: String) = ChatSession(
        chatId = id,
        tabId = tab.id,
        pageUrl = pageUrl,
        approvedHost = approvedHost,
        sources = listOf(AiSource(tab.id, tab.incognito, hostOf(content.url).orEmpty())),
        document = content,
        languageTag = languageTag(),
    )

    private fun restoreTurns(session: ChatSession, saved: List<ChatMessage>) {
        val summarized = saved.any { it.kind == MessageKind.SUMMARY }
        val openingQuestion = saved.firstOrNull { it.kind == MessageKind.QUESTION }?.takeUnless { summarized }
        if (summarized) session.summaryOpening()
        openingQuestion?.let { session.questionOpening(Prompts.question(it.text)) }
        saved.forEach { message ->
            if (message == openingQuestion) return@forEach
            historyTurn(message)?.let(session::addTurn)
        }
    }
}
