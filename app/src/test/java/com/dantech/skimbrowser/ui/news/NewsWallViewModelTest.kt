package com.dantech.skimbrowser.ui.news

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.news.NewsArticle
import com.dantech.skimbrowser.domain.news.NewsBoard
import com.dantech.skimbrowser.domain.news.NewsFeedClient
import com.dantech.skimbrowser.domain.news.NewsParser
import com.dantech.skimbrowser.domain.news.NewsPreferences
import com.dantech.skimbrowser.domain.news.NewsRepository
import com.dantech.skimbrowser.domain.news.NewsSnapshot
import com.dantech.skimbrowser.domain.news.NewsTopic
import com.dantech.skimbrowser.domain.news.RefreshNews
import com.dantech.skimbrowser.domain.news.ThumbnailPolicy
import com.dantech.skimbrowser.testing.MainDispatcherRule
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NewsWallViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    @Test
    fun newsThatIsOffStaysHiddenAndDoesNotFetch() = runTest(dispatcher) {
        val client = RecordingClient()
        val vm = viewModel(MemoryNews(NewsPreferences(enabled = false)), client, images = true)

        val state = vm.state.first { it.ready }

        assertFalse(state.visible)
        assertTrue(client.urls.isEmpty())
    }

    @Test
    fun aStaleWallRefreshesAndDropsThumbnailsOnAMeteredNetwork() = runTest(dispatcher) {
        val cached = NewsSnapshot(
            listOf(NewsArticle("https://example.com/old", "Old", "bbc", "BBC", 1, "https://images.example/old.jpg")),
            fetchedAtMillis = 0,
        )
        val store = MemoryNews(snapshot = cached)
        val client = RecordingClient()
        val vm = viewModel(store, client, images = false, now = com.dantech.skimbrowser.domain.news.NewsBoard.TTL_MILLIS + 1)

        val state = vm.state.first { it.articles.singleOrNull()?.title == "New" }

        assertTrue(state.visible)
        assertEquals("New", state.articles.single().title)
        assertEquals(null, state.articles.single().thumbnailUrl)
        assertTrue(client.urls.isNotEmpty())
    }

    @Test
    fun choosingATopicAndHidingASourceAreSaved() = runTest(dispatcher) {
        val store = MemoryNews()
        val vm = viewModel(store, RecordingClient(), images = true)
        vm.state.first { it.ready }

        vm.selectTopic(NewsTopic.SPORTS)
        vm.hideSource("bbc")
        vm.addFeed("http://example.com/rss")

        val prefs = store.preferences().first { it.topic == NewsTopic.SPORTS && "bbc" in it.hiddenSourceIds }
        assertEquals(NewsTopic.SPORTS, prefs.topic)
        assertTrue("bbc" in prefs.hiddenSourceIds)
        assertTrue(vm.state.value.feedError)
        assertTrue(store.currentPreferences().customFeedUrls.isEmpty())
    }

    @Test
    fun turningNewsOffHidesTheWallAndStoresTheChoice() = runTest(dispatcher) {
        val store = MemoryNews()
        val vm = viewModel(store, RecordingClient(), images = true)
        vm.state.first { it.ready && it.visible }

        vm.setEnabled(false)

        val state = vm.state.first { it.ready && !it.enabled }
        assertFalse(state.visible)
        assertFalse(store.currentPreferences().enabled)
    }

    @Test
    fun hidingASourceDropsItsStoriesAndFetchesAgain() = runTest(dispatcher) {
        val now = NewsBoard.TTL_MILLIS
        val cached = NewsSnapshot(
            listOf(NewsArticle("https://example.com/bbc", "Old", "bbc", "BBC", 1, null)),
            fetchedAtMillis = now,
        )
        val store = MemoryNews(snapshot = cached)
        val client = RecordingClient()
        val vm = viewModel(store, client, images = true, now = now)
        vm.state.first { it.articles.singleOrNull()?.sourceId == "bbc" }
        val before = client.urls.size

        vm.hideSource("bbc")

        val state = vm.state.first { it.articles.none { card -> card.sourceId == "bbc" } }
        assertTrue(state.articles.none { it.sourceId == "bbc" })
        assertTrue(client.urls.size > before)
    }

    @Test
    fun aFullDiskKeepsTheStoriesAlreadySaved() = runTest(dispatcher) {
        val cached = NewsSnapshot(
            listOf(NewsArticle("https://example.com/kept", "Kept", "bbc", "BBC", 1, null)),
            fetchedAtMillis = 0,
        )
        val store = MemoryNews(snapshot = cached, failSave = true)
        val vm = viewModel(store, RecordingClient(), images = true, now = NewsBoard.TTL_MILLIS + 1)

        val state = vm.state.first { it.ready && it.articles.isNotEmpty() }

        assertEquals("Kept", state.articles.single().title)
    }

    @Test
    fun openingHomeAgainWaitsOutTheHalfHour() = runTest(dispatcher) {
        var time = 1L
        val client = RecordingClient()
        val vm = viewModel(MemoryNews(), client, images = true, clock = { time })
        vm.state.first { it.articles.singleOrNull()?.title == "New" }
        val afterFirst = client.urls.size

        vm.onShown()
        assertEquals(afterFirst, client.urls.size)

        time += NewsBoard.TTL_MILLIS + 1
        vm.onShown()
        assertTrue(client.urls.size > afterFirst)
    }

    @Test
    fun switchingTopicDuringAFetchStillLoadsThatTopic() = runTest(dispatcher) {
        val client = HoldingClient()
        val store = MemoryNews()
        val vm = viewModel(store, client, images = true, now = 1)
        assertEquals(4, client.urls.size)

        vm.selectTopic(NewsTopic.SPORTS)
        client.release()

        assertTrue(client.urls.any { it == "https://feeds.bbci.co.uk/sport/rss.xml" })
        assertEquals(NewsTopic.SPORTS, store.currentPreferences().topic)
    }

    @Test
    fun turningNewsOffLeavesAFeedThatHasNotStarted() = runTest(dispatcher) {
        val client = HoldingClient()
        val vm = viewModel(MemoryNews(), client, images = true, now = 1)
        assertEquals(4, client.urls.size)

        vm.setEnabled(false)
        client.release()

        assertEquals(4, client.urls.size)
        assertFalse(vm.state.value.enabled)
    }

    @Test
    fun turningNewsBackOnWhileACancelledFetchStillHoldsTheGateLoadsAgain() = runTest(dispatcher) {
        val client = BlockingHoldClient()
        val store = MemoryNews()
        val vm = viewModel(store, client, images = true, now = 1)
        assertEquals(4, client.urls.size)

        vm.setEnabled(false)
        vm.setEnabled(true)
        client.release()

        assertTrue(client.urls.groupingBy { it }.eachCount().any { it.value >= 2 })
        assertTrue(store.currentPreferences().enabled)
    }

    private fun viewModel(
        store: MemoryNews,
        client: RecordingClient,
        images: Boolean,
        now: Long = 0,
        clock: () -> Long = { now },
    ) = viewModel(store, client as NewsFeedClient, images, now, clock)

    private fun viewModel(
        store: MemoryNews,
        client: NewsFeedClient,
        images: Boolean,
        now: Long = 0,
        clock: () -> Long = { now },
    ) = NewsWallViewModel(
        repository = store,
        refreshNews = RefreshNews(
            store,
            client,
            object : NewsParser {
                override fun parse(xml: String, sourceId: String, sourceName: String) = listOf(
                    NewsArticle("https://example.com/new", "New", sourceId, sourceName, 10, "https://images.example/new.jpg"),
                )
            },
            clock = clock,
            io = dispatcher,
        ),
        thumbnails = ThumbnailPolicy { images },
        clock = clock,
        zone = { ZoneOffset.UTC },
        language = "en",
    )

    private class RecordingClient : NewsFeedClient {
        val urls = mutableListOf<String>()
        override suspend fun fetch(url: String): String {
            urls += url
            return "<rss/>"
        }
    }

    private class HoldingClient : NewsFeedClient {
        val urls = mutableListOf<String>()
        private val hold = CompletableDeferred<Unit>()

        override suspend fun fetch(url: String): String {
            urls += url
            hold.await()
            return "<rss/>"
        }

        fun release() {
            hold.complete(Unit)
        }
    }

    /** A fetch that keeps running after cancellation, the way a blocking HTTP read does. */
    private class BlockingHoldClient : NewsFeedClient {
        val urls = mutableListOf<String>()
        private val hold = CompletableDeferred<Unit>()

        override suspend fun fetch(url: String): String {
            urls += url
            withContext(NonCancellable) { hold.await() }
            return "<rss/>"
        }

        fun release() {
            hold.complete(Unit)
        }
    }

    private class MemoryNews(
        prefs: NewsPreferences = NewsPreferences(),
        snapshot: NewsSnapshot? = null,
        private val failSave: Boolean = false,
    ) : NewsRepository {
        private val prefs = MutableStateFlow(prefs)
        private val stored = mutableMapOf(NewsTopic.TOP to MutableStateFlow(snapshot))

        override fun preferences(): Flow<NewsPreferences> = prefs
        override suspend fun currentPreferences(): NewsPreferences = prefs.value
        override suspend fun setEnabled(enabled: Boolean) { prefs.update { it.copy(enabled = enabled) } }
        override suspend fun setTopic(topic: NewsTopic) { prefs.update { it.copy(topic = topic) } }
        override suspend fun setSourceShown(sourceId: String, shown: Boolean) {
            prefs.update { current ->
                val hidden = current.hiddenSourceIds.toMutableSet()
                if (shown) hidden.remove(sourceId) else hidden.add(sourceId)
                current.copy(hiddenSourceIds = hidden)
            }
        }
        override suspend fun addCustomFeed(url: String): Boolean = false
        override fun snapshot(language: String, topic: NewsTopic): Flow<NewsSnapshot?> = bucket(topic)
        override suspend fun currentSnapshot(language: String, topic: NewsTopic): NewsSnapshot? = bucket(topic).value
        override suspend fun saveSnapshot(language: String, topic: NewsTopic, snapshot: NewsSnapshot) {
            if (failSave) throw java.io.IOException("full")
            bucket(topic).value = snapshot
        }

        private fun bucket(topic: NewsTopic) = stored.getOrPut(topic) { MutableStateFlow(null) }
    }
}
