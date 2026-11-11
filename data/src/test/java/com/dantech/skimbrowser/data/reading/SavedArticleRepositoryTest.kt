package com.dantech.skimbrowser.data.reading

import com.dantech.skimbrowser.domain.page.PageBlock
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedArticleRepositoryTest {

    @Test
    fun aSavedArticleOpensWithoutThePageAndKeepsItsSummary() = runTest {
        val database = inMemorySkimDatabase()
        val store = SavedArticleRepository(database)
        val id = store.save(
            url = "https://example.com/story",
            host = "example.com",
            title = "A story",
            byline = "Ada",
            blocks = listOf(PageBlock("1", "p", "The first paragraph.")),
        )

        val again = store.save("https://example.com/story", "example.com", "A story", null, emptyList())
        store.setSummary(id, "Three points.")

        val stored = store.article(id)
        assertEquals(id, again)
        assertEquals("The first paragraph.", stored?.blocks?.single()?.text)
        assertEquals("Three points.", stored?.summary)
        assertEquals("A story", store.articles.first().single().title)

        store.delete(id)
        assertNull(store.article(id))
        database.close()
    }
}
