package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoCommands
import com.dantech.skimbrowser.domain.video.VideoExtractFailure
import com.dantech.skimbrowser.domain.video.VideoExtractor
import com.dantech.skimbrowser.domain.video.VideoQualities
import com.dantech.skimbrowser.domain.video.VideoSaveResult
import com.dantech.skimbrowser.domain.video.VideoVariant
import com.dantech.skimbrowser.domain.video.collectVariants
import com.dantech.skimbrowser.domain.video.listedQualities
import com.dantech.skimbrowser.domain.video.videoCaption
import kotlinx.coroutines.CancellationException
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.VideoDownloadKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class VideoChoice(val index: Int, val title: String, val quality: String, val sizeBytes: Long?)

@Immutable
data class VideoDownloadUiState(
    val loading: Boolean = true,
    val choices: List<VideoChoice> = emptyList(),
    val notice: Int? = null,
    val started: Boolean = false,
)

@HiltViewModel(assistedFactory = VideoDownloadViewModel.Factory::class)
class VideoDownloadViewModel @AssistedInject constructor(
    @Assisted private val tabId: String,
    private val tabs: TabsController,
    private val extractors: Set<@JvmSuppressWildcards VideoExtractor>,
    private val videos: VideoCommands,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tabId: String): VideoDownloadViewModel
    }

    private val _state = MutableStateFlow(VideoDownloadUiState())
    val state: StateFlow<VideoDownloadUiState> = _state.asStateFlow()
    private var variants: List<VideoVariant> = emptyList()

    init {
        viewModelScope.launch {
            val tab = tabs.state.value.tab(tabId)
            val runtime = tabs.runtime(tabId).value
            val hints = PageVideoHints(
                pageUrl = tab?.url.orEmpty(),
                pageTitle = tab?.title,
                mediaUrls = runtime.mediaUrls,
                userAgent = null,
                cookieHeader = null,
            )
            val collected = collectVariants(extractors, hints)
            val ready = mutableListOf<VideoVariant>()
            var protected = collected.failure == com.dantech.skimbrowser.domain.video.VideoExtractFailure.DRM
            for (variant in collected.variants) {
                try {
                    when (val offer = videos.qualities(tabId, variant)) {
                        is VideoQualities.Ready -> ready += offer.variants
                        VideoQualities.Protected -> protected = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ready += variant
                }
            }
            variants = listedQualities(ready)
            _state.value = VideoDownloadUiState(
                loading = false,
                choices = variants.mapIndexed { index, variant ->
                    VideoChoice(index, variant.title, variant.qualityLabel, variant.sizeBytes)
                },
                notice = when {
                    variants.isNotEmpty() -> null
                    protected -> R.string.video_protected
                    else -> collected.failure?.let(::videoNotice)
                },
            )
        }
    }

    fun save(index: Int) {
        val variant = variants.getOrNull(index) ?: return
        viewModelScope.launch {
            when (videos.save(tabId, variant)) {
                VideoSaveResult.Protected -> _state.value = _state.value.copy(
                    notice = R.string.video_protected,
                    choices = emptyList(),
                )
                VideoSaveResult.Started -> _state.value = _state.value.copy(started = true)
            }
        }
    }
}

private fun videoNotice(failure: VideoExtractFailure): Int = when (failure) {
    VideoExtractFailure.DRM -> R.string.video_protected
    VideoExtractFailure.AGE_RESTRICTED -> R.string.video_age_restricted
    VideoExtractFailure.MEMBERS_ONLY -> R.string.video_members_only
    VideoExtractFailure.PREMIUM -> R.string.video_premium
    VideoExtractFailure.PAID -> R.string.video_paid
    VideoExtractFailure.PRIVATE -> R.string.video_private
    VideoExtractFailure.UNAVAILABLE -> R.string.video_unavailable
}

@Composable
fun VideoDownloadRoute(key: VideoDownloadKey, navigator: SkimNavigator) {
    val model = hiltViewModel<VideoDownloadViewModel, VideoDownloadViewModel.Factory> { factory ->
        factory.create(key.tabId)
    }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.started) {
        if (state.started) navigator.back()
    }
    VideoDownloadSheet(state, model::save)
}

@Composable
fun VideoDownloadSheet(state: VideoDownloadUiState, onSave: (Int) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.video_download_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            state.notice != null -> Text(
                stringResource(state.notice),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.loading -> Text(
                stringResource(R.string.video_download_loading),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.choices.isEmpty() -> Text(
                stringResource(R.string.video_none),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            else -> state.choices.forEach { choice ->
                val size = choice.sizeBytes?.let { sizeLabel(it) }
                val caption = videoCaption(choice.title).ifBlank { null }
                SkimListItem(
                    headline = choice.quality,
                    supporting = listOfNotNull(caption, size).joinToString(" · "),
                    index = choice.index,
                    count = state.choices.size,
                    onClick = { onSave(choice.index) },
                )
            }
        }
    }
}

@Composable
private fun sizeLabel(bytes: Long): String = when {
    bytes < 1024 -> stringResource(R.string.downloads_size_bytes, bytes)
    bytes < 1024 * 1024 -> stringResource(R.string.downloads_size_kilobytes, bytes / 1024)
    else -> stringResource(R.string.downloads_size_megabytes, bytes / (1024 * 1024))
}
