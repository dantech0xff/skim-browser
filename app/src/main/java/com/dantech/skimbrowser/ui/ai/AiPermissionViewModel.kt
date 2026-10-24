package com.dantech.skimbrowser.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.usecase.SetSkimAi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Records that [host] may be read, then continues. The write finishes before [onAllowed]. */
@HiltViewModel
class AiPermissionViewModel @Inject constructor(
    private val setSkimAi: SetSkimAi,
) : ViewModel() {
    fun allow(host: String, onAllowed: () -> Unit) {
        viewModelScope.launch {
            setSkimAi(host, SkimAiState.ALLOWED)
            onAllowed()
        }
    }
}
