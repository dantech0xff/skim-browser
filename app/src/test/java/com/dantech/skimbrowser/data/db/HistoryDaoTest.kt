package com.dantech.skimbrowser.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dantech.skimbrowser.data.db.dao.HistoryDao
import com.dantech.skimbrowser.data.db.entity.HistoryEntryEntity
import com.dantech.skimbrowser.testing.assertThrowsSuspending
import com.dantech.skimbrowser.testing.awaitItemMatching
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.longQuery
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {

    private lateinit var db: SkimDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        dao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertOfANewUrlInsertsWithTheGivenId() = runTest {
        val id = dao.upsertKeepingLatest(entry(id = 42, url = URL, title = "Heat pumps", visitedAt = 100))

        assertEquals(42L, id)
        assertEquals(42L, dao.byUrl(URL)?.id)
    }

    @Test
    fun upsertOfAKnownUrlKeepsItsRowAndTheLatestVisit() = runTest {
        val id = dao.insert(entry(url = URL, title = "Old title", visitedAt = 100))

        assertEquals(id, dao.upsertKeepingLatest(entry(url = URL, title = "New title", visitedAt = 200)))
        assertEquals(id, dao.upsertKeepingLatest(entry(id = id + 50, url = URL, title = "Older visit", visitedAt = 150)))

        val stored = dao.byUrl(URL)
        assertEquals(id, stored?.id)
        assertEquals(200L, stored?.visitedAt)
        assertEquals("New title", stored?.title)
        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM history"))
    }

    @Test
    fun aLaterVisitWithoutATitleKeepsTheKnownTitle() = runTest {
        dao.insert(entry(url = URL, title = "Why heat pumps are winning", visitedAt = 100))

        dao.upsertKeepingLatest(entry(url = URL, title = " ", visitedAt = 200))

        assertEquals("Why heat pumps are winning", dao.byUrl(URL)?.title)
        assertEquals(200L, dao.byUrl(URL)?.visitedAt)
    }

    @Test
    fun theSameUrlCannotBeInsertedTwice() = runTest {
        dao.insert(entry(url = URL, visitedAt = 100))

        assertThrowsSuspending<SQLiteConstraintException> { dao.insert(entry(url = URL, visitedAt = 200)) }
    }

    @Test
    fun observeLatestListsNewestFirstUpToTheLimit() = runTest {
        dao.observeLatest(limit = 3).test {
            assertEquals(emptyList<HistoryEntryEntity>(), awaitItem())
            (1..5).forEach { dao.insert(entry(url = "https://example.com/$it", title = "Page $it", visitedAt = it * 10L)) }
            val latest = awaitItemMatching { it.size == 3 && it.first().title == "Page 5" }
            assertEquals(listOf("Page 5", "Page 4", "Page 3"), latest.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun matchingTreatsTheUsersWildcardsLiterally() = runTest {
        dao.insert(entry(url = "https://juice.example/a", title = "100% juice", visitedAt = 1))
        dao.insert(entry(url = "https://juice.example/b", title = "100 juice", visitedAt = 2))
        dao.insert(entry(url = "https://code.example/a", title = "snake_case names", visitedAt = 3))
        dao.insert(entry(url = "https://code.example/b", title = "snakeXcase names", visitedAt = 4))
        dao.insert(entry(url = "https://path.example/a", title = "C:\\Users", visitedAt = 5))
        dao.insert(entry(url = "https://path.example/b", title = "C:Users", visitedAt = 6))

        assertEquals(listOf("100% juice"), titlesMatching("0%"))
        assertEquals(listOf("snake_case names"), titlesMatching("e_c"))
        assertEquals(listOf("C:\\Users"), titlesMatching(":\\"))
    }

    @Test
    fun matchingSearchesTitleAndUrlIgnoringAsciiCase() = runTest {
        dao.insert(entry(url = "https://kotlinlang.org/docs", title = "Docs", visitedAt = 1))
        dao.insert(entry(url = "https://daylightjournal.com/", title = "Why Heat Pumps Win", visitedAt = 2))
        dao.insert(entry(url = "https://other.example/", title = "Other", visitedAt = 3))

        assertEquals(listOf("Docs"), titlesMatching("KOTLIN"))
        assertEquals(listOf("Why Heat Pumps Win"), titlesMatching("heat pumps"))
        assertEquals(listOf("Other", "Why Heat Pumps Win"), dao.matching(containsPattern("o"), limit = 2).map { it.title })
    }

    @Test
    fun removeReturnsTheDeletedEntryOnce() = runTest {
        val id = dao.insert(entry(url = URL, title = "Heat pumps", visitedAt = 100))

        assertEquals("Heat pumps", dao.remove(id)?.title)
        assertNull(dao.byId(id))
        assertNull(dao.remove(id))
    }

    private suspend fun titlesMatching(text: String) = dao.matching(containsPattern(text), limit = 10).map { it.title }

    private fun entry(id: Long = 0, url: String, title: String = "Title", visitedAt: Long) =
        HistoryEntryEntity(id = id, url = url, title = title, host = "example.com", visitedAt = visitedAt)

    private companion object {
        const val URL = "https://daylightjournal.com/energy/heat-pumps-cold"
    }
}
