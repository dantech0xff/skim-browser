package com.dantech.skimbrowser.browser.downloads

import com.dantech.skimbrowser.domain.download.DownloadDraft
import com.dantech.skimbrowser.domain.download.DownloadHeader
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.download.DownloadRetry
import com.dantech.skimbrowser.domain.download.VIDEO_QUALITY
import com.dantech.skimbrowser.domain.download.VIDEO_ROW
import com.dantech.skimbrowser.domain.download.VIDEO_TITLE
import com.dantech.skimbrowser.domain.download.videoRow
import com.dantech.skimbrowser.domain.tab.TabsController
import android.util.Log
import com.dantech.skimbrowser.domain.video.HlsVariant
import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoCommands
import com.dantech.skimbrowser.domain.video.VideoExtractor
import com.dantech.skimbrowser.domain.video.VideoQualities
import com.dantech.skimbrowser.domain.video.VideoSaveResult
import com.dantech.skimbrowser.domain.video.VideoSource
import com.dantech.skimbrowser.domain.video.VideoVariant
import com.dantech.skimbrowser.domain.video.collectVariants
import com.dantech.skimbrowser.domain.video.dashProtected
import com.dantech.skimbrowser.domain.video.MediaSpan
import com.dantech.skimbrowser.domain.video.mediaAddress
import com.dantech.skimbrowser.domain.video.mediaSpans
import com.dantech.skimbrowser.domain.video.parseDash
import com.dantech.skimbrowser.domain.video.parseHls
import com.dantech.skimbrowser.domain.video.preferredHlsAudio
import com.dantech.skimbrowser.domain.video.qualityHeight
import com.dantech.skimbrowser.domain.video.retryHeaders
import com.dantech.skimbrowser.domain.video.variantFromRetry
import com.dantech.skimbrowser.domain.video.videoDisplayTitle
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody

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
    private val maxPlaylistChars: Int = MAX_PLAYLIST_CHARS,
    private val extractors: Set<VideoExtractor> = emptySet(),
    private val rangeChunkBytes: Long = RANGE_CHUNK,
    private val rangeHost: (String) -> Boolean = ::isRangeHost,
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
        return saveVariant(retry.tabId, variant, retry.userAgent, retry.incognito, retry.videoRow())
    }

    private suspend fun saveVariant(
        tabId: String,
        variant: com.dantech.skimbrowser.domain.video.VideoVariant,
        userAgent: String?,
        incognito: Boolean,
        replaceId: Long? = null,
    ): VideoSaveResult {
        val source = variant.source
        val display = videoDisplayTitle(variant.title, source.mediaAddress())
        val named = if (source is VideoSource.Hls) segmentTitle(display) else display
        val nameMime = when {
            source is VideoSource.Hls && source.audioPlaylistUrl != null -> "video/mp4"
            source is VideoSource.Hls -> "video/mp2t"
            else -> variant.mimeType ?: "video/mp4"
        }
        val name = DownloadNames.sanitize(named.ifBlank { "video" }, nameMime)
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
            mimeType = nameMime,
            userInitiated = true,
            incognito = incognito,
            headers = retryHeaders(source) + listOf(
                DownloadHeader(VIDEO_QUALITY, variant.qualityLabel),
                DownloadHeader(VIDEO_TITLE, display),
            ) + (replaceId?.let { listOf(DownloadHeader(VIDEO_ROW, it.toString())) } ?: emptyList()),
        )
        try {
            if (source is VideoSource.Hls && hlsProtected(source, userAgent, incognito)) {
                return VideoSaveResult.Protected
            }
            if (source is VideoSource.Dash && manifestProtected(source.manifestUrl, source.headers, userAgent, incognito)) {
                return VideoSaveResult.Protected
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, failureCode(e))
            failBeforeStart(name, incognito, retry, failureCode(e), replaceId)
            return VideoSaveResult.Started
        }
        val localId = log.noteStarted(DownloadDraft(name, incognito, retry, replaceId))
        val job = scope.launch {
            finish(localId, name, incognito, retry, source, userAgent, variant.qualityLabel)
        }
        tracker.followJob(localId, job)
        return VideoSaveResult.Started
    }

    override suspend fun qualities(tabId: String, variant: VideoVariant): VideoQualities {
        val tab = tabs.state.value.tab(tabId)
        val incognito = tab?.incognito ?: true
        val agent = userAgentOf(tabId)
        return when (val source = variant.source) {
            is VideoSource.Hls -> hlsQualities(variant, source, agent, incognito)
            is VideoSource.Dash -> dashQualities(variant, source, agent, incognito)
            else -> VideoQualities.Ready(listOf(variant))
        }
    }

    private suspend fun failBeforeStart(
        name: String,
        incognito: Boolean,
        retry: DownloadRetry,
        reason: String,
        replaceId: Long? = null,
    ) {
        val localId = log.noteStarted(DownloadDraft(name, incognito, retry, replaceId))
        log.noteFailed(localId, reason)
        tracker.report(DownloadEvent.DownloadFailed(name, incognito, retry))
    }

    private suspend fun finish(
        localId: Long,
        name: String,
        incognito: Boolean,
        retry: DownloadRetry,
        source: VideoSource,
        userAgent: String?,
        quality: String,
    ) {
        try {
            val done = when (source) {
                is VideoSource.Progressive -> saveProgressive(
                    source.url,
                    source.headers,
                    userAgent,
                    incognito,
                    name,
                    localId,
                    retry,
                    quality,
                )
                is VideoSource.Hls -> savePlaylist(
                    source.playlistUrl,
                    source.headers,
                    userAgent,
                    incognito,
                    name,
                    localId,
                    source.audioPlaylistUrl,
                )
                is VideoSource.Dash -> saveDash(source.manifestUrl, source.headers, userAgent, incognito, name, localId, retry.tabId)
                is VideoSource.SeparateAv -> saveSeparate(
                    source.videoUrl,
                    source.audioUrl,
                    source.headers,
                    userAgent,
                    incognito,
                    name,
                    localId,
                    retry.tabId,
                    quality,
                )
            }
            log.noteFinished(localId, done.uri.toString(), done.mime, done.bytes, done.fileName)
            tracker.report(done.copy(incognito = incognito))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, failureCode(e))
            log.noteFailed(localId, failureCode(e))
            tracker.report(DownloadEvent.DownloadFailed(name, incognito, retry))
        }
    }

    private suspend fun hlsProtected(source: VideoSource.Hls, agent: String?, incognito: Boolean): Boolean {
        if (playlistProtected(source.playlistUrl, source.headers, agent, incognito)) return true
        val audio = source.audioPlaylistUrl ?: return false
        return playlistProtected(audio, source.headers, agent, incognito)
    }

    private suspend fun playlistProtected(url: String, headers: Map<String, String>, agent: String?, incognito: Boolean): Boolean {
        val fetched = getText(url, headers, agent, incognito)
        val playlist = parseHls(fetched.text, fetched.url)
        if (playlist.protected) return true
        val best = playlist.variants.maxByOrNull { it.bandwidth ?: 0 } ?: return false
        val media = getText(best.url, headers, agent, incognito)
        if (parseHls(media.text, media.url).protected) return true
        val audio = preferredHlsAudio(playlist.audios, best.audioGroup) ?: return false
        val audioBody = getText(audio.url, headers, agent, incognito)
        return parseHls(audioBody.text, audioBody.url).protected
    }

    private suspend fun manifestProtected(url: String, headers: Map<String, String>, agent: String?, incognito: Boolean): Boolean {
        val fetched = getText(url, headers, agent, incognito)
        return parseDash(fetched.text, fetched.url).protected
    }

    private suspend fun saveProgressive(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
        retry: DownloadRetry,
        quality: String,
    ): DownloadEvent.DownloadCompleted {
        try {
            return writeProgressive(url, headers, agent, incognito, name, localId, retry.mimeType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (httpCode(e) != 403 || !rangeHost(url)) throw e
            Log.i(TAG, "Refreshing the video address after HTTP 403")
            val fresh = try {
                freshProgressive(retry.tabId, headers, agent, incognito, quality)
            } catch (refresh: Exception) {
                if (refresh is CancellationException) throw refresh
                Log.w(TAG, "The video address refresh failed: ${failureCode(refresh)}")
                throw refresh
            }
            Log.i(TAG, "The video address refresh succeeded")
            return writeProgressive(fresh.url, fresh.headers, agent, incognito, name, localId, retry.mimeType)
        }
    }

    private suspend fun writeProgressive(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
        mime: String?,
    ): DownloadEvent.DownloadCompleted = if (rangeHost(url)) {
        saveRangedFile(url, headers, agent, incognito, name, localId, mime ?: "video/mp4")
    } else {
        files.download(
            url = url,
            userAgent = agent,
            cookies = { cookies(incognito, it) },
            rawName = name,
            declaredMime = mime,
            headers = headers,
            onProgress = { soFar, total -> log.noteProgress(localId, soFar, total) },
        )
    }

    private suspend fun savePlaylist(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
        audioPlaylistUrl: String?,
    ): DownloadEvent.DownloadCompleted {
        val opened = openMedia(url, headers, agent, incognito, audioPlaylistUrl)
        if (opened.playlist.protected) throw DownloadException("The video is protected")
        val audioOpened = opened.audioUrl?.let { audioUrl ->
            openMedia(audioUrl, headers, agent, incognito, null)
        }
        if (audioOpened?.playlist?.protected == true) throw DownloadException("The video is protected")
        val videoSpans = mediaSpans(opened.playlist)
        if (videoSpans.isEmpty()) throw DownloadException("The playlist has no video")
        val dir = jobDir(localId)
        val budget = ByteBudget(maxBytes)
        try {
            val video = downloadSpans(videoSpans, headers, agent, incognito, dir, localId, "video", budget)
            if (audioOpened == null) {
                return publish(video, name, containerMime(video, videoSpans.map { it.url }))
            }
            val audioSpans = mediaSpans(audioOpened.playlist)
            if (audioSpans.isEmpty()) throw DownloadException("The playlist has no video")
            val audio = downloadSpans(audioSpans, headers, agent, incognito, dir, localId, "audio", budget)
            val out = File(dir, "mux.mp4")
            muxWithRoom(video, audio, out)
            return publish(out, name, "video/mp4")
        } finally {
            dir.deleteRecursively()
        }
    }

    private data class OpenedMedia(val playlist: com.dantech.skimbrowser.domain.video.HlsPlaylist, val audioUrl: String?)

    private suspend fun openMedia(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        audioPlaylistUrl: String?,
    ): OpenedMedia {
        val fetched = getText(url, headers, agent, incognito)
        var playlist = parseHls(fetched.text, fetched.url)
        var audio = audioPlaylistUrl
        if (playlist.variants.isNotEmpty()) {
            val best = playlist.variants.maxBy { it.bandwidth ?: 0 }
            if (audio == null) audio = preferredHlsAudio(playlist.audios, best.audioGroup)?.url
            val media = getText(best.url, headers, agent, incognito)
            playlist = parseHls(media.text, media.url)
        }
        return OpenedMedia(playlist, audio)
    }

    private suspend fun downloadSpans(
        spans: List<MediaSpan>,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        dir: File,
        localId: Long,
        label: String,
        budget: ByteBudget,
    ): File {
        dir.mkdirs()
        val dest = File(dir, "$label.bin")
        val known = spans.all { it.length != null }
        val total = if (known) spans.sumOf { it.length ?: 0L } else -1L
        var soFar = 0L
        dest.outputStream().use { out ->
            for ((index, span) in spans.withIndex()) {
                soFar += appendSpan(
                    span,
                    headers,
                    agent,
                    incognito,
                    out,
                    soFar,
                    total,
                    localId,
                    budget,
                    segmentIndex = index,
                    segmentCount = spans.size,
                )
            }
        }
        return dest
    }

    private suspend fun appendSpan(
        span: MediaSpan,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        out: OutputStream,
        soFar: Long,
        total: Long,
        localId: Long,
        budget: ByteBudget,
        segmentIndex: Int = 0,
        segmentCount: Int = 1,
    ): Long {
        val start = span.start
        val length = span.length
        if (start == null || length == null) {
            if (rangeHost(span.url)) {
                val temp = File.createTempFile("range-", ".bin")
                return try {
                    downloadInRanges(
                        span.url,
                        headers,
                        agent,
                        incognito,
                        temp,
                        soFar,
                        { written, size -> log.noteProgress(localId, written, size) },
                        budget,
                    )
                    temp.inputStream().use { it.copyTo(out) }
                    temp.length()
                } finally {
                    temp.delete()
                }
            }
            return fetch(span.url, headers, agent, incognito) { response ->
                val body = response.body ?: throw DownloadException("The response has no body")
                val segmentLength = body.contentLength()
                val copied = copyBody(
                    body,
                    out,
                    soFar,
                    -1L,
                    onProgress = { written, _ ->
                        val into = written - soFar
                        val progress = hlsProgress(
                            cumulative = written,
                            segmentIndex = segmentIndex,
                            bytesIntoSegment = into,
                            segmentLength = segmentLength,
                            segmentCount = segmentCount,
                            segmentFinished = segmentLength > 0L && into >= segmentLength,
                        )
                        log.noteProgress(localId, progress.arrived, progress.implied)
                    },
                    charge = budget,
                )
                if (segmentLength <= 0L) {
                    val progress = hlsProgress(
                        cumulative = soFar + copied,
                        segmentIndex = segmentIndex,
                        bytesIntoSegment = copied,
                        segmentLength = segmentLength,
                        segmentCount = segmentCount,
                        segmentFinished = true,
                    )
                    log.noteProgress(localId, progress.arrived, progress.implied)
                }
                copied
            }
        }
        val end = start + length - 1
        return fetch(span.url, headers + ("Range" to "bytes=$start-$end"), agent, incognito) { response ->
            val body = response.body ?: throw DownloadException("The response has no body")
            val partial = response.code == 206 || !response.header("Content-Range").isNullOrBlank()
            if (!partial && start > 0L && (body.contentLength() < 0L || body.contentLength() > length)) {
                discardBytes(body, start)
            }
            copyAtMost(body, out, length, soFar, total, budget) { written, size -> log.noteProgress(localId, written, size) }
        }
    }

    private suspend fun saveDash(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
        tabId: String,
    ): DownloadEvent.DownloadCompleted {
        val fetched = getText(url, headers, agent, incognito)
        val manifest = parseDash(fetched.text, fetched.url)
        if (manifest.protected) throw DownloadException("The video is protected")
        val video = rankedVideos(manifest.videos).firstOrNull() ?: throw DownloadException("The manifest has no video")
        val audio = preferredAudio(manifest.audios)
        return if (audio == null) {
            saveSegments(listOf(video.url), headers, agent, incognito, name, localId)
        } else {
            saveSeparate(video.url, audio.url, headers, agent, incognito, name, localId, tabId, video.height?.let { "${it}p" } ?: "DASH")
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
        tabId: String,
        quality: String,
    ): DownloadEvent.DownloadCompleted {
        try {
            return muxPair(videoUrl, audioUrl, headers, agent, incognito, name, localId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (httpCode(e) != 403) throw e
            Log.i(TAG, "Refreshing the video address after HTTP 403")
            val fresh = try {
                freshSeparate(tabId, headers, agent, incognito, quality)
            } catch (refresh: Exception) {
                if (refresh is CancellationException) throw refresh
                Log.w(TAG, "The video address refresh failed: ${failureCode(refresh)}")
                throw refresh
            }
            Log.i(TAG, "The video address refresh succeeded")
            return muxPair(fresh.videoUrl, fresh.audioUrl, fresh.headers, agent, incognito, name, localId)
        }
    }

    private suspend fun muxPair(
        videoUrl: String,
        audioUrl: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
    ): DownloadEvent.DownloadCompleted {
        val dir = jobDir(localId)
        val budget = ByteBudget(maxBytes)
        try {
            val progress = PairProgress(localId)
            val (video, audio) = coroutineScope {
                val videoFile = async {
                    downloadFile(videoUrl, headers, agent, incognito, dir, localId, 0, onProgress = progress::video, budget = budget)
                }
                val audioFile = async {
                    downloadFile(audioUrl, headers, agent, incognito, dir, localId, 0, onProgress = progress::audio, budget = budget)
                }
                videoFile.await() to audioFile.await()
            }
            val out = File(dir, "mux.mp4")
            muxWithRoom(video, audio, out)
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
            val known = mutableListOf<Long>()
            val parts = mutableListOf<File>()
            for (url in urls) {
                val planned = projectedTotal(known, urls.size)
                val part = downloadFile(url, headers, agent, incognito, dir, localId, soFar, planned, urls.size)
                parts += part
                known += part.length()
                soFar += part.length()
            }
            val joined = File(dir, "joined")
            joined.outputStream().use { out -> copyWithRoom(parts, out) }
            return publish(joined, name, containerMime(joined, urls))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun jobDir(localId: Long): File = File(workDir, localId.toString()).apply { mkdirs() }

    private suspend fun saveRangedFile(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        name: String,
        localId: Long,
        mime: String,
    ): DownloadEvent.DownloadCompleted {
        val dir = jobDir(localId)
        try {
            val file = downloadFile(url, headers, agent, incognito, dir, localId, 0)
            return publish(file, name, mime)
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun downloadFile(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        dir: File,
        localId: Long,
        already: Long,
        plannedTotal: Long = -1,
        segmentCount: Int = 1,
        onProgress: suspend (Long, Long) -> Unit = { soFar, total -> log.noteProgress(localId, soFar, total) },
        budget: ByteBudget? = null,
    ): File {
        dir.mkdirs()
        val dest = File.createTempFile("part-", ".bin", dir)
        try {
            requireRoom()
            if (rangeHost(url)) {
                downloadInRanges(url, headers, agent, incognito, dest, already, onProgress, budget)
            } else {
                fetch(url, headers, agent, incognito) { response ->
                    val body = response.body ?: throw DownloadException("The response has no body")
                    val length = body.contentLength()
                    if (length > 0 && budget == null && already + length > maxBytes) {
                        throw DownloadException("The file is larger than the limit")
                    }
                    if (length > 0) budget?.add(length)
                    val total = when {
                        plannedTotal > 0 -> plannedTotal
                        length > 0 && segmentCount > 1 -> length * segmentCount
                        length > 0 -> length
                        else -> -1L
                    }
                    dest.outputStream().use { out ->
                        copyBody(body, out, already, total, onProgress, charge = budget?.takeIf { length <= 0 })
                    }
                }
            }
            return dest
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    private suspend fun downloadInRanges(
        url: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        dest: File,
        already: Long,
        onProgress: suspend (Long, Long) -> Unit,
        budget: ByteBudget?,
    ) {
        requireRoom()
        var start = 0L
        var total = -1L
        var reserved = false
        dest.outputStream().use { out ->
            while (total < 0 || start < total) {
                currentCoroutineContext().ensureActive()
                val end = if (total > 0) minOf(start + rangeChunkBytes - 1, total - 1) else start + rangeChunkBytes - 1
                val requested = end - start + 1
                val piece = fetch(withRange(url, start, end), headers, agent, incognito) { response ->
                    val body = response.body ?: throw DownloadException("The response has no body")
                    val declared = contentRangeTotal(response.header("Content-Range"))
                    if (declared != null) {
                        total = declared
                        if (!reserved) {
                            budget?.add(declared)
                            reserved = true
                        }
                    }
                    val copied = copyBody(
                        body,
                        out,
                        already + start,
                        if (total > 0) total else -1L,
                        onProgress,
                        charge = budget?.takeIf { !reserved },
                    )
                    val wholeFile = response.header("Content-Range") == null && copied > requested
                    wholeFile to copied
                }
                if (piece.second <= 0) {
                    if (start > 0) break
                    throw DownloadException("The response has no body")
                }
                if (piece.first) break
                start += piece.second
                if (piece.second < requested) break
            }
        }
    }

    private suspend fun getText(url: String, headers: Map<String, String>, agent: String?, incognito: Boolean): TextBody =
        fetch(url, headers, agent, incognito) { response ->
            TextBody(readLimited(response), response.request.url.toString())
        }

    /** Stops once [maxPlaylistChars] bytes are in hand, before the rest of a chunked body arrives. */
    private suspend fun readLimited(response: Response): String {
        val body = response.body ?: return ""
        val type = body.contentType()
        val declared = body.contentLength()
        if (declared > maxPlaylistChars) throw DownloadException("The playlist is larger than the limit")
        val stream = body.byteStream()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val room = maxPlaylistChars - total + 1
                val read = stream.read(buffer, 0, minOf(buffer.size, room))
                if (read < 0) break
                total += read
                if (total > maxPlaylistChars) throw DownloadException("The playlist is larger than the limit")
                out.write(buffer, 0, read)
            }
        } finally {
            stream.close()
        }
        return out.toByteArray().toResponseBody(type).string()
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
                    throw DownloadException("The server answered ${response.code}", response.code)
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

    /**
     * Leaves [InProcessDownloader.MIN_FREE_BYTES] free. [pending] is a copy that is not on disk yet.
     * Bytes already written are already gone from [freeBytes], so they are not added again.
     */
    private fun requireRoom(pending: Long = 0) {
        val free = freeBytes()
        val needed = InProcessDownloader.MIN_FREE_BYTES + pending.coerceAtLeast(0)
        if (free != Long.MAX_VALUE && free < needed) {
            throw DownloadException("Too little free space")
        }
    }

    private fun muxWithRoom(video: File, audio: File, out: File) {
        requireRoom(video.length() + audio.length())
        mux(video, audio, out)
    }

    private suspend fun publish(file: File, name: String, mime: String): DownloadEvent.DownloadCompleted {
        requireRoom(file.length())
        return store.save(DownloadNames.sanitize(name, mime), mime) { out -> copyWithRoom(listOf(file), out) }
    }

    private suspend fun copyWithRoom(files: List<File>, out: OutputStream) {
        var pending = files.sumOf { it.length() }
        requireRoom(pending)
        val buffer = ByteArray(COPY_BUFFER)
        var written = 0L
        for (file in files) {
            file.inputStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    written += read
                    pending -= read
                    if (written == read.toLong() || written % InProcessDownloader.FREE_SPACE_CHECK_BYTES < read) {
                        requireRoom(pending)
                    }
                }
            }
        }
    }

    private data class TextBody(val text: String, val url: String)

    private sealed interface FetchStep<out T> {
        class Redirect(val to: HttpUrl) : FetchStep<Nothing>
        class Ready<T>(val value: T) : FetchStep<T>
    }

    private fun audioRate(url: String?): Int {
        if (url.isNullOrBlank()) return 0
        return Regex("""(\d{2,4})""").findAll(url).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0
    }

    private suspend fun hlsQualities(
        variant: VideoVariant,
        source: VideoSource.Hls,
        agent: String?,
        incognito: Boolean,
    ): VideoQualities {
        val fetched = getText(source.playlistUrl, source.headers, agent, incognito)
        val playlist = parseHls(fetched.text, fetched.url)
        if (playlist.protected) return VideoQualities.Protected
        val title = segmentTitle(variant.title)
        if (playlist.variants.isEmpty()) {
            return VideoQualities.Ready(listOf(variant.copy(title = title, mimeType = "video/mp2t")))
        }
        val chosen = playlist.variants
            .groupBy { it.url }
            .map { (_, group) ->
                group.maxBy { item -> audioRate(preferredHlsAudio(playlist.audios, item.audioGroup)?.url) }
            }
            .groupBy { hlsQuality(it) }
            .map { (_, group) -> group.maxBy { it.bandwidth ?: 0 } }
            .sortedByDescending { qualityHeight(hlsQuality(it)) ?: -1 }
        val rows = chosen.map { item ->
            val audio = preferredHlsAudio(playlist.audios, item.audioGroup)?.url
            variant.copy(
                title = title,
                qualityLabel = hlsQuality(item),
                mimeType = if (audio == null) "video/mp2t" else "video/mp4",
                sizeBytes = null,
                source = VideoSource.Hls(item.url, source.headers, audio),
            )
        }
        return VideoQualities.Ready(rows)
    }

    private suspend fun dashQualities(
        variant: VideoVariant,
        source: VideoSource.Dash,
        agent: String?,
        incognito: Boolean,
    ): VideoQualities {
        val fetched = getText(source.manifestUrl, source.headers, agent, incognito)
        val manifest = parseDash(fetched.text, fetched.url)
        if (manifest.protected) return VideoQualities.Protected
        val videos = rankedVideos(manifest.videos)
        if (videos.isEmpty()) return VideoQualities.Ready(listOf(variant))
        val audio = preferredAudio(manifest.audios)
        val rows = videos.map { file ->
            val label = file.height?.let { "${it}p" } ?: variant.qualityLabel
            if (audio == null) {
                variant.copy(
                    qualityLabel = label,
                    mimeType = "video/mp4",
                    sizeBytes = null,
                    source = VideoSource.Progressive(file.url, source.headers),
                )
            } else {
                variant.copy(
                    qualityLabel = label,
                    mimeType = "video/mp4",
                    sizeBytes = null,
                    source = VideoSource.SeparateAv(file.url, audio.url, source.headers),
                )
            }
        }
        return VideoQualities.Ready(rows)
    }

    private suspend fun freshSeparate(
        tabId: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        quality: String,
    ): VideoSource.SeparateAv {
        val tab = tabs.state.value.tab(tabId)
        val page = headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
            ?: tab?.url
            ?: throw DownloadException("That quality is no longer available")
        val hints = PageVideoHints(
            pageUrl = page,
            pageTitle = tab?.title,
            mediaUrls = tabs.runtime(tabId).value.mediaUrls,
            userAgent = agent,
            cookieHeader = cookies(incognito, page),
        )
        val found = collectVariants(extractors, hints).variants
        val direct = found.firstOrNull { variant ->
            variant.qualityLabel == quality && variant.source is VideoSource.SeparateAv
        }
        if (direct != null) return direct.source as VideoSource.SeparateAv
        val dash = found.firstOrNull { it.source is VideoSource.Dash }?.source as? VideoSource.Dash
        if (dash != null) {
            separateFromDash(dash, quality, agent, incognito)?.let { return it }
        }
        throw DownloadException("That quality is no longer available")
    }

    private suspend fun freshProgressive(
        tabId: String,
        headers: Map<String, String>,
        agent: String?,
        incognito: Boolean,
        quality: String,
    ): VideoSource.Progressive {
        val tab = tabs.state.value.tab(tabId)
        val page = headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
            ?: tab?.url
            ?: throw DownloadException("That quality is no longer available")
        val hints = PageVideoHints(
            pageUrl = page,
            pageTitle = tab?.title,
            mediaUrls = tabs.runtime(tabId).value.mediaUrls,
            userAgent = agent,
            cookieHeader = cookies(incognito, page),
        )
        val found = collectVariants(extractors, hints).variants
        val height = qualityHeight(quality)
        val direct = found.firstOrNull { it.qualityLabel == quality && it.source is VideoSource.Progressive }
            ?: found.firstOrNull { variant ->
                height != null && qualityHeight(variant.qualityLabel) == height && variant.source is VideoSource.Progressive
            }
        return direct?.source as? VideoSource.Progressive
            ?: throw DownloadException("That quality is no longer available")
    }

    private suspend fun separateFromDash(
        source: VideoSource.Dash,
        quality: String,
        agent: String?,
        incognito: Boolean,
    ): VideoSource.SeparateAv? {
        val fetched = getText(source.manifestUrl, source.headers, agent, incognito)
        if (dashProtected(fetched.text)) return null
        val manifest = parseDash(fetched.text, fetched.url)
        if (manifest.protected) return null
        val height = qualityHeight(quality) ?: return null
        val video = rankedVideos(manifest.videos).firstOrNull { it.height == height } ?: return null
        val audio = preferredAudio(manifest.audios) ?: return null
        return VideoSource.SeparateAv(video.url, audio.url, source.headers)
    }

    private fun discardBytes(body: ResponseBody, count: Long) {
        val input = body.byteStream()
        var left = count
        val buffer = ByteArray(COPY_BUFFER)
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) break
            left -= read
        }
    }

    private suspend fun copyAtMost(
        body: ResponseBody,
        out: OutputStream,
        limit: Long,
        base: Long,
        total: Long,
        budget: ByteBudget,
        onProgress: suspend (Long, Long) -> Unit,
    ): Long {
        requireRoom()
        val input = body.byteStream()
        val buffer = ByteArray(COPY_BUFFER)
        var written = 0L
        while (written < limit) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - written).toInt())
            if (read < 0) break
            budget.add(read.toLong())
            if (written == 0L || written % InProcessDownloader.FREE_SPACE_CHECK_BYTES < read) {
                requireRoom()
            }
            out.write(buffer, 0, read)
            written += read
            onProgress(base + written, total)
        }
        return written
    }

    private suspend fun copyBody(
        body: ResponseBody,
        out: OutputStream,
        base: Long,
        total: Long,
        onProgress: suspend (Long, Long) -> Unit,
        charge: ByteBudget? = null,
    ): Long {
        requireRoom()
        val input = body.byteStream()
        val buffer = ByteArray(COPY_BUFFER)
        var written = 0L
        var reported = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            written += read
            if (charge != null) charge.add(read.toLong())
            else if (base + written > maxBytes) throw DownloadException("The file is larger than the limit")
            if (written == read.toLong() || written % InProcessDownloader.FREE_SPACE_CHECK_BYTES < read) {
                requireRoom()
            }
            out.write(buffer, 0, read)
            if (written - reported >= REPORT_BYTES) {
                reported = written
                onProgress(base + written, total)
            }
        }
        onProgress(base + written, if (total > 0) total else -1L)
        return written
    }

    private inner class PairProgress(private val localId: Long) {
        private val gate = Mutex()
        private var video = 0L
        private var audio = 0L
        private var videoTotal = -1L
        private var audioTotal = -1L

        suspend fun video(written: Long, total: Long) = note(videoTrack = true, written, total)

        suspend fun audio(written: Long, total: Long) = note(videoTrack = false, written, total)

        private suspend fun note(videoTrack: Boolean, written: Long, total: Long) {
            gate.withLock {
                if (videoTrack) {
                    video = written
                    if (total > 0) videoTotal = total
                } else {
                    audio = written
                    if (total > 0) audioTotal = total
                }
                val combined = if (videoTotal > 0 && audioTotal > 0) videoTotal + audioTotal else -1L
                log.noteProgress(localId, video + audio, combined)
            }
        }
    }

    private companion object {
        const val TAG = "VideoSaver"
        const val MAX_REDIRECTS = 20
        const val COPY_BUFFER = 64 * 1024
        const val MAX_PLAYLIST_CHARS = 2 * 1024 * 1024
        const val RANGE_CHUNK = 10L * 1024 * 1024
        const val REPORT_BYTES = 256L * 1024
    }
}

