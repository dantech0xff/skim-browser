package com.dantech.skimbrowser.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import org.robolectric.Robolectric

/**
 * A document provider for tests, standing for the system file picker's: each document is a real
 * file, read through the real ContentResolver, with the display name the picker would show (or
 * none). Register it once per test with [TestDocuments.register].
 */
class TestDocuments : ContentProvider() {

    private class Document(val file: File, val displayName: String?)

    private val documents = HashMap<String, Document>()
    private val failures = HashMap<String, RuntimeException>()

    /** Serves [file] as a document named [displayName] (no name when null) and returns its URI. */
    fun add(id: String, file: File, displayName: String?): Uri {
        documents[id] = Document(file, displayName)
        return uriOf(id)
    }

    /** A document that fails with [failure] when it is opened, as the provider of another app can. */
    fun addFailing(id: String, failure: RuntimeException): Uri {
        failures[id] = failure
        return uriOf(id)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val document = documents[uri.lastPathSegment] ?: return null
        val name = document.displayName ?: return MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf(document.file.length())) }
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(name, document.file.length()))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        failures[uri.lastPathSegment]?.let { throw it }
        val document = documents[uri.lastPathSegment] ?: throw FileNotFoundException("No such document")
        return ParcelFileDescriptor.open(document.file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "text/html"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val AUTHORITY = "com.dantech.skimbrowser.test.documents"

        /** The URI of document [id], whether or not it exists. */
        fun uriOf(id: String): Uri = Uri.parse("content://$AUTHORITY/document/$id")

        /** Registers a fresh provider with the test's ContentResolver. */
        fun register(): TestDocuments = Robolectric.buildContentProvider(TestDocuments::class.java).create(AUTHORITY).get()
    }
}
