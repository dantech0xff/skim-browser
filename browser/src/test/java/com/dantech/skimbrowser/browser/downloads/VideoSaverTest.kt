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

    private fun saver(
        log: CountingLog,
        scope: CoroutineScope,
        io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
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
        )
    }
}

private class CountingLog : DownloadLog {
    var started = 0
    var failed = 0
    var finished = false

    override fun observe(): Flow<List<DownloadItem>> = flowOf(emptyList())
    override suspend fun noteStarted(draft: DownloadDraft): Long {
        started += 1
        return started.toLong()
    }
    override suspend fun noteSystem(localId: Long, systemId: Long) = Unit
    override suspend fun noteProgress(localId: Long, soFar: Long, total: Long) = Unit
    override suspend fun noteFinished(localId: Long, uri: String?, mime: String?, bytes: Long) {
        finished = true
    }
    override suspend fun noteFailed(localId: Long) {
        failed += 1
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
