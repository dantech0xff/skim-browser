package com.dantech.skimbrowser.browser.downloads

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves the downloads Skim writes itself (those of incognito tabs and of `data:` and `blob:` URLs)
 * into the shared Downloads folder, where the Files app and other apps find them.
 *
 * From Android 10 (API 29) the file is a `MediaStore.Downloads` row in `Download/`, kept pending
 * (hidden from other apps) while it is written and published once complete. Before that (API
 * 26–28) the file is written into the public Downloads folder, which needs
 * `WRITE_EXTERNAL_STORAGE` ([needsStoragePermission]), under a name no file there has yet
 * (`report (1).pdf`…), then listed in MediaStore so that other apps can open it through a content
 * URI.
 *
 * A save that fails or is cancelled leaves nothing behind: the row or file being written is
 * deleted. The address the file came from is never stored with it.
 */
class MediaStoreDownloads(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    /** The public Downloads folder, written directly before scoped storage. */
    private val legacyFolder: () -> File = ::publicDownloadsFolder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** True when saving needs `WRITE_EXTERNAL_STORAGE` first and Skim does not hold it (API 26–28). */
    val needsStoragePermission: Boolean
        get() = !hasScopedStorage() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    /**
     * Saves a file named [fileName] (already made safe) of type [mimeType], whose bytes [write]
     * puts into the stream it is given. Returns the saved file as MediaStore lists it: its name
     * (MediaStore may change it to keep names apart), its content URI and the type MediaStore
     * keeps for it.
     *
     * @throws IOException when the file cannot be saved; nothing is left behind.
     */
    suspend fun save(fileName: String, mimeType: String, write: suspend (OutputStream) -> Unit): DownloadEvent.DownloadCompleted =
        withContext(ioDispatcher) {
            if (fileName.isEmpty() || fileName.contains('/') || fileName == "." || fileName == "..") {
                throw IOException("Not a file name")
            }
            if (hasScopedStorage()) saveScoped(fileName, mimeType, write) else saveLegacy(fileName, mimeType, write)
        }

    /**
     * Bytes free on the volume the Downloads folder is on; [Long.MAX_VALUE] when that cannot be
     * read, so a save goes ahead and fails on its own if the volume is full.
     */
    fun freeBytes(): Long = try {
        // The app's own folder on shared storage is on the same volume as Downloads.
        val folder = if (hasScopedStorage()) context.getExternalFilesDir(null) else legacyFolder()
        // A folder not made yet is on the volume of its nearest parent that exists.
        val existing = generateSequence(folder) { it.parentFile }.firstOrNull(File::exists)
        if (existing == null) Long.MAX_VALUE else StatFs(existing.path).availableBytes
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "The free space could not be read: ${e.javaClass.simpleName}")
        Long.MAX_VALUE
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private fun hasScopedStorage(): Boolean = sdkInt >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveScoped(fileName: String, mimeType: String, write: suspend (OutputStream) -> Unit): DownloadEvent.DownloadCompleted {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            // Hidden from other apps until it is complete.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore did not take the download")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("The download could not be opened for writing")
            out.use { write(it) }
            val published = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            if (resolver.update(uri, published, null, null) < 1) throw IOException("The download could not be published")
        } catch (failure: Throwable) {
            discard(uri)
            throw failure
        }
        return listed(uri, fileName, mimeType)
    }

    private suspend fun saveLegacy(fileName: String, mimeType: String, write: suspend (OutputStream) -> Unit): DownloadEvent.DownloadCompleted {
        val folder = legacyFolder()
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("There is no Downloads folder")
        val file = newFile(folder, fileName)
        val uri = try {
            FileOutputStream(file).use { write(it) }
            listInMediaStore(file, mimeType)
        } catch (failure: Throwable) {
            if (!file.delete()) Log.w(TAG, "A download that failed could not be deleted")
            throw failure
        }
        return listed(uri, file.name, mimeType)
    }

    /** A new empty file in [folder] named [fileName], or `name (1).ext`, `name (2).ext`… when that is taken. */
    private fun newFile(folder: File, fileName: String): File {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""
        for (copy in 0..MAX_COPIES) {
            val candidate = File(folder, if (copy == 0) fileName else "$base ($copy)$extension")
            if (candidate.createNewFile()) return candidate
        }
        throw IOException("Every name for the download is taken")
    }

    /** Lists [file] in MediaStore, which gives it the content URI other apps open it through. */
    private fun listInMediaStore(file: File, mimeType: String): Uri {
        val values = ContentValues().apply {
            // Before scoped storage a file is listed by its path; DATA is deprecated from API 29 only.
            @Suppress("DEPRECATION")
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.TITLE, file.nameWithoutExtension)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.SIZE, file.length())
        }
        return context.contentResolver.insert(MediaStore.Files.getContentUri(EXTERNAL_VOLUME), values)
            ?: throw IOException("MediaStore did not list the download")
    }

    /** The saved file as MediaStore lists it; what Skim asked for where MediaStore says nothing. */
    private fun listed(uri: Uri, fileName: String, mimeType: String): DownloadEvent.DownloadCompleted {
        val columns = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE)
        val stored = try {
            context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "A saved download could not be read back: ${e.javaClass.simpleName}")
            null
        }
        return DownloadEvent.DownloadCompleted(
            fileName = stored?.first?.takeIf { it.isNotBlank() } ?: fileName,
            uri = uri,
            mime = stored?.second?.takeIf { it.isNotBlank() } ?: mimeType,
        )
    }

    private fun discard(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "A download that failed could not be deleted: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "SkimDownloads"
        const val EXTERNAL_VOLUME = "external"
        const val MAX_COPIES = 999
    }
}

/** The shared Downloads folder; only written directly before scoped storage (API 29). */
@Suppress("DEPRECATION")
private fun publicDownloadsFolder(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
