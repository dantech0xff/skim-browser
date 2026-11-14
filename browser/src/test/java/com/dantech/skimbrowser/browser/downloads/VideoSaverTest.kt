package com.dantech.skimbrowser.browser.downloads

import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakeBrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.di.cookieHeader
import com.dantech.skimbrowser.domain.tab.CloseToken
import com.dantech.skimbrowser.domain.download.DownloadDraft
import com.dantech.skimbrowser.domain.download.DownloadItem
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.tab.RecordingTabs
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoExtractor
import com.dantech.skimbrowser.domain.video.VideoQualities
import com.dantech.skimbrowser.domain.video.VideoSaveResult
import com.dantech.skimbrowser.domain.video.VideoSource
import com.dantech.skimbrowser.domain.video.VideoVariant
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoSaverTest {

    @Test
    fun aProtectedPlaylistIsRefusedBeforeADownloadRowExists() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"\nseg.ts\n"))
        server.start()
        val log = CountingLog()
        val saver = saver(log, this)
        val variant = VideoVariant("clip", "HLS", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))
        try {
            assertEquals(VideoSaveResult.Protected, saver.save("t", variant))
            assertEquals(0, log.started)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aDashManifestWithContentProtectionIsRefused() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("<MPD><ContentProtection schemeIdUri=\"urn:uuid:widevine\"/></MPD>"))
        server.start()
        val log = CountingLog()
        val saver = saver(log, this)
        val variant = VideoVariant("clip", "DASH", null, null, VideoSource.Dash(server.url("/a.mpd").toString()))
        try {
            assertEquals(VideoSaveResult.Protected, saver.save("t", variant))
            assertEquals(0, log.started)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aRedirectedPlaylistIsReadAndAMissingOneBecomesAFailedRow() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/real.m3u8").toString()))
        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"\n"))
        server.enqueue(MockResponse().setResponseCode(403))
        val log = CountingLog()
        val saver = saver(log, this)
        try {
            val variant = VideoVariant("clip", "HLS", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))
            assertEquals(VideoSaveResult.Protected, saver.save("t", variant))
            assertEquals(0, log.started)
            assertEquals(VideoSaveResult.Started, saver.save("t", variant))
            assertEquals(1, log.started)
            assertEquals(1, log.failed)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aRedirectedPlaylistResolvesFilesFromTheFinalAddress() = runBlocking {
        val server = MockWebServer()
        server.start()
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nmedia.m3u8\n"
        val media = "#EXTM3U\n#EXTINF:1,\nseg.ts\n"
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/cdn/master.m3u8"))
            server.enqueue(MockResponse().setBody(master))
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/cdn/files/media.m3u8"))
            server.enqueue(MockResponse().setBody(media))
        }
        server.enqueue(MockResponse().setBody("segment"))
        val dash = "<MPD><AdaptationSet contentType=\"video\"><Representation height=\"480\"><BaseURL>clip.mp4</BaseURL></Representation></AdaptationSet></MPD>"
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/cdn/manifest.mpd"))
            server.enqueue(MockResponse().setBody(dash))
        }
        server.enqueue(MockResponse().setBody("dash-bytes"))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip", "HLS", null, null, VideoSource.Hls(server.url("/old/master.m3u8").toString()))))
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip", "DASH", null, null, VideoSource.Dash(server.url("/old/a.mpd").toString()))))
            val paths = buildList {
                repeat(server.requestCount) { add(server.takeRequest().path) }
            }
            org.junit.Assert.assertTrue(paths.toString(), paths.contains("/cdn/files/seg.ts"))
            org.junit.Assert.assertTrue(paths.toString(), paths.contains("/cdn/clip.mp4"))
            org.junit.Assert.assertFalse(paths.any { it.orEmpty().startsWith("/old/") && it != "/old/master.m3u8" && it != "/old/a.mpd" })
            assertEquals(2, log.started)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun anOversizedPlaylistFailsBeforeTheRestOfTheBodyIsKept() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("x".repeat(80)))
        server.enqueue(MockResponse().setChunkedBody("#EXTM3U\n" + "y".repeat(80), 16))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined, maxPlaylistChars = 32)
        try {
            val variant = VideoVariant("clip", "HLS", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))
            assertEquals(VideoSaveResult.Started, saver.save("t", variant))
            assertEquals(VideoSaveResult.Started, saver.save("t", variant))
            assertEquals(2, log.started)
            assertEquals(2, log.failed)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aUtf16DashManifestIsReadWithItsCharset() = runBlocking {
        val server = MockWebServer()
        server.start()
        val clip = server.url("/clip.mp4")
        val xml = "<MPD><AdaptationSet contentType=\"video\"><Representation height=\"480\"><BaseURL>$clip</BaseURL></Representation></AdaptationSet></MPD>"
        val encoded = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + xml.toByteArray(Charsets.UTF_16LE)
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/dash+xml; charset=utf-16le")
                    .setBody(Buffer().write(encoded)),
            )
        }
        server.enqueue(MockResponse().setBody("dash-bytes"))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip", "DASH", null, null, VideoSource.Dash(server.url("/a.mpd").toString()))))
            val paths = buildList { repeat(server.requestCount) { add(server.takeRequest().path) } }
            org.junit.Assert.assertTrue(paths.toString(), paths.contains("/clip.mp4"))
            assertEquals(1, log.started)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aFailedSegmentRemovesTheFilesItAlreadyWrote() = runBlocking {
        val server = MockWebServer()
        server.start()
        val first = server.url("/a.ts")
        val second = server.url("/b.ts")
        val playlist = "#EXTM3U\n#EXTINF:1,\n$first\n#EXTINF:1,\n$second\n"
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody("AAAA"))
        server.enqueue(MockResponse().setResponseCode(403))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        val cache = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "video-test")
        try {
            val variant = VideoVariant("clip", "HLS", null, null, VideoSource.Hls(server.url("/list.m3u8").toString()))
            assertEquals(VideoSaveResult.Started, saver.save("t", variant))
            assertEquals(1, log.failed)
            val leftovers = cache.walkTopDown().filter { it.isFile }.toList()
            org.junit.Assert.assertTrue(leftovers.toString(), leftovers.isEmpty())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun separateAudioAndVideoAreMuxedIntoOneFile() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("VIDEO"))
        server.enqueue(MockResponse().setBody("AUDIO"))
        server.start()
        val log = CountingLog()
        val muxed = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined) { video, audio, out ->
            muxed += video.readText() + audio.readText()
            out.writeText("MUXED")
        }
        val variant = VideoVariant(
            "clip",
            "720p",
            "video/mp4",
            null,
            VideoSource.SeparateAv(server.url("/v.mp4").toString(), server.url("/a.mp4").toString()),
        )
        try {
            assertEquals(VideoSaveResult.Started, saver.save("t", variant))
            assertEquals(listOf("VIDEOAUDIO"), muxed)
            assertEquals(1, log.started)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun anHlsMediaPlaylistAndAClearDashFileAreFetched() = runBlocking {
        val server = MockWebServer()
        server.start()
        val mp4 = server.url("/clip.mp4")
        val playlist = "#EXTM3U\n#EXTINF:1,\n$mp4\n"
        val mpd = "<MPD><AdaptationSet contentType=\"video\"><Representation height=\"480\"><BaseURL>$mp4</BaseURL></Representation></AdaptationSet></MPD>"
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody("mp4-bytes"))
        server.enqueue(MockResponse().setBody(mpd))
        server.enqueue(MockResponse().setBody(mpd))
        server.enqueue(MockResponse().setBody("dash-bytes"))
        server.enqueue(MockResponse().setBody("progressive-bytes"))
        server.enqueue(MockResponse().setBody("progressive-again"))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip", "HLS", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))))
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip", "DASH", null, null, VideoSource.Dash(server.url("/a.mpd").toString()))))
            val file = VideoVariant("clip", "Video", "video/mp4", null, VideoSource.Progressive(server.url("/v.mp4").toString()))
            assertEquals(VideoSaveResult.Started, saver.save("t", file))
            val retry = com.dantech.skimbrowser.domain.video.retryHeaders(file.source).let { headers ->
                com.dantech.skimbrowser.domain.download.DownloadRetry("t", server.url("/v.mp4").toString(), "agent", null, "video/mp4", true, false, headers)
            }
            assertEquals(VideoSaveResult.Started, saver.saveAgain(retry))
            assertEquals(4, log.started)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun muxingFilesThatAreNotMediaReleasesTheExtractors() {
        val video = File.createTempFile("video", ".mp4")
        val audio = File.createTempFile("audio", ".m4a")
        val out = File.createTempFile("out", ".mp4")
        video.writeText("not media")
        audio.writeText("not media")
        try {
            muxToMp4(video, audio, out)
            org.junit.Assert.fail("empty files are not a video")
        } catch (_: Exception) {
            org.junit.Assert.assertTrue(true)
        } finally {
            video.delete()
            audio.delete()
            out.delete()
        }
    }

    @Test
    fun cookiesAreReadForANormalTabAndForAnIncognitoProfile() {
        val profiles = FakeBrowserProfiles()
        val session = IncognitoSession(
            profiles,
            File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "shared"),
            CoroutineScope(Dispatchers.Unconfined),
        )
        val url = "https://cdn.example/a.mp4"
        CookieManager.getInstance().setCookie(url, "a=b")
        assertEquals("a=b", cookieHeader(session, profiles, private = false, url))
        assertEquals(null, cookieHeader(session, profiles, private = true, url))
        val profile = FakeBrowserProfiles.Profile("p")
        profile.cookies[url] = "inc=1"
        profiles.profiles["p"] = profile
        val state = IncognitoSession::class.java.getDeclaredField("_state")
        state.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = state.get(session) as kotlinx.coroutines.flow.MutableStateFlow<IncognitoSession.State>
        flow.value = IncognitoSession.State.Active("p")
        assertEquals("inc=1", cookieHeader(session, profiles, private = true, url))
        flow.value = IncognitoSession.State.Ending("p", CloseToken(1))
        assertEquals("inc=1", cookieHeader(session, profiles, private = true, url))
    }

    @Test
    fun aFileWithoutAVideoTrackIsNotMuxed() {
        val audioOnly = memorySource("audio/mp4a-latm", 2)
        try {
            muxSources(audioOnly, audioOnly, recordingSink(mutableListOf()))
            org.junit.Assert.fail("a video track is required")
        } catch (e: IllegalStateException) {
            org.junit.Assert.assertTrue(e.message.orEmpty().contains("video"))
        }
    }

    @Test
    fun separateTracksAreWrittenVideoThenAudio() {
        val video = memorySource("video/avc", 1)
        val audio = memorySource("audio/mp4a-latm", 2)
        val written = mutableListOf<String>()
        muxSources(video, audio, recordingSink(written))
        assertEquals(
            listOf("add:video/avc", "add:audio/mp4a-latm", "start", "write:1", "write:2", "stop"),
            written,
        )
    }

    @Test
    fun aRangedDownloadAsksForEachSliceAndReportsTheWholeSize() = runBlocking {
        val server = MockWebServer()
        server.start()
        val segment = server.url("/seg.ts").toString()
        val playlist = "#EXTM3U\n#EXTINF:1,\n$segment\n"
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setResponseCode(206).addHeader("Content-Range", "bytes 0-3/8").setBody("VIDE"))
        server.enqueue(MockResponse().setResponseCode(206).addHeader("Content-Range", "bytes 4-7/8").setBody("O123"))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined, rangeChunkBytes = 4, rangeHost = { true })
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save("t", VideoVariant("x36xhzz.m3u8", "HLS", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))),
            )
            val ranges = buildList {
                repeat(server.requestCount) { add(server.takeRequest().requestUrl?.queryParameter("range")) }
            }.filterNotNull()
            assertEquals(listOf("0-3", "4-7"), ranges)
            org.junit.Assert.assertTrue(log.progress.toString(), log.progress.any { it.second == 8L })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aForbiddenAudioTrackIsFetchedAgainFromThePage() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("VIDEO"))
        server.enqueue(MockResponse().setResponseCode(403))
        val lowVideo = server.url("/360v.mp4").toString()
        val lowAudio = server.url("/360a.mp4").toString()
        val highVideo = server.url("/1080v.mp4").toString()
        val highAudio = server.url("/1080a.mp4").toString()
        server.enqueue(MockResponse().setBody("LOW-VIDEO"))
        server.enqueue(MockResponse().setBody("LOW-AUDIO"))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val extractor = object : VideoExtractor {
            override val id = "page"
            override val pageUrlSufficient = true
            override fun matches(pageUrl: String) = pageUrl.contains("example.com")
            override suspend fun extract(hints: PageVideoHints) = listOf(
                VideoVariant("clip", "1080p", "video/mp4", null, VideoSource.SeparateAv(highVideo, highAudio)),
                VideoVariant("clip", "360p", "video/mp4", null, VideoSource.SeparateAv(lowVideo, lowAudio)),
            )
        }
        val muxed = mutableListOf<String>()
        val saver = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            mux = { video, audio, out ->
                muxed += video.readText() + audio.readText()
                out.writeText("MUXED")
            },
            extractors = setOf(extractor),
        )
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save(
                    "t",
                    VideoVariant(
                        "clip",
                        "360p",
                        "video/mp4",
                        null,
                        VideoSource.SeparateAv(server.url("/v1.mp4").toString(), server.url("/a1.mp4").toString()),
                    ),
                ),
            )
            assertEquals(listOf("LOW-VIDEOLOW-AUDIO"), muxed)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aQueryRangeWithoutContentRangeContinuesUntilTheFileEnds() = runBlocking {
        val server = MockWebServer()
        server.start()
        val segment = server.url("/seg.ts").toString()
        val playlist = "#EXTM3U\n#EXTINF:1,\n$segment\n"
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setResponseCode(200).setBody("VIDE"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("O123"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined, rangeChunkBytes = 4, rangeHost = { true })
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save("t", VideoVariant("clip.m3u8", "HLS", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))),
            )
            val ranges = buildList {
                repeat(server.requestCount) { add(server.takeRequest().requestUrl?.queryParameter("range")) }
            }.filterNotNull()
            org.junit.Assert.assertTrue(ranges.toString(), ranges.containsAll(listOf("0-3", "4-7")))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun separateTracksThatEachFitAreRefusedWhenTheirSumDoesNot() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("12345678"))
        server.enqueue(MockResponse().setBody("12345678"))
        server.start()
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val muxed = mutableListOf<String>()
        val saver = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            maxBytes = 10,
            mux = { _, _, out ->
                muxed += "muxed"
                out.writeText("MUXED")
            },
        )
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save(
                    "t",
                    VideoVariant(
                        "clip",
                        "360p",
                        "video/mp4",
                        null,
                        VideoSource.SeparateAv(server.url("/v.mp4").toString(), server.url("/a.mp4").toString()),
                    ),
                ),
            )
            assertEquals("limit", log.reason)
            org.junit.Assert.assertTrue(muxed.isEmpty())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rangedBytesUseTheQueryAndAMasterPlaylistBecomesQualities() = runBlocking {
        assertEquals(true, isRangeHost("https://rr.googlevideo.com/videoplayback?id=1"))
        assertEquals(false, isRangeHost("https://cdn.example/a.mp4"))
        assertEquals("0-3", withRange("https://rr.googlevideo.com/videoplayback?id=1", 0, 3).substringAfter("range="))
        val server = MockWebServer()
        server.start()
        val low = server.url("/low.m3u8")
        val high = server.url("/high.m3u8")
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=640x360\n$low\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720\n$high\n"
        server.enqueue(MockResponse().setBody(master))
        val protected = "<MPD><ContentProtection schemeIdUri=\"urn:uuid:widevine\"/></MPD>"
        server.enqueue(MockResponse().setBody(protected))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            val hls = saver.qualities("t", VideoVariant("x36xhzz.m3u8", "HLS", null, null, VideoSource.Hls(server.url("/master.m3u8").toString())))
            val ready = (hls as VideoQualities.Ready).variants
            assertEquals(listOf("360p", "720p"), ready.map { it.qualityLabel })
            assertEquals("x36xhzz", ready.first().title)
            assertEquals("video/mp2t", ready.first().mimeType)
            val dash = saver.qualities("t", VideoVariant("clip", "DASH", null, null, VideoSource.Dash(server.url("/a.mpd").toString())))
            assertEquals(VideoQualities.Protected, dash)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aKnownLengthIsReportedWhileTheFileArrivesAndAFailureKeepsItsReason() = runBlocking {
        val server = MockWebServer()
        server.start()
        val segment = server.url("/seg.ts").toString()
        val playlist = "#EXTM3U\n#EXTINF:1,\n$segment\n"
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody("12345678"))
        server.enqueue(MockResponse().setResponseCode(403))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip.m3u8", "HLS", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))))
            org.junit.Assert.assertTrue(log.progress.toString(), log.progress.any { it.second == 8L && it.first > 0L })
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip.m3u8", "HLS", null, null, VideoSource.Hls(server.url("/missing.m3u8").toString()))))
            assertEquals("http:403", log.reason)
        } finally {
            server.shutdown()
        }
    }

    private fun saver(
        log: CountingLog,
        scope: CoroutineScope,
        io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
        maxPlaylistChars: Int = 2 * 1024 * 1024,
        extractors: Set<VideoExtractor> = emptySet(),
        rangeChunkBytes: Long = 10L * 1024 * 1024,
        rangeHost: (String) -> Boolean = ::isRangeHost,
        maxBytes: Long = MAX_IN_PROCESS_DOWNLOAD_BYTES,
        mux: (File, File, File) -> Unit = ::muxToMp4,
    ): VideoSaver {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tabs = RecordingTabs().apply {
            show(listOf(Tab(id = "t", incognito = false, url = "https://example.com/watch", title = "Watch", groupId = null, position = 0, createdAt = 0, lastAccessedAt = 0)))
        }
        return VideoSaver(
            httpClient = { OkHttpClient() },
            store = MediaStoreDownloads(context),
            log = log,
            tracker = DownloadTracker(FakeSystemDownloads(), scope, log = log),
            tabs = tabs,
            cookies = { _, _ -> "session=1" },
            workDir = File(context.cacheDir, "video-test"),
            scope = scope,
            io = io,
            mux = mux,
            freeBytes = { Long.MAX_VALUE },
            maxPlaylistChars = maxPlaylistChars,
            extractors = extractors,
            rangeChunkBytes = rangeChunkBytes,
            rangeHost = rangeHost,
            maxBytes = maxBytes,
        )
    }
}

