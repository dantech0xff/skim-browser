package com.dantech.skimbrowser.ui.skimhistory

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ai.AiDataEraser
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.MessageStatus
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.SkimMessageEntity
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.DateBucket
import com.dantech.skimbrowser.ui.text.UiText
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Skim history groups saved chats by day, and Undo cannot bring a chat back after the history is cleared. */
@RunWith(AndroidJUnit4::class)
class SkimHistoryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val databases = mutableListOf<SkimDatabase>()
    private val stores = mutableListOf<ViewModelStore>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        databases.forEach { it.close() }
    }

    @Test
    fun chatsGroupByDayAndSearchKeepsMatches() = runTest {
        val chats = chats()
        val page = chat(chats, ChatKind.PAGE, "Why heat pumps are winning in cold climates", NOW, host = "daylightjournal.com")
        chats.addMessage(question(page, "Why?"))
        val tabs = chat(chats, ChatKind.TABS, "Summarize my 7 open tabs", NOW - 1_000, tabCount = 7)
        chats.addMessage(overview(tabs))
        chat(chats, ChatKind.PAGE, "Osaka night buses: a complete guide", NOW - DAY, host = "tabi-note.jp")
        val model = model(chats)

        val grouped = model.state.first { it.sections.size == 2 }
        assertEquals(DateBucket.Today, grouped.sections[0].bucket)
        assertEquals(
            listOf("Why heat pumps are winning in cold climates", "Summarize my 7 open tabs"),
            grouped.sections[0].rows.map { it.title },
        )
        assertEquals(
            UiText.Plural(R.plurals.skim_history_questions_about, 1, listOf(1, "daylightjournal.com")),
            grouped.sections[0].rows[0].subtitle,
        )
        assertEquals(
            UiText.Plural(R.plurals.skim_history_answers_across_tabs, 1, listOf(1, 7)),
            grouped.sections[0].rows[1].subtitle,
        )
        assertEquals("D", grouped.sections[0].rows[0].letter)
        assertEquals(DateBucket.Yesterday, grouped.sections[1].bucket)
        assertEquals(
            UiText.Res(R.string.skim_history_summary_of, listOf("tabi-note.jp")),
            grouped.sections[1].rows.single().subtitle,
        )

        model.setQuery("osaka")
        val found = model.state.first { it.searching && it.sections.size == 1 }
        assertEquals("Osaka night buses: a complete guide", found.sections.single().rows.single().title)
    }

    @Test
    fun undoRestoresAfterTheScreenIsGone() = runTest {
        val chats = chats()
        val id = chat(chats, ChatKind.PAGE, "Why heat pumps are winning", NOW, host = "daylightjournal.com")
        val undo = UndoRegistry(backgroundScope)
        val snackbar = SnackbarController(undo)
        val model = model(chats, undo, snackbar)
        model.state.first { it.sections.isNotEmpty() }

        model.remove(id)
        chats.observeAll(null).first { it.isEmpty() }
        stores.forEach { it.clear() }

        val token = (snackbar.current.value?.action as SnackbarAction.Undo).token
        undo.undo(token)
        val restored = chats.observeAll(null).first { it.isNotEmpty() }
        assertEquals(id, restored.single().chat.id)
    }

    @Test
    fun undoAfterClearSkimHistoryDoesNothing() = runTest {
        val db = inMemorySkimDatabase().also { databases += it }
        val chats = ChatRepository(db.skimChatDao(), db)
        val sites = SitePermissionRepository(db.sitePermissionDao(), db)
        sites.setSkimAi("daylightjournal.com", SkimAiState.ALLOWED)
        val id = chat(chats, ChatKind.PAGE, "Why heat pumps are winning", NOW, host = "daylightjournal.com")
        val undo = UndoRegistry(backgroundScope)
        val snackbar = SnackbarController(undo)
        val eraser = AiDataEraser(chats, sites, undo, snackbar)
        val model = model(chats, undo, snackbar)
        model.state.first { it.sections.isNotEmpty() }

        model.remove(id)
        chats.observeAll(null).first { it.isEmpty() }
        val token = (snackbar.current.value?.action as SnackbarAction.Undo).token
        eraser.clearSkimHistory()
        undo.undo(token)

        assertTrue(chats.observeAll(null).first().isEmpty())
        assertEquals(SkimAiState.ALLOWED, sites.skimAiStates(setOf("daylightjournal.com"))["daylightjournal.com"])
    }

    private fun chats(): ChatRepository {
        val db = inMemorySkimDatabase().also { databases += it }
        return ChatRepository(db.skimChatDao(), db)
    }

    private suspend fun chat(
        chats: ChatRepository,
        kind: ChatKind,
        title: String,
        updatedAt: Long,
        host: String? = null,
        tabCount: Int = 0,
    ): Long = chats.createChat(
        SkimChatEntity(
            kind = kind,
            url = host?.let { "https://$it/" },
            host = host,
            title = title,
            tabCount = tabCount,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        ),
    )

    private fun question(chatId: Long, text: String) = SkimMessageEntity(
        chatId = chatId,
        role = MessageRole.USER,
        kind = MessageKind.QUESTION,
        text = text,
        status = MessageStatus.COMPLETE,
        createdAt = 1,
    )

    private fun overview(chatId: Long) = SkimMessageEntity(
        chatId = chatId,
        role = MessageRole.ASSISTANT,
        kind = MessageKind.TABS_OVERVIEW,
        text = """{"intro":"Kept.","groups":[],"followUps":[]}""",
        status = MessageStatus.COMPLETE,
        createdAt = 2,
    )

    private fun model(
        chats: ChatRepository,
        undo: UndoRegistry = UndoRegistry(kotlinx.coroutines.test.TestScope().backgroundScope),
        snackbar: SnackbarController = SnackbarController(undo),
    ): SkimHistoryViewModel {
        val store = ViewModelStore().also { stores += it }
        return ViewModelProvider(
            store,
            viewModelFactory {
                initializer {
                    SkimHistoryViewModel(chats, snackbar, undo, clock = { NOW }, zone = ZoneId.of("UTC"))
                }
            },
        )[SkimHistoryViewModel::class.java]
    }

    private companion object {
        const val NOW = 1_761_228_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }
}
