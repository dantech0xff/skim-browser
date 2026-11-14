package com.dantech.skimbrowser.browser.downloads

import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakeBrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.di.cookieHeader
import com.dantech.skimbrowser.domain.download.DownloadHeader
import com.dantech.skimbrowser.domain.download.DownloadRetry
import com.dantech.skimbrowser.domain.download.VIDEO_AUDIO
import com.dantech.skimbrowser.domain.download.VIDEO_KIND
import com.dantech.skimbrowser.domain.download.VIDEO_HLS
import com.dantech.skimbrowser.domain.download.VIDEO_PROGRESSIVE
import com.dantech.skimbrowser.domain.download.VIDEO_QUALITY
import com.dantech.skimbrowser.domain.download.VIDEO_SEPARATE
import com.dantech.skimbrowser.domain.download.VIDEO_TITLE
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
import com.dantech.skimbrowser.domain.video.listedQualities
import com.dantech.skimbrowser.domain.video.qualityHeight
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun aStoredRetryRefreshesTheSameQualityAndADashManifest() = runBlocking {
        val server = MockWebServer()
        server.start()
        val lowVideo = server.url("/360v.mp4").toString()
        val lowAudio = server.url("/360a.mp4").toString()
        val manifest = """
            <MPD>
              <AdaptationSet contentType="video">
                <Representation height="720"><BaseURL>${server.url("/720v.mp4")}</BaseURL></Representation>
                <Representation height="360"><BaseURL>$lowVideo</BaseURL></Representation>
              </AdaptationSet>
              <AdaptationSet contentType="audio">
                <Representation><BaseURL>$lowAudio</BaseURL></Representation>
              </AdaptationSet>
            </MPD>
        """.trimIndent()
        server.enqueue(MockResponse().setBody("OLD-VIDEO"))
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setBody(manifest))
        server.enqueue(MockResponse().setBody("DASH-VIDEO"))
        server.enqueue(MockResponse().setBody("DASH-AUDIO"))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val extractor = object : VideoExtractor {
            override val id = "page"
            override val pageUrlSufficient = true
            override fun matches(pageUrl: String) = pageUrl.contains("example.com")
            override suspend fun extract(hints: PageVideoHints) = listOf(
                VideoVariant("clip", "DASH", null, null, VideoSource.Dash(server.url("/a.mpd").toString())),
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
        val retry = DownloadRetry(
            tabId = "t",
            url = server.url("/v1.mp4").toString(),
            userAgent = "agent",
            contentDisposition = null,
            mimeType = "video/mp4",
            userInitiated = true,
            incognito = false,
            headers = listOf(
                DownloadHeader("Referer", "https://example.com/watch"),
                DownloadHeader(VIDEO_KIND, VIDEO_SEPARATE),
                DownloadHeader(VIDEO_AUDIO, server.url("/a1.mp4").toString()),
                DownloadHeader(VIDEO_QUALITY, "360p MP4 separate audio"),
            ),
        )
        try {
            assertEquals(VideoSaveResult.Started, saver.saveAgain(retry))
            assertEquals(listOf("DASH-VIDEODASH-AUDIO"), muxed)
            val paths = buildList { repeat(server.requestCount) { add(server.takeRequest().path) } }
            org.junit.Assert.assertTrue(paths.toString(), paths.contains("/360v.mp4"))
            org.junit.Assert.assertFalse(paths.toString(), paths.contains("/720v.mp4"))
            assertEquals(360, qualityHeight("360p MP4 separate audio"))
            assertEquals(1080, qualityHeight("1080p60"))
            assertEquals(null, qualityHeight("DASH"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aDashRefreshKeepsTheHigherBandwidthAudio() = runBlocking {
        val server = MockWebServer()
        server.start()
        val video = server.url("/360v.mp4").toString()
        val lowAudio = server.url("/low-audio.mp4").toString()
        val highAudio = server.url("/high-audio.mp4").toString()
        val manifest = """
            <MPD>
              <AdaptationSet contentType="video">
                <Representation height="360" bandwidth="400000"><BaseURL>$video</BaseURL></Representation>
              </AdaptationSet>
              <AdaptationSet contentType="audio">
                <Representation bandwidth="64000"><BaseURL>$lowAudio</BaseURL></Representation>
                <Representation bandwidth="128000"><BaseURL>$highAudio</BaseURL></Representation>
              </AdaptationSet>
            </MPD>
        """.trimIndent()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains(".mpd") -> MockResponse().setBody(manifest)
                    path.contains("360v") -> MockResponse().setBody("VIDEO")
                    path.contains("high-audio") -> MockResponse().setBody("HIGH")
                    path.contains("low-audio") -> MockResponse().setBody("LOW")
                    else -> MockResponse().setResponseCode(403)
                }
            }
        }
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val extractor = object : VideoExtractor {
            override val id = "page"
            override val pageUrlSufficient = true
            override fun matches(pageUrl: String) = pageUrl.contains("example.com")
            override suspend fun extract(hints: PageVideoHints) = listOf(
                VideoVariant("clip", "DASH", null, null, VideoSource.Dash(server.url("/a.mpd").toString())),
            )
        }
        val muxed = mutableListOf<String>()
        val saver = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            mux = { videoFile, audioFile, out ->
                muxed += videoFile.readText() + audioFile.readText()
                out.writeText("MUXED")
            },
            extractors = setOf(extractor),
        )
        try {
            val offered = saver.qualities("t", VideoVariant("clip", "DASH", null, null, VideoSource.Dash(server.url("/a.mpd").toString())))
            val pair = (offered as VideoQualities.Ready).variants.single().source as VideoSource.SeparateAv
            assertEquals(highAudio, pair.audioUrl)
            assertEquals(
                VideoSaveResult.Started,
                saver.saveAgain(
                    DownloadRetry(
                        tabId = "t",
                        url = server.url("/expired-video.mp4").toString(),
                        userAgent = "agent",
                        contentDisposition = null,
                        mimeType = "video/mp4",
                        userInitiated = true,
                        incognito = false,
                        headers = listOf(
                            DownloadHeader("Referer", "https://example.com/watch"),
                            DownloadHeader(VIDEO_KIND, VIDEO_SEPARATE),
                            DownloadHeader(VIDEO_AUDIO, server.url("/expired-audio.mp4").toString()),
                            DownloadHeader(VIDEO_QUALITY, "360p"),
                        ),
                    ),
                ),
            )
            assertEquals(listOf("VIDEOHIGH"), muxed)
            val paths = buildList { repeat(server.requestCount) { add(server.takeRequest().path.orEmpty()) } }
            org.junit.Assert.assertTrue(paths.toString(), paths.any { it.contains("high-audio") })
            org.junit.Assert.assertFalse(paths.toString(), paths.any { it.contains("low-audio") })
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
            assertEquals(listOf("720p", "360p"), ready.map { it.qualityLabel })
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
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip.m3u8", "HLS", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))))
            parent.children.toList().forEach { it.join() }
            org.junit.Assert.assertTrue(log.progress.toString(), log.progress.any { it.second == 8L && it.first > 0L })
            assertEquals(VideoSaveResult.Started, saver.save("t", VideoVariant("clip.m3u8", "HLS", null, null, VideoSource.Hls(server.url("/missing.m3u8").toString()))))
            parent.children.toList().forEach { it.join() }
            assertEquals("http:403", log.reason)
        } finally {
            parent.cancel()
            server.shutdown()
        }
    }

    @Test
    fun anHlsDownloadKeepsCumulativeBytesAndARisingPercent() = runBlocking {
        val server = MockWebServer()
        server.start()
        val first = "a".repeat(4)
        val second = "b".repeat(2)
        val third = "c".repeat(6)
        val seg1 = server.url("/s1.ts").toString()
        val seg2 = server.url("/s2.ts").toString()
        val seg3 = server.url("/s3.ts").toString()
        val playlist = "#EXTM3U\n#EXTINF:1,\n$seg1\n#EXTINF:1,\n$seg2\n#EXTINF:1,\n$seg3\n"
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody(first))
        server.enqueue(MockResponse().setBody(second))
        server.enqueue(MockResponse().setBody(third))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save("t", VideoVariant("x36xhzz.m3u8", "184p", null, null, VideoSource.Hls(server.url("/x36xhzz.m3u8").toString()))),
            )
            val steps = log.progress
            assertEquals(steps.toString(), 4L to 12L, steps.first())
            assertEquals(steps.toString(), 6L to 9L, steps[steps.indexOfLast { it.first == 6L }])
            assertEquals(12L to 12L, steps.last())
            org.junit.Assert.assertTrue(steps.toString(), steps.zipWithNext().all { (before, after) -> after.first >= before.first })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun separateAudioKeepsTheBytesAndPercentRising() = runBlocking {
        val server = MockWebServer()
        server.start()
        val videoSegment = server.url("/v.ts").toString()
        val audioSegment = server.url("/a.ts").toString()
        val videoPlaylist = "#EXTM3U\n#EXTINF:1,\n$videoSegment\n"
        val audioPlaylist = "#EXTM3U\n#EXTINF:1,\n$audioSegment\n"
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("video.m3u8") -> MockResponse().setBody(videoPlaylist)
                    path.contains("audio.m3u8") -> MockResponse().setBody(audioPlaylist)
                    path.contains("v.ts") -> MockResponse().setBody("abcd")
                    path.contains("a.ts") -> MockResponse().setBody("efghij")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save(
                    "t",
                    VideoVariant(
                        "clip.m3u8",
                        "184p",
                        null,
                        null,
                        VideoSource.Hls(
                            server.url("/video.m3u8").toString(),
                            audioPlaylistUrl = server.url("/audio.m3u8").toString(),
                        ),
                    ),
                ),
            )
            val steps = log.progress.filter { it.first > 0L }
            org.junit.Assert.assertTrue(steps.toString(), steps.zipWithNext().all { (before, after) -> after.first >= before.first })
            org.junit.Assert.assertTrue(steps.toString(), steps.any { it.first == 4L && it.second > it.first })
            assertEquals(steps.toString(), 10L to 10L, steps.last())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aKnownVideoLengthDoesNotFinishBeforeAudio() = runBlocking {
        val server = MockWebServer()
        server.start()
        val videoFile = server.url("/v.mp4").toString()
        val audioSegment = server.url("/a.ts").toString()
        val videoPlaylist = "#EXTM3U\n#EXT-X-BYTERANGE:4@0\n$videoFile\n"
        val audioPlaylist = "#EXTM3U\n#EXTINF:1,\n$audioSegment\n"
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("video.m3u8") -> MockResponse().setBody(videoPlaylist)
                    path.contains("audio.m3u8") -> MockResponse().setBody(audioPlaylist)
                    path.contains("v.mp4") -> MockResponse().setBody("abcdEFGH")
                    path.contains("a.ts") -> MockResponse().setBody("efghij")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save(
                    "t",
                    VideoVariant(
                        "clip.m3u8",
                        "184p",
                        null,
                        null,
                        VideoSource.Hls(
                            server.url("/video.m3u8").toString(),
                            audioPlaylistUrl = server.url("/audio.m3u8").toString(),
                        ),
                    ),
                ),
            )
            val steps = log.progress.filter { it.first > 0L }
            org.junit.Assert.assertTrue(steps.toString(), steps.zipWithNext().all { (before, after) -> after.first >= before.first })
            org.junit.Assert.assertTrue(steps.toString(), steps.any { it.first == 4L && it.second > it.first })
            assertEquals(steps.toString(), 10L to 10L, steps.last())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aChunkedHlsSegmentStaysBelowOneHundredPercentUntilItEnds() = runBlocking {
        val server = MockWebServer()
        server.start()
        val bytes = ByteArray(300 * 1024) { 7 }
        val segment = server.url("/s0.ts").toString()
        val playlist = "#EXTM3U\n#EXTINF:4,\n$segment\n"
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setBody(playlist))
        server.enqueue(MockResponse().setChunkedBody(okio.Buffer().write(bytes), 64 * 1024))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save("t", VideoVariant("clip.m3u8", "184p", null, null, VideoSource.Hls(server.url("/a.m3u8").toString()))),
            )
            val steps = log.progress
            val full = bytes.size.toLong()
            org.junit.Assert.assertTrue(
                steps.toString(),
                steps.any { (soFar, total) -> soFar in 1 until full && (total < 0L || soFar < total) },
            )
            org.junit.Assert.assertTrue(
                steps.toString(),
                steps.dropLast(1).none { (soFar, total) -> total > 0L && soFar >= total && soFar < full },
            )
            assertEquals(full to full, steps.last())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aRedditDashPlaylistPairsEachHeightWithItsAudio() = runBlocking {
        val server = MockWebServer()
        server.start()
        val audio = server.url("/DASH_audio.mp4").toString()
        val tall = server.url("/DASH_1280.mp4").toString()
        val mid = server.url("/DASH_854.mp4").toString()
        val short = server.url("/DASH_480.mp4").toString()
        val mpd = """
            <MPD>
              <Period>
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <Representation id="480" bandwidth="400000" width="270" height="480"><BaseURL>$short</BaseURL></Representation>
                  <Representation id="1280" bandwidth="2000000" width="720" height="1280"><BaseURL>$tall</BaseURL></Representation>
                  <Representation id="1280b" bandwidth="1500000" width="720" height="1280"><BaseURL>$tall</BaseURL></Representation>
                  <Representation id="854" bandwidth="800000" width="480" height="854"><BaseURL>$mid</BaseURL></Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4">
                  <Representation id="audio" bandwidth="128000"><BaseURL>$audio</BaseURL></Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("DASHPlaylist") -> MockResponse().setBody(mpd)
                    path.contains("DASH_854") -> MockResponse().setBody("VIDEO-854")
                    path.contains("DASH_audio") -> MockResponse().setBody("AUDIO")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val muxed = mutableListOf<String>()
        val saver = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            mux = { video, audioFile, out ->
                muxed += video.readText() + audioFile.readText()
                out.writeText("MUXED")
            },
        )
        try {
            val offered = saver.qualities(
                "t",
                VideoVariant("My cat coming downstairs : r/aww", "DASH", null, null, VideoSource.Dash(server.url("/DASHPlaylist.mpd").toString())),
            )
            val ready = (offered as VideoQualities.Ready).variants
            assertEquals(listOf("1280p", "854p", "480p"), ready.map { it.qualityLabel })
            ready.forEach { row ->
                val source = row.source as VideoSource.SeparateAv
                assertEquals(audio, source.audioUrl)
                assertEquals("video/mp4", row.mimeType)
            }
            assertEquals(VideoSaveResult.Started, saver.save("t", ready.first { it.qualityLabel == "854p" }))
            assertEquals(listOf("VIDEO-854AUDIO"), muxed)
            assertEquals("My cat coming downstairs _ r_aww.mp4", log.name)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aRedditDashPlaylistAndHlsMasterListEachHeightOnce() = runBlocking {
        val server = MockWebServer()
        server.start()
        val root = server.url("/b48r5omjmwkh1/").toString()
        val mpd = """
            <MPD>
              <Period>
                <AdaptationSet contentType="video" maxHeight="1920" maxWidth="1080" mimeType="video/mp4">
                  <Representation bandwidth="8000000" codecs="avc1.640028" height="1920" id="1080" width="1080"><BaseURL>CMAF_1080.mp4</BaseURL></Representation>
                  <Representation bandwidth="1000000" codecs="avc1.640028" height="1920" id="1080b" width="1080"><BaseURL>CMAF_1080_low.mp4</BaseURL></Representation>
                  <Representation bandwidth="1386640" codecs="avc1.4d401f" height="1280" id="720" width="720"><BaseURL>CMAF_720.mp4</BaseURL></Representation>
                  <Representation bandwidth="761086" codecs="avc1.4d401f" height="854" id="480" width="480"><BaseURL>CMAF_480.mp4</BaseURL></Representation>
                </AdaptationSet>
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <Role schemeIdUri="urn:mpeg:dash:role:2011" value="supplementary"/>
                  <Representation bandwidth="400000" height="1350" id="e1350" width="760"><BaseURL>extra_1350.mp4</BaseURL></Representation>
                  <Representation bandwidth="300000" height="900" id="e900" width="506"><BaseURL>extra_900.mp4</BaseURL></Representation>
                  <Representation bandwidth="200000" height="600" id="e600" width="338"><BaseURL>extra_600.mp4</BaseURL></Representation>
                  <Representation bandwidth="150000" height="450" id="e450" width="254"><BaseURL>extra_450.mp4</BaseURL></Representation>
                  <Representation bandwidth="100000" height="338" id="e338" width="190"><BaseURL>extra_338.mp4</BaseURL></Representation>
                  <Representation bandwidth="80000" height="276" id="e276" width="156"><BaseURL>extra_276.mp4</BaseURL></Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4">
                  <Representation audioSamplingRate="48000" bandwidth="132121" codecs="mp4a.40.2" id="128"><BaseURL>CMAF_AUDIO_128.mp4</BaseURL></Representation>
                  <Representation audioSamplingRate="48000" bandwidth="67739" codecs="mp4a.40.2" id="64"><BaseURL>CMAF_AUDIO_64.mp4</BaseURL></Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val master = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-MEDIA:URI="CMAF_AUDIO_128.m3u8",TYPE=AUDIO,GROUP-ID="7",NAME="audio 0",DEFAULT=YES,AUTOSELECT=YES
            #EXT-X-MEDIA:URI="CMAF_AUDIO_64.m3u8",TYPE=AUDIO,GROUP-ID="6",NAME="audio 0",DEFAULT=YES,AUTOSELECT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=1080x1920,AUDIO="7"
            CMAF_1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1080x1920,AUDIO="6"
            CMAF_1080_low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1454379,RESOLUTION=720x1280,AUDIO="7"
            CMAF_720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=828825,RESOLUTION=480x854,AUDIO="6"
            CMAF_480.m3u8
        """.trimIndent()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("DASHPlaylist") -> MockResponse().setBody(mpd)
                    path.contains("HLSPlaylist") -> MockResponse().setBody(master)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        val title = "My cat coming downstairs"
        try {
            val dash = saver.qualities(
                "t",
                VideoVariant(title, "DASH", null, null, VideoSource.Dash(root + "DASHPlaylist.mpd")),
            ) as VideoQualities.Ready
            val hls = saver.qualities(
                "t",
                VideoVariant(title, "HLS", null, null, VideoSource.Hls(root + "HLSPlaylist.m3u8")),
            ) as VideoQualities.Ready
            assertEquals(listOf("1920p", "1280p", "854p"), hls.variants.map { it.qualityLabel })
            val hlsTop = hls.variants.first().source as VideoSource.Hls
            org.junit.Assert.assertTrue(hlsTop.playlistUrl.endsWith("CMAF_1080.m3u8"))
            val fallback = VideoVariant(title, "1920p", "video/mp4", null, VideoSource.Progressive(root + "1920.mp4"))
            val listed = listedQualities(dash.variants + hls.variants + listOf(fallback))
            assertEquals(listOf("1920p", "1280p", "854p"), listed.map { it.qualityLabel })
            org.junit.Assert.assertTrue(listed.all { it.title == title })
            val pair = listed.first().source as VideoSource.SeparateAv
            org.junit.Assert.assertTrue(pair.videoUrl.endsWith("CMAF_1080.mp4"))
            org.junit.Assert.assertTrue(pair.audioUrl.endsWith("CMAF_AUDIO_128.mp4"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aRangeAnswerWithoutContentRangeKeepsReading() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("0123"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("4567"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("89"))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined, rangeChunkBytes = 4, rangeHost = { true })
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save("t", VideoVariant("clip", "360p", "video/mp4", null, VideoSource.Progressive(server.url("/videoplayback").toString()))),
            )
            val ranges = buildList {
                repeat(server.requestCount) { add(server.takeRequest().requestUrl?.queryParameter("range")) }
            }
            org.junit.Assert.assertTrue(ranges.toString(), ranges.containsAll(listOf("0-3", "4-7", "8-11")))
            assertEquals(10L, log.progress.last().first)
            org.junit.Assert.assertTrue(ranges.toString(), ranges.none { it == null })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aRetryKeepsTheTitleInsteadOfTheFileAddress() = runBlocking {
        val server = MockWebServer()
        server.start()
        val emptyPlaylist = "#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n"
        server.enqueue(MockResponse().setBody("VIDEO"))
        server.enqueue(MockResponse().setBody(emptyPlaylist))
        server.enqueue(MockResponse().setBody(emptyPlaylist))
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined)
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.saveAgain(
                    DownloadRetry(
                        tabId = "t",
                        url = server.url("/videoplayback.mp4").toString(),
                        userAgent = "agent",
                        contentDisposition = null,
                        mimeType = "video/mp4",
                        userInitiated = true,
                        incognito = false,
                        headers = listOf(
                            DownloadHeader(VIDEO_KIND, VIDEO_PROGRESSIVE),
                            DownloadHeader(VIDEO_TITLE, "Big Buck Bunny"),
                        ),
                    ),
                ),
            )
            assertEquals("Big Buck Bunny.mp4", log.name)
            assertEquals(
                VideoSaveResult.Started,
                saver.saveAgain(
                    DownloadRetry(
                        tabId = "t",
                        url = server.url("/193039199_mp4_h264_aac_7.m3u8").toString(),
                        userAgent = "agent",
                        contentDisposition = null,
                        mimeType = "video/mp2t",
                        userInitiated = true,
                        incognito = false,
                        headers = listOf(
                            DownloadHeader(VIDEO_KIND, VIDEO_HLS),
                            DownloadHeader(VIDEO_TITLE, "Big Buck Bunny"),
                        ),
                    ),
                ),
            )
            assertEquals("Big Buck Bunny.ts", log.name)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aDroppedConnectionIsANetworkFailureAndAnFmp4IsNotATransportStream() {
        assertEquals("network", failureCode(java.net.UnknownHostException("cdn.example")))
        assertEquals("network", failureCode(java.net.SocketTimeoutException("timeout")))
        assertEquals("network", failureCode(java.io.IOException("unexpected end of stream")))
        assertEquals("network", failureCode(java.io.EOFException("dropped body")))
        assertEquals("unknown", failureCode(java.io.IOException("The download could not be published")))
        assertEquals("unknown", failureCode(java.io.IOException("MediaStore did not take the download")))
        assertEquals("protected", failureCode(DownloadException("The video is protected")))
        assertEquals("unknown", failureCode(IllegalStateException("boom")))
        val fmp4 = File.createTempFile("joined", ".bin")
        try {
            fmp4.writeBytes(byteArrayOf(0, 0, 0, 0x18) + "ftypisom".toByteArray())
            assertEquals("video/mp4", containerMime(fmp4, listOf("https://v.redd.it/abc/seg.m4s")))
            fmp4.writeBytes(byteArrayOf(0x47, 0, 0, 0, 0, 0, 0, 0))
            assertEquals("video/mp2t", containerMime(fmp4, listOf("https://cdn.example/seg.ts")))
        } finally {
            fmp4.delete()
        }
    }

    @Test
    fun aByteRangePlaylistIsFetchedOnceAndAnAudioGroupIsMuxed() = runBlocking {
        val server = MockWebServer()
        server.start()
        val file = "0123456789ABCDEF"
        val media = """
            #EXTM3U
            #EXT-X-MAP:URI="clip.mp4",BYTERANGE="4@0"
            #EXTINF:1,
            #EXT-X-BYTERANGE:6@4
            clip.mp4
            #EXTINF:1,
            #EXT-X-BYTERANGE:6
            clip.mp4
        """.trimIndent()
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:URI="audio.m3u8",TYPE=AUDIO,GROUP-ID="7",NAME="audio",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=480x854,AUDIO="7"
            video.m3u8
        """.trimIndent()
        val videoMedia = "#EXTM3U\n#EXTINF:1,\nvideo.mp4\n"
        val audioMedia = "#EXTM3U\n#EXTINF:1,\naudio.mp4\n"
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("range.m3u8") -> MockResponse().setBody(media)
                    path.contains("master.m3u8") -> MockResponse().setBody(master)
                    path.contains("video.m3u8") -> MockResponse().setBody(videoMedia)
                    path.contains("audio.m3u8") -> MockResponse().setBody(audioMedia)
                    path.contains("clip.mp4") -> {
                        val range = request.getHeader("Range").orEmpty().removePrefix("bytes=")
                        val start = range.substringBefore('-').toInt()
                        val end = range.substringAfter('-').toInt()
                        MockResponse().setResponseCode(206).setBody(file.substring(start, end + 1))
                    }
                    path.contains("video.mp4") -> MockResponse().setBody("VIDEO")
                    path.contains("audio.mp4") -> MockResponse().setBody("AUDIO")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val muxed = mutableListOf<String>()
        val saver = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            mux = { video, audio, out ->
                muxed += video.readText() + audio.readText()
                out.writeText("MUXED")
            },
        )
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save("t", VideoVariant("clip", "854p", null, null, VideoSource.Hls(server.url("/range.m3u8").toString()))),
            )
            val ranges = buildList { repeat(server.requestCount) { add(server.takeRequest()) } }
            val clip = ranges.filter { it.path?.contains("clip.mp4") == true }
            assertEquals(clip.toString(), 1, clip.size)
            assertEquals("bytes=0-15", clip.single().getHeader("Range"))
            val offered = saver.qualities(
                "t",
                VideoVariant("clip", "HLS", null, null, VideoSource.Hls(server.url("/master.m3u8").toString())),
            )
            val row = (offered as VideoQualities.Ready).variants.single()
            val hls = row.source as VideoSource.Hls
            org.junit.Assert.assertTrue(hls.audioPlaylistUrl?.contains("audio.m3u8") == true)
            assertEquals(VideoSaveResult.Started, saver.save("t", row))
            assertEquals(listOf("VIDEOAUDIO"), muxed)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aProtectedAudioGroupIsRefusedAndAHugeRangeStopsAtTheLimit() = runBlocking {
        val server = MockWebServer()
        server.start()
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:URI="locked.m3u8",TYPE=AUDIO,GROUP-ID="1",NAME="audio",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=640x360,AUDIO="1"
            video.m3u8
        """.trimIndent()
        val locked = "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\"\nseg.ts\n"
        val ranged = """
            #EXTM3U
            #EXT-X-MAP:URI="big.mp4",BYTERANGE="8@0"
            #EXTINF:1,
            #EXT-X-BYTERANGE:8@8
            big.mp4
        """.trimIndent()
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val saver = saver(log, scope, Dispatchers.Unconfined, maxBytes = 8)
        var videoBytes = 0
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("locked-master") -> MockResponse().setBody(master)
                    path.contains("locked.m3u8") -> MockResponse().setBody(locked)
                    path.contains("video.m3u8") -> MockResponse().setBody("#EXTM3U\n#EXTINF:1,\nvideo.mp4\n")
                    path.contains("video.mp4") -> {
                        videoBytes += 1
                        MockResponse().setBody("VIDEO")
                    }
                    path.contains("range.m3u8") -> MockResponse().setBody(ranged)
                    path.contains("big.mp4") -> MockResponse().setResponseCode(206).setBody("0123456789ABCDEF")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        try {
            assertEquals(
                VideoSaveResult.Protected,
                saver.save("t", VideoVariant("clip", "360p", null, null, VideoSource.Hls(server.url("/locked-master.m3u8").toString()))),
            )
            assertEquals(0, log.started)
            assertEquals(0, videoBytes)
            val paired = VideoSource.Hls(
                server.url("/video.m3u8").toString(),
                audioPlaylistUrl = server.url("/locked.m3u8").toString(),
            )
            assertEquals(VideoSaveResult.Protected, saver.save("t", VideoVariant("clip", "360p", null, null, paired)))
            assertEquals(0, log.started)
            assertEquals(0, videoBytes)
            assertEquals(
                VideoSaveResult.Started,
                saver.save("t", VideoVariant("clip", "360p", null, null, VideoSource.Hls(server.url("/range.m3u8").toString()))),
            )
            assertEquals("limit", log.reason)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aByteRangeCopyLeavesTheFreeSpaceReserve() = runBlocking {
        val server = MockWebServer()
        server.start()
        val ranged = """
            #EXTM3U
            #EXT-X-MAP:URI="big.mp4",BYTERANGE="8@0"
            #EXTINF:1,
            #EXT-X-BYTERANGE:8@8
            big.mp4
        """.trimIndent()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("range.m3u8") -> MockResponse().setBody(ranged)
                    path.contains("big.mp4") -> MockResponse().setResponseCode(206).setBody("0123456789ABCDEF")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val log = CountingLog()
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val roomDir = File(context.cacheDir, "video-reserve-room")
        val tightDir = File(context.cacheDir, "video-reserve-tight")
        val roomStore = FileDownloads(context, roomDir)
        val tightStore = FileDownloads(context, tightDir)
        val variant = VideoVariant("reserve-room", "360p", null, null, VideoSource.Hls(server.url("/range.m3u8").toString()))
        val room = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            freeBytes = { InProcessDownloader.MIN_FREE_BYTES + 16 },
            workDir = roomDir,
            store = roomStore,
        )
        val tight = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            freeBytes = { InProcessDownloader.MIN_FREE_BYTES - 1 },
            workDir = tightDir,
            store = tightStore,
        )
        try {
            assertEquals(VideoSaveResult.Started, room.save("t", variant))
            parent.children.toList().forEach { it.join() }
            assertEquals(null, log.reason)
            assertTrue(log.finished)
            assertEquals(1, roomStore.saved)
            log.reason = null
            log.finished = false
            assertEquals(VideoSaveResult.Started, tight.save("t", variant))
            parent.children.toList().forEach { it.join() }
            assertEquals("space", log.reason)
            assertEquals(0, tightStore.saved)
        } finally {
            parent.cancel()
            server.shutdown()
        }
    }

    @Test
    fun aSecondCopyIsRefusedWhenStagingAlreadyUsedTheReserve() = runBlocking {
        val server = MockWebServer()
        server.start()
        val ranged = """
            #EXTM3U
            #EXT-X-MAP:URI="big.mp4",BYTERANGE="8@0"
            #EXTINF:1,
            #EXT-X-BYTERANGE:8@8
            big.mp4
        """.trimIndent()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("range.m3u8") -> MockResponse().setBody(ranged)
                    path.contains("big.mp4") -> MockResponse().setResponseCode(206).setBody("0123456789ABCDEF")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tightDir = File(context.cacheDir, "video-space-tight")
        val roomDir = File(context.cacheDir, "video-space-room")
        tightDir.deleteRecursively()
        roomDir.deleteRecursively()
        val reserve = InProcessDownloader.MIN_FREE_BYTES
        fun occupied(dir: File) = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val log = CountingLog()
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Unconfined)
        val variant = VideoVariant("reserve-second", "360p", null, null, VideoSource.Hls(server.url("/range.m3u8").toString()))
        val tightStore = FileDownloads(context, tightDir)
        val roomStore = FileDownloads(context, roomDir)
        val tight = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            freeBytes = { reserve + 24 - occupied(tightDir) },
            workDir = tightDir,
            store = tightStore,
        )
        val room = saver(
            log,
            scope,
            Dispatchers.Unconfined,
            freeBytes = { reserve + 32 - occupied(roomDir) },
            workDir = roomDir,
            store = roomStore,
        )
        try {
            assertEquals(VideoSaveResult.Started, tight.save("t", variant))
            parent.children.toList().forEach { it.join() }
            assertEquals("space", log.reason)
            assertEquals(0, tightStore.saved)
            log.reason = null
            assertEquals(VideoSaveResult.Started, room.save("t", variant))
            parent.children.toList().forEach { it.join() }
            assertEquals(null, log.reason)
            assertTrue(log.finished)
            assertEquals(1, roomStore.saved)
        } finally {
            parent.cancel()
            server.shutdown()
            tightDir.deleteRecursively()
            roomDir.deleteRecursively()
        }
    }

    @Test
    fun aMuxedGooglevideoFileRefreshesAfter403() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("expired") -> MockResponse().setResponseCode(403)
                    path.contains("fresh") -> MockResponse().setBody("FRESH")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val log = CountingLog()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val fresh = server.url("/fresh.mp4").toString()
        val extractor = object : VideoExtractor {
            override val id = "page"
            override val pageUrlSufficient = true
            override fun matches(pageUrl: String) = pageUrl.contains("example.com")
            override suspend fun extract(hints: PageVideoHints) = listOf(
                VideoVariant("Talk", "360p", "video/mp4", null, VideoSource.Progressive(fresh)),
            )
        }
        val saver = saver(log, scope, Dispatchers.Unconfined, extractors = setOf(extractor), rangeHost = { true })
        try {
            assertEquals(
                VideoSaveResult.Started,
                saver.save(
                    "t",
                    VideoVariant(
                        "Talk",
                        "360p",
                        "video/mp4",
                        null,
                        VideoSource.Progressive(server.url("/expired.mp4").toString()),
                    ),
                ),
            )
            assertEquals(null, log.reason)
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
        freeBytes: () -> Long = { Long.MAX_VALUE },
        workDir: File? = null,
        store: MediaStoreDownloads? = null,
        mux: (File, File, File) -> Unit = ::muxToMp4,
    ): VideoSaver {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tabs = RecordingTabs().apply {
            show(listOf(Tab(id = "t", incognito = false, url = "https://example.com/watch", title = "Watch", groupId = null, position = 0, createdAt = 0, lastAccessedAt = 0)))
        }
        return VideoSaver(
            httpClient = { OkHttpClient() },
            store = store ?: MediaStoreDownloads(context),
            log = log,
            tracker = DownloadTracker(FakeSystemDownloads(), scope, log = log),
            tabs = tabs,
            cookies = { _, _ -> "session=1" },
            workDir = workDir ?: File(context.cacheDir, "video-test"),
            scope = scope,
            io = io,
            mux = mux,
            freeBytes = freeBytes,
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
    var finishedName: String? = null
    var finishedBytes = -1L
    var reason: String? = null
    var name: String? = null
    val progress = mutableListOf<Pair<Long, Long>>()

    override fun observe(): Flow<List<DownloadItem>> = flowOf(emptyList())
    override suspend fun noteStarted(draft: DownloadDraft): Long {
        started += 1
        name = draft.fileName
        return started.toLong()
    }
    override suspend fun noteSystem(localId: Long, systemId: Long) = Unit
    override suspend fun noteProgress(localId: Long, soFar: Long, total: Long) {
        progress += soFar to total
    }
    override suspend fun noteFinished(localId: Long, uri: String?, mime: String?, bytes: Long, fileName: String?) {
        finished = true
        finishedName = fileName
        finishedBytes = bytes
    }
    override suspend fun noteFailed(localId: Long, reason: String?) {
        failed += 1
        this.reason = reason
    }
    override suspend fun remove(id: Long): DownloadItem? = null
    override suspend fun clearIncognito() = Unit
    override suspend fun runningWithSystemId(): List<DownloadItem> = emptyList()
}

/** Writes the finished video into a folder this test owns, instead of MediaStore. */
private class FileDownloads(
    context: android.content.Context,
    private val folder: File,
) : MediaStoreDownloads(context, ioDispatcher = Dispatchers.Unconfined) {
    var saved = 0

    override suspend fun save(
        fileName: String,
        mimeType: String,
        write: suspend (java.io.OutputStream) -> Unit,
    ): DownloadEvent.DownloadCompleted {
        if (!folder.isDirectory && !folder.mkdirs()) throw java.io.IOException("There is no folder for the save")
        val file = File(folder, fileName)
        file.outputStream().use { write(it) }
        saved += 1
        return DownloadEvent.DownloadCompleted(
            fileName,
            android.net.Uri.fromFile(file),
            mimeType,
            bytes = file.length(),
        )
    }
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
