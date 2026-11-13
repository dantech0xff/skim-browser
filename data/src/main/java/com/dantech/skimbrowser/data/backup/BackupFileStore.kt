package com.dantech.skimbrowser.data.backup

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.dantech.skimbrowser.domain.backup.BackupCodec
import com.dantech.skimbrowser.domain.backup.BackupDecode
import com.dantech.skimbrowser.domain.backup.BackupDocument
import com.dantech.skimbrowser.domain.backup.BackupFailure
import com.dantech.skimbrowser.domain.backup.BackupFile
import com.dantech.skimbrowser.domain.backup.BackupFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the file the person picked. JSON is parsed from the stream and reading stops
 * at [BackupCodec.MAX_BYTES]. A Netscape bookmark file is recognized and left for the bookmark
 * importer, which applies the same cap.
 */
@Singleton
class BackupFileStore(
    private val resolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupFiles {

    @Inject constructor(app: Application) : this(app.contentResolver)

    override suspend fun read(location: String): BackupFile = withContext(ioDispatcher) {
        try {
            resolver.openInputStream(Uri.parse(location))?.use { readStream(it) } ?: BackupFile.Unreadable
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            Log.w(TAG, "Backup could not be read: ${error.javaClass.simpleName}")
            BackupFile.Unreadable
        } catch (error: IOException) {
            Log.w(TAG, "Backup could not be read: ${error.javaClass.simpleName}")
            BackupFile.Unreadable
        }
    }

    override suspend fun write(location: String, document: BackupDocument): Boolean = withContext(ioDispatcher) {
        val bytes = BackupCodec.encode(document).toByteArray(Charsets.UTF_8)
        if (bytes.size > BackupCodec.MAX_BYTES) return@withContext false
        try {
            resolver.openOutputStream(Uri.parse(location))?.use { stream -> stream.write(bytes) } != null
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            Log.w(TAG, "Backup could not be written: ${error.javaClass.simpleName}")
            false
        } catch (error: IOException) {
            Log.w(TAG, "Backup could not be written: ${error.javaClass.simpleName}")
            false
        }
    }

    private fun readStream(stream: InputStream): BackupFile {
        val prefix = ByteArrayOutputStream()
        val one = ByteArray(1)
        var kind = BackupCodec.Sniff.OTHER
        var decided = false
        while (prefix.size() < SNIFF_LIMIT) {
            val count = stream.read(one)
            if (count < 0) break
            prefix.write(one[0].toInt())
            val sniffed = BackupCodec.sniff(prefix.toByteArray())
            if (sniffed != BackupCodec.Sniff.OTHER) {
                kind = sniffed
                decided = true
                break
            }
        }
        if (!decided) return BackupFile.Json(BackupDecode.Rejected(BackupFailure.MALFORMED))
        if (kind == BackupCodec.Sniff.HTML) return BackupFile.Html
        val combined = SequenceInputStream(ByteArrayInputStream(prefix.toByteArray()), stream)
        val decoded = BackupCodec.decode(combined)
        return when {
            decoded is BackupDecode.Rejected && decoded.failure == BackupFailure.TOO_LARGE -> BackupFile.TooLarge
            else -> BackupFile.Json(decoded)
        }
    }

    private companion object {
        const val SNIFF_LIMIT = 64
        const val TAG = "SkimBackup"
    }
}
