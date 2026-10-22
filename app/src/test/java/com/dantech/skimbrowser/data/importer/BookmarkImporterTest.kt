package com.dantech.skimbrowser.data.importer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.FolderKind
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.testing.TestDocuments
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BookmarkImporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: SkimDatabase
    private lateinit var bookmarks: BookmarkRepository
    private lateinit var documents: TestDocuments

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        bookmarks = BookmarkRepository(db.bookmarkDao())
        documents = TestDocuments.register()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aChromeExportBecomesAnImportedFolderNamedAfterTheFile() = runTest {
        val uri = documents.add("chrome", file("chrome-export.html"), displayName = "bookmarks_10_22_26.html")

        val result = importer().import(uri)

        val imported = result as? ImportResult.Imported ?: throw AssertionError("not imported: $result")
        assertEquals(6, imported.bookmarkCount)
        val root = bookmarks.observeRoot().first()
        val folder = root.folders.single { it.folder.kind == FolderKind.IMPORTED }
        assertEquals(imported.folderId, folder.folder.id)
        assertEquals("bookmarks_10_22_26.html", folder.folder.sourceFileName)
        assertEquals(6, folder.bookmarkCount)
        val content = bookmarks.observeFolder(imported.folderId).first()
        assertEquals(listOf("Bookmarks bar", "Other bookmarks"), content.subfolders.map { it.folder.name })
        assertEquals(listOf("Wikipedia"), content.bookmarks.map { it.title })
    }

    @Test
    fun theNameThePickerShowsIsKeptAsPlainText() = runTest {
        val uri = documents.add("named", file("chrome-export.html"), displayName = "Chrome\u202e lmth.bookmarks\u200b.html")

        val imported = importer().import(uri) as ImportResult.Imported

        assertEquals("Chrome lmth.bookmarks.html", folderOf(imported).sourceFileName)
    }

    @Test
    fun withoutANameFromThePickerTheLastPartOfTheAddressNamesTheFolder() = runTest {
        val uri = documents.add("exported.html", file("chrome-export.html"), displayName = null)

        val imported = importer().import(uri) as ImportResult.Imported

        assertEquals("exported.html", folderOf(imported).sourceFileName)
    }

    @Test
    fun aFileLargerThanTenMegabytesIsNotRead() = runTest {
        val large = file("chrome-export.html").also { padTo(it, BookmarkImporter.MAX_FILE_BYTES + 1L) }
        val uri = documents.add("large", large, displayName = "large.html")

        assertEquals(ImportResult.Failed(ImportResult.Reason.TooLarge), importer().import(uri))
        assertEquals(0, importedFolderCount())
    }

    @Test
    fun aFileOfExactlyTenMegabytesIsRead() = runTest {
        val limit = file("chrome-export.html").also { padTo(it, BookmarkImporter.MAX_FILE_BYTES.toLong()) }
        val uri = documents.add("limit", limit, displayName = "limit.html")

        val result = importer().import(uri)

        assertEquals(6, (result as? ImportResult.Imported)?.bookmarkCount)
        assertEquals(10L * 1024 * 1024, limit.length())
    }

    @Test
    fun aPageThatIsNotABookmarkFileImportsNothing() = runTest {
        val uri = documents.add("page", file("not-bookmarks.html"), displayName = "reading-list.html")

        assertEquals(ImportResult.Failed(ImportResult.Reason.NotBookmarks), importer().import(uri))
        assertEquals(0, importedFolderCount())
    }

    @Test
    fun aBookmarkFileWithoutWebLinksLeavesNoEmptyFolder() = runTest {
        val onlyScripts = folder.newFile("scripts.html").apply {
            writeText(
                "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n" +
                    "<DT><H3>Tools</H3>\n<DL><p>\n<DT><A HREF=\"javascript:void(0)\">Bookmarklet</A>\n</DL><p>\n" +
                    "<DT><A HREF=\"file:///sdcard/a.html\">Local</A>\n</DL><p>\n",
            )
        }
        val uri = documents.add("scripts", onlyScripts, displayName = "scripts.html")

        assertEquals(ImportResult.Failed(ImportResult.Reason.NoBookmarks), importer().import(uri))
        assertEquals(0, importedFolderCount())
    }

    @Test
    fun aDocumentThatCannotBeOpenedIsUnreadable() = runTest {
        assertEquals(ImportResult.Failed(ImportResult.Reason.Unreadable), importer().import(TestDocuments.uriOf("missing")))
        assertEquals(0, importedFolderCount())
    }

    @Test
    fun aFileOfMoreTagsThanTheParserWillBuildIsNotParsed() = runTest {
        // Small enough to read, and every link in it is a valid one: only the tag count rejects it.
        val bare = folder.newFile("bare-tags.html").apply {
            writeText(
                "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n" +
                    "<DT><A HREF=http://a.b>\n".repeat(NetscapeBookmarkParser.MAX_TAGS / 2 + 1) + "</DL><p>\n",
            )
        }
        val uri = documents.add("bare", bare, displayName = "bare.html")

        assertEquals(ImportResult.Failed(ImportResult.Reason.TooLarge), importer().import(uri))
        assertEquals(0, importedFolderCount())
    }

    @Test
    fun aProviderThatFailsInAnyWayImportsNothingAndSaysSo() = runTest {
        val failures = listOf(
            IllegalArgumentException("content://provider/secret-name.html"),
            IllegalStateException("closed"),
            UnsupportedOperationException(),
            NullPointerException(),
        )

        failures.forEachIndexed { index, failure ->
            val uri = documents.addFailing("broken-$index", failure)

            assertEquals(failure.javaClass.simpleName, ImportResult.Failed(ImportResult.Reason.Unreadable), importer().import(uri))
        }
        assertEquals(0, importedFolderCount())
    }

    @Test
    fun cancellingAnImportIsNotTurnedIntoAFailure() = runTest {
        val uri = documents.add("chrome", file("chrome-export.html"), displayName = "bookmarks.html")
        var answer: ImportResult? = null

        val job = launch(start = CoroutineStart.UNDISPATCHED) { answer = importer().import(uri) }
        job.cancel()
        runCurrent()

        assertNull("a cancelled import answers nothing", answer)
        assertEquals(0, importedFolderCount())
    }

    private fun TestScope.importer() = BookmarkImporter(
        resolver = context.contentResolver,
        bookmarks = bookmarks,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        parseDispatcher = StandardTestDispatcher(testScheduler),
    )

    private suspend fun folderOf(imported: ImportResult.Imported) =
        bookmarks.observeRoot().first().folders.single { it.folder.id == imported.folderId }.folder

    private suspend fun importedFolderCount(): Int =
        bookmarks.observeRoot().first().folders.count { it.folder.kind == FolderKind.IMPORTED }

    /** A copy of the fixture [name] in the test's folder. */
    private fun file(name: String): File {
        val bytes = checkNotNull(javaClass.classLoader?.getResourceAsStream("bookmarks/$name")) { "Missing fixture $name" }
            .use { it.readBytes() }
        return File(folder.root, "${System.nanoTime()}-$name").apply { writeBytes(bytes) }
    }

    /** Pads [file] with an HTML comment after its content until it is [size] bytes long. */
    private fun padTo(file: File, size: Long) {
        val open = "\n<!--".toByteArray()
        val close = "-->\n".toByteArray()
        val fill = size - file.length() - open.size - close.size
        require(fill >= 0) { "The fixture is already larger than $size bytes" }
        FileOutputStream(file, true).use { out ->
            out.write(open)
            val chunk = ByteArray(64 * 1024) { 'x'.code.toByte() }
            var left = fill
            while (left > 0) {
                val count = minOf(left, chunk.size.toLong()).toInt()
                out.write(chunk, 0, count)
                left -= count
            }
            out.write(close)
        }
    }
}
