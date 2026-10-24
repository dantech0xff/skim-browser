package com.dantech.skimbrowser.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Records that [host] may be read, then continues. The write finishes before [onAllowed]. */
@HiltViewModel
class AiPermissionViewModel @Inject constructor(
    private val sites: SitePermissionRepository,
) : ViewModel() {
    fun allow(host: String, onAllowed: () -> Unit) {
        viewModelScope.launch {
            sites.setSkimAi(host, SkimAiState.ALLOWED)
            onAllowed()
        }
    }
}
