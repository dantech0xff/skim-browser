package com.dantech.skimbrowser.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.testing.assertThrowsSuspending
import com.dantech.skimbrowser.testing.awaitItemMatching
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutRepositoryTest {

    private lateinit var db: SkimDatabase
    private lateinit var shortcuts: ShortcutRepository

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        shortcuts = ShortcutRepository(db.shortcutDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun shortcutsAreAddedAfterTheLastOne() = runTest {
        shortcuts.observe().test {
            assertEquals(emptyList<Any>(), awaitItem())
            shortcuts.add("Wikipedia", "wikipedia.org")
            shortcuts.add(" GitHub ", " github.com ")
            val added = awaitItemMatching { it.size == 2 }
            assertEquals(listOf("Wikipedia", "GitHub"), added.map { it.name })
            assertEquals(listOf("wikipedia.org", "github.com"), added.map { it.url })
            assertEquals(listOf(0, 1), added.map { it.position })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun undoOfARemovedShortcutBringsItBackWithItsIdAndPlace() = runTest {
        listOf("Wikipedia", "GitHub", "YouTube", "Hacker News").forEach { shortcuts.add(it, "${it.lowercase()}.example") }
        val before = shortcuts.observe().first()

        val removed = checkNotNull(shortcuts.remove(before[1].id))
        assertEquals(listOf("Wikipedia", "YouTube", "Hacker News"), shortcuts.observe().first().map { it.name })
        shortcuts.restore(removed)

        assertEquals(before, shortcuts.observe().first())
    }

    @Test
    fun undoOfAShortcutThatIsStillThereChangesNothing() = runTest {
        shortcuts.add("Wikipedia", "wikipedia.org")
        val stored = shortcuts.observe().first().single()

        shortcuts.restore(stored.copy(name = "Changed"))

        assertEquals(listOf(stored), shortcuts.observe().first())
    }

    @Test
    fun removingAMissingShortcutReturnsNothing() = runTest {
        assertNull(shortcuts.remove(99))
    }

    @Test
    fun updateChangesNameAndUrlInPlace() = runTest {
        shortcuts.add("Wiki", "wiki.example")
        shortcuts.add("GitHub", "github.com")
        val wiki = shortcuts.observe().first().first()

        shortcuts.update(wiki.id, "Wikipedia", "https://wikipedia.org")

        val updated = shortcuts.observe().first()
        assertEquals(listOf("Wikipedia", "GitHub"), updated.map { it.name })
        assertEquals("https://wikipedia.org", updated.first().url)
        assertEquals(0, updated.first().position)
    }

    @Test
    fun aShortcutNeedsAUrl() = runTest {
        assertThrowsSuspending<IllegalArgumentException> { shortcuts.add("Empty", "  ") }
        shortcuts.add("Wikipedia", "wikipedia.org")
        val id = shortcuts.observe().first().single().id
        assertThrowsSuspending<IllegalArgumentException> { shortcuts.update(id, "Wikipedia", "") }
    }
}
