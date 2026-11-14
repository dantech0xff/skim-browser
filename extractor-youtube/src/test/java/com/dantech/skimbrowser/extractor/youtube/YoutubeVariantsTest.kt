package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoExtractException
import com.dantech.skimbrowser.domain.video.VideoExtractFailure
import com.dantech.skimbrowser.domain.video.VideoSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class YoutubeVariantsTest {

    @Test
    fun fixtureKeepsMuxedStreamsAndPairsHigherVideoWithAudio() {
        val info = infoFromFixture("fixtures/youtube-streams.json", ageLimit = 0)
        val hints = hints(info.url)

        val variants = YoutubeVariants.fromInfo(info, hints)

        assertEquals(listOf("1080p", "720p", "360p"), variants.map { it.qualityLabel })
        assertEquals("Fixture video", variants.first().title)
        val hd = variants[0]
        val separate = hd.source as VideoSource.SeparateAv
        assertEquals("https://example.invalid/1080.webm", separate.videoUrl)
        assertEquals("https://example.invalid/audio.webm", separate.audioUrl)
        assertEquals("video/webm", hd.mimeType)
        val mid = variants[1].source as VideoSource.SeparateAv
        assertEquals("https://example.invalid/720.mp4", mid.videoUrl)
        assertEquals("https://example.invalid/audio.m4a", mid.audioUrl)
        val low = variants[2].source as VideoSource.Progressive
        assertEquals("https://example.invalid/360.mp4", low.url)
        assertEquals(hints.userAgent, low.headers["User-Agent"])
        assertNull(low.headers["Cookie"])
        assertTrue(variants.none { it.qualityLabel == "2160p" })
    }

    @Test
    fun aWebmVideoWithoutWebmAudioIsOmitted() {
        val info = StreamInfo(0, "https://www.youtube.com/watch?v=x", "https://www.youtube.com/watch?v=x", StreamType.VIDEO_STREAM, "x", "Clip", 0)
        info.videoOnlyStreams = listOf(
            VideoStream.Builder()
                .setId("248")
                .setContent("https://example.invalid/1080.webm", true)
                .setIsVideoOnly(true)
                .setResolution("1080p")
                .setMediaFormat(MediaFormat.WEBM)
                .build(),
        )
        info.audioStreams = listOf(
            AudioStream.Builder()
                .setId("140")
                .setContent("https://example.invalid/audio.m4a", true)
                .setMediaFormat(MediaFormat.M4A)
                .setAverageBitrate(128000)
                .build(),
        )

        val failure = runCatching { YoutubeVariants.fromInfo(info, hints(info.url)) }.exceptionOrNull()

        assertTrue(failure is VideoExtractException)
        assertEquals(VideoExtractFailure.UNAVAILABLE, (failure as VideoExtractException).failure)
    }

    @Test
    fun muxedAndSeparateCopiesOfTheSameResolutionGetDifferentLabels() {
        val info = StreamInfo(0, "https://www.youtube.com/watch?v=x", "https://www.youtube.com/watch?v=x", StreamType.VIDEO_STREAM, "x", "Clip", 0)
        info.videoStreams = listOf(
            VideoStream.Builder()
                .setId("22")
                .setContent("https://example.invalid/720.mp4", true)
                .setIsVideoOnly(false)
                .setResolution("720p")
                .setMediaFormat(MediaFormat.MPEG_4)
                .build(),
        )
        info.videoOnlyStreams = listOf(
            VideoStream.Builder()
                .setId("136")
                .setContent("https://example.invalid/720-video.mp4", true)
                .setIsVideoOnly(true)
                .setResolution("720p")
                .setMediaFormat(MediaFormat.MPEG_4)
                .build(),
        )
        info.audioStreams = listOf(
            AudioStream.Builder()
                .setId("140")
                .setContent("https://example.invalid/audio.m4a", true)
                .setMediaFormat(MediaFormat.M4A)
                .setAverageBitrate(128000)
                .build(),
        )

        val labels = YoutubeVariants.fromInfo(info, hints(info.url)).map { it.qualityLabel }

        assertEquals(listOf("720p MP4 muxed", "720p MP4 separate audio"), labels)
    }

    @Test
    fun pageCookiesStayOnThatHostsHttpsUrls() {
        val page = "https://www.youtube.com/watch?v=x"
        val sameHost = "https://youtube.com/watch/file.mp4"
        val otherHost = "https://rr.googlevideo.com/videoplayback"
        val insecure = "http://youtube.com/watch/file.mp4"
        val hints = hints(page)

        assertEquals("VISITOR=1", YoutubeVariants.headersFor(hints, listOf(sameHost))["Cookie"])
        assertNull(YoutubeVariants.headersFor(hints, listOf(otherHost))["Cookie"])
        assertNull(YoutubeVariants.headersFor(hints, listOf(insecure))["Cookie"])
        assertNull(YoutubeVariants.headersFor(hints, listOf(sameHost, otherHost))["Cookie"])
    }

    @Test
    fun anAgeRestrictedPlayerResponseFails() {
        val info = infoFromFixture("fixtures/youtube-streams.json", ageLimit = 18)
        val failure = runCatching { YoutubeVariants.fromInfo(info, hints(info.url)) }.exceptionOrNull()

        assertTrue(failure is VideoExtractException)
        assertEquals(VideoExtractFailure.AGE_RESTRICTED, (failure as VideoExtractException).failure)
    }

    @Test
    fun aLivePlayerResponseWithOnlyHlsReturnsThatPlaylist() {
        val info = StreamInfo(0, "https://www.youtube.com/watch?v=live", "https://www.youtube.com/watch?v=live", StreamType.LIVE_STREAM, "live", "Live", 0)
        info.hlsUrl = "https://example.invalid/live.m3u8"

        val variants = YoutubeVariants.fromInfo(info, hints(info.url))

        assertEquals(1, variants.size)
        val source = variants.single().source as VideoSource.Hls
        assertEquals("https://example.invalid/live.m3u8", source.playlistUrl)
        assertEquals("HLS", variants.single().qualityLabel)
        assertNull(variants.single().sizeBytes)
    }

    private fun hints(pageUrl: String) = PageVideoHints(
        pageUrl = pageUrl,
        pageTitle = "ignored when the player has a name",
        mediaUrls = emptyList(),
        userAgent = "SkimTest/1.0",
        cookieHeader = "VISITOR=1",
    )

    private fun infoFromFixture(resource: String, ageLimit: Int): StreamInfo {
        val json = Json { ignoreUnknownKeys = true }
        val fixture = json.decodeFromString<Fixture>(javaClass.classLoader!!.getResource(resource)!!.readText())
        val page = fixture.pageUrl
        val info = StreamInfo(0, page, page, StreamType.VIDEO_STREAM, "fixture", fixture.title, ageLimit)
        info.videoStreams = fixture.muxed.map { it.toVideo() }
        info.videoOnlyStreams = fixture.videoOnly.map { it.toVideo() }
        info.audioStreams = fixture.audio.map { it.toAudio() }
        return info
    }
}

@Serializable
private data class Fixture(
    val title: String,
    val pageUrl: String,
    val muxed: List<VideoFixture>,
    val videoOnly: List<VideoFixture>,
    val audio: List<AudioFixture>,
)

@Serializable
private data class VideoFixture(
    val id: String,
    val url: String,
    val videoOnly: Boolean,
    val resolution: String,
    val mime: String,
    val isUrl: Boolean = true,
) {
    fun toVideo(): VideoStream = VideoStream.Builder()
        .setId(id)
        .setContent(url, isUrl)
        .setIsVideoOnly(videoOnly)
        .setResolution(resolution)
        .setMediaFormat(MediaFormat.getFromMimeType(mime))
        .build()
}

@Serializable
private data class AudioFixture(
    val id: String,
    val url: String,
    val mime: String,
    val bitrate: Int,
) {
    fun toAudio(): AudioStream = AudioStream.Builder()
        .setId(id)
        .setContent(url, true)
        .setMediaFormat(MediaFormat.getFromMimeType(mime))
        .setAverageBitrate(bitrate)
        .build()
}
