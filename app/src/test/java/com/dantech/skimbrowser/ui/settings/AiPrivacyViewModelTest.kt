package com.dantech.skimbrowser.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** AI & privacy follows the two switches and the number of saved chats. */
@RunWith(AndroidJUnit4::class)
class AiPrivacyViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databases = mutableListOf<SkimDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        scope.cancel()
    }

    @Test
    fun switchesWriteSettingsAndTheCountFollowsSavedChats() = runTest {
        val db = inMemorySkimDatabase().also { databases += it }
        val settings = SettingsRepository(
            SettingsRepository.createDataStore(scope) { File(folder.root, "settings.preferences_pb") },
        )
        val chats = ChatRepository(db.skimChatDao(), db)
        chats.createChat(
            SkimChatEntity(
                kind = ChatKind.PAGE,
                url = "https://daylightjournal.com/",
                host = "daylightjournal.com",
                title = "Heat pumps",
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        val model = AiPrivacyViewModel(settings, chats)
        model.state.first { it.loaded && it.conversationCount == 1 }
        assertEquals(true, model.state.value.askBeforeSending)

        model.setAskBeforeSending(false)
        model.setKeepChatHistory(false)
        model.state.first { !it.askBeforeSending && !it.keepChatHistory }
        val stored = settings.settings.first()
        assertFalse(stored.askBeforeSending)
        assertFalse(stored.keepChatHistory)

        chats.deleteAll()
        model.state.first { it.conversationCount == 0 }
    }
}
