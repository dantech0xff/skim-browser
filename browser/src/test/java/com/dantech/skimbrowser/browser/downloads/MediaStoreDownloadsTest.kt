package com.dantech.skimbrowser.browser.downloads

import android.Manifest
import android.app.Application
import android.content.ContextWrapper
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

/**
 * MediaStore saves through the ContentResolver, against a MediaStore that keeps its rows in
 * memory. Scoped storage (API 29+) runs on the default SDK; the legacy folder path runs on API 26.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MediaStoreDownloadsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var media: FakeMediaStore
    private lateinit var downloadsFolder: File

    @Before
    fun setUp() {
        media = FakeMediaStore.install(folder.newFolder("media"))
        downloadsFolder = File(folder.root, "Download")
    }

    @Test
    fun aFileIsHiddenWhileItIsWrittenThenPublishedInDownloads() = runTest {
        var pendingWhileWriting: Int? = null

        val saved = store().save("report.pdf", "application/pdf") { out ->
            pendingWhileWriting = media.rows().single().int(MediaStore.Downloads.IS_PENDING)
            out.write(PDF)
        }

        val (uri, given) = media.inserts().single()
        assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI, uri)
        assertEquals("report.pdf", given.getAsString(MediaStore.Downloads.DISPLAY_NAME))
        assertEquals("application/pdf", given.getAsString(MediaStore.Downloads.MIME_TYPE))
        assertEquals("Download/", given.getAsString(MediaStore.Downloads.RELATIVE_PATH))
        assertEquals(1, given.getAsInteger(MediaStore.Downloads.IS_PENDING))
        assertEquals(1, pendingWhileWriting)
        val row = media.rows().single()
        assertEquals("published once complete", 0, row.int(MediaStore.Downloads.IS_PENDING))
        assertArrayEquals(PDF, row.file.readBytes())
        assertEquals(row.uri, saved.uri)
        assertEquals("report.pdf", saved.fileName)
        assertEquals("application/pdf", saved.mime)
    }

    @Test
    fun theAddressAFileCameFromIsNeverStored() = runTest {
        store().save("report.pdf", "application/pdf") { it.write(PDF) }

        val stored = media.inserts().single().second.keySet() + media.rows().single().values.keySet()
        assertFalse(MediaStore.Downloads.DOWNLOAD_URI in stored)
        assertFalse(MediaStore.Downloads.REFERER_URI in stored)
    }

    @Test
    fun theSavedFileCarriesTheNameAndTypeMediaStoreKeeps() = runTest {
        media.keptMimeType = "application/x-pdf"

        val saved = store().save("report.pdf", "application/pdf") { it.write(PDF) }

        assertEquals("application/x-pdf", saved.mime)
    }

    @Test
    fun aFailedWriteLeavesNoRowBehind() = runTest {
        val failure = runCatching {
            store().save("report.pdf", "application/pdf") { out ->
                out.write(PDF)
                throw IOException("connection reset")
            }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(media.rows().isEmpty())
        assertEquals(1, media.deletes().size)
    }

    @Test
    fun aCancelledSaveLeavesNoRowBehind() = runTest {
        val writing = async(start = CoroutineStart.UNDISPATCHED) {
            store().save("big.zip", "application/zip") { out ->
                out.write(PDF)
                awaitCancellation()
            }
        }
        runCurrent()
        assertEquals("the row exists while it is written", 1, media.rows().size)

        writing.cancel()
        runCurrent()

        assertTrue(runCatching { writing.await() }.exceptionOrNull() is CancellationException)
        assertTrue(media.rows().isEmpty())
    }

    @Test
    fun aRefusedRowIsAFailure() = runTest {
        media.refuseInserts = true

        val failure = runCatching { store().save("report.pdf", "application/pdf") { it.write(PDF) } }.exceptionOrNull()

        assertTrue(failure is IOException)
    }

    @Test
    fun aNameThatIsAPathIsRefused() = runTest {
        listOf("../report.pdf", "a/b.pdf", "..", ".", "").forEach { name ->
            val failure = runCatching { store().save(name, "application/pdf") { it.write(PDF) } }.exceptionOrNull()
            assertTrue(name, failure is IOException)
        }
        assertTrue(media.inserts().isEmpty())
    }

    @Test
    fun scopedStorageNeedsNoPermission() {
        assertFalse(MediaStoreDownloads(app).needsStoragePermission)
    }

    @Test
    @Config(sdk = [26])
    fun beforeScopedStorageTheFolderNeedsTheStoragePermission() {
        val downloads = MediaStoreDownloads(app, legacyFolder = { downloadsFolder })
        assertTrue(downloads.needsStoragePermission)

        shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        assertFalse(downloads.needsStoragePermission)
    }

    @Test
    @Config(sdk = [26])
    fun beforeScopedStorageTheFileGoesToTheFolderAndIsListed() = runTest {
        val saved = legacyStore().save("report.pdf", "application/pdf") { it.write(PDF) }

        val file = File(downloadsFolder, "report.pdf")
        assertArrayEquals(PDF, file.readBytes())
        val (uri, given) = media.inserts().single()
        assertEquals(MediaStore.Files.getContentUri("external"), uri)
        @Suppress("DEPRECATION")
        assertEquals(file.absolutePath, given.getAsString(MediaStore.MediaColumns.DATA))
        assertEquals("application/pdf", given.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertEquals(media.rows().single().uri, saved.uri)
        assertEquals("report.pdf", saved.fileName)
        assertEquals("application/pdf", saved.mime)
    }

    @Test
    @Config(sdk = [26])
    fun beforeScopedStorageANameAlreadyTakenGetsANumber() = runTest {
        val downloads = legacyStore()

        val names = (1..3).map { downloads.save("report.pdf", "application/pdf") { it.write(PDF) }.fileName } +
            downloads.save("notes", "text/plain") { it.write(PDF) }.fileName +
            downloads.save("notes", "text/plain") { it.write(PDF) }.fileName

        assertEquals(listOf("report.pdf", "report (1).pdf", "report (2).pdf", "notes", "notes (1)"), names)
        assertEquals(names.toSet(), downloadsFolder.list()!!.toSet())
    }

    @Test
    @Config(sdk = [26])
    fun beforeScopedStorageAFailedWriteLeavesNoFileBehind() = runTest {
        val failure = runCatching {
            legacyStore().save("report.pdf", "application/pdf") { out ->
                out.write(PDF)
                throw IOException("connection reset")
            }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(emptyList<String>(), downloadsFolder.list()!!.toList())
        assertTrue("nothing listed", media.inserts().isEmpty())
    }

    @Test
    @Config(sdk = [26])
    fun beforeScopedStorageAFileMediaStoreRefusesIsDeleted() = runTest {
        media.refuseInserts = true

        val failure = runCatching { legacyStore().save("report.pdf", "application/pdf") { it.write(PDF) } }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertNull(downloadsFolder.list()?.firstOrNull())
    }

    @Test
    fun theFreeSpaceIsThatOfTheVolumeDownloadsAreSavedOn() {
        ShadowStatFs.registerStats(app.getExternalFilesDir(null)!!, 100_000, 60_000, 50_000)

        assertEquals(50_000L * ShadowStatFs.BLOCK_SIZE, MediaStoreDownloads(app).freeBytes())
    }

    @Test
    @Config(sdk = [26])
    fun beforeScopedStorageTheFreeSpaceIsThatOfTheDownloadsFolderEvenBeforeItExists() {
        ShadowStatFs.registerStats(folder.root, 100_000, 60_000, 40_000)
        assertFalse(downloadsFolder.exists())

        assertEquals(40_000L * ShadowStatFs.BLOCK_SIZE, MediaStoreDownloads(app, legacyFolder = { downloadsFolder }).freeBytes())
    }

    @Test
    fun withoutSharedStorageTheFreeSpaceHoldsNoDownloadBack() {
        val unmounted = object : ContextWrapper(app) {
            override fun getExternalFilesDir(type: String?): File? = null
        }

        assertEquals(Long.MAX_VALUE, MediaStoreDownloads(unmounted).freeBytes())
    }

    private fun TestScope.store() = MediaStoreDownloads(app, ioDispatcher = StandardTestDispatcher(testScheduler))

    private fun TestScope.legacyStore() =
        MediaStoreDownloads(app, legacyFolder = { downloadsFolder }, ioDispatcher = StandardTestDispatcher(testScheduler))

    private companion object {
        val PDF = "%PDF-1.4\n1 0 obj\n".toByteArray()
    }
}
