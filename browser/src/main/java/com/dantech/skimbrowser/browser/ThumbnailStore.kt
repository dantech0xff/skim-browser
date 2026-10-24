package com.dantech.skimbrowser.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The preview image of each tab card (09, 09b).
 *
 * A normal tab's thumbnail is a WebP file `<dir>/<tabId>.webp` (dir is a cache directory), read
 * back lazily when first observed after process death; once read or put it stays in memory until
 * the tab is deleted. An incognito tab's thumbnail never reaches the disk: it lives in a
 * least-recently-used memory cache of [incognitoCacheBytes], and one pushed out of it is gone.
 * File work runs on [ioDispatcher], one operation at a time in the order of the calls.
 */
class ThumbnailStore(
    private val dir: File,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    incognitoCacheBytes: Int = DEFAULT_INCOGNITO_CACHE_BYTES,
) {
    private class Entry {
        val flow = MutableStateFlow<ImageBitmap?>(null)

        /** Bumped by every put, so a slower read from disk never replaces a newer image. */
        var version = 0L
        var readRequested = false
        var incognito = false
    }

    private val writer = SerialWriter(scope, ioDispatcher, TAG)
    private val lock = Any()
    private val entries = HashMap<String, Entry>()

    private val incognitoImages = object : LruCache<String, Bitmap>(incognitoCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) synchronized(lock) { entries[key]?.flow?.value = null }
        }
    }

    /**
     * Shows [bitmap] as the thumbnail of [tabId] and, for a normal tab, writes it to disk. The store
     * keeps the bitmap: the caller must not recycle it.
     */
    @AnyThread
    fun put(tabId: String, bitmap: Bitmap, incognito: Boolean) {
        if (!isStorableTabId(tabId)) return
        synchronized(lock) {
            val entry = entries.getOrPut(tabId) { Entry() }
            entry.version++
            entry.incognito = incognito
            entry.flow.value = bitmap.asImageBitmap()
        }
        if (incognito) {
            incognitoImages.put(tabId, bitmap)
        } else {
            writer.post { writeImage(tabId, bitmap) }
        }
    }

    /** The thumbnail of [tabId]; null until there is one. A saved one is read in the background. */
    @AnyThread
    fun observe(tabId: String): StateFlow<ImageBitmap?> {
        if (!isStorableTabId(tabId)) return MutableStateFlow<ImageBitmap?>(null).asStateFlow()
        var readVersion: Long? = null
        var incognito = false
        val entry = synchronized(lock) {
            entries.getOrPut(tabId) { Entry() }.also { entry ->
                incognito = entry.incognito
                if (entry.flow.value == null && !entry.readRequested && !entry.incognito) {
                    entry.readRequested = true
                    readVersion = entry.version
                }
            }
        }
        // A card that shows an incognito thumbnail keeps it among the most recently used.
        if (incognito) incognitoImages.get(tabId)
        readVersion?.let { version -> writer.post { readImage(tabId, entry, version) } }
        return entry.flow.asStateFlow()
    }

    /** Forgets the thumbnail of [tabId], in memory and on disk. */
    @AnyThread
    fun delete(tabId: String) {
        if (!isStorableTabId(tabId)) return
        forget(setOf(tabId))
        writer.post { File(dir, tabId + IMAGE_SUFFIX).delete() }
    }

    /** Forgets every thumbnail. */
    @AnyThread
    fun clearAll() {
        forget(synchronized(lock) { entries.keys.toSet() })
        incognitoImages.evictAll()
        writer.post { dir.listFiles()?.forEach { it.delete() } }
    }

    /** Forgets every thumbnail but those of [keep], unfinished writes included. */
    @AnyThread
    fun purgeExcept(keep: Set<String>) {
        forget(synchronized(lock) { entries.keys.filterNot { it in keep }.toSet() })
        writer.post {
            dir.listFiles()?.forEach { file ->
                val tabId = file.name.removeSuffix(IMAGE_SUFFIX).takeIf { file.name.endsWith(IMAGE_SUFFIX) }
                if (tabId == null || tabId !in keep) file.delete()
            }
        }
    }

    /** Returns once the file work requested so far is done. */
    suspend fun awaitIdle() = writer.awaitIdle()

    private fun forget(tabIds: Set<String>) {
        synchronized(lock) {
            tabIds.forEach { tabId -> entries.remove(tabId)?.flow?.value = null }
        }
        tabIds.forEach { incognitoImages.remove(it) }
    }

    @WorkerThread
    private fun writeImage(tabId: String, bitmap: Bitmap) {
        if (!dir.isDirectory && !dir.mkdirs()) return
        val temp = File(dir, tabId + IMAGE_SUFFIX + TEMP_SUFFIX)
        val written = FileOutputStream(temp).use { bitmap.compress(WEBP, WEBP_QUALITY, it) }
        if (!written || !temp.renameTo(File(dir, tabId + IMAGE_SUFFIX))) {
            Log.w(TAG, "Thumbnail not written")
            temp.delete()
        }
    }

    @WorkerThread
    private fun readImage(tabId: String, entry: Entry, version: Long) {
        val file = File(dir, tabId + IMAGE_SUFFIX)
        if (!file.isFile) return
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) {
            file.delete()
            return
        }
        synchronized(lock) {
            if (entries[tabId] === entry && entry.version == version && entry.flow.value == null) {
                entry.flow.value = bitmap.asImageBitmap()
            }
        }
    }

    private companion object {
        const val TAG = "SkimThumbnails"
        const val IMAGE_SUFFIX = ".webp"
        const val TEMP_SUFFIX = ".tmp"
        const val WEBP_QUALITY = 80
        const val DEFAULT_INCOGNITO_CACHE_BYTES = 16 * 1024 * 1024

        val WEBP: Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                // Before Android 11 this is the lossy WebP format for any quality below 100.
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
    }
}
