package com.dantech.skimbrowser.domain.news

import kotlinx.coroutines.flow.Flow

/** The news wall's choices and the last stories kept on disk. */
interface NewsRepository {
    fun preferences(): Flow<NewsPreferences>

    suspend fun currentPreferences(): NewsPreferences

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setTopic(topic: NewsTopic)

    suspend fun setSourceShown(sourceId: String, shown: Boolean)

    /** False when [url] is not an https feed address, or the custom list is already full. */
    suspend fun addCustomFeed(url: String): Boolean

    fun snapshot(language: String, topic: NewsTopic): Flow<NewsSnapshot?>

    suspend fun currentSnapshot(language: String, topic: NewsTopic): NewsSnapshot?

    suspend fun saveSnapshot(language: String, topic: NewsTopic, snapshot: NewsSnapshot)
}

/** Reads one feed body. Throws when the server cannot be read. */
interface NewsFeedClient {
    suspend fun fetch(url: String): String
}

/** Turns a feed body into stories. A broken document yields the stories read before it broke. */
interface NewsParser {
    fun parse(xml: String, sourceId: String, sourceName: String): List<NewsArticle>
}

/**
 * Whether story images may be downloaded. False on a metered network. Skim has no separate
 * data-saver switch, so the platform's metered flag is the one this follows.
 */
fun interface ThumbnailPolicy {
    fun showThumbnails(): Boolean
}
