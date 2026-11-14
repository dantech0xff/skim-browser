package com.dantech.skimbrowser.domain.news

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Loads the feeds for the current choice and stores the merged list. While news is off, or the
 * stored list is still fresh, it does not open a connection. A failed round leaves the stored list.
 */
class RefreshNews(
    private val repository: NewsRepository,
    private val client: NewsFeedClient,
    private val parser: NewsParser,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend operator fun invoke(language: String, force: Boolean) = withContext(io) {
        val prefs = repository.currentPreferences()
        if (!prefs.enabled) return@withContext
        val cached = repository.currentSnapshot(language, prefs.topic)
        val now = clock()
        if (!force && cached != null && NewsBoard.isFresh(cached.fetchedAtMillis, now)) return@withContext
        val group = NewsCatalog.languageGroup(language)
        val feeds = NewsCatalog.feeds(group, prefs.topic, prefs.hiddenSourceIds, prefs.customFeedUrls)
        if (feeds.isEmpty()) return@withContext
        val fetched = fetch(feeds)
        if (!repository.currentPreferences().enabled) return@withContext
        if (fetched.all { it == null }) return@withContext
        val merged = NewsBoard.merge(fetched.filterNotNull())
        repository.saveSnapshot(language, prefs.topic, NewsSnapshot(merged, clock()))
    }

    private suspend fun fetch(feeds: List<NewsFeed>): List<List<NewsArticle>?> = coroutineScope {
        val gate = Semaphore(PARALLEL)
        feeds.map { feed ->
            async {
                gate.withPermit {
                    if (!repository.currentPreferences().enabled) return@withPermit null
                    val xml = runCatching { client.fetch(feed.url) }.getOrNull() ?: return@withPermit null
                    runCatching { parser.parse(xml, feed.sourceId, feed.sourceName) }.getOrDefault(emptyList())
                }
            }
        }.awaitAll()
    }

    private companion object {
        const val PARALLEL = 4
    }
}
