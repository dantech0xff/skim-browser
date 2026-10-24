package com.dantech.skimbrowser.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dantech.skimbrowser.data.db.dao.BookmarkDao
import com.dantech.skimbrowser.data.db.dao.FolderBookmarkCount
import com.dantech.skimbrowser.data.db.dao.ImportRow
import com.dantech.skimbrowser.data.db.entity.BookmarkFolderEntity
import com.dantech.skimbrowser.data.db.entity.FolderKind
import com.dantech.skimbrowser.testing.assertThrowsSuspending
import com.dantech.skimbrowser.testing.awaitItemMatching
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
class BookmarkDaoTest {

    private lateinit var db: SkimDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        dao = db.bookmarkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun mobileBookmarksExistsAsSoonAsTheDatabaseIsCreated() = runTest {
        val mobile = dao.topLevelFolders().single()

        assertEquals(BookmarkFolderEntity.MOBILE_ID, mobile.id)
        assertEquals(FolderKind.MOBILE, mobile.kind)
        assertNull(mobile.name)
        assertNull(mobile.parentId)
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM bookmarks"))
    }

    @Test
    fun insertImportNestsFoldersAndKeepsTheFileOrder() = runTest {
        val top = dao.insertImport(
            fileName = "bookmarks.html",
            rows = listOf(
                ImportRow.Bookmark(null, "Kotlin", "https://kotlinlang.org/", "kotlinlang.org"),
                ImportRow.Folder(null, "Travel"),
                ImportRow.Bookmark(1, "Night buses", "https://tabi-note.jp/osaka", "tabi-note.jp"),
                ImportRow.Folder(1, "Japan"),
                ImportRow.Bookmark(3, "Stays", "https://staysearch.example/", "staysearch.example"),
                ImportRow.Bookmark(null, "News", "https://news.example/", "news.example"),
            ),
            now = 500,
        )

        val folders = dao.observeFolders().first()
        val imported = folders.single { it.id == top }
        val travel = folders.single { it.name == "Travel" }
        val japan = folders.single { it.name == "Japan" }
        assertEquals(FolderKind.IMPORTED, imported.kind)
        assertEquals("bookmarks.html", imported.sourceFileName)
        assertNull(imported.name)
        assertEquals(1, imported.position)
        assertEquals(top, travel.parentId)
        assertEquals(FolderKind.FOLDER, travel.kind)
        assertEquals(travel.id, japan.parentId)
        assertEquals(listOf("Kotlin", "News"), dao.observeInFolder(top).first().map { it.title })
        assertEquals(listOf(0, 2), dao.observeInFolder(top).first().map { it.position })
        assertEquals(listOf("Night buses"), dao.observeInFolder(travel.id).first().map { it.title })
        assertEquals(listOf("Stays"), dao.observeInFolder(japan.id).first().map { it.title })
        assertTrue(dao.observeInFolder(japan.id).first().all { it.addedAt == 500L })
    }

    @Test
    fun insertImportRejectsARowWhoseFolderComesLaterAndWritesNothing() = runTest {
        assertThrowsSuspending<IllegalArgumentException> {
            dao.insertImport(
                fileName = "bookmarks.html",
                rows = listOf(
                    ImportRow.Bookmark(null, "Kotlin", "https://kotlinlang.org/", "kotlinlang.org"),
                    ImportRow.Bookmark(2, "Lost", "https://lost.example/", "lost.example"),
                    ImportRow.Folder(null, "Later"),
                ),
                now = 500,
            )
        }

        assertEquals(listOf(BookmarkFolderEntity.MOBILE_ID), dao.topLevelFolders().map { it.id })
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM bookmarks"))
    }

    @Test
    fun deletingAFolderDeletesItsSubfoldersAndTheirBookmarks() = runTest {
        val top = dao.insertImport(
            fileName = "bookmarks.html",
            rows = listOf(
                ImportRow.Folder(null, "Travel"),
                ImportRow.Bookmark(0, "Night buses", "https://tabi-note.jp/osaka", "tabi-note.jp"),
            ),
            now = 500,
        )

        db.writeRaw("DELETE FROM bookmark_folders WHERE id = ?", top)

        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM bookmark_folders"))
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM bookmarks"))
    }

