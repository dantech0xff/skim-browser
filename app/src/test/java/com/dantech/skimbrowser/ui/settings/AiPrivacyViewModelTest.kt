package com.dantech.skimbrowser.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.chat.ChatKind
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.fake.FakeChatRepository
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.usecase.SetAskBeforeSending
import com.dantech.skimbrowser.domain.usecase.SetKeepChatHistory
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** AI & privacy follows the two switches and the number of saved chats. */
@RunWith(AndroidJUnit4::class)
class AiPrivacyViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun switchesWriteSettingsAndTheCountFollowsSavedChats() = runTest {
        val settings = FakeSettingsRepository()
        val chats = FakeChatRepository()
        chats.insert(
            SavedChat(
                kind = ChatKind.PAGE,
                url = "https://daylightjournal.com/",
                host = "daylightjournal.com",
                title = "Heat pumps",
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        val model = AiPrivacyViewModel(settings, SetAskBeforeSending(settings), SetKeepChatHistory(settings), chats)
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
