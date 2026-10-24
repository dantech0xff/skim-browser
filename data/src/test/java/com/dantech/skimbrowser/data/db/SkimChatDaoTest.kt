package com.dantech.skimbrowser.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dantech.skimbrowser.data.db.dao.SkimChatDao
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.MessageStatus
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.SkimMessageEntity
import com.dantech.skimbrowser.testing.awaitItemMatching
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.longQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkimChatDaoTest {

    private lateinit var db: SkimDatabase
    private lateinit var dao: SkimChatDao

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        dao = db.skimChatDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun listItemsCountQuestionsSummaryAndAnswersThatKeptText() = runTest {
        val page = dao.insertChat(chat(ChatKind.PAGE, title = "Why heat pumps are winning", host = "daylightjournal.com", updatedAt = 30))
        dao.insertMessage(message(page, MessageRole.ASSISTANT, MessageKind.SUMMARY, MessageStatus.COMPLETE))
        repeat(3) { dao.insertMessage(message(page, MessageRole.USER, MessageKind.QUESTION, MessageStatus.COMPLETE)) }
        dao.insertMessage(message(page, MessageRole.ASSISTANT, MessageKind.ANSWER, MessageStatus.COMPLETE))
        dao.insertMessage(message(page, MessageRole.ASSISTANT, MessageKind.ANSWER, MessageStatus.STOPPED))
        dao.insertMessage(message(page, MessageRole.ASSISTANT, MessageKind.ANSWER, MessageStatus.ERROR))
        dao.insertMessage(message(page, MessageRole.ASSISTANT, MessageKind.ANSWER, MessageStatus.REFUSED))
        val tabs = dao.insertChat(chat(ChatKind.TABS, title = "Summarize my 7 open tabs", host = null, updatedAt = 20))
        dao.insertMessage(message(tabs, MessageRole.ASSISTANT, MessageKind.TABS_OVERVIEW, MessageStatus.COMPLETE))
        val refused = dao.insertChat(chat(ChatKind.PAGE, title = "Refused", host = "tabi-note.jp", updatedAt = 10))
        dao.insertMessage(message(refused, MessageRole.ASSISTANT, MessageKind.SUMMARY, MessageStatus.REFUSED))

        val items = dao.observeListItems(pattern = null).first().associateBy { it.chat.id }

        assertEquals(listOf(page, tabs, refused), dao.observeListItems(null).first().map { it.chat.id })
        assertEquals(3, items.getValue(page).questionCount)
        assertTrue(items.getValue(page).hasSummary)
        assertEquals(2, items.getValue(page).answerCount)
        assertEquals(0, items.getValue(tabs).questionCount)
        assertFalse(items.getValue(tabs).hasSummary)
        assertEquals(1, items.getValue(tabs).answerCount)
        assertFalse(items.getValue(refused).hasSummary)
        assertEquals(0, items.getValue(refused).answerCount)
    }

    @Test
    fun listItemsFilterByTitleOrHost() = runTest {
        dao.insertChat(chat(ChatKind.PAGE, title = "Kotlin 2.2 release notes", host = "kotlinlang.org", updatedAt = 3))
        dao.insertChat(chat(ChatKind.PAGE, title = "Heat pump rebates 2026", host = "energysaver.example", updatedAt = 2))
        dao.insertChat(chat(ChatKind.TABS, title = "Summarize my 7 open tabs", host = null, updatedAt = 1))

        assertEquals(listOf("Kotlin 2.2 release notes"), titles("kotlinlang"))
        assertEquals(listOf("Heat pump rebates 2026"), titles("REBATES"))
        assertEquals(listOf("Summarize my 7 open tabs"), titles("7 open"))
        assertEquals(emptyList<String>(), titles("%"))
    }

    @Test
    fun deletingAChatDeletesItsMessages() = runTest {
        val kept = dao.insertChat(chat(ChatKind.PAGE, title = "Kept", host = "a.example", updatedAt = 1))
        val deleted = dao.insertChat(chat(ChatKind.PAGE, title = "Deleted", host = "b.example", updatedAt = 2))
        dao.insertMessage(message(kept, MessageRole.USER, MessageKind.QUESTION, MessageStatus.COMPLETE))
        dao.insertMessage(message(deleted, MessageRole.USER, MessageKind.QUESTION, MessageStatus.COMPLETE))
        dao.insertMessage(message(deleted, MessageRole.ASSISTANT, MessageKind.ANSWER, MessageStatus.COMPLETE))

        dao.deleteChat(deleted)

        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM skim_messages"))
        assertEquals(1, dao.messages(kept).size)
    }

    @Test
    fun recentChatsOfAKindAreNewestFirstUpToTheLimit() = runTest {
        dao.insertChat(chat(ChatKind.PAGE, title = "Old page", host = "a.example", updatedAt = 1))
        dao.insertChat(chat(ChatKind.TABS, title = "Tabs", host = null, updatedAt = 5))
        dao.insertChat(chat(ChatKind.PAGE, title = "New page", host = "b.example", updatedAt = 3))
        dao.insertChat(chat(ChatKind.PAGE, title = "Middle page", host = "c.example", updatedAt = 2))

        assertEquals(listOf("New page", "Middle page"), dao.observeRecent(ChatKind.PAGE, limit = 2).first().map { it.title })
    }

    @Test
    fun countFollowsSavedChats() = runTest {
        dao.observeCount().test {
            assertEquals(0, awaitItem())
            val id = dao.insertChat(chat(ChatKind.PAGE, title = "A", host = "a.example", updatedAt = 1))
            awaitItemMatching { it == 1 }
            dao.deleteChat(id)
            awaitItemMatching { it == 0 }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messagesComeBackInTheOrderTheyWereWritten() = runTest {
        val chat = dao.insertChat(chat(ChatKind.PAGE, title = "A", host = "a.example", updatedAt = 1))
        val first = dao.insertMessage(message(chat, MessageRole.ASSISTANT, MessageKind.SUMMARY, MessageStatus.COMPLETE, createdAt = 5))
        val second = dao.insertMessage(message(chat, MessageRole.USER, MessageKind.QUESTION, MessageStatus.COMPLETE, createdAt = 5))
        val third = dao.insertMessage(message(chat, MessageRole.ASSISTANT, MessageKind.ANSWER, MessageStatus.COMPLETE, createdAt = 6))

        assertEquals(listOf(first, second, third), dao.messages(chat).map { it.id })
    }

    private suspend fun titles(query: String) = dao.observeListItems(containsPattern(query)).first().map { it.chat.title }

    private fun chat(kind: ChatKind, title: String, host: String?, updatedAt: Long) = SkimChatEntity(
        kind = kind,
        url = host?.let { "https://$it/" },
        host = host,
        title = title,
        tabCount = if (kind == ChatKind.TABS) 7 else 0,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun message(chatId: Long, role: MessageRole, kind: MessageKind, status: MessageStatus, createdAt: Long = 1) =
        SkimMessageEntity(chatId = chatId, role = role, kind = kind, text = "$kind text", status = status, createdAt = createdAt)
}
