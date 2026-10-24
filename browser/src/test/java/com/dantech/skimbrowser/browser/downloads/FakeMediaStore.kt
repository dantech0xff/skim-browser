package com.dantech.skimbrowser.browser.downloads

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileNotFoundException
import org.robolectric.Robolectric

/**
 * MediaStore for JVM tests: rows in memory, each with its own file, served through the
 * ContentResolver like the real provider (insert, update, query, delete, openFile). [install]
 * registers it for the `media` authority. Calls may come from any thread.
 */
class FakeMediaStore : ContentProvider() {

    class Row(val uri: Uri, val values: ContentValues, val file: File) {
        fun string(column: String): String? = values.getAsString(column)

        fun int(column: String): Int? = values.getAsInteger(column)
    }

    private val rows = LinkedHashMap<Long, Row>()
    private val insertLog = mutableListOf<Pair<Uri, ContentValues>>()
    private val deleteLog = mutableListOf<Uri>()
    private var lastId = 0L
    private lateinit var directory: File

    /** Makes [insert] answer null, as MediaStore does when it refuses a row. */
    @Volatile
    var refuseInserts = false

    /** The type MediaStore keeps for each row, when it is not the one it was given. */
    @Volatile
    var keptMimeType: String? = null

    /** Called when a row's file is opened for writing. */
    @Volatile
    var onOpen: ((Uri) -> Unit)? = null

    override fun onCreate(): Boolean = true

    @Synchronized
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val given = ContentValues(values ?: ContentValues())
        insertLog += uri to ContentValues(given)
        if (refuseInserts) return null
        keptMimeType?.let { given.put(MediaStore.MediaColumns.MIME_TYPE, it) }
        val id = ++lastId
        val rowUri = ContentUris.withAppendedId(uri, id)
        rows[id] = Row(rowUri, given, File(directory, "row-$id"))
        return rowUri
    }

    @Synchronized
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val row = rows[ContentUris.parseId(uri)] ?: return 0
        values?.let(row.values::putAll)
        return 1
    }

    @Synchronized
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        deleteLog += uri
        val row = rows.remove(ContentUris.parseId(uri)) ?: return 0
        row.file.delete()
        return 1
    }

    @Synchronized
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE)
        val cursor = MatrixCursor(columns)
        rows[ContentUris.parseId(uri)]?.let { row -> cursor.addRow(columns.map { row.values.get(it) }) }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val row = synchronized(this) { rows[ContentUris.parseId(uri)] } ?: throw FileNotFoundException("No such row")
        onOpen?.invoke(uri)
        return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String? = null

    /** The rows that exist now, oldest first. */
    @Synchronized
    fun rows(): List<Row> = rows.values.toList()

    /** Every insert as it came, before MediaStore changed anything. */
    @Synchronized
    fun inserts(): List<Pair<Uri, ContentValues>> = insertLog.toList()

    @Synchronized
    fun deletes(): List<Uri> = deleteLog.toList()

    companion object {
        /** Registers a new store for the `media` authority, keeping its files in [directory]. */
        fun install(directory: File): FakeMediaStore =
            Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY).also { it.directory = directory }
    }
}
