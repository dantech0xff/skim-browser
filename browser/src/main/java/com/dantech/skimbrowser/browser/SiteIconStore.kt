package com.dantech.skimbrowser.browser

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.normalizeHost
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The icon each site sent, kept per host: the page's host in lower case without a leading `www.`.
 * Icons only come from pages the person opened (`WebChromeClient.onReceivedIcon`); this store never
 * asks the network for one. An icon larger than [MAX_ICON_PX] on its longer side is scaled down to
 * it, a smaller one is kept as it is.
 *
 * A normal tab's icon is a PNG file `<dir>/<host>.png` (dir is a cache directory), read lazily the
 * first time its host is observed, and kept in a least-recently-used memory cache of [cacheBytes].
 * An incognito tab's icon never reaches the disk: it lives in a memory cache of its own
 * ([incognitoCacheBytes]), only [observe] with `incognito = true` sees it, and [clearIncognito]
 * forgets it when the session ends.
 *
 * A cache pushing an icon out only drops this store's reference: a flow already handed out keeps
 * its value. A host that is not `[a-z0-9.-]`, 1 to 253 characters, is never stored, so no file is
 * named from anything else a page controls. File work runs on [ioDispatcher], one operation at a
 * time in the order of the calls.
 */
@Singleton
class SiteIconStore(
    private val dir: File,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    cacheBytes: Int = DEFAULT_CACHE_BYTES,
    incognitoCacheBytes: Int = DEFAULT_INCOGNITO_CACHE_BYTES,
) {

    @Inject constructor(
        storage: AppStorage,
        app: Application,
        @AppScope scope: CoroutineScope,
    ) : this(storage.siteIconDir(app), scope)

    /**
     * One host in one of the caches. A normal entry's [flow] shows its own [image]. An incognito
     * entry's [flow] shows its own [image], or the normal icon of the host while it has none.
     */
    private class Entry {
        val flow = MutableStateFlow<ImageBitmap?>(null)
        var image: ImageBitmap? = null
        var bytes = 0

        /** Bumped by every put, so a slower read from disk never replaces a newer icon. */
        var version = 0L
        var readRequested = false
    }

    private val writer = SerialWriter(scope, ioDispatcher, TAG)
    private val lock = Any()
    private val normalEntries = EntryCache(cacheBytes)
    private val incognitoEntries = EntryCache(incognitoCacheBytes)

    /**
     * Shows [icon] as the icon of [pageUrl]'s host and, for a normal tab, writes it to disk. The
     * store keeps [icon] when it is not scaled down: the caller must not recycle it.
     */
    @AnyThread
    fun put(pageUrl: String?, icon: Bitmap, incognito: Boolean) {
        val key = pageUrl?.let(::hostOf)?.takeIf(::isStorableKey) ?: return
        val scaled = scaledDown(icon)
        val image = scaled.asImageBitmap()
        var changed = true
        synchronized(lock) {
            if (incognito) {
                val entry = incognitoEntries.get(key) ?: Entry()
                entry.show(image, scaled.allocationByteCount)
                incognitoEntries.put(key, entry)
            } else {
                val entry = normalEntries.get(key) ?: Entry()
                changed = entry.image?.let { scaled.sameAs(it.asAndroidBitmap()) } != true
                entry.version++
                // A put is newer than the file, so the file is not read any more.
                entry.readRequested = true
                entry.show(image, scaled.allocationByteCount)
                normalEntries.put(key, entry)
                incognitoEntries.get(key)?.let { fallback -> if (fallback.image == null) fallback.flow.value = image }
            }
        }
        if (!incognito && changed) writer.post { writeImage(key, scaled) }
    }

    /**
     * The icon of [host]; null until there is one. A saved icon is read in the background. With
     * [incognito] an icon an incognito tab received comes first and the normal icon of the host
     * stands in for it; without, an incognito icon is never returned.
     */
    @AnyThread
    fun observe(host: String?, incognito: Boolean = false): StateFlow<ImageBitmap?> {
        val key = host?.let(::normalizeHost)?.takeIf(::isStorableKey) ?: return NONE
        var readVersion: Long? = null
        val entry: Entry
        val flow = synchronized(lock) {
            entry = normalEntries.get(key) ?: Entry().also { normalEntries.put(key, it) }
            if (!entry.readRequested) {
                entry.readRequested = true
                readVersion = entry.version
            }
            if (incognito) {
                val own = incognitoEntries.get(key) ?: Entry().also {
                    it.flow.value = entry.image
                    incognitoEntries.put(key, it)
                }
                own.flow
            } else {
                entry.flow
            }
        }
        readVersion?.let { version -> writer.post { readImage(key, entry, version) } }
        return flow.asStateFlow()
    }

    /** Forgets every icon, the incognito ones too, in memory and on disk. */
    @AnyThread
    fun clearAll() {
        synchronized(lock) {
            normalEntries.snapshot().values.forEach { it.flow.value = null }
            normalEntries.evictAll()
            incognitoEntries.snapshot().values.forEach { it.flow.value = null }
            incognitoEntries.evictAll()
        }
        writer.post { dir.listFiles()?.forEach { it.delete() } }
    }

    /** Forgets the icons incognito tabs received. The normal icons stay. */
    @AnyThread
    fun clearIncognito() {
        synchronized(lock) {
            val normals = normalEntries.snapshot()
            incognitoEntries.snapshot().forEach { (key, entry) ->
                entry.image = null
                entry.flow.value = normals[key]?.image
            }
            incognitoEntries.evictAll()
        }
    }

    /** Returns once the file work requested so far is done. */
    suspend fun awaitIdle() = writer.awaitIdle()

    private fun Entry.show(icon: ImageBitmap, size: Int) {
        image = icon
        bytes = size
        flow.value = icon
    }

    @WorkerThread
    private fun writeImage(key: String, bitmap: Bitmap) {
        if (!dir.isDirectory && !dir.mkdirs()) return
        val temp = File(dir, key + IMAGE_SUFFIX + TEMP_SUFFIX)
        val written = FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        if (!written || !temp.renameTo(File(dir, key + IMAGE_SUFFIX))) {
            Log.w(TAG, "Site icon not written")
            temp.delete()
        }
    }

    @WorkerThread
    private fun readImage(key: String, entry: Entry, version: Long) {
        val file = File(dir, key + IMAGE_SUFFIX)
        if (!file.isFile) return
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) {
            file.delete()
            return
        }
        synchronized(lock) {
            if (normalEntries.get(key) !== entry || entry.version != version || entry.image != null) return
            val image = bitmap.asImageBitmap()
            entry.show(image, bitmap.allocationByteCount)
            // Put again, so the cache counts the icon's bytes.
            normalEntries.put(key, entry)
            incognitoEntries.get(key)?.let { fallback -> if (fallback.image == null) fallback.flow.value = image }
        }
    }

    /** Entries by host, sized by the bytes of their icons; one without an icon counts one byte. */
    private class EntryCache(maxBytes: Int) : LruCache<String, Entry>(maxBytes) {
        override fun sizeOf(key: String, value: Entry): Int = max(value.bytes, 1)
    }

    companion object {
        /** The longest side of a stored icon, in pixels: the largest tile draws 40 dp at xxhdpi and more. */
        const val MAX_ICON_PX = 96

        private const val TAG = "SkimSiteIcons"
        private const val IMAGE_SUFFIX = ".png"
        private const val TEMP_SUFFIX = ".tmp"

        /** PNG is lossless; the platform ignores the quality. */
        private const val PNG_QUALITY = 100
        private const val DEFAULT_CACHE_BYTES = 4 * 1024 * 1024
        private const val DEFAULT_INCOGNITO_CACHE_BYTES = 1024 * 1024
        private const val MAX_KEY_LENGTH = 253
        private val STORABLE_KEY = Regex("[a-z0-9.-]+")
        private val NONE: StateFlow<ImageBitmap?> = MutableStateFlow<ImageBitmap?>(null).asStateFlow()

        private fun isStorableKey(key: String): Boolean = key.length in 1..MAX_KEY_LENGTH && STORABLE_KEY.matches(key)

        private fun scaledDown(icon: Bitmap): Bitmap {
            val longer = max(icon.width, icon.height)
            if (longer <= MAX_ICON_PX) return icon
            val scale = MAX_ICON_PX.toFloat() / longer
            val width = max(1, (icon.width * scale).roundToInt())
            val height = max(1, (icon.height * scale).roundToInt())
            return Bitmap.createScaledBitmap(icon, width, height, true)
        }
    }
}
