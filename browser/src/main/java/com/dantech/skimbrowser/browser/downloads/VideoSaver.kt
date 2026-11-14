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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
        if (source is VideoSource.Hls && playlistProtected(source.playlistUrl, source.headers, userAgent, incognito)) {
            return VideoSaveResult.Protected
        }
        if (source is VideoSource.Dash && manifestProtected(source.manifestUrl, source.headers, userAgent, incognito)) {
            return VideoSaveResult.Protected
        }
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
        val localId = log.noteStarted(DownloadDraft(name, incognito, retry))
        scope.launch {
            finish(localId, name, incognito, retry, source, userAgent)
        }
        return VideoSaveResult.Started
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
        val body = getText(url, headers, agent, incognito)
        val playlist = parseHls(body, url)
        if (playlist.protected) return true
        val best = playlist.variants.maxByOrNull { it.bandwidth ?: 0 } ?: return false
        return parseHls(getText(best.url, headers, agent, incognito), best.url).protected
    }

    private suspend fun manifestProtected(url: String, headers: Map<String, String>, agent: String?, incognito: Boolean): Boolean =
        parseDash(getText(url, headers, agent, incognito), url).protected

    private suspend fun savePlaylist(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
    ): DownloadEvent.DownloadCompleted {
        var playlist = parseHls(getText(url, headers, agent, incognito), url)
        if (playlist.variants.isNotEmpty()) {
            val best = playlist.variants.maxBy { it.bandwidth ?: 0 }
            playlist = parseHls(getText(best.url, headers, agent, incognito), best.url)
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
        val manifest = parseDash(getText(url, headers, agent, incognito), url)
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
        val video = downloadFile(videoUrl, headers, agent, incognito, localId, 0)
        val audio = downloadFile(audioUrl, headers, agent, incognito, localId, video.length())
        val out = File(workDir, "$name.mp4")
        try {
            mux(video, audio, out)
            return publish(out, name, "video/mp4")
        } finally {
            video.delete()
            audio.delete()
            out.delete()
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
        var soFar = 0L
        val parts = urls.map { url ->
            downloadFile(url, headers, agent, incognito, localId, soFar).also { soFar += it.length() }
        }
        val joined = File(workDir, "$name.joined")
        try {
            joined.outputStream().use { out -> parts.forEach { file -> file.inputStream().use { it.copyTo(out) } } }
            val progressive = urls.size == 1 && mediaStream(urls.single(), null) == MediaStream.Progressive
            val mime = if (progressive) "video/mp4" else "video/mp2t"
            return publish(joined, name, mime)
        } finally {
            parts.forEach { it.delete() }
            joined.delete()
        }
    }

    private suspend fun downloadFile(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        localId: Long,
        already: Long,
    ): File = withContext(io) {
        workDir.mkdirs()
        val dest = File.createTempFile("skim-video", ".part", workDir)
        val request = Request.Builder().url(url).get().apply {
            headers.forEach { (name, value) ->
                if (!name.equals("Cookie", ignoreCase = true)) header(name, value)
            }
            agent?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
            cookies(incognito, url)?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
        }.build()
        files.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DownloadException("The server answered ${response.code}")
            val body = response.body ?: throw DownloadException("The response has no body")
            dest.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        log.noteProgress(localId, already + dest.length(), -1)
        dest
    }

    private suspend fun getText(url: String, headers: Map<String, String>, agent: String?, incognito: Boolean): String =
        withContext(io) {
            val request = Request.Builder().url(url).get().apply {
                headers.forEach { (name, value) ->
                    if (!name.equals("Cookie", ignoreCase = true)) header(name, value)
                }
                agent?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
                cookies(incognito, url)?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }.build()
            files.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DownloadException("The server answered ${response.code}")
                response.body?.string().orEmpty()
            }
        }

    private suspend fun publish(file: File, name: String, mime: String): DownloadEvent.DownloadCompleted =
        store.save(DownloadNames.sanitize(name, mime), mime) { out -> file.inputStream().use { it.copyTo(out) } }
}
