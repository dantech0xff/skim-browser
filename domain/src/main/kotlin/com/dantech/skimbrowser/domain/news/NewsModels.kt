package com.dantech.skimbrowser.domain.news

/** A topic chip on the news wall. Feeds that do not cover a topic are left out of it. */
enum class NewsTopic {
    TOP,
    TECH,
    BUSINESS,
    WORLD,
    SPORTS,
    ENTERTAINMENT,
}

/**
 * One story from a feed. [publishedAtMillis] is null when the feed gave no date we could read.
 * [thumbnailUrl] is null when the feed had no image we could take without another request.
 */
data class NewsArticle(
    val link: String,
    val title: String,
    val sourceId: String,
    val sourceName: String,
    val publishedAtMillis: Long?,
    val thumbnailUrl: String?,
)

/** Stories already on disk for one language and topic, and when they were fetched. */
data class NewsSnapshot(
    val articles: List<NewsArticle>,
    val fetchedAtMillis: Long,
)

/** What the person has chosen. The wall does not fetch while [enabled] is false. */
data class NewsPreferences(
    val enabled: Boolean = true,
    val topic: NewsTopic = NewsTopic.TOP,
    val hiddenSourceIds: Set<String> = emptySet(),
    val customFeedUrls: List<String> = emptyList(),
)

/** A built-in or custom feed for one topic. */
data class NewsFeed(
    val sourceId: String,
    val sourceName: String,
    val topic: NewsTopic,
    val url: String,
)

/** A source row the person can show or hide. */
data class NewsSource(
    val id: String,
    val name: String,
    val shown: Boolean,
    val custom: Boolean,
)
