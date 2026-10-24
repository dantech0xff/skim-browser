package com.dantech.skimbrowser.data.repo

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.HistoryEntryEntity
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.longQuery
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {

    private lateinit var db: SkimDatabase
    private lateinit var history: HistoryRepository

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        history = HistoryRepository(db.historyDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun onlyHttpAndHttpsPagesAreRecorded() = runTest {
        listOf(
            "javascript:alert(1)",
            "data:text/html,<p>hi</p>",
            "about:blank",
            "file:///sdcard/Download/page.html",
            "view-source:https://daylightjournal.com/",
            "chrome://flags",
            "",
            "https://",
        ).forEach { history.recordVisit(it, "Not a page", at = 1) }
        history.recordVisit("HTTPS://WWW.Daylightjournal.com/energy", "Heat pumps", at = 2)

        val stored = history.observe(null).first().single()
        assertEquals("HTTPS://WWW.Daylightjournal.com/energy", stored.url)
        assertEquals("daylightjournal.com", stored.host)
    }

    @Test
    fun aUrlVisitedAgainKeepsOneRowWithTheLatestVisit() = runTest {
        history.recordVisit(URL, "Heat pumps", at = 100)
        history.recordVisit(URL, "Why heat pumps are winning", at = 200)

        val stored = history.observe(null).first().single()
        assertEquals(200L, stored.visitedAt)
        assertEquals("Why heat pumps are winning", stored.title)
    }

    @Test
    fun undoOfARemovedEntryBringsItBackWithItsIdAndPlace() = runTest {
        history.recordVisit("https://a.example/", "A", at = 1)
        history.recordVisit(URL, "Heat pumps", at = 2)
        history.recordVisit("https://c.example/", "C", at = 3)
        val before = history.observe(null).first()
        val removed = before.single { it.url == URL }

        assertEquals(removed, history.remove(removed.id))
        assertEquals(listOf("C", "A"), history.observe(null).first().map { it.title })
        history.restore(removed)

        assertEquals(before, history.observe(null).first())
    }

    @Test
    fun undoAfterTheUrlWasVisitedAgainKeepsTheNewerRowWithoutBreakingUniqueness() = runTest {
        history.recordVisit(URL, "Heat pumps", at = 100)
        val removed = checkNotNull(history.remove(history.observe(null).first().single().id))
        history.recordVisit(URL, "Why heat pumps are winning", at = 200)
        val revisited = history.observe(null).first().single()
        assertNotEquals(removed.id, revisited.id)

        history.restore(removed)

        val stored = history.observe(null).first().single()
        assertEquals(revisited.id, stored.id)
        assertEquals(200L, stored.visitedAt)
        assertEquals("Why heat pumps are winning", stored.title)
        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM history"))
    }

    @Test
    fun theListStopsAtOneThousandEntriesButSearchCoversThemAll() = runTest {
        db.withTransaction {
            (1..1005).forEach {
                db.historyDao().insert(
                    HistoryEntryEntity(url = "https://example.com/$it", title = "Page %04d".format(Locale.ROOT, it), host = "example.com", visitedAt = it.toLong()),
                )
            }
        }

        val listed = history.observe(null).first()
        assertEquals(HistoryRepository.LIST_LIMIT, listed.size)
        assertEquals("Page 1005", listed.first().title)
        assertEquals("Page 0006", listed.last().title)
        assertEquals(listOf("Page 0001"), history.observe("page 0001").first().map { it.title })
        assertEquals(listOf("Page 0001"), history.observe("  Page 0001 ").first().map { it.title })
    }

    @Test
    fun matchesNeedTextAndAPositiveLimit() = runTest {
        history.recordVisit("https://kotlinlang.org/docs", "Kotlin docs", at = 1)
        history.recordVisit("https://kotlinlang.org/blog", "Kotlin blog", at = 2)

        assertEquals(emptyList<HistoryEntryEntity>(), history.matches("  ", limit = 5))
        assertEquals(emptyList<HistoryEntryEntity>(), history.matches("kotlin", limit = 0))
        assertEquals(listOf("Kotlin blog"), history.matches("kotlin", limit = 1).map { it.title })
    }

    @Test
    fun aBlankTitleNeverReplacesAKnownOne() = runTest {
        history.recordVisit(URL, "", at = 1)
        history.updateTitle(URL, "Heat pumps")
        history.updateTitle(URL, "   ")

        assertEquals("Heat pumps", history.observe(null).first().single().title)
    }

    @Test
    fun aUrlLongerThanTheLimitIsNotRecorded() = runTest {
        val prefix = "https://example.com/"
        val atLimit = prefix + "a".repeat(HistoryRepository.MAX_URL_LENGTH - prefix.length)

        history.recordVisit(atLimit, "At the limit", at = 1)
        history.recordVisit(atLimit + "a", "One character over", at = 2)

        assertEquals(listOf(atLimit), history.observe(null).first().map { it.url })
    }

    @Test
    fun aTitleLongerThanTheLimitIsCutWithoutSplittingACharacter() = runTest {
        val limit = HistoryRepository.MAX_TITLE_LENGTH
        val emoji = "😀"

        // The emoji is two chars and straddles the limit: both halves go, so no lone surrogate stays.
        history.recordVisit(URL, "a".repeat(limit - 1) + emoji + "tail", at = 1)
        history.recordVisit("https://b.example/", "b".repeat(limit * 2), at = 2)
        history.recordVisit("https://c.example/", "c".repeat(limit), at = 3)
        val stored = history.observe(null).first().associate { it.url to it.title }

        assertEquals("a".repeat(limit - 1), stored.getValue(URL))
        assertEquals("b".repeat(limit), stored.getValue("https://b.example/"))
        assertEquals("c".repeat(limit), stored.getValue("https://c.example/"))

        history.updateTitle(URL, "d".repeat(limit + 5))
        assertEquals("d".repeat(limit), history.observe(null).first().single { it.url == URL }.title)
    }

    @Test
    fun clearAllEmptiesTheHistory() = runTest {
        history.recordVisit("https://a.example/", "A", at = 1)
        history.recordVisit(URL, "Heat pumps", at = 2)

        history.clearAll()

        assertTrue(history.observe(null).first().isEmpty())
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM history"))
    }

    private companion object {
        const val URL = "https://daylightjournal.com/energy/heat-pumps-cold"
    }
}
