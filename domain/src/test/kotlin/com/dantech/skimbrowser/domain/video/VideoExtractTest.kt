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
import kotlinx.coroutines.runBlocking
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
        assertEquals(MediaStream.Progressive, mediaStream("https://cdn.example/v.mov", null))
        assertEquals(MediaStream.Progressive, mediaStream("https://cdn.example/clip", "video/webm"))
        assertEquals(MediaStream.Hls, mediaStream("https://cdn.example/a.m3u8?x=1", null))
        assertEquals(MediaStream.Dash, mediaStream("https://cdn.example/p", "application/dash+xml"))
        assertNull(mediaStream("https://cdn.example/seg-1.ts", null))
        assertNull(mediaStream("https://cdn.example/seg.m4s", "video/mp2t"))
        assertNull(mediaStream("https://cdn.example/poster.jpg", "image/jpeg"))
    }

    @Test
    fun aSnifferKeepsWholeFilesAndMapsASegmentToItsPlaylist() {
        assertEquals(MediaStream.Progressive, (sightMedia(MediaLook("https://cdn.example/v.mp4"), false) as MediaSight.File).stream)
        assertEquals(MediaStream.Progressive, (sightMedia(MediaLook("https://cdn.example/v.webm"), false) as MediaSight.File).stream)
        assertEquals(MediaStream.Progressive, (sightMedia(MediaLook("https://cdn.example/v.mov"), false) as MediaSight.File).stream)
        assertEquals(MediaStream.Progressive, (sightMedia(MediaLook("https://cdn.example/v.m4v"), false) as MediaSight.File).stream)
        assertEquals(MediaStream.Hls, (sightMedia(MediaLook("https://cdn.example/a.m3u8"), false) as MediaSight.File).stream)
        assertEquals(MediaStream.Dash, (sightMedia(MediaLook("https://cdn.example/a.mpd"), false) as MediaSight.File).stream)
        val ranged = sightMedia(MediaLook("https://video.example/playback", range = "bytes=0-"), tracker = false)
        assertEquals("https://video.example/playback", (ranged as MediaSight.File).url)
        val accepted = sightMedia(MediaLook("https://video.example/clip", accept = "video/mp4, */*;q=0.8"), tracker = false)
        assertEquals(MediaStream.Progressive, (accepted as MediaSight.File).stream)
        assertNull(sightMedia(MediaLook("https://cdn.example/app.js", range = "bytes=0-"), false))
        assertNull(sightMedia(MediaLook("https://cdn.example/thumb.jpg"), false))
        assertNull(sightMedia(MediaLook("https://cdn.example/thumbs/poster.mp4"), false))
        assertNull(sightMedia(MediaLook("https://ads.example/ad.mp4"), tracker = true))
        assertNull(sightMedia(MediaLook("blob:https://news.example/1"), false))
        assertNull(sightMedia(MediaLook("https://cdn.example/nope"), false))
        assertNull(sightMedia(MediaLook("https://cdn.example/song.mp3", range = "bytes=0-"), false))
        assertNull(sightMedia(MediaLook("https://cdn.example/song.m4a", range = "bytes=0-1"), false))
        assertNull(sightMedia(MediaLook("https://cdn.example/song", accept = "audio/mpeg, video/*;q=0.1"), false))
        assertEquals("https://news.example/story", playingPage("""{"page":"https://news.example/story","urls":[],"docs":[]}"""))
        assertNull(playingPage("{}"))
        assertEquals(MediaStream.Hls, textPlaylist("#EXTM3U\n#EXTINF:1,\nseg.ts\n"))
        assertEquals(MediaStream.Dash, textPlaylist("<?xml version=\"1.0\"?><MPD></MPD>"))
        assertNull(textPlaylist("\u0000\u0000\u0000 ftypisom"))

        val playlist = "https://cdn.example/hls/master.m3u8"
        val nested = "https://cdn.example/hls/720/index.m3u8"
        val segment = sightMedia(
            MediaLook("https://cdn.example/hls/720/seg-1.ts"),
            tracker = false,
            knownManifests = listOf(playlist, nested, "https://cdn.example/other/clip.mp4"),
        ) as MediaSight.Segment
        assertEquals(nested, segment.manifestUrl)
        val alone = sightMedia(MediaLook("https://cdn.example/hls/seg-1.m4s"), tracker = false) as MediaSight.Segment
        assertNull(alone.manifestUrl)
    }

    @Test
    fun aProbeDropsBlobMediaSourceAndThumbnailsAndKeepsAPlayingFile() {
        val json = """
            {"urls":[
              "https://cdn.example/direct.mp4",
              "blob:https://news.example/1",
              "mediasource:https://news.example/2",
              {"src":"https://cdn.example/playing.mp4","duration":12.5,"width":1280,"height":720},
              {"src":"https://cdn.example/thumb.mp4","duration":1.2,"width":160,"height":90},
              {"src":"blob:https://news.example/3","duration":20,"width":1920,"height":1080}
            ],"docs":["{\"play_addr\":\"https://v.example/a.mp4\"}"]}
        """.trimIndent()
        val found = mediaFromProbe(json)
        assertEquals(
            listOf("https://cdn.example/direct.mp4", "https://cdn.example/playing.mp4"),
            found.filterNot { it.startsWith(PAGE_DOCUMENT) },
        )
        assertTrue(found.any { it.startsWith(PAGE_DOCUMENT) })
        assertTrue(isThumbnailSample(1.2, 160, 90))
        assertFalse(isThumbnailSample(12.5, 1280, 720))
        assertFalse(isThumbnailSample(1_800.0, 144, 144))
        assertFalse(isThumbnailSample(0.0, 0, 0))
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
        assertEquals(listOf("https://cdn.example/seg.mp4"), parsed.segments.map { it.url })

        val sample = "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\"\nseg.ts"
        assertTrue(parseHls(sample, "https://cdn.example/a.m3u8").protected)
        val widevine = "#EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\",URI=\"data:text/plain,widevine\""
        assertTrue(hlsProtected(widevine))
        val fairplay = "#EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT=\"com.apple.streamingkeydelivery\",URI=\"skd://x\""
        assertTrue(hlsProtected(fairplay))
        val aes = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://cdn.example/key\"\nseg.ts"
        assertTrue(parseHls(aes, "https://cdn.example/a.m3u8").protected)
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
    fun aByteRangePlaylistIsOneContiguousReadAndAnAudioGroupIsKept() {
        val media = """
            #EXTM3U
            #EXT-X-MAP:URI="CMAF_480.mp4",BYTERANGE="4@0"
            #EXTINF:4,
            #EXT-X-BYTERANGE:6@4
            CMAF_480.mp4
            #EXTINF:4,
            #EXT-X-BYTERANGE:6
            CMAF_480.mp4
        """.trimIndent()
        val parsed = parseHls(media, "https://v.redd.it/abc/CMAF_480.m3u8")
        val spans = mediaSpans(parsed)
        assertEquals(1, spans.size)
        assertEquals("https://v.redd.it/abc/CMAF_480.mp4", spans.single().url)
        assertEquals(0L, spans.single().start)
        assertEquals(16L, spans.single().length)

        val master = """
            #EXTM3U
            #EXT-X-MEDIA:URI="CMAF_AUDIO_64.m3u8",TYPE=AUDIO,GROUP-ID="6",NAME="audio 0",DEFAULT=YES
            #EXT-X-MEDIA:URI="CMAF_AUDIO_128.m3u8",TYPE=AUDIO,GROUP-ID="7",NAME="audio 0",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=480x854,AUDIO="7"
            CMAF_480.m3u8
        """.trimIndent()
        val hls = parseHls(master, "https://v.redd.it/abc/HLSPlaylist.m3u8")
        assertEquals("7", hls.variants.single().audioGroup)
        assertEquals(
            "https://v.redd.it/abc/CMAF_AUDIO_128.m3u8",
            preferredHlsAudio(hls.audios, "7")?.url,
        )
        assertEquals(null, preferredHlsAudio(hls.audios, null))

        val switched = """
            #EXTM3U
            #EXT-X-MAP:URI="init-a.mp4",BYTERANGE="4@0"
            #EXTINF:1,
            #EXT-X-BYTERANGE:4@4
            part-a.mp4
            #EXT-X-MAP:URI="init-b.mp4",BYTERANGE="4@0"
            #EXTINF:1,
            #EXT-X-BYTERANGE:4@4
            part-b.mp4
        """.trimIndent()
        val order = mediaSpans(parseHls(switched, "https://cdn.example/media.m3u8")).map { it.url.substringAfterLast('/') }
        assertEquals(listOf("init-a.mp4", "part-a.mp4", "init-b.mp4", "part-b.mp4"), order)
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
                "https://other.example/clip.mov",
                "https://files.example/movie.webm",
                "https://cdn.example/thumbs/poster.mp4",
                "https://video.example/playback",
                "${PAGE_DOCUMENT}{}",
            ),
        )
        val variants = GenericVideoExtractor().extract(hints)
        assertEquals(
            listOf(MediaStream.Hls, MediaStream.Progressive, MediaStream.Progressive, MediaStream.Progressive),
            variants.map { streamOf(it) },
        )
        val hls = variants.first().source as VideoSource.Hls
        assertEquals("https://cdn.example/a.m3u8", hls.playlistUrl)
        assertEquals("https://news.example/story", hls.headers["Referer"])
        assertEquals("test", hls.headers["User-Agent"])
        assertEquals("Story", variants.first().title)
        assertEquals("https://other.example/clip.mov", (variants[1].source as VideoSource.Progressive).url)
        assertEquals("video/quicktime", variants[1].mimeType)
        assertEquals("https://files.example/movie.webm", (variants[2].source as VideoSource.Progressive).url)
        assertEquals("video/webm", variants[2].mimeType)
        assertEquals("https://video.example/playback", (variants[3].source as VideoSource.Progressive).url)
        assertEquals("video/mp4", variants[3].mimeType)
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
            listOf("https://rr.example/video|https://rr.example/audio"),
            collected.variants.map { it.source.address() },
        )
        assertNull(collected.failure)
        assertTrue(collected.variants.first().source is VideoSource.SeparateAv)

        val elsewhere = collectVariants(
            listOf(generic, youtube),
            hints("https://news.example/story", "Story", listOf("https://cdn.example/also.mp4")),
        )
        assertEquals(listOf("https://cdn.example/also.mp4"), elsewhere.variants.map { it.source.address() })

        val emptySite = extractor("tiktok", "https://www.tiktok.com/@a/video/1") { emptyList() }
        val fellBack = collectVariants(
            listOf(emptySite, generic),
            hints("https://www.tiktok.com/@a/video/1", "clip", listOf("https://cdn.example/also.mp4")),
        )
        assertEquals(listOf("https://cdn.example/also.mp4"), fellBack.variants.map { it.source.address() })

        val refused = collectVariants(
            listOf(generic, extractor("youtube", page) { throw VideoExtractException(VideoExtractFailure.DRM) }),
            hints(page, "Talk", listOf("https://cdn.example/also.mp4")),
        )
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
            VideoVariant("cat", "1280p", "video/mp2t", null, VideoSource.Hls("https://v.redd.it/abc/CMAF_720.m3u8")),
            VideoVariant("pair", "720p", "video/mp4", null, VideoSource.SeparateAv("https://cdn.example/a/video.mp4", "https://cdn.example/a/audio.mp4")),
            VideoVariant("other", "720p", "video/mp2t", null, VideoSource.Hls("https://cdn.example/b/clip.m3u8")),
            VideoVariant("other", "720p", "video/mp2t", null, VideoSource.Hls("https://cdn.example/other/master.m3u8")),
            VideoVariant("other", "720p", "video/mp4", null, VideoSource.Progressive("https://cdn.example/other/clip.mp4")),
        )
        val listed = listedQualities(variants)
        assertEquals(listOf("1280p", "720p", "720p", "720p", "720p", "480p", "360p"), listed.map { it.qualityLabel })
        assertTrue(listed.any { (it.source as? VideoSource.Hls)?.playlistUrl == "https://cdn.example/b/clip.m3u8" })
        assertTrue(listed.any { (it.source as? VideoSource.Hls)?.playlistUrl == "https://cdn.example/other/master.m3u8" })
        assertTrue(listed.any { (it.source as? VideoSource.Progressive)?.url == "https://cdn.example/other/clip.mp4" })
        assertTrue(listed.none { (it.source as? VideoSource.Hls)?.playlistUrl?.contains("v.redd.it") == true })
        assertTrue(listed.none { (it.source as? VideoSource.Hls)?.playlistUrl?.contains("CMAF_720") == true })
        assertTrue(listed.none { (it.source as? VideoSource.Progressive)?.url?.contains("DASH_") == true })
    }

    @Test
    fun distinctClipsThatShareAHeightStayListed() {
        val variants = listOf(
            VideoVariant("Cat", "720p", "video/mp2t", null, VideoSource.Hls("https://cdn.example/videos/cat-720.m3u8")),
            VideoVariant("Dog", "720p", "video/mp2t", null, VideoSource.Hls("https://cdn.example/videos/dog-720.m3u8")),
            VideoVariant("Cat", "720p", "video/mp4", null, VideoSource.SeparateAv("https://cdn.example/clips/cat.mp4", "https://cdn.example/clips/cat.m4a")),
            VideoVariant("Dog", "720p", "video/mp4", null, VideoSource.SeparateAv("https://cdn.example/clips/dog.mp4", "https://cdn.example/clips/dog.m4a")),
            VideoVariant("Left", "480p", "video/mp2t", null, VideoSource.Hls("https://cdn.example/shows/a/480.m3u8")),
            VideoVariant("Right", "480p", "video/mp2t", null, VideoSource.Hls("https://cdn.example/episodes/a/480.m3u8")),
        )
        val listed = listedQualities(variants)
        assertEquals(6, listed.size)
        assertTrue(listed.any { (it.source as? VideoSource.Hls)?.playlistUrl == "https://cdn.example/videos/cat-720.m3u8" })
        assertTrue(listed.any { (it.source as? VideoSource.Hls)?.playlistUrl == "https://cdn.example/videos/dog-720.m3u8" })
        assertTrue(listed.any { (it.source as? VideoSource.SeparateAv)?.videoUrl == "https://cdn.example/clips/cat.mp4" })
        assertTrue(listed.any { (it.source as? VideoSource.SeparateAv)?.videoUrl == "https://cdn.example/clips/dog.mp4" })
        assertTrue(listed.any { (it.source as? VideoSource.Hls)?.playlistUrl == "https://cdn.example/shows/a/480.m3u8" })
        assertTrue(listed.any { (it.source as? VideoSource.Hls)?.playlistUrl == "https://cdn.example/episodes/a/480.m3u8" })
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

    @Test
    fun aPlayedListDropsThumbnailsBeforeTheCapAndKeepsTheNewestClip() {
        val preview = PlayedClip("https://cdn.example/thumb.mp4", 1.0, 120, 80)
        val poster = PlayedClip("https://cdn.example/thumbs/poster.mp4", 30.0, 1280, 720)
        assertNull(rememberPlayed(emptyList(), preview))
        assertNull(rememberPlayed(emptyList(), poster))

        var played = emptyList<PlayedClip>()
        repeat(MAX_REMEMBERED_MEDIA) { index ->
            val clip = PlayedClip("https://cdn.example/feed-$index.mp4", 40.0, 640, 360)
            played = rememberPlayed(played, clip) ?: played
        }
        assertEquals(MAX_REMEMBERED_MEDIA, played.size)
        assertEquals("https://cdn.example/feed-31.mp4", played.first().src)
        assertNull(rememberPlayed(played, preview))
        assertEquals(MAX_REMEMBERED_MEDIA, played.size)

        val latest = PlayedClip("https://cdn.example/the-one.mp4", 90.0, 1280, 720)
        val next = rememberPlayed(played, latest)
        assertEquals("https://cdn.example/the-one.mp4", next?.first()?.src)
        assertEquals(MAX_REMEMBERED_MEDIA, next?.size)
        assertEquals("https://cdn.example/feed-1.mp4", next?.last()?.src)
        assertEquals(null, next?.find { it.src == "https://cdn.example/feed-0.mp4" })

        val again = rememberPlayed(next!!, latest)
        assertEquals(latest.src, again?.first()?.src)
        assertEquals(1, again?.count { it.src == latest.src })

        assertEquals(listOf(latest.src), rememberPlayed(emptyList(), latest)?.map { it.src })
        val remembered = (0 until 34).fold(emptyList<String>()) { urls, index ->
            rememberMediaUrl(urls, "https://cdn.example/n-$index.mp4")
        }
        assertEquals("https://cdn.example/n-33.mp4", remembered.first())
        assertEquals(MAX_REMEMBERED_MEDIA, remembered.size)
        assertFalse(remembered.contains("https://cdn.example/n-0.mp4"))
        assertFalse(remembered.contains("https://cdn.example/n-1.mp4"))

        val json = """{"download_addr":{"url_list":["https://v.tiktok.com/a/download.mp4"]}}"""
        val documents = rememberPageDocument(emptyList(), PAGE_DOCUMENT + json)
        val files = (0 until 40).fold(emptyList<String>()) { urls, index ->
            rememberMediaUrl(urls, "https://cdn.example/file-$index.mp4")
        }
        assertEquals(listOf(json), documents)
        assertEquals(MAX_REMEMBERED_MEDIA, files.size)
        assertFalse(files.any { it.startsWith(PAGE_DOCUMENT) })
        val saved = runBlocking {
            TikTokVideoExtractor().extract(
                PageVideoHints("https://www.tiktok.com/@a/video/1", "clip", files, "test", null, documents),
            )
        }
        assertEquals("https://v.tiktok.com/a/download.mp4", (saved.single().source as VideoSource.Progressive).url)
    }

    @Test
    fun aPlayingMessageKeepsOnlyAShortWebAddress() {
        val report = """
            {"page":"https://news.example/story","urls":[
              {"src":"https://cdn.example/stream","duration":30,"width":640,"height":360},
              {"src":"javascript:alert(1)","duration":30,"width":640,"height":360},
              {"src":"file:///sdcard/secret.mp4","duration":30,"width":640,"height":360},
              "blob:https://news.example/1"
            ],"docs":["{\"play_addr\":\"https://cdn.example/hidden.mp4\"}"]}
        """.trimIndent()
        assertEquals(listOf("https://cdn.example/stream"), mediaFromPlayingBridge(report))

        val longUrl = "https://cdn.example/" + "a".repeat(MAX_BRIDGE_URL_LENGTH)
        val tooLong = """{"page":"https://news.example/story","urls":[{"src":"$longUrl","duration":30,"width":640,"height":360}],"docs":[]}"""
        assertEquals(emptyList<String>(), mediaFromPlayingBridge(tooLong))

        val scriptPage = """{"page":"javascript:alert(1)","urls":[{"src":"https://cdn.example/stream","duration":30,"width":640,"height":360}],"docs":[]}"""
        assertEquals(emptyList<String>(), mediaFromPlayingBridge(scriptPage))
        assertEquals(emptyList<String>(), mediaFromPlayingBridge("x".repeat(MAX_PLAYING_REPORT_CHARS + 1)))
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
        mediaUrls = media.filterNot { it.startsWith(PAGE_DOCUMENT) },
        userAgent = "test",
        cookieHeader = "session=secret",
        pageDocuments = media.mapNotNull { it.takeIf { url -> url.startsWith(PAGE_DOCUMENT) }?.removePrefix(PAGE_DOCUMENT) },
    )

    private fun streamOf(variant: VideoVariant): MediaStream = when (variant.source) {
        is VideoSource.Progressive -> MediaStream.Progressive
        is VideoSource.Hls -> MediaStream.Hls
        is VideoSource.Dash -> MediaStream.Dash
        is VideoSource.SeparateAv -> MediaStream.Progressive
    }
}
