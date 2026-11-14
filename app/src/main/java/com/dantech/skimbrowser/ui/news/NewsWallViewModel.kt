package com.dantech.skimbrowser.ui.news

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.dantech.skimbrowser.di.NewsLanguage
import com.dantech.skimbrowser.di.WallClock
import com.dantech.skimbrowser.di.WallZone
import com.dantech.skimbrowser.domain.news.NewsCatalog
import com.dantech.skimbrowser.domain.news.NewsPreferences
import com.dantech.skimbrowser.domain.news.NewsRepository
import com.dantech.skimbrowser.domain.news.NewsSnapshot
import com.dantech.skimbrowser.domain.news.NewsTopic
import com.dantech.skimbrowser.domain.news.RefreshNews
import com.dantech.skimbrowser.domain.news.ThumbnailPolicy
import com.dantech.skimbrowser.ui.text.UiText
import com.dantech.skimbrowser.ui.text.relativeTimeText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One story card. [timeText] is empty when the feed had no date. */
@Immutable
data class NewsCardUi(
    val link: String,
    val title: String,
    val sourceId: String,
    val sourceName: String,
    val timeText: UiText,
    val thumbnailUrl: String?,
)

/** A source the person can show or hide, including one they added. */
@Immutable
data class NewsSourceUi(
    val id: String,
    val name: String,
    val shown: Boolean,
    val custom: Boolean,
)

/**
 * The news wall. [visible] is false until the saved choice is read, and while news is off,
 * so the New tab page does not fetch or reserve space before it knows.
 */
@Immutable
data class NewsWallUiState(
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val topic: NewsTopic = NewsTopic.TOP,
    val refreshing: Boolean = false,
    val articles: ImmutableList<NewsCardUi> = persistentListOf(),
    val sources: ImmutableList<NewsSourceUi> = persistentListOf(),
    val feedError: Boolean = false,
) {
    val visible: Boolean get() = ready && enabled
}

@Immutable
data class NewsActions(
    val onTopic: (NewsTopic) -> Unit,
    val onOpen: (NewsCardUi) -> Unit,
    val onOpenInNewTab: (NewsCardUi) -> Unit,
    val onShare: (NewsCardUi) -> Unit,
    val onHideSource: (NewsCardUi) -> Unit,
    val onToggleSource: (id: String, shown: Boolean) -> Unit,
    val onAddFeed: (String) -> Unit,
    val onDismissFeedError: () -> Unit,
)

