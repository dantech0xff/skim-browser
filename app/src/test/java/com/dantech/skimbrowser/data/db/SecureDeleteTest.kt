package com.dantech.skimbrowser.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.MessageKind
import com.dantech.skimbrowser.data.db.entity.MessageRole
import com.dantech.skimbrowser.data.db.entity.MessageStatus
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.chat.SavedMessage
import com.dantech.skimbrowser.data.repo.ChatRepository
import com.dantech.skimbrowser.data.repo.HistoryRepository
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.testing.assertThrowsSuspending
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Deleted history, chats and AI site decisions must not survive on disk: not in the database
 * file's free space and not in the write-ahead log. Databases here are real files in WAL mode.
 *
 * The SQLite used by these tests (like Android's) turns secure delete on by default, which would
 * hide a missing PRAGMA. Every database is therefore opened through [SecureDeleteOffByDefault],
 * which switches it off on the write connection before Skim's own callback runs, so only Skim's
 * callback can turn it back on.
 */
@RunWith(AndroidJUnit4::class)
class SecureDeleteTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<SkimDatabase>()
    private lateinit var dbFile: File
    private val walFile get() = File(dbFile.path + "-wal")

    @Before
    fun setUp() {
        dbFile = File(folder.newFolder(), "skim.db")
    }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
    }

    @Test
    fun secureDeleteIsOnForTheWriteConnectionEachTimeTheDatabaseOpens() = runTest {
        val withoutSkimSetup = open(File(folder.newFolder(), "plain.db"), skimSetup = false)
        assertEquals("the helper really starts with secure delete off", 0L, withoutSkimSetup.secureDeleteOnWriteConnection())

        val db = open()
        assertEquals(1L, db.secureDeleteOnWriteConnection())
        db.close()

        assertEquals(1L, open().secureDeleteOnWriteConnection())
    }

    @Test
    fun aRemovedHistoryEntryLeavesNoBytesOnDisk() = runTest {
        val db = open()
        val history = HistoryRepository(db.historyDao(), db)
        val marker = marker()
        (1..10).forEach { history.recordVisit("https://before.example/$it", "Filler $it", at = it.toLong()) }
        history.recordVisit("https://daylightjournal.com/$marker", "Title $marker", at = 20)
        (1..10).forEach { history.recordVisit("https://after.example/$it", "Filler $it", at = 30L + it) }
        assertTrue(db.checkpointTruncate())
        assertTrue("the scan finds the entry before it is removed", dbFile.holds(marker))

        history.remove(history.observe(null).first().single { it.title.contains(marker) }.id)
        assertTrue(db.checkpointTruncate())

        assertNoTraceOf(marker)
        assertEquals(20, history.observe(null).first().size)
    }

    @Test
    fun clearingHistoryTruncatesTheLogAndLeavesNoTrace() = runTest {
        val db = open()
        val history = HistoryRepository(db.historyDao(), db)
        val marker = marker()
        repeat(30) { history.recordVisit("https://daylightjournal.com/$marker/$it", "Title $marker $it", at = it.toLong()) }
        assertTrue("the visits are in the log before the delete", walFile.holds(marker))

        history.clearAll()

        assertNoTraceOf(marker)
    }

    @Test
    fun deletingAllChatsTruncatesTheLogAndLeavesNoTrace() = runTest {
        val db = open()
        val chats = ChatRepository(db.skimChatDao(), db)
        val marker = marker()
        val id = chats.createChat(
            SavedChat(kind = ChatKind.PAGE, url = "https://daylightjournal.com/$marker", host = "daylightjournal.com", title = "Chat $marker", createdAt = 1, updatedAt = 1),
        )
        chats.addMessage(SavedMessage(chatId = id, role = MessageRole.USER, kind = MessageKind.QUESTION, text = "Question $marker", status = MessageStatus.COMPLETE, createdAt = 1))
        chats.addMessage(SavedMessage(chatId = id, role = MessageRole.ASSISTANT, kind = MessageKind.ANSWER, text = "Answer $marker", status = MessageStatus.COMPLETE, createdAt = 2))
        assertTrue("the chat is in the log before the delete", walFile.holds(marker))

        chats.deleteAll()

        assertNoTraceOf(marker)
    }

    @Test
    fun deletingAllAiDataTruncatesTheLogAndLeavesNoHostBehind() = runTest {
        val db = open()
        val sites = SitePermissionRepository(db.sitePermissionDao(), db)
        val marker = marker()
        sites.setSkimAi("$marker.example", SkimAiState.ALLOWED)
        sites.setSkimAi("kept.example", SkimAiState.ASK)
        sites.setPermission("kept.example", SitePermissionKind.LOCATION, PermissionState.BLOCKED)
        assertTrue("the host is in the log before the delete", walFile.holds(marker))

        sites.deleteAllSkimAiData()

        assertNoTraceOf(marker)
        assertNotNull(sites.get("kept.example"))
    }

    @Test
    fun aCheckpointInsideATransactionIsRefused() = runTest {
        val db = open()

        assertThrowsSuspending<IllegalStateException> { db.withTransaction { db.checkpointTruncate() } }
    }

    private fun assertNoTraceOf(marker: String) {
        assertEquals("the log is truncated", 0L, walFile.length())
        assertFalse("the database file still holds deleted text", dbFile.holds(marker))
        assertFalse("the log still holds deleted text", walFile.holds(marker))
    }

    private fun open(file: File = dbFile, skimSetup: Boolean = true): SkimDatabase {
        val builder = Room.databaseBuilder(context, SkimDatabase::class.java, file.path)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .openHelperFactory(SecureDeleteOffByDefault())
        return (if (skimSetup) builder.withSkimSetup() else builder).build().also { opened += it }
    }

    private fun marker() = "skim-trace-" + UUID.randomUUID()

    /** Whether the raw bytes of this file contain [text] in any encoding SQLite may store it in. */
    private fun File.holds(text: String): Boolean {
        if (!exists()) return false
        val bytes = readBytes()
        return listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).any { bytes.indexOf(text.toByteArray(it)) >= 0 }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        outer@ for (start in 0..size - needle.size) {
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) continue@outer
            }
            return start
        }
        return -1
    }

    /** The secure delete setting of the connection that writes and deletes. */
    private fun SkimDatabase.secureDeleteOnWriteConnection(): Long {
        val db = openHelper.writableDatabase
        db.beginTransaction()
        try {
            return db.query("PRAGMA secure_delete").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Opens databases like an SQLite build without secure delete on by default: the write
     * connection has it off before Room, and with it Skim's callback, sees the database.
     */
    private class SecureDeleteOffByDefault(
        private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
    ) : SupportSQLiteOpenHelper.Factory {

        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val callback = configuration.callback
            val offFirst = object : SupportSQLiteOpenHelper.Callback(callback.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = callback.onConfigure(db)

                override fun onCreate(db: SupportSQLiteDatabase) = callback.onCreate(db)

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    callback.onUpgrade(db, oldVersion, newVersion)

                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    callback.onDowngrade(db, oldVersion, newVersion)

                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.beginTransaction()
                    try {
                        db.query("PRAGMA secure_delete = OFF").use { it.moveToFirst() }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                    callback.onOpen(db)
                }

                override fun onCorruption(db: SupportSQLiteDatabase) = callback.onCorruption(db)
            }
            return delegate.create(
                SupportSQLiteOpenHelper.Configuration(
                    configuration.context,
                    configuration.name,
                    offFirst,
                    configuration.useNoBackupDirectory,
                    configuration.allowDataLossOnRecovery,
                ),
            )
        }
    }
}
