package com.dantech.skimbrowser.data.importer

import android.app.Application
import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.dantech.skimbrowser.domain.text.cutTo
import com.dantech.skimbrowser.domain.text.toVisibleText
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.domain.bookmark.BookmarkFiles
import com.dantech.skimbrowser.domain.bookmark.ImportResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports the bookmarks file a user picked (03): read once through [resolver] (at most
 * [MAX_FILE_BYTES]) on [ioDispatcher], parsed on [parseDispatcher], then saved as a new Imported
 * folder named after the file. Nothing about the file (name, links, titles) is ever logged.
 */
@Singleton
class BookmarkImporter(
    private val resolver: ContentResolver,
    private val bookmarks: BookmarkRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BookmarkFiles {

    @Inject constructor(app: Application, bookmarks: BookmarkRepository) : this(app.contentResolver, bookmarks)

    override suspend fun import(location: String): ImportResult = import(Uri.parse(location))


    /**
     * Imports the file [uri]; a file that cannot be read or holds no bookmark imports nothing and
     * says why. The file comes from another app's document provider, which can fail in ways nobody
     * listed, so no failure leaves this function except the cancellation of the caller.
     */
    suspend fun import(uri: Uri): ImportResult = try {
        importFile(uri)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w(TAG, "Bookmarks could not be imported: ${e.javaClass.simpleName}")
        ImportResult.Failed(ImportResult.Reason.Unreadable)
    }

    private suspend fun importFile(uri: Uri): ImportResult {
        val file = withContext(ioDispatcher) { read(uri) }
        val bytes = when (file) {
            is FileRead.Bytes -> file.bytes
            FileRead.TooLarge -> return ImportResult.Failed(ImportResult.Reason.TooLarge)
            FileRead.Unreadable -> return ImportResult.Failed(ImportResult.Reason.Unreadable)
        }
        if (withContext(parseDispatcher) { NetscapeBookmarkParser.hasTooManyTags(bytes) }) {
            return ImportResult.Failed(ImportResult.Reason.TooLarge)
        }
        val nodes = try {
            withContext(parseDispatcher) { NetscapeBookmarkParser.parse(bytes) }
        } catch (e: IOException) {
            Log.w(TAG, "Bookmarks file could not be decoded: ${e.javaClass.simpleName}")
            return ImportResult.Failed(ImportResult.Reason.Unreadable)
        } ?: return ImportResult.Failed(ImportResult.Reason.NotBookmarks)
        val name = withContext(ioDispatcher) { displayName(uri) }
        val outcome = try {
            bookmarks.importTree(name, nodes)
        } catch (e: SQLiteException) {
            Log.w(TAG, "Imported bookmarks could not be saved: ${e.javaClass.simpleName}")
            return ImportResult.Failed(ImportResult.Reason.Unreadable)
        } ?: return ImportResult.Failed(ImportResult.Reason.NoBookmarks)
        return ImportResult.Imported(outcome.folderId, outcome.bookmarkCount)
    }

    private sealed interface FileRead {
        class Bytes(val bytes: ByteArray) : FileRead

        data object TooLarge : FileRead

        data object Unreadable : FileRead
    }

    private fun read(uri: Uri): FileRead = try {
        resolver.openInputStream(uri)?.use { stream -> stream.readAtMost(MAX_FILE_BYTES)?.let(FileRead::Bytes) ?: FileRead.TooLarge }
            ?: FileRead.Unreadable
    } catch (e: IOException) {
        Log.w(TAG, "Bookmarks file could not be read: ${e.javaClass.simpleName}")
        FileRead.Unreadable
    } catch (e: SecurityException) {
        Log.w(TAG, "Bookmarks file is not readable: ${e.javaClass.simpleName}")
        FileRead.Unreadable
    }

    /**
     * The name the picker showed for the file, as plain visible text; the last segment of [uri]
     * when the provider does not say, and empty when that is missing too.
     */
    private fun displayName(uri: Uri): String {
        val shown = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bookmarks file has no readable name: ${e.javaClass.simpleName}")
            null
        }
        val name = shown ?: uri.lastPathSegment?.substringAfterLast('/')
        return name.orEmpty().toVisibleText().cutTo(MAX_NAME_LENGTH)
    }

    companion object {
        /** The largest file read: 10 MB holds tens of thousands of bookmarks. */
        const val MAX_FILE_BYTES = 10 * 1024 * 1024

        private const val MAX_NAME_LENGTH = 255
        private const val TAG = "SkimBookmarkImport"
    }
}

/** Every byte of the stream, or null as soon as there are more than [limit]. */
private fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return out.toByteArray()
        total += count
        if (total > limit) return null
        out.write(buffer, 0, count)
    }
}

private const val BUFFER_SIZE = 64 * 1024
