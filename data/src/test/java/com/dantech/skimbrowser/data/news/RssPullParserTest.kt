package com.dantech.skimbrowser.data.news

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.news.NewsArticle
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssPullParserTest {
    private val parser = RssPullParser()

    @Test
    fun rssKeepsAThumbnailAnEnclosureAndAnImageInTheDescription() {
        val stories = parser.parse(RSS, "bbc", "BBC")

        assertEquals(listOf("Thumb story", "Enclosure story", "Html story"), stories.map { it.title })
        assertEquals("https://images.example/thumb.jpg", stories[0].thumbnailUrl)
        assertEquals("https://images.example/file.jpg", stories[1].thumbnailUrl)
        assertEquals("https://images.example/inside.png", stories[2].thumbnailUrl)
        assertEquals(Instant.parse("2026-11-14T15:00:00Z").toEpochMilli(), stories[0].publishedAtMillis)
        assertEquals("BBC", stories[0].sourceName)
    }

    @Test
    fun atomReadsTheAlternateLinkAndAnImageInTheContent() {
        val stories = parser.parse(ATOM, "verge", "The Verge")

        assertEquals(1, stories.size)
        assertEquals("https://www.theverge.com/story", stories[0].link)
        assertEquals("https://images.example/verge.jpg", stories[0].thumbnailUrl)
        assertEquals(Instant.parse("2026-11-14T15:22:41Z").toEpochMilli(), stories[0].publishedAtMillis)
    }

    @Test
    fun oddDatesAndAFeedWithNoImageStillProduceStories() {
        val stories = parser.parse(ODD_DATES, "paper", "Paper")

        assertEquals(2, stories.size)
        assertEquals("GMT+7 story", stories[0].title)
        assertTrue(stories[0].publishedAtMillis != null)
        assertEquals("Two digit year", stories[1].title)
        assertTrue(stories[1].publishedAtMillis != null)
        assertNull(stories[0].thumbnailUrl)
    }

    @Test
    fun aBrokenDocumentKeepsTheStoryReadBeforeItBroke() {
        val stories = parser.parse(BROKEN, "paper", "Paper")

        assertEquals(listOf("https://example.com/kept"), stories.map { it.link })
    }

    @Test
    fun emptyAndNonXmlFeedsProduceNothing() {
        assertTrue(parser.parse("", "paper", "Paper").isEmpty())
        assertTrue(parser.parse("<rss><channel></channel></rss>", "paper", "Paper").isEmpty())
        assertTrue(parser.parse("<html><body>not a feed</body></html>", "paper", "Paper").isEmpty())
        assertTrue(parser.parse("this is not xml <", "paper", "Paper").isEmpty())
    }

    @Test
    fun aStoryWithoutATitleOrALinkIsDropped() {
        val stories = parser.parse(INCOMPLETE, "paper", "Paper")

        assertEquals(listOf("https://example.com/whole"), stories.map { it.link })
    }

    @Test
    fun aGmtDateSortsAsUtc() {
        assertEquals(
            java.time.Instant.parse("2026-11-14T15:00:00Z").toEpochMilli(),
            parseNewsDate("Sat, 14 Nov 2026 15:00:00 GMT"),
        )
    }

    @Test
    fun anAudioEnclosureIsNotUsedAsAThumbnail() {
        val stories = parser.parse(AUDIO, "npr", "NPR")

        assertNull(stories.single().thumbnailUrl)
        assertEquals("Audio story", stories.single().title)
    }
}

private const val RSS = """
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
  <channel>
    <title>Channel</title>
    <image><url>https://images.example/logo.png</url></image>
    <item>
      <title>Thumb story</title>
      <link>https://example.com/thumb</link>
      <pubDate>Sat, 14 Nov 2026 15:00:00 +0000</pubDate>
      <media:thumbnail url="https://images.example/thumb.jpg" />
    </item>
    <item>
      <title>Enclosure story</title>
      <link>https://example.com/file</link>
      <enclosure url="https://images.example/file.jpg" type="image/jpeg" />
    </item>
    <item>
      <title>Html story</title>
      <link>https://example.com/html</link>
      <description><![CDATA[<p><img src="https://images.example/inside.png" /></p>]]></description>
    </item>
  </channel>
</rss>
"""

private const val ATOM = """
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>The Verge</title>
  <entry>
    <title>Atom story</title>
    <link rel="alternate" type="text/html" href="https://www.theverge.com/story" />
    <link rel="related" href="https://www.theverge.com/ignore" />
    <updated>2026-11-14T15:22:41+00:00</updated>
    <content type="html">&lt;img src="https://images.example/verge.jpg" /&gt;</content>
  </entry>
</feed>
"""

private const val ODD_DATES = """
<rss><channel>
  <item>
    <title>GMT+7 story</title>
    <link>https://example.com/gmt</link>
    <pubDate>Sat, 14 Nov 2026 22:48:32 GMT+7</pubDate>
  </item>
  <item>
    <title>Two digit year</title>
    <link>https://example.com/yy</link>
    <pubDate>Sat, 14 Nov 26 22:53:16 +0700</pubDate>
  </item>
</channel></rss>
"""

private const val BROKEN = """
<rss><channel>
  <item>
    <title>Kept</title>
    <link>https://example.com/kept</link>
  </item>
  <item>
    <title>Cut off
"""

private const val INCOMPLETE = """
<rss><channel>
  <item><title>No link</title></item>
  <item><link>https://example.com/no-title</link></item>
  <item><title>Whole</title><link>https://example.com/whole</link></item>
</channel></rss>
"""

private const val AUDIO = """
<rss><channel>
  <item>
    <title>Audio story</title>
    <link>https://example.com/audio</link>
    <enclosure url="https://example.com/show.mp3" type="audio/mpeg" />
  </item>
</channel></rss>
"""