    @Test
    fun countsByFolderCountOnlyTheFoldersOwnBookmarks() = runTest {
        val top = dao.insertImport(
            fileName = "bookmarks.html",
            rows = listOf(
                ImportRow.Bookmark(null, "A", "https://a.example/", "a.example"),
                ImportRow.Folder(null, "Sub"),
                ImportRow.Bookmark(1, "B", "https://b.example/", "b.example"),
                ImportRow.Bookmark(1, "C", "https://c.example/", "c.example"),
            ),
            now = 500,
        )
        dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, "D", "https://d.example/", "d.example", addedAt = 600)
        val sub = dao.observeFolders().first().single { it.name == "Sub" }.id

        val counts = dao.observeCountsByFolder().first().toSet()

        assertEquals(
            setOf(FolderBookmarkCount(top, 1), FolderBookmarkCount(sub, 2), FolderBookmarkCount(BookmarkFolderEntity.MOBILE_ID, 1)),
            counts,
        )
    }

    @Test
    fun observeByUrlFollowsTheNewestBookmarkOfThatUrl() = runTest {
        dao.observeByUrl(URL).test {
            assertNull(awaitItem())
            val older = dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, "Older", URL, "daylightjournal.com", addedAt = 100)
            assertEquals(older, awaitItemMatching { it?.id == older }?.id)
            val newer = dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, "Newer", URL, "daylightjournal.com", addedAt = 200)
            assertEquals(newer, awaitItemMatching { it?.id == newer }?.id)
            dao.remove(newer)
            assertEquals(older, awaitItemMatching { it?.id == older }?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addToFolderAppendsAfterTheLastBookmark() = runTest {
        dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, "A", "https://a.example/", "a.example", addedAt = 1)
        dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, "B", "https://b.example/", "b.example", addedAt = 2)

        assertEquals(listOf(0, 1), dao.observeInFolder(BookmarkFolderEntity.MOBILE_ID).first().map { it.position })
    }

    @Test
    fun moveAppendsToTheTargetFolderAndRefusesAMissingOne() = runTest {
        val top = dao.insertImport(
            fileName = "bookmarks.html",
            rows = listOf(ImportRow.Bookmark(null, "Kotlin", "https://kotlinlang.org/", "kotlinlang.org")),
            now = 1,
        )
        val id = dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, "Heat pumps", URL, "daylightjournal.com", addedAt = 2)

        assertFalse(dao.move(id, folderId = 999))
        assertTrue(dao.move(id, folderId = top))

        val moved = dao.byId(id)
        assertEquals(top, moved?.folderId)
        assertEquals(1, moved?.position)
        assertFalse(dao.move(id + 100, folderId = top))
    }

    @Test
    fun searchMatchesTitleOrUrlInEveryFolderNewestFirst() = runTest {
        dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, "Heat pumps", URL, "daylightjournal.com", addedAt = 1)
        dao.insertImport(
            fileName = "bookmarks.html",
            rows = listOf(ImportRow.Bookmark(null, "Pump rebates", "https://energysaver.example/rebates", "energysaver.example")),
            now = 2,
        )
        dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, "Osaka", "https://tabi-note.jp/osaka", "tabi-note.jp", addedAt = 3)

        assertEquals(listOf("Pump rebates", "Heat pumps"), dao.search(containsPattern("PUMP")).first().map { it.title })
        assertEquals(listOf("Osaka"), dao.search(containsPattern("tabi-note")).first().map { it.title })
        assertEquals(listOf("Pump rebates"), dao.matching(containsPattern("pump"), limit = 1).map { it.title })
    }

    private companion object {
        const val URL = "https://daylightjournal.com/energy/heat-pumps-cold"
    }
}