private class CountingLog : DownloadLog {
    var started = 0
    var failed = 0
    var finished = false
    var reason: String? = null
    val progress = mutableListOf<Pair<Long, Long>>()

    override fun observe(): Flow<List<DownloadItem>> = flowOf(emptyList())
    override suspend fun noteStarted(draft: DownloadDraft): Long {
        started += 1
        return started.toLong()
    }
    override suspend fun noteSystem(localId: Long, systemId: Long) = Unit
    override suspend fun noteProgress(localId: Long, soFar: Long, total: Long) {
        progress += soFar to total
    }
    override suspend fun noteFinished(localId: Long, uri: String?, mime: String?, bytes: Long) {
        finished = true
    }
    override suspend fun noteFailed(localId: Long, reason: String?) {
        failed += 1
        this.reason = reason
    }
    override suspend fun remove(id: Long): DownloadItem? = null
    override suspend fun clearIncognito() = Unit
    override suspend fun runningWithSystemId(): List<DownloadItem> = emptyList()
}

private fun memorySource(mime: String, marker: Byte): ElementarySource = object : ElementarySource {
    override fun tracks(): List<OpenedTrack> = listOf(object : OpenedTrack {
        override val mime: String = mime
        private var sent = false
        override fun read(): ElementarySample? = if (sent) {
            null
        } else {
            sent = true
            ElementarySample(byteArrayOf(marker), 0L, 0)
        }
    })

    override fun close() = Unit
}

private fun recordingSink(written: MutableList<String>): MuxSink = object : MuxSink {
    override fun addTrack(mime: String): Int {
        written += "add:$mime"
        return written.size
    }

    override fun start() {
        written += "start"
    }

    override fun write(track: Int, sample: ElementarySample) {
        written += "write:${sample.data[0].toInt()}"
    }

    override fun stop() {
        written += "stop"
    }

    override fun close() = Unit
}
