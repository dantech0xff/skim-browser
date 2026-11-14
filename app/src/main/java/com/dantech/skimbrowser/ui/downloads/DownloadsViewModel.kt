package com.dantech.skimbrowser.ui.downloads

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.download.DownloadCommands
import com.dantech.skimbrowser.domain.download.DownloadHeader
import com.dantech.skimbrowser.domain.download.DownloadItem
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.download.VIDEO_ROW
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
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
    @AppScope private val appScope: CoroutineScope,
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
        val headers = retry.headers.filterNot { it.name == VIDEO_ROW } + DownloadHeader(VIDEO_ROW, item.id.toString())
        commands.retry(retry.copy(headers = headers))
    }

    fun remove(id: Long, deleteFile: Boolean) {
        // Outlives this screen: leaving Downloads must not leave a deleted file or a stuck row.
        appScope.launch { commands.remove(id, deleteFile) }
    }
}
