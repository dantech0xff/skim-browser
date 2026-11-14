package com.dantech.skimbrowser.domain.news

/**
 * The public feeds Skim fetches on the device. Vietnamese for a `vi` language tag, English
 * otherwise. Each address here was read successfully as a feed. Reuters publishes no public feed
 * that answers, so the English set uses The Guardian for international news.
 *
 * The Verge and Ars Technica cover technology, so they are on Top and Tech only.
 */
object NewsCatalog {
    const val ENGLISH = "en"
    const val VIETNAMESE = "vi"
    const val MAX_CUSTOM_FEEDS = 8

    fun languageGroup(tag: String): String =
        if (tag.trim().lowercase().startsWith("vi")) VIETNAMESE else ENGLISH

    fun feeds(
        language: String,
        topic: NewsTopic,
        hiddenSourceIds: Set<String>,
        customFeedUrls: List<String>,
    ): List<NewsFeed> {
        val builtIn = table(language).filter { it.topic == topic && it.sourceId !in hiddenSourceIds }
        val custom = customFeedUrls
            .filter { NewsUrls.customId(it) !in hiddenSourceIds }
            .map { url -> NewsFeed(NewsUrls.customId(url), NewsUrls.hostLabel(url), topic, url) }
        return builtIn + custom
    }

    fun sources(language: String, hiddenSourceIds: Set<String>, customFeedUrls: List<String>): List<NewsSource> {
        val builtIn = table(language).distinctBy { it.sourceId }.map { feed ->
            NewsSource(feed.sourceId, feed.sourceName, shown = feed.sourceId !in hiddenSourceIds, custom = false)
        }
        val custom = customFeedUrls.map { url ->
            val id = NewsUrls.customId(url)
            NewsSource(id, NewsUrls.hostLabel(url), shown = id !in hiddenSourceIds, custom = true)
        }
        return builtIn + custom
    }

    private fun table(language: String): List<NewsFeed> =
        if (language == VIETNAMESE) VIETNAMESE_FEEDS else ENGLISH_FEEDS

    private fun source(id: String, name: String, urls: Map<NewsTopic, String>): List<NewsFeed> =
        urls.map { (topic, url) -> NewsFeed(id, name, topic, url) }

    private fun google(hl: String, gl: String, ceid: String): Map<NewsTopic, String> {
        val query = "hl=$hl&gl=$gl&ceid=$ceid"
        val sections = mapOf(
            NewsTopic.TECH to "TECHNOLOGY",
            NewsTopic.BUSINESS to "BUSINESS",
            NewsTopic.WORLD to "WORLD",
            NewsTopic.SPORTS to "SPORTS",
            NewsTopic.ENTERTAINMENT to "ENTERTAINMENT",
        )
        return buildMap {
            put(NewsTopic.TOP, "https://news.google.com/rss?$query")
            sections.forEach { (topic, section) ->
                put(topic, "https://news.google.com/rss/headlines/section/topic/$section?$query")
            }
        }
    }

