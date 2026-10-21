package com.dantech.skimbrowser.browser

import android.Manifest
import android.app.DownloadManager
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.skimbrowser.browser.downloads.MediaStoreDownloads
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.rules.ExternalResource

/**
 * What downloads leave on the device: the app's DownloadManager downloads (rows, files and
 * notifications) and the files it saved itself in the shared Downloads folder.
 *
 * From Android 10 those files are rows of MediaStore's Downloads collection, and DownloadManager
 * and MediaStore only show an app its own. Before that Skim writes each file into the public
 * Downloads folder itself and lists it in MediaStore's Files collection, which holds every app's
 * files: there a file counts as the app's when its row was made during the test, for a file in
 * that folder. Nothing of another app is touched either way.
 *
 * As a rule it gives the app Android's storage permission where saving to the shared Downloads
 * folder needs one (before Android 10), removes every download before and after the test, then
 * waits until the shared Downloads folder holds exactly the files it held before the test: a test
 * leaves nothing behind.
 */
class DeviceDownloads : ExternalResource() {

    /** One of the app's DownloadManager downloads. */
    data class SystemDownload(
        val id: Long,
        val url: String?,
        val title: String?,
        val status: Int,
        val localUri: String?,
        val mediaType: String?,
    )

    /** A file the app saved in the shared Downloads folder, as MediaStore lists it. */
    data class SavedFile(val uri: Uri, val name: String?, val mime: String?, val pending: Boolean, val size: Long, val relativePath: String?)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: DownloadManager = checkNotNull(context.getSystemService(DownloadManager::class.java)) { "No DownloadManager" }
    private var folderBefore: List<String> = emptyList()

    /** MediaStore's Downloads collection, where the app's own files are listed, arrived with Android 10. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private fun scoped(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Before Android 10, the newest row of MediaStore's Files collection when the test started; later rows are the test's. */
    private var newestRowBefore = Long.MAX_VALUE

    override fun before() {
        grantStorageWhereNeeded()
        removeAll()
        folderBefore = downloadFolder()
        if (!scoped()) newestRowBefore = newestFileRow()
    }

    override fun after() {
        removeAll()
        waitUntil("the shared Downloads folder to hold only what it held before the test") { downloadFolder() == folderBefore }
    }