internal fun isRangeHost(url: String): Boolean {
    val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return false
    return host == "googlevideo.com" || host.endsWith(".googlevideo.com")
}

internal fun withRange(url: String, start: Long, end: Long): String {
    val http = url.toHttpUrlOrNull() ?: return url
    return http.newBuilder().setQueryParameter("range", "$start-$end").build().toString()
}

internal fun contentRangeTotal(header: String?): Long? {
    if (header.isNullOrBlank()) return null
    val total = header.substringAfter('/', "").trim()
    return total.toLongOrNull()?.takeIf { it > 0 }
}

internal fun failureCode(error: Throwable): String {
    httpCode(error)?.let { return "http:$it" }
    val chain = generateSequence(error) { it.cause }.toList()
    val text = chain.mapNotNull { it.message }.joinToString(" ").lowercase()
    return when {
        "protected" in text -> "protected"
        "larger than the limit" in text -> "limit"
        "free space" in text -> "space"
        "no video" in text -> "empty"
        "no longer available" in text -> "quality"
        chain.any(::isNetworkFailure) -> "network"
        else -> "unknown"
    }
}

/** One video file per height, tallest first. A repeated height keeps the larger bandwidth. */
internal fun preferredAudio(audios: List<com.dantech.skimbrowser.domain.video.DashFile>): com.dantech.skimbrowser.domain.video.DashFile? =
    audios.maxByOrNull { it.bandwidth ?: 0 }

