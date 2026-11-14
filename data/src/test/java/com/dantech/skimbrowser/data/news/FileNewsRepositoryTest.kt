package com.dantech.skimbrowser.data.news

import com.dantech.skimbrowser.domain.news.NewsArticle
import com.dantech.skimbrowser.domain.news.NewsBoard
import com.dantech.skimbrowser.domain.news.NewsSnapshot
import com.dantech.skimbrowser.domain.news.NewsTopic
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileNewsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun newsStartsOnAndTheOffSwitchSurvivesANewStore() = runTest {
        val dir = folder.newFolder("news")
        val store = repository(dir)
        assertTrue(store.currentPreferences().enabled)

        store.setEnabled(false)
        store.setTopic(NewsTopic.TECH)
        store.setSourceShown("bbc", shown = false)
        assertTrue(store.addCustomFeed(" https://Example.com/rss "))
        assertFalse(store.addCustomFeed("http://example.com/rss"))

        val reopened = repository(dir)
        val prefs = reopened.currentPreferences()
        assertFalse(prefs.enabled)
        assertEquals(NewsTopic.TECH, prefs.topic)
        assertEquals(setOf("bbc"), prefs.hiddenSourceIds)
        assertEquals(listOf("https://example.com/rss"), prefs.customFeedUrls)
        assertFalse(reopened.addCustomFeed("https://example.com/rss"))
    }

    @Test
    fun aCachedListIsReadableAtOnceAndABadFileIsIgnored() = runTest {
        val dir = folder.newFolder("cache")
        val store = repository(dir)
        val snapshot = NewsSnapshot(
            articles = listOf(
                NewsArticle("https://example.com/a", "He said \"hi\"\nnext", "bbc", "BBC", 20, "https://images.example/a.jpg"),
            ),
            fetchedAtMillis = 20,
        )
        store.saveSnapshot("en-US", NewsTopic.TOP, snapshot)

        assertEquals(snapshot, store.currentSnapshot("en", NewsTopic.TOP))
        assertEquals(snapshot, store.snapshot("en", NewsTopic.TOP).first())
        assertTrue(NewsBoard.isFresh(snapshot.fetchedAtMillis, snapshot.fetchedAtMillis + 1_000))
        assertNull(store.currentSnapshot("en", NewsTopic.SPORTS))

        File(dir, "cache-en-TOP.json").writeText("{not json")
        assertNull(repository(dir).currentSnapshot("en", NewsTopic.TOP))
        File(dir, "preferences.json").writeText("nope")
        assertTrue(repository(dir).currentPreferences().enabled)
    }

    private fun repository(dir: File) = FileNewsRepository(dir, io = kotlinx.coroutines.Dispatchers.Unconfined)
}
