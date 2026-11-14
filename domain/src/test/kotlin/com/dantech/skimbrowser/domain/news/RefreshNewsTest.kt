package com.dantech.skimbrowser.domain.news

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshNewsTest {
    @Test
    fun newsThatIsOffDoesNotFetch() = runTest {
        val store = MemoryNews(NewsPreferences(enabled = false))
        val client = RecordingClient()
        refresh(store, client).invoke("en", force = true)

        assertTrue(client.urls.isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun aFreshListIsShownWithoutAFetchUntilItIsForced() = runTest {
        val cached = NewsSnapshot(listOf(article("https://example.com/old", "Old", 1)), fetchedAtMillis = 1_000)
        val store = MemoryNews(snapshot = cached)
        val client = RecordingClient(body = RSS)
        val clock = mutableListOf(1_000 + NewsBoard.TTL_MILLIS - 1)
        refresh(store, client, clock = { clock.removeAt(0) }).invoke("en", force = false)

        assertTrue(client.urls.isEmpty())

        clock += 1_000 + NewsBoard.TTL_MILLIS - 1
        clock += 2_000
        refresh(store, client, clock = { clock.removeAt(0) }).invoke("en", force = true)
        assertEquals(listOf("https://example.com/new"), store.saved.last().articles.map { it.link })
    }

    @Test
    fun aStaleListIsReplacedAndDuplicatesCollapse() = runTest {
        val store = MemoryNews(snapshot = NewsSnapshot(emptyList(), fetchedAtMillis = 0))
        val client = RecordingClient(body = RSS)
        refresh(store, client, clock = { NewsBoard.TTL_MILLIS + 1 }).invoke("en", force = false)

        assertTrue(client.urls.isNotEmpty())
        assertEquals(listOf("https://example.com/new"), store.saved.last().articles.map { it.link })
        assertEquals("Google News", store.saved.last().articles.single().sourceName)
    }

    @Test
    fun aFailedRoundKeepsTheStoredList() = runTest {
        val cached = NewsSnapshot(listOf(article("https://example.com/kept", "Kept", 1)), fetchedAtMillis = 0)
        val store = MemoryNews(snapshot = cached)
        val client = RecordingClient(error = IllegalStateException("down"))
        refresh(store, client, clock = { NewsBoard.TTL_MILLIS + 1 }).invoke("en", force = true)

        assertEquals(cached, store.stored.value)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun turningNewsOffDuringAFetchDoesNotPublishTheResult() = runTest {
        val store = MemoryNews(snapshot = NewsSnapshot(listOf(article("https://example.com/kept", "Kept", 1)), 0))
        val client = RecordingClient(body = RSS, onFetch = { store.prefs = store.prefs.copy(enabled = false) })
        refresh(store, client, clock = { NewsBoard.TTL_MILLIS + 1 }).invoke("en", force = true)

        assertTrue(client.urls.isNotEmpty())
        assertTrue(store.saved.isEmpty())
    }

    private fun refresh(
        store: MemoryNews,
        client: RecordingClient,
        clock: () -> Long = { 0 },
    ) = RefreshNews(
        store,
        client,
        parser = object : NewsParser {
            override fun parse(xml: String, sourceId: String, sourceName: String) =
                listOf(article("https://example.com/new", "New", 10, sourceId, sourceName))
        },
        clock = clock,
        io = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    private fun article(
        link: String,
        title: String,
        published: Long,
        sourceId: String = "example",
        sourceName: String = "Example",
    ) = NewsArticle(link, title, sourceId, sourceName, published, null)

    private class RecordingClient(
        private val body: String = "",
        private val error: Throwable? = null,
        private val onFetch: () -> Unit = {},
    ) : NewsFeedClient {
        val urls = mutableListOf<String>()
        override suspend fun fetch(url: String): String {
            urls += url
            onFetch()
            error?.let { throw it }
            return body
        }
    }

    private class MemoryNews(
        prefs: NewsPreferences = NewsPreferences(),
        snapshot: NewsSnapshot? = null,
    ) : NewsRepository {
        var prefs = prefs
        var stored = MutableStateFlow(snapshot)
        val saved = mutableListOf<NewsSnapshot>()

        override fun preferences(): Flow<NewsPreferences> = MutableStateFlow(prefs)
        override suspend fun currentPreferences(): NewsPreferences = prefs
        override suspend fun setEnabled(enabled: Boolean) { prefs = prefs.copy(enabled = enabled) }
        override suspend fun setTopic(topic: NewsTopic) { prefs = prefs.copy(topic = topic) }
        override suspend fun setSourceShown(sourceId: String, shown: Boolean) = Unit
        override suspend fun addCustomFeed(url: String): Boolean = false
        override fun snapshot(language: String, topic: NewsTopic): Flow<NewsSnapshot?> = stored
        override suspend fun currentSnapshot(language: String, topic: NewsTopic): NewsSnapshot? = stored.value
        override suspend fun saveSnapshot(language: String, topic: NewsTopic, snapshot: NewsSnapshot) {
            saved += snapshot
            stored.value = snapshot
        }
    }

    private companion object {
        const val RSS = "<rss><channel><item><title>New</title></item></channel></rss>"
    }
}