/**
 * Headlines for the New tab page. The saved list is shown at once. A fetch runs only while news
 * is on, and only when the list is missing, stale, or the person pulls to refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewsWallViewModel @Inject constructor(
    private val repository: NewsRepository,
    private val refreshNews: RefreshNews,
    private val thumbnails: ThumbnailPolicy,
    @WallClock private val clock: () -> Long,
    @WallZone private val zone: @JvmSuppressWildcards () -> ZoneId,
    @NewsLanguage private val language: String,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val feedError = MutableStateFlow(false)
    private val refreshGate = Mutex()
    private val refreshScope = CoroutineScope(
        viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]),
    )
    private var seen: NewsPreferences? = null

    val state: StateFlow<NewsWallUiState> = repository.preferences().flatMapLatest { prefs ->
        repository.snapshot(language, prefs.topic).map { snap -> prefs to snap }
    }.combine(refreshing) { loaded, busy -> loaded to busy }
        .combine(feedError) { (loaded, busy), error ->
            val (prefs, snap) = loaded
            toState(prefs, snap, busy, error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NewsWallUiState())

    init {
        viewModelScope.launch {
            repository.preferences()
                .distinctUntilChanged { old, new ->
                    old.enabled == new.enabled &&
                        old.topic == new.topic &&
                        old.hiddenSourceIds == new.hiddenSourceIds &&
                        old.customFeedUrls == new.customFeedUrls
                }
                .collect { prefs ->
                    val previous = seen
                    seen = prefs
                    if (!prefs.enabled) {
                        cancelRefreshes()
                        return@collect
                    }
                    val sourcesChanged = previous != null && (
                        previous.hiddenSourceIds != prefs.hiddenSourceIds ||
                            previous.customFeedUrls != prefs.customFeedUrls
                        )
                    val topicChanged = previous != null && previous.topic != prefs.topic
                    val turnedOn = previous != null && !previous.enabled
                    refresh(force = sourcesChanged, wait = sourcesChanged || topicChanged || turnedOn)
                }
        }
    }

    /** Re-checks the cache when Home is shown again. A fresh list does not open a connection. */
    fun onShown() = refresh(force = false)

    fun allowThumbnails(): Boolean = thumbnails.showThumbnails()

    fun refresh(force: Boolean) = refresh(force, wait = force)

    /**
     * [wait] queues behind a fetch already running. A topic change waits, so that topic is loaded
     * when the current round finishes. Turning news back on waits too: a cancelled round can still
     * hold the gate while a blocking read ends, and that round does not save. A second open of Home
     * does not wait, so two opens share one fetch.
     */
    private fun refresh(force: Boolean, wait: Boolean) {
        refreshScope.launch {
            if (!force && !wait && refreshGate.isLocked) return@launch
            refreshGate.withLock {
                try {
                    if (!repository.currentPreferences().enabled) return@withLock
                    refreshing.value = true
                    refreshNews(language, force)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Log.w(TAG, "News was not saved: ${e.javaClass.simpleName}")
                } finally {
                    refreshing.value = false
                }
            }
        }
    }

    private fun cancelRefreshes() {
        refreshScope.coroutineContext.cancelChildren()
    }

    fun selectTopic(topic: NewsTopic) = launchEdit { repository.setTopic(topic) }

    fun hideSource(sourceId: String) = setSourceShown(sourceId, shown = false)

    fun setSourceShown(sourceId: String, shown: Boolean) = launchEdit { repository.setSourceShown(sourceId, shown) }

    fun addFeed(url: String) = launchEdit { feedError.value = !repository.addCustomFeed(url) }

    fun dismissFeedError() {
        feedError.value = false
    }

    fun setEnabled(enabled: Boolean) = launchEdit { repository.setEnabled(enabled) }

    private fun launchEdit(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "News choice was not saved: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun toState(
        prefs: NewsPreferences,
        snap: NewsSnapshot?,
        busy: Boolean,
        error: Boolean,
    ): NewsWallUiState {
        val images = thumbnails.showThumbnails()
        val now = clock()
        val zoneId = zone()
        val cards = snap?.articles.orEmpty()
            .filter { it.sourceId !in prefs.hiddenSourceIds }
            .map { article ->
            NewsCardUi(
                link = article.link,
                title = article.title,
                sourceId = article.sourceId,
                sourceName = article.sourceName,
                timeText = article.publishedAtMillis?.let { relativeTimeText(it, now, zoneId) } ?: UiText.Raw(""),
                thumbnailUrl = if (images) article.thumbnailUrl else null,
            )
        }.toImmutableList()
        val sources = NewsCatalog.sources(
            NewsCatalog.languageGroup(language),
            prefs.hiddenSourceIds,
            prefs.customFeedUrls,
        ).map { source -> NewsSourceUi(source.id, source.name, source.shown, source.custom) }.toImmutableList()
        return NewsWallUiState(true, prefs.enabled, prefs.topic, busy, cards, sources, error)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TAG = "NewsWall"
    }
}

/** The Settings switch. It does not fetch. Turning news off is what stops the fetch. */
@HiltViewModel
class NewsPreferencesViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {
    val enabled: StateFlow<Boolean?> = repository.preferences()
        .map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setEnabled(enabled)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w("NewsWall", "News choice was not saved: ${e.javaClass.simpleName}")
            }
        }
    }
}
