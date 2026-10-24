package com.dantech.skimbrowser.browser

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.util.AtomicFile
import androidx.webkit.WebViewCompat
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The saved WebView state (back history, scroll position, form data) of each tab, as a Bundle.
 *
 * A normal tab's state is the file `<dir>/<tabId>.bin`, written whole with [AtomicFile]: a header
 * (magic, format version, WebView version, `SDK_INT`, payload length, CRC32 of the payload)
 * followed by the Bundle's Parcel bytes. An incognito tab's state stays in memory and never
 * reaches the disk.
 *
 * Parcel bytes are only readable by the Android and WebView build that wrote them, so a header from
 * another build, a wrong checksum, a cut file or a Parcel that does not read back all mean "no
 * state": the file is deleted and [load] returns null; it never throws. File work runs on
 * [ioDispatcher], one operation at a time in the order of the calls.
 */
class TabStateStore(
    private val dir: File,
    private val webViewVersion: () -> String,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    private val writer = SerialWriter(scope, ioDispatcher, TAG)
    private val memory = ConcurrentHashMap<String, ByteArray>()

    /** Read once: an updated WebView restarts the app's process. */
    private val currentWebViewVersion: String by lazy {
        try {
            webViewVersion().cutTo(MAX_VERSION_LENGTH)
        } catch (e: RuntimeException) {
            Log.w(TAG, "WebView version unavailable", e)
            ""
        }
    }

    /**
     * Saves [state] for [tabId]: in memory for an [incognito] tab, else to its file. The Bundle is
     * copied before this returns, so the caller may reuse it.
     */
    @AnyThread
    fun save(tabId: String, state: Bundle, incognito: Boolean) {
        if (!isStorableTabId(tabId)) return
        val payload = marshall(state) ?: return
        if (payload.size > MAX_PAYLOAD_BYTES) {
            Log.w(TAG, "Tab state of ${payload.size} bytes not saved")
            return
        }
        if (incognito) {
            memory[tabId] = payload
        } else {
            writer.post { write(tabId, payload) }
        }
    }

    /**
     * The state saved for [tabId], or null when there is none or it cannot be read back (the file is
     * deleted then). Waits for the saves and deletions requested before it.
     */
    @AnyThread
    suspend fun load(tabId: String, incognito: Boolean): Bundle? {
        if (!isStorableTabId(tabId)) return null
        if (incognito) {
            val payload = memory[tabId] ?: return null
            return unmarshallOrNull(payload) ?: run {
                memory.remove(tabId, payload)
                null
            }
        }
        return writer.run { read(tabId) }
    }

    /** Forgets the state of [tabId], in memory and on disk. */
    @AnyThread
    fun delete(tabId: String) {
        memory.remove(tabId)
        if (isStorableTabId(tabId)) writer.post { AtomicFile(fileOf(tabId)).delete() }
    }

    /** Forgets every state but those of [keep], files left by an interrupted write included. */
    @AnyThread
    fun purgeExcept(keep: Set<String>) {
        memory.keys.retainAll(keep)
        writer.post { purgeFiles(keep) }
    }

    /** Returns once the file work requested so far is done. */
    internal suspend fun awaitIdle() = writer.awaitIdle()

    private fun fileOf(tabId: String) = File(dir, tabId + STATE_SUFFIX)

    @WorkerThread
    private fun write(tabId: String, payload: ByteArray) {
        val crc = CRC32().apply { update(payload) }.value
        val bytes = ByteArrayOutputStream(payload.size + HEADER_ALLOWANCE).also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(FORMAT_VERSION)
                out.writeUTF(currentWebViewVersion)
                out.writeInt(sdkInt)
                out.writeInt(payload.size)
                out.writeLong(crc)
                out.write(payload)
            }
        }.toByteArray()
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w(TAG, "No directory for tab state")
            return
        }
        val file = AtomicFile(fileOf(tabId))
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    @WorkerThread
    private fun read(tabId: String): Bundle? {
        val file = AtomicFile(fileOf(tabId))
        val payload = try {
            file.openRead().use { stream ->
                if (file.baseFile.length() > MAX_PAYLOAD_BYTES + HEADER_ALLOWANCE) throw StateFileException("too large")
                val input = DataInputStream(stream.buffered())
                if (input.readInt() != MAGIC) throw StateFileException("not a tab state file")
                if (input.readInt() != FORMAT_VERSION) throw StateFileException("another format")
                if (input.readUTF() != currentWebViewVersion) throw StateFileException("another WebView")
                if (input.readInt() != sdkInt) throw StateFileException("another Android version")
                val size = input.readInt()
                if (size !in 0..MAX_PAYLOAD_BYTES) throw StateFileException("bad length")
                val crc = input.readLong()
                ByteArray(size).also { bytes ->
                    input.readFully(bytes)
                    if (input.read() != -1) throw StateFileException("trailing bytes")
                    if (CRC32().apply { update(bytes) }.value != crc) throw StateFileException("bad checksum")
                }
            }
        } catch (_: FileNotFoundException) {
            return null
        } catch (e: Throwable) {
            discard(file, e)
            return null
        }
        return unmarshallOrNull(payload) ?: run {
            discard(file, null)
            null
        }
    }

    @WorkerThread
    private fun discard(file: AtomicFile, cause: Throwable?) {
        Log.w(TAG, "Unreadable tab state deleted: ${cause?.message ?: "bad Parcel"}")
        file.delete()
    }

    @WorkerThread
    private fun purgeFiles(keep: Set<String>) {
        dir.listFiles()?.forEach { file ->
            val base = file.name.removeSuffix(BACKUP_SUFFIX).removeSuffix(NEW_SUFFIX)
            val tabId = base.removeSuffix(STATE_SUFFIX).takeIf { base.endsWith(STATE_SUFFIX) }
            if (tabId == null || tabId !in keep) file.delete()
        }
    }

    private class StateFileException(message: String) : Exception(message)

    companion object {
        private const val TAG = "SkimTabState"

        /** "SKTS". */
        private const val MAGIC = 0x534B5453
        private const val FORMAT_VERSION = 1

        /** WebView keeps a state near the 256 KB it is asked for; anything far larger is not a tab state. */
        private const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
        private const val MAX_VERSION_LENGTH = 200
        private const val HEADER_ALLOWANCE = 1024
        private const val STATE_SUFFIX = ".bin"
        private const val BACKUP_SUFFIX = ".bak"
        private const val NEW_SUFFIX = ".new"

        /** The WebView package and version, which a saved state must match to be read back. */
        fun webViewVersionOf(context: Context): String =
            WebViewCompat.getCurrentWebViewPackage(context)
                ?.let { "${it.packageName}/${it.versionName.orEmpty()}" }
                .orEmpty()

        private fun marshall(state: Bundle): ByteArray? {
            val parcel = Parcel.obtain()
            return try {
                parcel.writeBundle(state)
                parcel.marshall()
            } catch (e: RuntimeException) {
                // A Bundle holding binders or file descriptors cannot be flattened.
                Log.w(TAG, "Tab state not flattened", e)
                null
            } finally {
                parcel.recycle()
            }
        }

        /** The Bundle in [payload], fully read so a broken Parcel fails here and not later. */
        private fun unmarshallOrNull(payload: ByteArray): Bundle? {
            val parcel = Parcel.obtain()
            return try {
                parcel.unmarshall(payload, 0, payload.size)
                parcel.setDataPosition(0)
                parcel.readBundle(TabStateStore::class.java.classLoader)?.also { it.size() }
            } catch (_: Throwable) {
                // Parcel reads of foreign bytes fail with anything from BadParcelableException to
                // OutOfMemoryError; none of it may crash a tab restore.
                null
            } finally {
                parcel.recycle()
            }
        }
    }
}

/** Tab ids name files: only the characters of a UUID, so no id can reach outside its directory. */
internal fun isStorableTabId(tabId: String): Boolean = STORABLE_TAB_ID.matches(tabId)

private val STORABLE_TAB_ID = Regex("[A-Za-z0-9_-]{1,64}")
