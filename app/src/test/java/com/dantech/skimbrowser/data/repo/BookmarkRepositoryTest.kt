package com.dantech.skimbrowser.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.dao.FolderBookmarkCount
import com.dantech.skimbrowser.data.db.entity.BookmarkEntity
import com.dantech.skimbrowser.data.db.entity.BookmarkFolderEntity
import com.dantech.skimbrowser.data.db.entity.FolderKind
import com.dantech.skimbrowser.testing.assertThrowsSuspending
import com.dantech.skimbrowser.testing.awaitItemMatching
import com.dantech.skimbrowser.testing.execRaw
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.longQuery
import com.dantech.skimbrowser.testing.writeRaw
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkRepositoryTest {

    private lateinit var db: SkimDatabase
    private lateinit var bookmarks: BookmarkRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        bookmarks = BookmarkRepository(db.bookmarkDao(), clock = { now++ })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun theImportedFolderCountsTheBookmarksOfItsSubfoldersToo() = runTest {
        val outcome = importFile("bookmarks.html", bookmarksFile())

        assertEquals(24, outcome.bookmarkCount)
        val root = bookmarks.observeRoot().first()
        assertEquals(listOf(FolderKind.MOBILE, FolderKind.IMPORTED), root.folders.map { it.folder.kind })
        assertEquals(listOf(0, 24), root.folders.map { it.bookmarkCount })
        val imported = root.folders[1].folder
        assertEquals(outcome.folderId, imported.id)
        assertEquals("bookmarks.html", imported.sourceFileName)

        val content = bookmarks.observeFolder(imported.id).first()
        assertEquals(imported, content.folder)
        assertEquals(listOf("Travel" to 14, "Work" to 6), content.subfolders.map { it.folder.name to it.bookmarkCount })
        assertEquals(4, content.bookmarks.size)
        val travel = content.subfolders.first().folder
        assertEquals(listOf("Japan" to 6), bookmarks.observeFolder(travel.id).first().subfolders.map { it.folder.name to it.bookmarkCount })
    }

    @Test
    fun importKeepsOnlyWebBookmarksWithAHost() = runTest {
        val outcome = importFile(
            "bookmarks.html",
            listOf(
                ImportedNode("Bookmarklet", "javascript:alert(document.cookie)"),
                ImportedNode("Smart folder", "place:sort=8&maxResults=10"),
                ImportedNode("Local file", "file:///home/me/notes.html"),
                ImportedNode("Data", "data:text/html,<b>hi</b>"),
                ImportedNode("No host", "https://"),
                ImportedNode("Kotlin", " https://kotlinlang.org/ "),
            ),
        )

        assertEquals(1, outcome.bookmarkCount)
        val stored = bookmarks.observeFolder(outcome.folderId).first().bookmarks.single()
        assertEquals("https://kotlinlang.org/", stored.url)
        assertEquals("kotlinlang.org", stored.host)
    }

    @Test
    fun aFileWithoutAnyWebBookmarkImportsNothing() = runTest {
        val outcome = bookmarks.importTree(
            "bookmarks.html",
            listOf(
                ImportedNode("Empty folder", url = null),
                ImportedNode("Folder of scripts", url = null, children = listOf(ImportedNode("Bookmarklet", "javascript:void(0)"))),
                ImportedNode("Local file", "file:///home/me/notes.html"),
            ),
        )

        assertNull(outcome)
        assertEquals(listOf(BookmarkFolderEntity.MOBILE_ID), bookmarks.topLevelFolders().map { it.id })
        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM bookmark_folders"))
    }

    @Test
    fun aFailedImportLeavesNothingBehind() = runTest {
        // Make one bookmark in the middle of the file fail to insert.
        db.execRaw(
            "CREATE TEMP TRIGGER fail_import BEFORE INSERT ON bookmarks WHEN NEW.title = 'Broken' " +
                "BEGIN SELECT RAISE(ABORT, 'bookmark write failed'); END",
        )
        val nodes = bookmarksFile() + ImportedNode("Broken", "https://broken.example/") + ImportedNode("After", "https://after.example/")

        assertThrowsSuspending<SQLiteConstraintException> { bookmarks.importTree("bookmarks.html", nodes) }

        assertEquals(listOf(BookmarkFolderEntity.MOBILE_ID), bookmarks.topLevelFolders().map { it.id })
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM bookmarks"))
        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM bookmark_folders"))
    }

    @Test
    fun eachImportGetsItsOwnImportedFolderAfterMobileBookmarks() = runTest {
        val first = importFile("bookmarks.html", listOf(ImportedNode("A", "https://a.example/")))
        val second = importFile("firefox.html", listOf(ImportedNode("B", "https://b.example/")))

        val folders = bookmarks.topLevelFolders()
        assertEquals(listOf(BookmarkFolderEntity.MOBILE_ID, first.folderId, second.folderId), folders.map { it.id })
        assertEquals(listOf(null, "bookmarks.html", "firefox.html"), folders.map { it.sourceFileName })
    }

    @Test
    fun recentlyAddedShowsTheTenNewestBookmarksOfEveryFolder() = runTest {
        (1..6).forEach { bookmarks.addToMobile("Mobile $it", "https://mobile.example/$it") }
        bookmarks.importTree("bookmarks.html", (1..6).map { ImportedNode("Imported $it", "https://imported.example/$it") })

        val recent = bookmarks.observeRoot().first().recentlyAdded

        assertEquals(BookmarkRepository.RECENTLY_ADDED_LIMIT, recent.size)
        assertEquals("Imported 6", recent.first().title)
        assertEquals("Mobile 3", recent.last().title)
    }

    @Test
    fun undoOfADeletedBookmarkBringsItBackWithItsIdAndPlace() = runTest {
        listOf("Daylight Journal", "Osaka night buses", "Kotlin").forEachIndexed { index, title ->
            bookmarks.addToMobile(title, "https://site$index.example/")
        }
        val before = bookmarks.observeFolder(BookmarkFolderEntity.MOBILE_ID).first().bookmarks
        val deleted = checkNotNull(bookmarks.delete(before[1].id))

        assertEquals(listOf("Daylight Journal", "Kotlin"), bookmarks.observeFolder(BookmarkFolderEntity.MOBILE_ID).first().bookmarks.map { it.title })
        bookmarks.restore(deleted)

        assertEquals(before, bookmarks.observeFolder(BookmarkFolderEntity.MOBILE_ID).first().bookmarks)
    }

    @Test
    fun undoIntoAFolderThatIsGoneGoesToTheEndOfMobileBookmarks() = runTest {
        bookmarks.addToMobile("Daylight Journal", "https://daylightjournal.com/")
        val imported = importFile("bookmarks.html", listOf(ImportedNode("Kotlin", "https://kotlinlang.org/")))
        val bookmark = bookmarks.observeFolder(imported.folderId).first().bookmarks.single()
        val deleted = checkNotNull(bookmarks.delete(bookmark.id))
        db.writeRaw("DELETE FROM bookmark_folders WHERE id = ?", imported.folderId)

        bookmarks.restore(deleted)

        val mobile = bookmarks.observeFolder(BookmarkFolderEntity.MOBILE_ID).first().bookmarks
        assertEquals(listOf("Daylight Journal", "Kotlin"), mobile.map { it.title })
        assertEquals(bookmark.id, mobile.last().id)
        assertEquals(1, mobile.last().position)
    }

    @Test
    fun undoOfABookmarkThatIsStillThereChangesNothing() = runTest {
        val id = bookmarks.addToMobile("Kotlin", "https://kotlinlang.org/")
        val stored = checkNotNull(bookmarks.observe(id).first())

        bookmarks.restore(stored.copy(title = "Changed"))

        assertEquals(stored, bookmarks.observe(id).first())
    }

    @Test
    fun addToMobileAppendsWithTheSiteHost() = runTest {
        bookmarks.addToMobile("Why heat pumps are winning", "https://www.daylightjournal.com/energy/heat-pumps-cold")
        bookmarks.addToMobile("Osaka night buses", "https://tabi-note.jp/osaka")

        val mobile = bookmarks.observeFolder(BookmarkFolderEntity.MOBILE_ID).first().bookmarks
        assertEquals(listOf("daylightjournal.com", "tabi-note.jp"), mobile.map { it.host })
        assertEquals(listOf(0, 1), mobile.map { it.position })
        assertThrowsSuspending<IllegalArgumentException> { bookmarks.addToMobile("Nothing", " ") }
    }

    @Test
    fun updateRewritesTitleUrlAndHost() = runTest {
        val id = bookmarks.addToMobile("Old", "https://old.example/")

        bookmarks.update(id, "Kotlin", "https://www.kotlinlang.org/docs")

        val stored = checkNotNull(bookmarks.observe(id).first())
        assertEquals("Kotlin", stored.title)
        assertEquals("https://www.kotlinlang.org/docs", stored.url)
        assertEquals("kotlinlang.org", stored.host)
    }

    @Test
    fun moveAppendsToTheEndOfAnotherFolder() = runTest {
        val imported = importFile("bookmarks.html", listOf(ImportedNode("Kotlin", "https://kotlinlang.org/")))
        val id = bookmarks.addToMobile("Daylight Journal", "https://daylightjournal.com/")

        assertTrue(bookmarks.move(id, imported.folderId))
        assertFalse(bookmarks.move(id, folderId = 4242))

        assertEquals(listOf("Kotlin", "Daylight Journal"), bookmarks.observeFolder(imported.folderId).first().bookmarks.map { it.title })
        assertTrue(bookmarks.observeFolder(BookmarkFolderEntity.MOBILE_ID).first().bookmarks.isEmpty())
    }

    @Test
    fun anOpenedFolderThatIsDeletedReportsNoFolder() = runTest {
        val imported = importFile("bookmarks.html", listOf(ImportedNode("Kotlin", "https://kotlinlang.org/")))

        bookmarks.observeFolder(imported.folderId).test {
            awaitItemMatching { it.folder != null && it.bookmarks.size == 1 }
            db.writeRaw("DELETE FROM bookmark_folders WHERE id = ?", imported.folderId)
            val gone = awaitItemMatching { it.folder == null && it.bookmarks.isEmpty() }
            assertTrue(gone.subfolders.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun searchCoversEveryFolderAndNeedsText() = runTest {
        bookmarks.addToMobile("Heat pumps", "https://daylightjournal.com/energy")
        bookmarks.importTree("bookmarks.html", listOf(ImportedNode("Travel", null, listOf(ImportedNode("Pump rebates", "https://energysaver.example/")))))

        assertEquals(listOf("Pump rebates", "Heat pumps"), bookmarks.search("pump").first().map { it.title })
        assertEquals(emptyList<BookmarkEntity>(), bookmarks.search("   ").first())
        assertEquals(listOf("Pump rebates"), bookmarks.matches("PUMP", limit = 1).map { it.title })
        assertEquals(emptyList<BookmarkEntity>(), bookmarks.matches("pump", limit = 0))
    }

    @Test
    fun observeByUrlTellsWhetherAPageIsBookmarked() = runTest {
        val url = "https://daylightjournal.com/energy/heat-pumps-cold"

        bookmarks.observeByUrl(url).test {
            assertNull(awaitItem())
            val id = bookmarks.addToMobile("Heat pumps", url)
            assertEquals(id, awaitItemMatching { it != null }?.id)
            bookmarks.delete(id)
            awaitItemMatching { it == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recursiveCountsHandleDeepNestingAndLoopsInParentLinks() {
        val chain = (1L..20_000L).map { folder(it, parentId = if (it == 1L) null else it - 1) }
        val deep = recursiveCounts(chain, listOf(FolderBookmarkCount(20_000L, 2), FolderBookmarkCount(1L, 1)))
        assertEquals(3, deep[1L])
        assertEquals(2, deep[19_999L])

        val loop = listOf(folder(1, parentId = 2), folder(2, parentId = 1))
        val counted = recursiveCounts(loop, listOf(FolderBookmarkCount(1, 4), FolderBookmarkCount(2, 5)))
        assertEquals(setOf(1L, 2L), counted.keys)
    }

    /** Imports a file that holds at least one web bookmark. */
    private suspend fun importFile(fileName: String, nodes: List<ImportedNode>): ImportOutcome =
        checkNotNull(bookmarks.importTree(fileName, nodes)) { "The file held no web bookmark" }

    /** The drawn "24 bookmarks from bookmarks.html": 4 loose, Travel with Japan inside, and Work. */
    private fun bookmarksFile(): List<ImportedNode> =
        (1..4).map { ImportedNode("Loose $it", "https://loose.example/$it") } + listOf(
            ImportedNode(
                "Travel",
                url = null,
                children = (1..8).map { ImportedNode("Travel $it", "https://travel.example/$it") } +
                    ImportedNode("Japan", url = null, children = (1..6).map { ImportedNode("Japan $it", "https://tabi-note.jp/$it") }),
            ),
            ImportedNode("Work", url = null, children = (1..6).map { ImportedNode("Work $it", "https://work.example/$it") }),
        )

    private fun folder(id: Long, parentId: Long?) =
        BookmarkFolderEntity(id = id, parentId = parentId, name = "F$id", kind = FolderKind.FOLDER, position = 0, createdAt = 0)
}
