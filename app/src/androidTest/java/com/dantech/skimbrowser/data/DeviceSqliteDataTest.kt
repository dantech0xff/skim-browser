package com.dantech.skimbrowser.data

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.app.Application
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.TestStorage
import com.dantech.skimbrowser.di.AppGraphEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.dantech.skimbrowser.domain.tab.PersistedTab
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The data layer on the device's own SQLite, which is 3.22 on Android 9 (older still on Android 8):
 * no upsert clause and at most 999 bound values in one statement, where newer SQLite has both. A
 * container of the app's runs on a temporary directory, its database a real file on the device:
 * a URL visited again keeps one row, clearing the history leaves an empty write-ahead log, closing
 * more tabs than one statement can bind closes every one, and the connection that writes deletes
 * securely.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DeviceSqliteDataTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var dir: File
    private lateinit var graph: ProcessGraph

    @Before
    fun setUp() {
        dir = File(app.cacheDir, "data-test-${UUID.randomUUID()}").apply { mkdirs() }
        TestStorage.current.set(AppStorage.temp(dir))
        graph = EntryPointAccessors.fromApplication(app, AppGraphEntryPoint::class.java).graph()
    }

    @After
    fun tearDown() {
        try {
            if (::graph.isInitialized) {
                EntryPointAccessors.fromApplication(app, AppGraphEntryPoint::class.java).shutdown().close()
            }
        } finally {
            TestStorage.current.set(null)
            dir.deleteRecursively()
        }
    }

    @Test
    fun aUrlVisitedAgainKeepsOneRowWithItsLatestVisitTime() = runBlocking<Unit> {
        withTimeout(TIMEOUT_MS) {
            val history = graph.historyRepository()

            history.recordVisit(FIRST_URL, "Article", at = EARLIER)
            history.recordVisit(FIRST_URL, "Article, updated", at = LATER)
            // A visit recorded after a later one does not move the row back in time.
            history.recordVisit(SECOND_URL, "Guide", at = LATER)
            history.recordVisit(SECOND_URL, "Guide", at = EARLIER)

            val version = sqliteVersion()
            assertEquals("one row per URL on SQLite $version", 1L, count("SELECT COUNT(*) FROM history WHERE url = ?", FIRST_URL))
            assertEquals("one row per URL on SQLite $version", 1L, count("SELECT COUNT(*) FROM history WHERE url = ?", SECOND_URL))
            val entries = history.observe(null).first().associateBy { it.url }
            assertEquals(LATER, entries.getValue(FIRST_URL).visitedAt)
            assertEquals("Article, updated", entries.getValue(FIRST_URL).title)
            assertEquals("the later of the two visits", LATER, entries.getValue(SECOND_URL).visitedAt)
        }
    }

    @Test
    fun clearingTheHistoryLeavesNoRowAndAnEmptyWriteAheadLog() = runBlocking<Unit> {
        withTimeout(TIMEOUT_MS) {
            val history = graph.historyRepository()
            repeat(VISITS) { history.recordVisit("https://daylightjournal.com/article/$it", "Article $it", at = it.toLong()) }
            val wal = File(dir, "skim.db-wal")
            assumeTrue("Room keeps no write-ahead log here (a low-RAM device)", writeAheadLogging())
            assertTrue("the visits are in the log first", wal.length() > 0)

            history.clearAll()

            assertTrue(history.observe(null).first().isEmpty())
            assertEquals("the log is truncated on SQLite ${sqliteVersion()}", 0L, wal.length())
        }
    }

    @Test
    fun closingMoreTabsThanOneStatementCanBindClosesEveryOne() = runBlocking<Unit> {
        withTimeout(TABS_TIMEOUT_MS) {
            val tabs = graph.tabRepository()
            val ids = List(TABS) { "tab-$it" }
            graph.database().withTransaction {
                ids.forEachIndexed { index, id ->
                    tabs.upsert(PersistedTab(id = id, url = "https://daylightjournal.com/$index", title = "Tab $index", position = index, createdAt = 1, lastAccessedAt = 1))
                }
            }

            tabs.markClosed(ids, at = CLOSED_AT)

            val version = sqliteVersion()
            assertEquals("every tab closed on SQLite $version", TABS.toLong(), count("SELECT COUNT(*) FROM tabs WHERE closedAt = ?", CLOSED_AT))
            assertEquals("none left open", 0L, count("SELECT COUNT(*) FROM tabs WHERE closedAt IS NULL"))
        }
    }

    @Test
    fun theConnectionThatWritesDeletesSecurely() = runBlocking<Unit> {
        withTimeout(TIMEOUT_MS) {
            graph.historyRepository().recordVisit(FIRST_URL, "Article", at = EARLIER)

            val secureDelete = secureDeleteOnWriteConnection()

            assertTrue("secure_delete is $secureDelete on SQLite ${sqliteVersion()}", secureDelete > 0)
        }
    }

    /** The one number [sql] counts, read on the database's own connections. */
    private suspend fun count(sql: String, vararg args: Any): Long = withContext(Dispatchers.IO) {
        graph.database().openHelper.readableDatabase.query(sql, args).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
    }

    private suspend fun writeAheadLogging(): Boolean = withContext(Dispatchers.IO) {
        graph.database().openHelper.writableDatabase.isWriteAheadLoggingEnabled
    }

    /**
     * The secure delete setting of the connection that writes and deletes: a transaction keeps the
     * query on that connection, as Skim's callback does when it turns the setting on.
     */
    private suspend fun secureDeleteOnWriteConnection(): Long = withContext(Dispatchers.IO) {
        val db = graph.database().openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.query("PRAGMA secure_delete").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        } finally {
            db.endTransaction()
        }
    }

    private suspend fun sqliteVersion(): String = withContext(Dispatchers.IO) {
        graph.database().openHelper.readableDatabase.query("SELECT sqlite_version()").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }
    }

    private companion object {
        const val FIRST_URL = "https://daylightjournal.com/heat-pumps"
        const val SECOND_URL = "https://daylightjournal.com/guide"
        const val EARLIER = 1_000L
        const val LATER = 2_000L
        const val VISITS = 30
        const val CLOSED_AT = 5_000L

        /** More than the 999 values one statement may bind on SQLite before 3.32. */
        const val TABS = 1_200
        const val TIMEOUT_MS = 15_000L

        /** Writing the 1,200 tabs first takes a while on a busy emulator. */
        const val TABS_TIMEOUT_MS = 60_000L
    }
}
