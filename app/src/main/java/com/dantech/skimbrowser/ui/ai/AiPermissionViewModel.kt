package com.dantech.skimbrowser.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.usecase.SetSkimAi
import kotlinx.coroutines.launch

/** Records that [host] may be read, then continues. The write finishes before [onAllowed]. */
class AiPermissionViewModel(
    private val setSkimAi: SetSkimAi,
) : ViewModel() {
    fun allow(host: String, onAllowed: () -> Unit) {
        viewModelScope.launch {
            setSkimAi(host, SkimAiState.ALLOWED)
            onAllowed()
        }
    }
}
