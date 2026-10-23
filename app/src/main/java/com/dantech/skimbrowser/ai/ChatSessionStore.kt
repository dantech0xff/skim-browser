package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.browser.PageEvent
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.page.PageContent
import com.dantech.skimbrowser.domain.chat.ChatKind
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.domain.chat.MessageKind
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.withoutFragment
import com.dantech.skimbrowser.domain.usecase.AddSavedMessage
import com.dantech.skimbrowser.domain.usecase.CreateSavedChat
import com.dantech.skimbrowser.domain.usecase.ReadSavedMessages
import com.dantech.skimbrowser.domain.usecase.UpdateSavedChat
import com.dantech.skimbrowser.domain.usecase.UpdateSavedMessage
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
    private val createChat: CreateSavedChat,
    private val messages: ReadSavedMessages,
    private val addMessage: AddSavedMessage,
    private val updateChat: UpdateSavedChat,
    private val updateMessage: UpdateSavedMessage,
    private val tabs: TabManager,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val clock: () -> Long = System::currentTimeMillis,
    private val languageTag: () -> String = { Locale.getDefault().toLanguageTag() },
) {
    private val byTab = ConcurrentHashMap<String, ChatSession>()
    private val byChat = ConcurrentHashMap<Long, ChatSession>()
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

    /** The across-tabs chat [chatId], when this process already has it open. */
    fun sessionForChat(chatId: Long): ChatSession? = byChat[chatId]

    /** Starts the chat for [tab] about [content]. [approvedHost] is the host the person allowed. */
    suspend fun openForTab(tab: Tab, content: PageContent, approvedHost: String): ChatSession {
        val url = withoutFragment(content.url)
        val keep = settings.settings.first().keepChatHistory && !tab.incognito
        val id = if (keep) {
            val now = clock()
            createChat(
                SavedChat(
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
        val chat = chats.load(chatId) ?: return null
        val saved = messages(chatId).map { it.toMessage() }
        val session = session(chat.id, tab, content, hostOf(content.url).orEmpty(), withoutFragment(chat.url ?: content.url))
        restoreTurns(session, saved)
        session.replaceMessages(saved)
        withContext(mainDispatcher) { tabs.bindChat(tab.id, chat.id) }
        byTab[tab.id] = session
        return session
    }

    /**
     * Starts an across-tabs chat. It is saved while Keep chat history is on, and it is never tied
     * to a tab. [title] is the question the person asked.
     */
    suspend fun openTabs(title: String, tabCount: Int, sources: List<AiSource>): ChatSession {
        val keep = settings.settings.first().keepChatHistory
        val id = if (keep) {
            val now = clock()
            createChat(
                SavedChat(
                    kind = ChatKind.TABS,
                    url = null,
                    host = null,
                    title = title,
                    tabCount = tabCount,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            nextMemoryId.getAndDecrement()
        }
        return tabsSession(id, title, tabCount, sources).also { byChat[id] = it }
    }

    /**
     * Opens the saved across-tabs chat [chatId] and fills it with the messages already stored.
     * Nothing is sent. Null when the chat is gone or it is about one page.
     */
    suspend fun openSavedTabs(chatId: Long): ChatSession? {
        byChat[chatId]?.let { return it }
        val chat = chats.load(chatId) ?: return null
        if (chat.kind != ChatKind.TABS) return null
        val saved = messages(chatId).map { it.toMessage() }
        val session = tabsSession(chat.id, chat.title, chat.tabCount, emptyList())
        restoreTabs(session, saved)
        session.replaceMessages(saved)
        byChat[chat.id] = session
        return session
    }

    /** Appends [message]. A memory-only chat is not written, and the tab's chat id is left alone. */
    suspend fun record(session: ChatSession, message: ChatMessage) {
        val stored = if (session.chatId <= 0) {
            message.copy(id = message.id.takeIf { it != 0L } ?: nextMemoryId.getAndDecrement())
        } else {
            val now = clock()
            val id = addMessage(message.toSaved(session.chatId, now))
            val chat = chats.load(session.chatId)
            if (chat != null) {
                val snippet = when (message.kind) {
                    MessageKind.SUMMARY -> summarySnippet(message.text) ?: chat.snippet
                    MessageKind.TABS_OVERVIEW -> overviewSnippet(message.text) ?: chat.snippet
                    else -> chat.snippet
                }
                val count = if (session.tabCount > 0) session.tabCount else chat.tabCount
                updateChat(chat.copy(snippet = snippet, tabCount = count, updatedAt = now))
            }
            message.copy(id = id)
        }
        session.replaceMessages(session.messages.value + stored)
    }

    suspend fun setFeedback(session: ChatSession, messageId: Long, feedback: MessageFeedback) {
        val updated = session.messages.value.map { if (it.id == messageId) it.copy(feedback = feedback) else it }
        session.replaceMessages(updated)
        if (session.chatId <= 0) return
        val saved = messages(session.chatId).firstOrNull { it.id == messageId } ?: return
        updateMessage(saved.copy(feedback = feedback))
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
        val gone = if (id == null) {
            (byTab.values + byChat.values).distinct()
        } else {
            byTab.values.filter { it.chatId == id } + listOfNotNull(byChat[id])
        }
        gone.distinct().forEach { session ->
            session.cancelStream()
            session.endStream()
            detach(session)
        }
    }

    private suspend fun detach(session: ChatSession) {
        if (session.chatId > 0 && session.tabId.isNotEmpty()) {
            withContext(mainDispatcher) { tabs.bindChat(session.tabId, null) }
        }
        if (session.tabId.isNotEmpty()) byTab.remove(session.tabId, session)
        byChat.remove(session.chatId, session)
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

    /** The first question is the opening text, added again when a follow-up is asked. The rest is replayed. */
    private fun restoreTabs(session: ChatSession, saved: List<ChatMessage>) {
        val openingQuestion = saved.firstOrNull { it.kind == MessageKind.QUESTION }
        saved.forEach { message ->
            if (message == openingQuestion) return@forEach
            historyTurn(message)?.let(session::addTurn)
        }
    }

    private fun tabsSession(id: Long, title: String, tabCount: Int, sources: List<AiSource>) = ChatSession(
        chatId = id,
        tabId = "",
        pageUrl = "",
        approvedHost = "",
        sources = sources,
        document = PageContent(
            url = "",
            loadNonce = 0,
            title = title,
            byline = null,
            siteName = null,
            lang = null,
            readerable = false,
            blocks = emptyList(),
        ),
        languageTag = languageTag(),
        tabCount = tabCount,
    )
}
