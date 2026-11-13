package com.dantech.skimbrowser.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.backup.BackupCodec
import com.dantech.skimbrowser.domain.backup.BackupDecode
import com.dantech.skimbrowser.domain.backup.BackupDocument
import com.dantech.skimbrowser.domain.backup.BackupFailure
import com.dantech.skimbrowser.domain.backup.BackupFile
import com.dantech.skimbrowser.testing.TestDocuments
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFileStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var documents: TestDocuments

    @Before
    fun setUp() {
        documents = TestDocuments.register()
    }

    @Test
    fun aJsonFileIsParsedAndAnHtmlFileIsLeftForTheBookmarkImporter() = runTest {
        val store = BackupFileStore(context.contentResolver)
        val json = documents.add("json", file("backup.json", BackupCodec.encode(emptyBackup())), displayName = "skim-backup.json")
        val html = documents.add("html", file("bookmarks.html", "<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p>"), displayName = "bookmarks.html")

        val parsed = store.read(json.toString()) as BackupFile.Json
        assertEquals("1.0", (parsed.result as BackupDecode.Ok).document.appVersion)
        assertEquals(BackupFile.Html, store.read(html.toString()))
    }

    @Test
    fun aFilePastTheCapAndAFileThatIsNotABackupAreRefused() = runTest {
        val store = BackupFileStore(context.contentResolver)
        val huge = documents.add("huge", file("huge.json", "{" + " ".repeat(BackupCodec.MAX_BYTES)), displayName = "huge.json")
        val text = documents.add("text", file("notes.txt", "hello"), displayName = "notes.txt")

        assertEquals(BackupFile.TooLarge, store.read(huge.toString()))
        val rejected = store.read(text.toString()) as BackupFile.Json
        assertEquals(BackupDecode.Rejected(BackupFailure.MALFORMED), rejected.result)
        assertTrue(store.read(TestDocuments.uriOf("missing").toString()) is BackupFile.Unreadable)
    }

    private fun file(name: String, text: String): File {
        val out = File(context.cacheDir, name)
        out.writeText(text)
        return out
    }

    private fun emptyBackup() = BackupDocument(schemaVersion = BackupCodec.SCHEMA_VERSION, appVersion = "1.0", createdAt = 1)
}
