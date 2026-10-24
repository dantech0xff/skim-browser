package com.dantech.skimbrowser.testing

import android.content.Context
import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.withSkimSetup

/** A fresh in-memory Skim database with the production callbacks (Mobile bookmarks, secure delete). */
fun inMemorySkimDatabase(): SkimDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SkimDatabase::class.java)
        .withSkimSetup()
        .build()

/** Runs [sql] on the write connection, for setups no DAO offers (a failing trigger). */
fun SkimDatabase.execRaw(sql: String, vararg args: Any) {
    openHelper.writableDatabase.execSQL(sql, args)
}

/**
 * Runs a data-changing [sql] no DAO offers (deleting a folder) through Room, so open flows see the
 * change: writes made through the open helper directly are invisible to Room's invalidation.
 */
suspend fun SkimDatabase.writeRaw(sql: String, vararg args: Long) {
    useWriterConnection { connection ->
        connection.usePrepared(sql) { statement ->
            args.forEachIndexed { index, value -> statement.bindLong(index + 1, value) }
            statement.step()
        }
    }
}

/** Reads one number with [sql] on the write connection. */
fun SkimDatabase.longQuery(sql: String): Long =
    openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst()) { "No row for $sql" }
        cursor.getLong(0)
    }

/**
 * The first item matching [predicate]. A flow combined from several tables may emit an
 * intermediate state per table; this skips those instead of depending on their order.
 */
suspend fun <T> ReceiveTurbine<T>.awaitItemMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

/** Runs [block], which must throw [T], and returns what it threw. */
suspend inline fun <reified T : Throwable> assertThrowsSuspending(block: suspend () -> Unit): T {
    val thrown = try {
        block()
        null
    } catch (error: Throwable) {
        error
    }
    if (thrown is T) return thrown
    throw AssertionError("Expected ${T::class.java.simpleName}, got ${thrown ?: "no exception"}", thrown)
}
