package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.usecase.AddSavedMessage
import com.dantech.skimbrowser.domain.usecase.CreateSavedChat
import com.dantech.skimbrowser.domain.usecase.ReadSavedMessages
import com.dantech.skimbrowser.domain.usecase.UpdateSavedChat
import com.dantech.skimbrowser.domain.usecase.UpdateSavedMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** A session store whose transcript writes go through the chat use cases. */
fun chatSessionsForTest(
    chats: ChatRepository,
    tabs: TabManager,
    settings: SettingsRepository,
    appScope: CoroutineScope,
    mainDispatcher: CoroutineDispatcher,
) = ChatSessionStore(
    chats = chats,
    createChat = CreateSavedChat(chats),
    messages = ReadSavedMessages(chats),
    addMessage = AddSavedMessage(chats),
    updateChat = UpdateSavedChat(chats),
    updateMessage = UpdateSavedMessage(chats),
    tabs = tabs,
    settings = settings,
    appScope = appScope,
    mainDispatcher = mainDispatcher,
)
