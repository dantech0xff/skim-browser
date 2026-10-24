package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.domain.undo.PendingUndo
import com.dantech.skimbrowser.domain.undo.UndoSubject
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.SnackbarUndo
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Delete all AI data and Clear Skim history share one chat deletion and cancel its Undo. */
@RunWith(AndroidJUnit4::class)
class AiDataEraserTest {

    private val databases = mutableListOf<SkimDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun deleteAllRemovesChatsAndSiteDecisionsAndCancelsUndo() = runTest {
        val db = database()
        val chats = ChatRepository(db.skimChatDao(), db)
        val sites = SitePermissionRepository(db.sitePermissionDao(), db)
        chats.createChat(chat("Heat pumps"))
        sites.setSkimAi("daylightjournal.com", SkimAiState.ALLOWED)
        val undo = UndoRegistry(backgroundScope)
        var finalized = false
        val token = undo.register(UndoKind.SKIM_CHAT, undo = { chats.createChat(chat("Brought back")) }, finalize = { finalized = true })
        val snackbar = SnackbarController(undo)
        snackbar.show(UiText.Raw("Removed"), SnackbarAction.Undo(token))
        val pending = object : PendingUndo {
            override suspend fun cancel(subject: UndoSubject) {
                assertEquals(UndoSubject.SKIM_CHAT, subject)
                assertEquals(1, chats.observeCount().first())
                SnackbarUndo(undo, snackbar).cancel(subject)
            }
        }

        chats.removals.test {
            assertEquals(AiDataCleared.CHATS_AND_SITE_CHOICES, AiDataEraser(chats, sites, pending).deleteAllAiData())
            assertNull(awaitItem())
        }

        assertEquals(0, chats.observeCount().first())
        assertNull(sites.get("daylightjournal.com"))
        assertTrue(finalized)
        assertNull(snackbar.current.value)
        undo.undo(token)
        assertEquals(0, chats.observeCount().first())
    }

    @Test
    fun clearSkimHistoryKeepsSiteDecisions() = runTest {
        val db = database()
        val chats = ChatRepository(db.skimChatDao(), db)
        val sites = SitePermissionRepository(db.sitePermissionDao(), db)
        chats.createChat(chat("Heat pumps"))
        sites.setSkimAi("daylightjournal.com", SkimAiState.ALLOWED)
        val undo = UndoRegistry(backgroundScope)

        assertEquals(
            AiDataCleared.CHATS,
            AiDataEraser(chats, sites, SnackbarUndo(undo, SnackbarController(undo))).clearSkimHistory(),
        )

        assertEquals(0, chats.observeCount().first())
        assertEquals(SkimAiState.ALLOWED, sites.get("daylightjournal.com")?.skimAi)
    }

    private fun database(): SkimDatabase = inMemorySkimDatabase().also { databases += it }

    private fun chat(title: String) = SavedChat(
        kind = ChatKind.PAGE,
        url = "https://daylightjournal.com/energy",
        host = "daylightjournal.com",
        title = title,
        createdAt = 1,
        updatedAt = 1,
    )
}
