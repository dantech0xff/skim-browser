package com.dantech.skimbrowser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dantech.skimbrowser.data.db.dao.BookmarkDao
import com.dantech.skimbrowser.data.db.dao.HistoryDao
import com.dantech.skimbrowser.data.db.dao.ShortcutDao
import com.dantech.skimbrowser.data.db.dao.SitePermissionDao
import com.dantech.skimbrowser.data.db.dao.SkimChatDao
import com.dantech.skimbrowser.data.db.dao.TabDao
import com.dantech.skimbrowser.data.db.entity.BookmarkEntity
import com.dantech.skimbrowser.data.db.entity.BookmarkFolderEntity
import com.dantech.skimbrowser.data.db.entity.FolderKind
import com.dantech.skimbrowser.data.db.entity.HistoryEntryEntity
import com.dantech.skimbrowser.data.db.entity.ShortcutEntity
import com.dantech.skimbrowser.data.db.entity.SitePermissionEntity
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.SkimMessageEntity
import com.dantech.skimbrowser.data.db.entity.TabEntity
import com.dantech.skimbrowser.data.db.entity.TabGroupEntity
import com.dantech.skimbrowser.data.db.entity.TabGroupingSnapshotEntity

/**
 * Skim's database (`skim.db`). Every query must also run on the SQLite of Android 8 to 10
 * (3.18 to 3.22): no upsert clause, window functions, RETURNING or JSON functions, and at most 999
 * bound values per statement. The schema is exported to `data/schemas/` for future migrations.
 */
@Database(
    entities = [
        TabEntity::class,
        TabGroupEntity::class,
        TabGroupingSnapshotEntity::class,
        HistoryEntryEntity::class,
        BookmarkFolderEntity::class,
        BookmarkEntity::class,
        ShortcutEntity::class,
        SkimChatEntity::class,
        SkimMessageEntity::class,
        SitePermissionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SkimDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao

    abstract fun historyDao(): HistoryDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun shortcutDao(): ShortcutDao

    abstract fun skimChatDao(): SkimChatDao

    abstract fun sitePermissionDao(): SitePermissionDao

    /**
     * Copies everything in the write-ahead log into the database file and truncates the log to
     * zero bytes, so rows deleted in bulk leave no copy behind in `-wal`. Call it after the delete
     * has committed: a checkpoint cannot run inside a transaction, and calling it from one throws.
     *
     * @return true when the log was fully checkpointed; false when a reader kept it busy.
     */
    suspend fun checkpointTruncate(): Boolean = useWriterConnection { connection ->
        check(!connection.inTransaction()) { "A WAL checkpoint cannot run inside a transaction" }
        connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { statement ->
            statement.step() && statement.getLong(0) == 0L
        }
    }

    companion object {
        /**
         * Opens [name] (a file in the app's database directory, or an absolute path), creating it
         * on first use.
         */
        fun open(context: Context, name: String): SkimDatabase =
            Room.databaseBuilder(context, SkimDatabase::class.java, name).withSkimSetup().build()
    }
}

/** Adds the callbacks every Skim database needs, on disk or in memory. */
fun RoomDatabase.Builder<SkimDatabase>.withSkimSetup(): RoomDatabase.Builder<SkimDatabase> =
    addCallback(SkimDatabaseCallback)

private object SkimDatabaseCallback : RoomDatabase.Callback() {

    /** Runs inside the transaction that creates the schema, so the folder exists with the tables. */
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO bookmark_folders (id, parentId, name, kind, sourceFileName, position, createdAt) " +
                "VALUES (?, NULL, NULL, ?, NULL, 0, ?)",
            arrayOf<Any>(BookmarkFolderEntity.MOBILE_ID, FolderKind.MOBILE.name, System.currentTimeMillis()),
        )
    }

    /**
     * Turns on secure delete, so deleted rows are overwritten with zeros instead of lingering in
     * free space. The setting belongs to one connection: the transaction pins the PRAGMA to the
     * write connection, the only one that ever deletes. A PRAGMA that returns a row must go
     * through query(), since execSQL() rejects it on some Android versions.
     */
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

/**
 * The most values one `IN (:list)` binds. SQLite before 3.32 (every Android before 11) rejects
 * statements with more than 999 bound values.
 */
internal const val SQL_IN_LIMIT = 500

/**
 * A LIKE pattern matching [text] anywhere, for queries that declare `ESCAPE '\'`: the user's `%`,
 * `_` and `\` match themselves instead of acting as wildcards.
 */
fun containsPattern(text: String): String = buildString(text.length + 2) {
    append('%')
    text.forEach { char ->
        if (char == '\\' || char == '%' || char == '_') append('\\')
        append(char)
    }
    append('%')
}