internal fun rankedVideos(videos: List<com.dantech.skimbrowser.domain.video.DashFile>): List<com.dantech.skimbrowser.domain.video.DashFile> =
    videos.groupBy { file -> file.height?.let { "h:$it" } ?: file.url }
        .map { (_, files) -> files.maxBy { it.bandwidth ?: 0 } }
        .sortedByDescending { it.height ?: -1 }

/**
 * The container the joined bytes actually are. An fMP4 or CMAF file is `video/mp4`. A transport
 * stream stays `video/mp2t`.
 */
internal fun containerMime(file: File, urls: List<String>): String {
    val header = ByteArray(12)
    val read = file.inputStream().use { it.read(header) }
    if (read >= 8) {
        val box = String(header, 4, 4, Charsets.ISO_8859_1)
        if (box in MP4_BOXES) return "video/mp4"
        if (header[0] == 0x47.toByte()) return "video/mp2t"
    }
    val fragmented = urls.isNotEmpty() && urls.all { url ->
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        path.endsWith(".m4s") || path.endsWith(".mp4") || path.endsWith(".m4v") ||
            path.endsWith(".cmfv") || path.endsWith(".cmfa")
    }
    return if (fragmented) "video/mp4" else "video/mp2t"
}

private val MP4_BOXES = setOf("ftyp", "moof", "styp", "mdat", "sidx", "free")

