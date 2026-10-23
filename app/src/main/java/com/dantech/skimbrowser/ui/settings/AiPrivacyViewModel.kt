package com.dantech.skimbrowser.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What AI & privacy (10) shows. */
@Immutable
data class AiPrivacyUiState(
    val loaded: Boolean = false,
    val askBeforeSending: Boolean = true,
    val keepChatHistory: Boolean = true,
    val conversationCount: Int = 0,
)

/**
 * AI & privacy (10). The switches write [settings]. [conversationCount] is the number of saved
 * Skim chats. Deleting all AI data is the dialog's job: it calls [com.dantech.skimbrowser.ai.AiDataEraser]
 * on the app scope so the delete finishes after the dialog closes.
 */
class AiPrivacyViewModel(
    private val settings: SettingsRepository,
    chats: ChatRepository,
) : ViewModel() {

    val state: StateFlow<AiPrivacyUiState> = combine(settings.settings, chats.observeCount()) { stored, count ->
        AiPrivacyUiState(
            loaded = true,
            askBeforeSending = stored.askBeforeSending,
            keepChatHistory = stored.keepChatHistory,
            conversationCount = count,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AiPrivacyUiState())

    fun setAskBeforeSending(enabled: Boolean) {
        viewModelScope.launch { settings.setAskBeforeSending(enabled) }
    }

    fun setKeepChatHistory(enabled: Boolean) {
        viewModelScope.launch { settings.setKeepChatHistory(enabled) }
    }
}
