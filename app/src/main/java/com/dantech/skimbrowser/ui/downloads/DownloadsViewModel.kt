package com.dantech.skimbrowser.ui.downloads

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.domain.download.DownloadCommands
import com.dantech.skimbrowser.domain.download.DownloadItem
import com.dantech.skimbrowser.domain.download.DownloadLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The download list, newest first, as [DownloadLog] holds it. */
@Immutable
data class DownloadsUiState(val items: List<DownloadItem> = emptyList())

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val log: DownloadLog,
    private val commands: DownloadCommands,
) : ViewModel() {

    val state: StateFlow<DownloadsUiState> = log.observe()
        .map(::DownloadsUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadsUiState())

    init {
        refresh()
    }

    /** Asks DownloadManager again about downloads that are still running. */
    fun refresh() {
        commands.refresh()
    }

    fun retry(item: DownloadItem) {
        val retry = item.retry ?: return
        commands.retry(retry)
    }

    fun remove(id: Long, deleteFile: Boolean) {
        viewModelScope.launch { commands.remove(id, deleteFile) }
    }
}
