package com.dantech.skimbrowser.domain.video

import com.dantech.skimbrowser.domain.download.DownloadHeader
import com.dantech.skimbrowser.domain.download.DownloadRetry
import com.dantech.skimbrowser.domain.download.VIDEO_HLS
import com.dantech.skimbrowser.domain.download.VIDEO_KIND
import com.dantech.skimbrowser.domain.download.VIDEO_QUALITY
import com.dantech.skimbrowser.domain.download.VIDEO_TITLE
import com.dantech.skimbrowser.domain.download.VIDEO_PROGRESSIVE
import com.dantech.skimbrowser.domain.download.videoKind
import com.dantech.skimbrowser.domain.download.wireHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExtractTest {

    @Test
    fun aMediaAddressIsAFileOrAPlaylistAndASegmentIsNeither() {
        assertEquals(MediaStream.Progressive, mediaStream("https://cdn.example/v.mp4", null))
        assertEquals(MediaStream.Progressive, mediaStream("https://cdn.example/clip", "video/webm"))
        assertEquals(MediaStream.Hls, mediaStream("https://cdn.example/a.m3u8?x=1", null))
        assertEquals(MediaStream.Dash, mediaStream("https://cdn.example/p", "application/dash+xml"))
        assertNull(mediaStream("https://cdn.example/seg-1.ts", null))
        assertNull(mediaStream("https://cdn.example/seg.m4s", "video/mp2t"))
        assertNull(mediaStream("https://cdn.example/poster.jpg", "image/jpeg"))
    }

    @Test
    fun sampleAesAndWidevinePlaylistsAreProtectedAndAClearPlaylistKeepsItsSegments() {
        val clear = """
            #EXTM3U
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4,
            https://cdn.example/seg.mp4
        """.trimIndent()
        val parsed = parseHls(clear, "https://cdn.example/master.m3u8")
        assertFalse(parsed.protected)
        assertEquals(listOf("https://cdn.example/seg.mp4"), parsed.segments)

        val sample = "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\"\nseg.ts"
        assertTrue(parseHls(sample, "https://cdn.example/a.m3u8").protected)
        val widevine = "#EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\",URI=\"data:text/plain,widevine\""
        assertTrue(hlsProtected(widevine))
        assertTrue(dashProtected("<MPD><ContentProtection schemeIdUri=\"urn:uuid:edef8ba9\"/></MPD>"))
    }

    @Test
    fun aDashManifestWithSeparateAudioNamesBothFiles() {
        val mpd = """
            <MPD>
              <AdaptationSet contentType="video">
                <Representation height="720" bandwidth="800000"><BaseURL>DASH_720.mp4</BaseURL></Representation>
              </AdaptationSet>
              <AdaptationSet contentType="audio">
                <Representation bandwidth="128000"><BaseURL>DASH_AUDIO.mp4</BaseURL></Representation>
              </AdaptationSet>
            </MPD>
        """.trimIndent()
        val manifest = parseDash(mpd, "https://v.redd.it/abc/DASHPlaylist.mpd")
        assertFalse(manifest.protected)
        assertEquals("https://v.redd.it/abc/DASH_720.mp4", manifest.videos.single().url)
        val encoded = """<MPD><AdaptationSet contentType="video"><Representation><BaseURL>https://video.fbcdn.net/v.mp4?oh=1&amp;oe=2</BaseURL></Representation></AdaptationSet></MPD>"""
        assertEquals(
            "https://video.fbcdn.net/v.mp4?oh=1&oe=2",
            parseDash(encoded, "https://www.facebook.com/reel/1").videos.single().url,
        )
        assertEquals("https://v.redd.it/abc/DASH_AUDIO.mp4", manifest.audios.single().url)
    }

    @Test
    fun aMasterPlaylistNamesEachRendition() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720
            720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=200000,RESOLUTION=640x360
            360.m3u8
        """.trimIndent()
        val playlist = parseHls(master, "https://cdn.example/master.m3u8")
        assertEquals("https://cdn.example/720.m3u8", playlist.variants.first().url)
        assertEquals(800000, playlist.variants.first().bandwidth)
        assertEquals("640x360", playlist.variants.last().resolution)
    }

    @Test
    fun theGenericExtractorOffersFilesSeenOnAnyPage() = runTest {
        val hints = hints(
            "https://news.example/story",
            "Story",
            listOf(
                "https://cdn.example/a.mp4",
                "https://cdn.example/a.m3u8",
                "https://cdn.example/seg.ts",
                "${PAGE_DOCUMENT}{}",
            ),
        )
        val variants = GenericVideoExtractor().extract(hints)
        assertEquals(listOf(MediaStream.Progressive, MediaStream.Hls), variants.map { streamOf(it) })
        assertEquals("https://news.example/story", (variants.first().source as VideoSource.Progressive).headers["Referer"])
    }

    @Test
    fun tiktokJsonInThePageBecomesProgressiveFiles() = runTest {
        val json = """
            {"video":{"download_addr":{"url_list":["https://v.tiktok.com/a/download.mp4"]},
            "bit_rate":[{"gear_name":"720","play_addr":{"url_list":["https://v.tiktok.com/a/720.mp4"]}}]}}
        """.trimIndent()
        val variants = TikTokVideoExtractor().extract(hints("https://www.tiktok.com/@a/video/1", "clip", listOf(PAGE_DOCUMENT + json)))
        assertEquals(setOf("https://v.tiktok.com/a/download.mp4", "https://v.tiktok.com/a/720.mp4"), variants.map { (it.source as VideoSource.Progressive).url }.toSet())
        assertTrue(variants.any { it.qualityLabel == "720p" })
        assertFalse(TikTokVideoExtractor().matches("https://example.com/"))
        val mixed = """
            {"cover":{"url_list":["https://p16.tiktokcdn.com/cover.jpeg"]},
            "music":{"play_url":{"url_list":["https://sf16.tiktokcdn.com/a.mp3"]}},
            "video":{"play_addr":{"url_list":["https://v.tiktok.com/a/720.mp4"]}}}
        """.trimIndent()
        val onlyVideo = TikTokVideoExtractor().extract(hints("https://www.tiktok.com/@a/video/2", "clip", listOf(PAGE_DOCUMENT + mixed)))
        assertEquals(listOf("https://v.tiktok.com/a/720.mp4"), onlyVideo.map { (it.source as VideoSource.Progressive).url })
    }

    @Test
    fun facebookReelsAndFbWatchReadThePageFilesAndSkipAProtectedManifest() = runTest {
        val extractor = FacebookVideoExtractor()
        assertTrue(extractor.matches("https://fb.watch/abc"))
        assertTrue(extractor.matches("https://www.facebook.com/reel/1"))
        val clear = """
            {"playable_url":"https://video.fbcdn.net/sd.mp4","playable_url_quality_hd":"https://video.fbcdn.net/hd.mp4"}
        """.trimIndent()
        val saved = extractor.extract(hints("https://m.facebook.com/reel/1", null, listOf(PAGE_DOCUMENT + clear)))
        assertEquals("HD", saved.first { (it.source as VideoSource.Progressive).url.endsWith("hd.mp4") }.qualityLabel)
        val locked = """{"dash_manifest":"<MPD><ContentProtection schemeIdUri=\"widevine\"/></MPD>"}"""
        assertTrue(extractor.extract(hints("https://www.facebook.com/watch", null, listOf(PAGE_DOCUMENT + locked))).isEmpty())
    }

    @Test
    fun facebookDashWithSeparateAudioIsOneMuxedVariant() = runTest {
        val xml = """
            <MPD><AdaptationSet contentType="video"><Representation height="1080"><BaseURL>https://video.fbcdn.net/v.mp4</BaseURL></Representation></AdaptationSet>
            <AdaptationSet contentType="audio"><Representation><BaseURL>https://video.fbcdn.net/a.mp4</BaseURL></Representation></AdaptationSet></MPD>
        """.trimIndent()
        val json = """{"dash_manifest":${kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<String>(), xml)}}"""
        val variants = FacebookVideoExtractor().extract(hints("https://www.facebook.com/reel/9", "reel", listOf(PAGE_DOCUMENT + json)))
        val source = variants.single().source as VideoSource.SeparateAv
        assertEquals("https://video.fbcdn.net/v.mp4", source.videoUrl)
        assertEquals("https://video.fbcdn.net/a.mp4", source.audioUrl)
    }

    @Test
    fun redditOffersTheDashManifestTheHlsPlaylistAndTheFallbackFile() = runTest {
        val json = """
            {"reddit_video":{"dash_url":"https://v.redd.it/abc/DASHPlaylist.mpd",
            "hls_url":"https://v.redd.it/abc/HLSPlaylist.m3u8",
            "fallback_url":"https://v.redd.it/abc/DASH_720.mp4?source=fallback"}}
        """.trimIndent()
        val variants = RedditVideoExtractor().extract(hints("https://www.reddit.com/r/videos/comments/1/x", "post", listOf(PAGE_DOCUMENT + json)))
        assertTrue(variants.any { it.source is VideoSource.Dash })
        assertTrue(variants.any { it.source is VideoSource.Hls })
        val file = variants.first { it.source is VideoSource.Progressive }
        assertEquals("720p", file.qualityLabel)
        assertTrue(RedditVideoExtractor().matches("https://v.redd.it/abc"))
    }

    @Test
    fun aYoutubeSeparatePairIsKeptAndARefusalSurfacesWhenNothingElseSaves() = runTest {
        val page = "https://www.youtube.com/watch?v=abc"
        val separate = VideoVariant(
            "Talk",
            "1080p separate audio",
            "video/mp4",
            50_000_000,
            VideoSource.SeparateAv("https://rr.example/video", "https://rr.example/audio"),
        )
        val youtube = extractor("youtube", page) { listOf(separate) }
        val generic = GenericVideoExtractor()
        val hints = hints(page, "Talk", listOf("https://cdn.example/also.mp4"))
        val collected = collectVariants(listOf(generic, youtube), hints)
        assertEquals(
            listOf("https://rr.example/video|https://rr.example/audio", "https://cdn.example/also.mp4"),
            collected.variants.map { it.source.address() },
        )
        assertNull(collected.failure)
        assertTrue(collected.variants.first().source is VideoSource.SeparateAv)

        val refused = collectVariants(listOf(extractor("youtube", page) { throw VideoExtractException(VideoExtractFailure.DRM) }), hints(page, "Talk", emptyList()))
        assertTrue(refused.variants.isEmpty())
        assertEquals(VideoExtractFailure.DRM, refused.failure)

        val broken = extractor("site", page) { throw IllegalStateException("boom") }
        val stillThere = collectVariants(listOf(broken, youtube), hints(page, "Talk", emptyList()))
        assertEquals(listOf(separate.source.address()), stillThere.variants.map { it.source.address() })

        try {
            collectVariants(listOf(extractor("youtube", page) { throw CancellationException("stop") }), hints(page, "Talk", emptyList()))
            error("cancellation should leave collectVariants")
        } catch (cancelled: CancellationException) {
            assertEquals("stop", cancelled.message)
        }
    }

    @Test
    fun aRetryKeepsTheReferrerAndDropsTheCookie() {
        val retry = DownloadRetry(
            tabId = "t",
            url = "https://cdn.example/a.mp4",
            userAgent = "agent",
            contentDisposition = null,
            mimeType = "video/mp4",
            userInitiated = true,
            incognito = false,
            headers = listOf(
                DownloadHeader("Referer", "https://www.tiktok.com/v"),
                DownloadHeader("Cookie", "session=secret"),
                DownloadHeader(VIDEO_KIND, VIDEO_PROGRESSIVE),
            ),
        )
        assertEquals(mapOf("Referer" to "https://www.tiktok.com/v"), retry.wireHeaders())
        assertEquals(VIDEO_PROGRESSIVE, retry.videoKind())
        val again = variantFromRetry(
            DownloadRetry(
                tabId = "t",
                url = "https://cdn.example/v.mp4",
                userAgent = "agent",
                contentDisposition = null,
                mimeType = "video/mp4",
                userInitiated = true,
                incognito = false,
                headers = listOf(
                    DownloadHeader("Referer", "https://www.youtube.com/watch?v=abc"),
                    DownloadHeader(VIDEO_KIND, com.dantech.skimbrowser.domain.download.VIDEO_SEPARATE),
                    DownloadHeader(com.dantech.skimbrowser.domain.download.VIDEO_AUDIO, "https://cdn.example/a.m4a"),
                    DownloadHeader(VIDEO_QUALITY, "360p"),
                    DownloadHeader(VIDEO_TITLE, "Big Buck Bunny"),
                ),
            ),
        )
        assertEquals("360p", again?.qualityLabel)
        assertEquals("Big Buck Bunny", again?.title)
        val fromAddress = variantFromRetry(
            DownloadRetry(
                tabId = "t",
                url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                userAgent = null,
                contentDisposition = null,
                mimeType = "video/mp2t",
                userInitiated = true,
                incognito = false,
                headers = listOf(DownloadHeader(VIDEO_KIND, VIDEO_HLS)),
            ),
        )
        assertEquals("x36xhzz.m3u8", fromAddress?.title)
        val separate = again?.source as VideoSource.SeparateAv
        assertEquals("https://cdn.example/a.m4a", separate.audioUrl)
        assertEquals(null, separate.headers[VIDEO_QUALITY])
    }

    @Test
    fun aPageWhoseTitleIsItsAddressUsesTheLastPathSegment() = runTest {
        val page = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val variants = GenericVideoExtractor().extract(hints(page, page, listOf(page)))
        assertEquals("x36xhzz.m3u8", variants.single().title)
        assertEquals("Cat", videoCaption("Cat | Facebook | Facebook"))
        assertEquals("clip", videoCaption("clip _ TikTok"))
    }

    @Test
    fun pairedQualitiesReplaceTheVideoOnlyPlaylistAndStaySorted() {
        val variants = listOf(
            VideoVariant("cat", "480p", "video/mp4", null, VideoSource.SeparateAv("https://v.redd.it/abc/DASH_480.mp4", "https://v.redd.it/abc/DASH_audio.mp4")),
            VideoVariant("cat", "1280p", "video/mp4", null, VideoSource.SeparateAv("https://v.redd.it/abc/DASH_1280.mp4", "https://v.redd.it/abc/DASH_audio.mp4")),
            VideoVariant("cat", "854p", "video/mp2t", null, VideoSource.Hls("https://v.redd.it/abc/HLS_854.m3u8")),
            VideoVariant("cat", "HLS", null, null, VideoSource.Hls("https://v.redd.it/abc/HLSPlaylist.m3u8")),
            VideoVariant("cat", "720p", "video/mp4", null, VideoSource.Progressive("https://v.redd.it/abc/DASH_720.mp4")),
            VideoVariant("Talk", "360p", "video/mp4", null, VideoSource.Progressive("https://rr.googlevideo.com/videoplayback?id=1")),
            VideoVariant("other", "720p", "video/mp2t", null, VideoSource.Hls("https://cdn.example/other/master.m3u8")),
            VideoVariant("other", "720p", "video/mp4", null, VideoSource.Progressive("https://cdn.example/other/clip.mp4")),
        )
        val listed = listedQualities(variants)
        assertEquals(listOf("1280p", "720p", "720p", "480p", "360p"), listed.map { it.qualityLabel })
        assertTrue(listed.any { (it.source as? VideoSource.Hls)?.playlistUrl == "https://cdn.example/other/master.m3u8" })
        assertTrue(listed.any { (it.source as? VideoSource.Progressive)?.url == "https://cdn.example/other/clip.mp4" })
        assertTrue(listed.none { (it.source as? VideoSource.Hls)?.playlistUrl?.contains("v.redd.it") == true })
        assertTrue(listed.none { (it.source as? VideoSource.Progressive)?.url?.contains("DASH_") == true })
    }

    @Test
    fun aPairedVideoFileIsNotAlsoOfferedWithoutAudio() {
        val variants = listOf(
            VideoVariant(
                "reel",
                "1080p",
                "video/mp4",
                null,
                VideoSource.SeparateAv("https://video.fbcdn.net/v.mp4?oh=1", "https://video.fbcdn.net/a.mp4"),
            ),
            VideoVariant("reel", "Video", "video/mp4", null, VideoSource.Progressive("https://video.fbcdn.net/v.mp4?oh=2")),
            VideoVariant("other", "SD", "video/mp4", null, VideoSource.Progressive("https://video.fbcdn.net/other.mp4")),
        )
        val listed = listedQualities(variants)
        assertEquals(listOf("1080p", "SD"), listed.map { it.qualityLabel })
        assertTrue(listed.none { (it.source as? VideoSource.Progressive)?.url?.contains("/v.mp4") == true })
        assertTrue(listed.any { (it.source as? VideoSource.Progressive)?.url == "https://video.fbcdn.net/other.mp4" })
    }

    @Test
    fun aMuxedGooglevideoRenditionStaysBesideASeparateOne() {
        val variants = listOf(
            VideoVariant(
                "Talk",
                "1080p",
                "video/mp4",
                null,
                VideoSource.SeparateAv(
                    "https://rr.googlevideo.com/videoplayback?id=clip&itag=137",
                    "https://rr.googlevideo.com/videoplayback?id=clip&itag=140",
                ),
            ),
            VideoVariant(
                "Talk",
                "360p",
                "video/mp4",
                null,
                VideoSource.Progressive("https://rr.googlevideo.com/videoplayback?id=clip&itag=18"),
            ),
        )
        assertEquals(listOf("1080p", "360p"), listedQualities(variants).map { it.qualityLabel })
    }

    private fun extractor(
        name: String,
        page: String,
        extract: suspend (PageVideoHints) -> List<VideoVariant>,
    ): VideoExtractor = object : VideoExtractor {
        override val id: String = name
        override fun matches(pageUrl: String): Boolean = pageUrl == page
        override suspend fun extract(hints: PageVideoHints): List<VideoVariant> = extract(hints)
    }

    private fun hints(page: String, title: String?, media: List<String>) = PageVideoHints(
        pageUrl = page,
        pageTitle = title,
        mediaUrls = media,
        userAgent = "test",
        cookieHeader = "session=secret",
    )

    private fun streamOf(variant: VideoVariant): MediaStream = when (variant.source) {
        is VideoSource.Progressive -> MediaStream.Progressive
        is VideoSource.Hls -> MediaStream.Hls
        is VideoSource.Dash -> MediaStream.Dash
        is VideoSource.SeparateAv -> MediaStream.Progressive
    }
}