internal fun isNetworkFailure(error: Throwable): Boolean {
    if (error is DownloadException) return false
    return when (error) {
        is java.net.UnknownHostException,
        is java.net.SocketTimeoutException,
        is java.net.ConnectException,
        is java.net.NoRouteToHostException,
        is java.net.SocketException,
        is java.net.ProtocolException,
        is javax.net.ssl.SSLException,
        is java.io.InterruptedIOException,
        is java.io.EOFException -> true
        is java.io.IOException -> droppedTransfer(error.message)
        else -> false
    }
}

private fun droppedTransfer(message: String?): Boolean {
    val text = message?.lowercase() ?: return false
    return "unexpected end of stream" in text ||
        "stream was reset" in text ||
        "connection reset" in text ||
        "connection abort" in text ||
        "broken pipe" in text ||
        "timed out" in text ||
        "timeout" in text ||
        "failed to connect" in text
}

private class ByteBudget(private val max: Long) {
    private val gate = Mutex()
    private var used = 0L

    suspend fun add(bytes: Long) {
        if (bytes <= 0) return
        gate.withLock {
            if (used > max - bytes) throw DownloadException("The file is larger than the limit")
            used += bytes
        }
    }
}

internal fun segmentTitle(title: String): String = title.trim().removeSuffix(".m3u8").removeSuffix(".m3u").ifBlank { title.trim() }

