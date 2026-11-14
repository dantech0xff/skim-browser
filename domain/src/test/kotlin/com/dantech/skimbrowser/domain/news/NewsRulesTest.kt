package com.dantech.skimbrowser.domain.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsUrlsTest {
    @Test
    fun anHttpsFeedIsStoredWithoutAFragmentOrUserInfo() {
        assertEquals(
            "https://example.com/rss?x=1",
            NewsUrls.canonical("  https://Example.com/rss?x=1#top  "),
        )
        assertEquals("https://example.com:8443/rss", NewsUrls.canonical("https://Example.com:8443/rss"))
    }

    @Test
    fun aFeedThatIsNotHttpsIsRefused() {
        assertNull(NewsUrls.canonical("http://example.com/rss"))
        assertNull(NewsUrls.canonical("javascript:alert(1)"))
        assertNull(NewsUrls.canonical("https://user:pass@example.com/rss"))
        assertNull(NewsUrls.canonical("https://localhost/rss"))
        assertNull(NewsUrls.canonical(" "))
        assertNull(NewsUrls.canonical("https://" + "a".repeat(NewsUrls.MAX_LENGTH)))
    }
}

class NewsBoardTest {
    @Test
    fun theSameLinkKeepsTheNewerStoryAndAnUndatedStorySortsLast() {
        val older = story("https://example.com/a", "First headline", 1_000)
        val newer = story("https://example.com/A#part", "Updated headline", 2_000)
        val undated = story("https://example.com/c", "Undated", null)
        val merged = NewsBoard.merge(listOf(listOf(older, undated, newer)))

        assertEquals(listOf("https://example.com/A#part", "https://example.com/c"), merged.map { it.link })
        assertEquals("Updated headline", merged.first().title)
    }

    @Test
    fun theSameTitleFromTwoFeedsIsKeptOnce() {
        val older = story("https://example.com/a", "Shared headline", 1_000)
        val newer = story("https://other.example/b", "  shared   headline ", 3_000)
        val merged = NewsBoard.merge(listOf(listOf(older), listOf(newer)))

        assertEquals(listOf("https://other.example/b"), merged.map { it.link })
    }

    @Test
    fun theWallStopsAtFiftyStories() {
        val stories = (1..60).map { story("https://example.com/$it", "Story $it", it.toLong()) }
        val merged = NewsBoard.merge(listOf(stories))

        assertEquals(50, merged.size)
        assertEquals("https://example.com/60", merged.first().link)
        assertEquals("https://example.com/11", merged.last().link)
    }

    @Test
    fun aListIsFreshForHalfAnHour() {
        val fetched = 1_000_000L
        assertTrue(NewsBoard.isFresh(fetched, fetched + NewsBoard.TTL_MILLIS - 1))
        assertFalse(NewsBoard.isFresh(fetched, fetched + NewsBoard.TTL_MILLIS))
        assertFalse(NewsBoard.isFresh(fetched, fetched - 1))
    }

    private fun story(link: String, title: String, published: Long?) = NewsArticle(
        link = link,
        title = title,
        sourceId = "src",
        sourceName = "Source",
        publishedAtMillis = published,
        thumbnailUrl = null,
    )
}

class NewsCatalogTest {
    @Test
    fun vietnameseGetsTheVietnameseFeedsAndEveryoneElseGetsEnglish() {
        assertEquals(NewsCatalog.VIETNAMESE, NewsCatalog.languageGroup("vi-VN"))
        assertEquals(NewsCatalog.ENGLISH, NewsCatalog.languageGroup("en-US"))
        assertEquals(NewsCatalog.ENGLISH, NewsCatalog.languageGroup("fr"))

        val vietnamese = NewsCatalog.feeds("vi", NewsTopic.TOP, emptySet(), emptyList()).map { it.sourceId }
        val english = NewsCatalog.feeds("en", NewsTopic.TOP, emptySet(), emptyList()).map { it.sourceId }

        assertEquals(listOf("vnexpress", "tuoitre", "thanhnien", "dantri", "google-news"), vietnamese)
        assertEquals(listOf("google-news", "bbc", "guardian", "verge", "ars"), english)
        assertTrue(NewsCatalog.feeds("vi", NewsTopic.TOP, emptySet(), emptyList()).all { it.url.startsWith("https://") })
        assertTrue(NewsCatalog.feeds("en", NewsTopic.TOP, emptySet(), emptyList()).all { it.url.startsWith("https://") })
    }

    @Test
    fun aTopicUsesOnlyFeedsThatCoverItAndAHiddenSourceDropsOut() {
        val tech = NewsCatalog.feeds("en", NewsTopic.TECH, setOf("bbc"), emptyList())
        assertTrue(tech.any { it.sourceId == "verge" && it.url == "https://www.theverge.com/rss/index.xml" })
        assertTrue(tech.none { it.sourceId == "bbc" })

        val sports = NewsCatalog.feeds("en", NewsTopic.SPORTS, emptySet(), emptyList()).map { it.sourceId }
        assertFalse(sports.contains("verge"))
        assertFalse(sports.contains("ars"))
        assertTrue(sports.contains("bbc"))
    }

    @Test
    fun aCustomFeedIsAddedOnEveryTopicUntilItIsHidden() {
        val url = "https://example.com/rss"
        val top = NewsCatalog.feeds("en", NewsTopic.TOP, emptySet(), listOf(url))
        assertEquals(url, top.last().url)
        assertEquals("example.com", top.last().sourceName)

        val hidden = NewsCatalog.feeds("en", NewsTopic.SPORTS, setOf(NewsUrls.customId(url)), listOf(url))
        assertTrue(hidden.none { it.url == url })
    }

    @Test
    fun theShippedAddressesAreTheOnesThatAnswered() {
        val english = NewsCatalog.feeds("en", NewsTopic.TOP, emptySet(), emptyList()).associate { it.sourceId to it.url }
        assertEquals("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", english["google-news"])
        assertEquals("https://feeds.bbci.co.uk/news/rss.xml", english["bbc"])
        assertEquals("https://www.theguardian.com/world/rss", english["guardian"])
        assertEquals("https://feeds.arstechnica.com/arstechnica/index", english["ars"])

        val vietnamese = NewsCatalog.feeds("vi", NewsTopic.TECH, emptySet(), emptyList()).associate { it.sourceId to it.url }
        assertEquals("https://vnexpress.net/rss/khoa-hoc-cong-nghe.rss", vietnamese["vnexpress"])
        assertEquals("https://dantri.com.vn/rss/cong-nghe.rss", vietnamese["dantri"])
        assertEquals("https://tuoitre.vn/rss/khoa-hoc.rss", vietnamese["tuoitre"])
        assertEquals("https://thanhnien.vn/rss/cong-nghe.rss", vietnamese["thanhnien"])
    }
}
