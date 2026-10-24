package com.dantech.skimbrowser.ui.reader

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.assisted.Assisted
import com.dantech.skimbrowser.di.AppScope
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Reader view is doing. [Loading] lasts until the page has been read, including a slow load. */
@Immutable
sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Ready(
        val article: ReaderArticle,
        val scalePercent: Int,
        val font: ReaderFont,
    ) : ReaderUiState

    /** The page was read and is not an article, or it never became readable. The route leaves. */
    data object Unavailable : ReaderUiState
}

/**
 * Reader view of one tab. The page is read once, after it has loaded. A page that is not an
 * article becomes [ReaderUiState.Unavailable] only then, so a slow load stays on [ReaderUiState.Loading].
 *
 * Text size and font are the saved reader settings. Changing them writes through [writes], which
 * outlives this screen, and the new value comes back through [settings].
 */
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val tabId: String,
    private val tabs: TabsController,
    private val settings: SettingsRepository,
    @AppScope private val writes: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tabId: String): ReaderViewModel
    }

    private val load = MutableStateFlow<Load>(Load.Pending)

    val state: StateFlow<ReaderUiState> = combine(load, settings.settings) { loaded, saved ->
        when (loaded) {
            Load.Pending -> ReaderUiState.Loading
            Load.Failed -> ReaderUiState.Unavailable
            is Load.Article -> ReaderUiState.Ready(
                article = loaded.article,
                scalePercent = saved.readerTextScalePercent,
                font = saved.readerFont,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderUiState.Loading)

    init {
        viewModelScope.launch {
            val page = tabs.extractPage(tabId)
            val article = page?.let(::articleOf)
            load.value = if (article == null) Load.Failed else Load.Article(article)
        }
    }

    /** Saves [percent] as the reader text size. The slider follows the stored value. */
    fun setScale(percent: Int) {
        writes.launch { settings.setReaderTextScale(percent) }
    }

    /** Saves [font] as the reader typeface. */
    fun setFont(font: ReaderFont) {
        writes.launch { settings.setReaderFont(font) }
    }

    /** Goes back in this tab's page history. The route leaves reader first. */
    fun goBack() {
        tabs.goBack(tabId)
    }

    /** Goes forward in this tab's page history. The route leaves reader first. */
    fun goForward() {
        tabs.goForward(tabId)
    }

    private sealed interface Load {
        data object Pending : Load
        data object Failed : Load
        data class Article(val article: ReaderArticle) : Load
    }
}