internal fun hlsQuality(item: HlsVariant): String {
    val resolution = item.resolution
    val height = resolution?.let { value -> Regex("""(\d+)x(\d+)""").find(value)?.groupValues?.get(2)?.toIntOrNull() }
    if (height != null) return "${height}p"
    if (!resolution.isNullOrBlank()) return resolution
    val bandwidth = item.bandwidth ?: return "HLS"
    return "${bandwidth / 1000} kbps"
}

internal data class HlsBytes(val arrived: Long, val implied: Long)

internal fun hlsProgress(
    cumulative: Long,
    segmentIndex: Int,
    bytesIntoSegment: Long,
    segmentLength: Long,
    segmentCount: Int,
    segmentFinished: Boolean,
): HlsBytes {
    if (cumulative < 0L || segmentCount <= 0) return HlsBytes(cumulative, -1L)
    val into = when {
        segmentLength > 0L -> bytesIntoSegment.coerceIn(0L, segmentLength).toDouble() / segmentLength.toDouble()
        segmentFinished && bytesIntoSegment > 0L -> 1.0
        else -> 0.0
    }
    val fraction = ((segmentIndex.coerceAtLeast(0) + into) / segmentCount.toDouble()).coerceIn(0.0, 1.0)
    if (fraction <= 0.0) return HlsBytes(cumulative, -1L)
    return HlsBytes(cumulative, (cumulative / fraction).toLong().coerceAtLeast(cumulative))
}

internal fun projectedTotal(known: List<Long>, count: Int): Long {
    if (known.isEmpty() || count <= 0) return -1
    val average = known.sum() / known.size
    return known.sum() + average * (count - known.size)
}

internal fun httpCode(error: Throwable): Int? {
    val pending = ArrayDeque<Throwable>()
    pending.add(error)
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (current is DownloadException && current.code != null) return current.code
        current.cause?.let { pending.add(it) }
        current.suppressed.forEach { pending.add(it) }
    }
    return null
}
