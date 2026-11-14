package com.dantech.skimbrowser.browser.downloads

import com.dantech.skimbrowser.domain.download.DownloadDraft
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.download.DownloadRetry
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.video.MediaStream
import com.dantech.skimbrowser.domain.video.VideoCommands
import com.dantech.skimbrowser.domain.video.VideoSaveResult
import com.dantech.skimbrowser.domain.video.VideoSource
import com.dantech.skimbrowser.domain.video.mediaStream
import com.dantech.skimbrowser.domain.video.parseDash
import com.dantech.skimbrowser.domain.video.parseHls
import com.dantech.skimbrowser.domain.video.retryHeaders
import com.dantech.skimbrowser.domain.video.variantFromRetry
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Saves a video in this process. Android's download manager is not used: a file the person asked
 * for would go out without its cookie, and a playlist is more than one request. Cookies are read
 * again for each address and are not stored on the retry.
 */
class VideoSaver(
    httpClient: () -> OkHttpClient,
    private val store: MediaStoreDownloads,
    private val log: DownloadLog,
    private val tracker: DownloadTracker,
    private val tabs: TabsController,
    private val cookies: suspend (incognito: Boolean, url: String) -> String?,
    private val workDir: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val mux: (File, File, File) -> Unit = ::muxToMp4,
    private val freeBytes: () -> Long = { store.freeBytes() },
    private val maxBytes: Long = MAX_IN_PROCESS_DOWNLOAD_BYTES,
) : VideoCommands {

    private val files = InProcessDownloader(httpClient, store)
    private var userAgentOf: (String) -> String? = { null }

    fun attach(userAgentOf: (String) -> String?) {
        this.userAgentOf = userAgentOf
    }

    override suspend fun save(tabId: String, variant: com.dantech.skimbrowser.domain.video.VideoVariant): VideoSaveResult {
        val tab = tabs.state.value.tab(tabId)
        val incognito = tab?.incognito ?: true
        val agent = userAgentOf(tabId)
        return saveVariant(tabId, variant, agent, incognito)
    }

    suspend fun saveAgain(retry: DownloadRetry): VideoSaveResult {
        val variant = variantFromRetry(retry) ?: return VideoSaveResult.Started
        return saveVariant(retry.tabId, variant, retry.userAgent, retry.incognito)
    }

    private suspend fun saveVariant(
        tabId: String,
        variant: com.dantech.skimbrowser.domain.video.VideoVariant,
        userAgent: String?,
        incognito: Boolean,
    ): VideoSaveResult {
        val source = variant.source
        val name = DownloadNames.sanitize(variant.title.ifBlank { "video" }, variant.mimeType ?: "video/mp4")
        val retry = DownloadRetry(
            tabId = tabId,
            url = when (source) {
                is VideoSource.Progressive -> source.url
                is VideoSource.Hls -> source.playlistUrl
                is VideoSource.Dash -> source.manifestUrl
                is VideoSource.SeparateAv -> source.videoUrl
            },
            userAgent = userAgent,
            contentDisposition = null,
            mimeType = variant.mimeType,
            userInitiated = true,
            incognito = incognito,
            headers = retryHeaders(source),
        )
        try {
            if (source is VideoSource.Hls && playlistProtected(source.playlistUrl, source.headers, userAgent, incognito)) {
                return VideoSaveResult.Protected
            }
            if (source is VideoSource.Dash && manifestProtected(source.manifestUrl, source.headers, userAgent, incognito)) {
                return VideoSaveResult.Protected
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failBeforeStart(name, incognito, retry)
            return VideoSaveResult.Started
        }
        val localId = log.noteStarted(DownloadDraft(name, incognito, retry))
        val job = scope.launch {
            finish(localId, name, incognito, retry, source, userAgent)
        }
        tracker.followJob(localId, job)
        return VideoSaveResult.Started
    }

    private suspend fun failBeforeStart(name: String, incognito: Boolean, retry: DownloadRetry) {
        val localId = log.noteStarted(DownloadDraft(name, incognito, retry))
        log.noteFailed(localId)
        tracker.report(DownloadEvent.DownloadFailed(name, incognito, retry))
    }

    private suspend fun finish(
        localId: Long,
        name: String,
        incognito: Boolean,
        retry: DownloadRetry,
        source: VideoSource,
        userAgent: String?,
    ) {
        try {
            val done = when (source) {
                is VideoSource.Progressive -> files.download(
                    url = source.url,
                    userAgent = userAgent,
                    cookies = { cookies(incognito, it) },
                    rawName = name,
                    declaredMime = retry.mimeType,
                    headers = source.headers,
                    onProgress = { soFar, total -> log.noteProgress(localId, soFar, total) },
                )
                is VideoSource.Hls -> savePlaylist(source.playlistUrl, source.headers, userAgent, incognito, name, localId)
                is VideoSource.Dash -> saveDash(source.manifestUrl, source.headers, userAgent, incognito, name, localId)
                is VideoSource.SeparateAv -> saveSeparate(source.videoUrl, source.audioUrl, source.headers, userAgent, incognito, name, localId)
            }
            log.noteFinished(localId, done.uri.toString(), done.mime, done.bytes)
            tracker.report(done.copy(incognito = incognito))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.noteFailed(localId)
            tracker.report(DownloadEvent.DownloadFailed(name, incognito, retry))
        }
    }

    private suspend fun playlistProtected(url: String, headers: Map<String, String>, agent: String?, incognito: Boolean): Boolean {
        val fetched = getText(url, headers, agent, incognito)
        val playlist = parseHls(fetched.text, fetched.url)
        if (playlist.protected) return true
        val best = playlist.variants.maxByOrNull { it.bandwidth ?: 0 } ?: return false
        val media = getText(best.url, headers, agent, incognito)
        return parseHls(media.text, media.url).protected
    }

    private suspend fun manifestProtected(url: String, headers: Map<String, String>, agent: String?, incognito: Boolean): Boolean {
        val fetched = getText(url, headers, agent, incognito)
        return parseDash(fetched.text, fetched.url).protected
    }

    private suspend fun savePlaylist(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
    ): DownloadEvent.DownloadCompleted {
        var fetched = getText(url, headers, agent, incognito)
        var playlist = parseHls(fetched.text, fetched.url)
        if (playlist.variants.isNotEmpty()) {
            val best = playlist.variants.maxBy { it.bandwidth ?: 0 }
            fetched = getText(best.url, headers, agent, incognito)
            playlist = parseHls(fetched.text, fetched.url)
        }
        if (playlist.protected) throw DownloadException("The video is protected")
        val segments = playlist.segments.ifEmpty { throw DownloadException("The playlist has no video") }
        return saveSegments(segments, headers, agent, incognito, name, localId)
    }

    private suspend fun saveDash(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
    ): DownloadEvent.DownloadCompleted {
        val fetched = getText(url, headers, agent, incognito)
        val manifest = parseDash(fetched.text, fetched.url)
        if (manifest.protected) throw DownloadException("The video is protected")
        val video = manifest.videos.maxByOrNull { it.height ?: 0 } ?: throw DownloadException("The manifest has no video")
        val audio = manifest.audios.firstOrNull()
        return if (audio == null) {
            saveSegments(listOf(video.url), headers, agent, incognito, name, localId)
        } else {
            saveSeparate(video.url, audio.url, headers, agent, incognito, name, localId)
        }
    }

    private suspend fun saveSeparate(
        videoUrl: String,
        audioUrl: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
    ): DownloadEvent.DownloadCompleted {
        val dir = jobDir(localId)
        try {
            val video = downloadFile(videoUrl, headers, agent, incognito, dir, localId, 0)
            val audio = downloadFile(audioUrl, headers, agent, incognito, dir, localId, video.length())
            val out = File(dir, "mux.mp4")
            mux(video, audio, out)
            return publish(out, name, "video/mp4")
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun saveSegments(
        urls: List<String>,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
    ): DownloadEvent.DownloadCompleted {
        val dir = jobDir(localId)
        try {
            var soFar = 0L
            val parts = mutableListOf<File>()
            for (url in urls) {
                val part = downloadFile(url, headers, agent, incognito, dir, localId, soFar)
                parts += part
                soFar += part.length()
            }
            val joined = File(dir, "joined")
            joined.outputStream().use { out -> parts.forEach { file -> file.inputStream().use { it.copyTo(out) } } }
            val progressive = urls.size == 1 && mediaStream(urls.single(), null) == MediaStream.Progressive
            val mime = if (progressive) "video/mp4" else "video/mp2t"
            return publish(joined, name, mime)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun jobDir(localId: Long): File = File(workDir, localId.toString()).apply { mkdirs() }

    private suspend fun downloadFile(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        dir: File,
        localId: Long,
        already: Long,
    ): File {
        dir.mkdirs()
        val dest = File.createTempFile("part-", ".bin", dir)
        try {
            fetch(url, headers, agent, incognito) { response ->
                val body = response.body ?: throw DownloadException("The response has no body")
                val length = body.contentLength()
                if (length > 0 && already + length > maxBytes) throw DownloadException("The file is larger than the limit")
                requireRoom(already)
                dest.outputStream().use { out ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var written = 0L
                    val input = body.byteStream()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (already + written > maxBytes) throw DownloadException("The file is larger than the limit")
                        if (written % InProcessDownloader.FREE_SPACE_CHECK_BYTES < read) requireRoom(already + written)
                        out.write(buffer, 0, read)
                    }
                }
            }
            log.noteProgress(localId, already + dest.length(), -1)
            return dest
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    private suspend fun getText(url: String, headers: Map<String, String>, agent: String?, incognito: Boolean): TextBody =
        fetch(url, headers, agent, incognito) { response ->
            val text = response.body?.string().orEmpty()
            if (text.length > MAX_PLAYLIST_CHARS) throw DownloadException("The playlist is larger than the limit")
            TextBody(text, response.request.url.toString())
        }

    private suspend fun <T> fetch(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        block: suspend (Response) -> T,
    ): T = withContext(io) {
        var address = url.toHttpUrlOrNull() ?: throw DownloadException("Not an http or https address")
        repeat(MAX_REDIRECTS + 1) {
            val hop = address.toString()
            val request = Request.Builder().url(address).get().apply {
                headers.forEach { (name, value) ->
                    if (!name.equals("Cookie", ignoreCase = true) && !name.startsWith("X-Skim-", ignoreCase = true)) {
                        header(name, value)
                    }
                }
                agent?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
                cookies(incognito, hop)?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }.build()
            val step = files.client.newCall(request).executeCancellable { response ->
                if (response.isRedirect) {
                    val location = response.header("Location") ?: throw DownloadException("A redirect without a location")
                    FetchStep.Redirect(address.resolve(location) ?: throw DownloadException("A redirect to an address that is not http or https"))
                } else if (!response.isSuccessful) {
                    throw DownloadException("The server answered ${response.code}")
                } else {
                    FetchStep.Ready(block(response))
                }
            }
            when (step) {
                is FetchStep.Redirect -> address = step.to
                is FetchStep.Ready -> return@withContext step.value
            }
        }
        throw DownloadException("Too many redirects")
    }

    private fun requireRoom(incoming: Long) {
        val free = freeBytes()
        if (free != Long.MAX_VALUE && free < InProcessDownloader.MIN_FREE_BYTES + incoming) {
            throw DownloadException("Too little free space")
        }
    }

    private suspend fun publish(file: File, name: String, mime: String): DownloadEvent.DownloadCompleted =
        store.save(DownloadNames.sanitize(name, mime), mime) { out -> file.inputStream().use { it.copyTo(out) } }

    private data class TextBody(val text: String, val url: String)

    private sealed interface FetchStep<out T> {
        class Redirect(val to: HttpUrl) : FetchStep<Nothing>
        class Ready<T>(val value: T) : FetchStep<T>
    }

    private companion object {
        const val MAX_REDIRECTS = 20
        const val COPY_BUFFER = 64 * 1024
        const val MAX_PLAYLIST_CHARS = 2 * 1024 * 1024
    }
}