    private val ENGLISH_FEEDS: List<NewsFeed> = source("google-news", "Google News", google("en-US", "US", "US:en")) +
        source(
            "bbc",
            "BBC",
            mapOf(
                NewsTopic.TOP to "https://feeds.bbci.co.uk/news/rss.xml",
                NewsTopic.TECH to "https://feeds.bbci.co.uk/news/technology/rss.xml",
                NewsTopic.BUSINESS to "https://feeds.bbci.co.uk/news/business/rss.xml",
                NewsTopic.WORLD to "https://feeds.bbci.co.uk/news/world/rss.xml",
                NewsTopic.SPORTS to "https://feeds.bbci.co.uk/sport/rss.xml",
                NewsTopic.ENTERTAINMENT to "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
            ),
        ) +
        source(
            "guardian",
            "The Guardian",
            mapOf(
                NewsTopic.TOP to "https://www.theguardian.com/world/rss",
                NewsTopic.TECH to "https://www.theguardian.com/uk/technology/rss",
                NewsTopic.BUSINESS to "https://www.theguardian.com/uk/business/rss",
                NewsTopic.WORLD to "https://www.theguardian.com/world/rss",
                NewsTopic.SPORTS to "https://www.theguardian.com/uk/sport/rss",
                NewsTopic.ENTERTAINMENT to "https://www.theguardian.com/uk/culture/rss",
            ),
        ) +
        source(
            "verge",
            "The Verge",
            mapOf(
                NewsTopic.TOP to "https://www.theverge.com/rss/index.xml",
                NewsTopic.TECH to "https://www.theverge.com/rss/index.xml",
            ),
        ) +
        source(
            "ars",
            "Ars Technica",
            mapOf(
                NewsTopic.TOP to "https://feeds.arstechnica.com/arstechnica/index",
                NewsTopic.TECH to "https://feeds.arstechnica.com/arstechnica/index",
            ),
        )

    private val VIETNAMESE_FEEDS: List<NewsFeed> = source(
        "vnexpress",
        "VnExpress",
        mapOf(
            NewsTopic.TOP to "https://vnexpress.net/rss/thoi-su.rss",
            NewsTopic.TECH to "https://vnexpress.net/rss/khoa-hoc-cong-nghe.rss",
            NewsTopic.BUSINESS to "https://vnexpress.net/rss/kinh-doanh.rss",
            NewsTopic.WORLD to "https://vnexpress.net/rss/the-gioi.rss",
            NewsTopic.SPORTS to "https://vnexpress.net/rss/the-thao.rss",
            NewsTopic.ENTERTAINMENT to "https://vnexpress.net/rss/giai-tri.rss",
        ),
    ) +
        source(
            "tuoitre",
            "Tuoi Tre",
            mapOf(
                NewsTopic.TOP to "https://tuoitre.vn/home.rss",
                NewsTopic.TECH to "https://tuoitre.vn/rss/khoa-hoc.rss",
                NewsTopic.BUSINESS to "https://tuoitre.vn/rss/kinh-doanh.rss",
                NewsTopic.WORLD to "https://tuoitre.vn/rss/the-gioi.rss",
                NewsTopic.SPORTS to "https://tuoitre.vn/rss/the-thao.rss",
                NewsTopic.ENTERTAINMENT to "https://tuoitre.vn/rss/giai-tri.rss",
            ),
        ) +
        source(
            "thanhnien",
            "Thanh Nien",
            mapOf(
                NewsTopic.TOP to "https://thanhnien.vn/rss/home.rss",
                NewsTopic.TECH to "https://thanhnien.vn/rss/cong-nghe.rss",
                NewsTopic.BUSINESS to "https://thanhnien.vn/rss/kinh-te.rss",
                NewsTopic.WORLD to "https://thanhnien.vn/rss/the-gioi.rss",
                NewsTopic.SPORTS to "https://thanhnien.vn/rss/the-thao.rss",
                NewsTopic.ENTERTAINMENT to "https://thanhnien.vn/rss/giai-tri.rss",
            ),
        ) +
        source(
            "dantri",
            "Dan Tri",
            mapOf(
                NewsTopic.TOP to "https://dantri.com.vn/rss/home.rss",
                NewsTopic.TECH to "https://dantri.com.vn/rss/cong-nghe.rss",
                NewsTopic.BUSINESS to "https://dantri.com.vn/rss/kinh-doanh.rss",
                NewsTopic.WORLD to "https://dantri.com.vn/rss/the-gioi.rss",
                NewsTopic.SPORTS to "https://dantri.com.vn/rss/the-thao.rss",
                NewsTopic.ENTERTAINMENT to "https://dantri.com.vn/rss/giai-tri.rss",
            ),
        ) +
        source("google-news", "Google News", google("vi", "VN", "VN:vi"))
}
