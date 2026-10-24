package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** A session store whose transcript writes go through the chat repository. */
fun chatSessionsForTest(
    chats: ChatRepository,
    tabs: TabManager,
    settings: SettingsRepository,
    appScope: CoroutineScope,
    mainDispatcher: CoroutineDispatcher,
) = ChatSessionStore(
    chats = chats,
    tabs = tabs,
    settings = settings,
    appScope = appScope,
    mainDispatcher = mainDispatcher,
)