    /** Every DownloadManager download of the app, oldest first. */
    fun systemDownloads(): List<SystemDownload> = manager.query(DownloadManager.Query())?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
        val uri = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
        val title = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
        val status = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
        val local = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
        val type = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)
        buildList {
            while (cursor.moveToNext()) {
                add(SystemDownload(cursor.getLong(id), cursor.getString(uri), cursor.getString(title), cursor.getInt(status), cursor.getString(local), cursor.getString(type)))
            }
        }.sortedBy { it.id }
    }.orEmpty()

    /** The app's DownloadManager downloads of [url]. */
    fun systemDownloadsOf(url: String): List<SystemDownload> = systemDownloads().filter { it.url == url }

    /** Waits until DownloadManager finished its one download of [url], successfully or not, and returns it. */
    fun awaitSystemDownload(url: String, timeoutMs: Long = DOWNLOAD_TIMEOUT_MS): SystemDownload {
        var found: SystemDownload? = null
        waitUntil("DownloadManager to finish its download", timeoutMs) {
            found = systemDownloadsOf(url).singleOrNull()?.takeIf { it.status == DownloadManager.STATUS_SUCCESSFUL || it.status == DownloadManager.STATUS_FAILED }
            found != null
        }
        return checkNotNull(found)
    }

    /**
     * The files the app saved in the shared Downloads folder, oldest first. From Android 11 the
     * pending ones (still being written) are listed too; before Android 10 a file is never pending,
     * and its relative path is its folder's under the shared storage.
     */
    fun savedFiles(): List<SavedFile> = if (scoped()) scopedSavedFiles() else legacySavedFiles().map { it.first }

    /**
     * Counts MediaStore's notices of changes in the collection the app's saved files are listed in
     * (a row made, changed or deleted).
     */
    inner class SavedFileChanges : AutoCloseable {
        private val seen = AtomicInteger()
        private val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                seen.incrementAndGet()
            }
        }

        init {
            val collection = if (scoped()) MediaStore.Downloads.EXTERNAL_CONTENT_URI else filesCollection()
            context.contentResolver.registerContentObserver(collection, true, observer)
        }

        val count: Int get() = seen.get()

        override fun close() = context.contentResolver.unregisterContentObserver(observer)
    }

    /** The bytes behind a content [uri] the app may read: a saved file or a finished DownloadManager download. */
    fun read(uri: Uri): ByteArray = checkNotNull(context.contentResolver.openInputStream(uri)) { "Nothing to read" }.use { it.readBytes() }

    /** The names in the shared Downloads folder, as the shell sees them (every app's files). */
    fun downloadFolder(): List<String> = shell("ls -1 /sdcard/Download").lines().map { it.trim() }.filter { it.isNotEmpty() }.sorted()

    /** Removes the app's DownloadManager downloads, then the files it saved itself. */
    fun removeAll() {
        val ids = systemDownloads().map { it.id }
        if (ids.isNotEmpty()) manager.remove(*ids.toLongArray())
        if (scoped()) {
            scopedSavedFiles().forEach { context.contentResolver.delete(it.uri, null, null) }
        } else {
            // The file first, then its row: the row going is not relied on to take the file with it.
            legacySavedFiles().forEach { (saved, path) ->
                val file = File(path)
                if (!file.delete() && file.exists()) shell("rm -f \"$path\"")
                context.contentResolver.delete(saved.uri, null, null)
            }
        }
    }

    /**
     * Where saving to the shared Downloads folder needs Android's storage permission (before
     * Android 10), gives it to the app as the user does when Skim asks: Android's prompt for
     * writing also grants reading, which an app asking for writing holds implicitly, and without
     * which MediaStore lists and opens nothing for it.
     */
    private fun grantStorageWhereNeeded() {
        // The manifest only asks for the storage permission before Android 10 (maxSdkVersion).
        @Suppress("DEPRECATION") // The flags overload is the one every supported version has.
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        if (Manifest.permission.WRITE_EXTERNAL_STORAGE !in requested) return
        val missing = STORAGE_PERMISSIONS.filter { it in requested && context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return
        val automation = shellAutomation()
        missing.forEach { permission -> automation.grantRuntimePermission(context.packageName, permission) }
        val folder = publicDownloads()
        waitUntil("the storage permission to reach the app") {
            !MediaStoreDownloads(context).needsStoragePermission &&
                missing.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } &&
                (folder.canWrite() || !folder.exists())
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun scopedSavedFiles(): List<SavedFile> {
        val columns = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.IS_PENDING,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.RELATIVE_PATH,
        )
        val args = Bundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return context.contentResolver.query(collection, columns, args, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SavedFile(
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(0)),
                            name = cursor.getString(1),
                            mime = cursor.getString(2),
                            pending = cursor.getInt(3) != 0,
                            size = cursor.getLong(4),
                            relativePath = cursor.getString(5),
                        ),
                    )
                }
            }
        }.orEmpty().sortedBy { ContentUris.parseId(it.uri) }
    }

    /**
     * Before Android 10: the rows MediaStore's Files collection got during the test for files in
     * the public Downloads folder, with each file's path, oldest first. None before the test began.
     */
    private fun legacySavedFiles(): List<Pair<SavedFile, String>> {
        if (newestRowBefore == Long.MAX_VALUE) return emptyList()
        val columns = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            LEGACY_PATH_COLUMN,
        )
        val folder = publicDownloads()
        val selection = "${MediaStore.Files.FileColumns._ID} > $newestRowBefore AND $LEGACY_PATH_COLUMN LIKE ?"
        val collection = filesCollection()
        return context.contentResolver.query(collection, columns, selection, arrayOf(folder.path + "/%"), null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val path = cursor.getString(4)
                    val saved = SavedFile(
                        uri = ContentUris.withAppendedId(collection, cursor.getLong(0)),
                        name = cursor.getString(1) ?: File(path).name,
                        mime = cursor.getString(2),
                        pending = false,
                        size = cursor.getLong(3),
                        relativePath = relativeFolderOf(path),
                    )
                    add(saved to path)
                }
            }
        }.orEmpty().sortedBy { ContentUris.parseId(it.first.uri) }
    }

    /** The newest row of MediaStore's Files collection; 0 when it has none. */
    private fun newestFileRow(): Long {
        val id = MediaStore.Files.FileColumns._ID
        return context.contentResolver.query(filesCollection(), arrayOf(id), null, null, "$id DESC")?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    }

    /** [path]'s folder relative to the shared storage, as MediaStore's RELATIVE_PATH writes it ("Download/"). */
    private fun relativeFolderOf(path: String): String? {
        @Suppress("DEPRECATION") // The shared storage's root, as paths before scoped storage start with it.
        val root = Environment.getExternalStorageDirectory().path
        val folder = File(path).parent ?: return null
        return if (folder.startsWith("$root/")) folder.removePrefix("$root/") + "/" else null
    }

    private fun filesCollection(): Uri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)

    private companion object {
        /** DownloadManager schedules its work as a job; on a busy emulator it can take a while to start. */
        const val DOWNLOAD_TIMEOUT_MS = 30_000L
        const val EXTERNAL_VOLUME = "external"

        /** Writing the shared Downloads folder before scoped storage, and reading it, which comes with writing. */
        val STORAGE_PERMISSIONS = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)

        /** A file's path, by which MediaStore lists a file before scoped storage; deprecated from Android 10 only. */
        @Suppress("DEPRECATION")
        const val LEGACY_PATH_COLUMN = MediaStore.MediaColumns.DATA

        @Suppress("DEPRECATION") // The shared Downloads folder, which Skim writes into directly before scoped storage.
        fun publicDownloads(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }
}
