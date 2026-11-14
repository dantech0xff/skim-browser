package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoExtractException
import com.dantech.skimbrowser.domain.video.VideoExtractFailure
import com.dantech.skimbrowser.domain.video.VideoSource
import com.dantech.skimbrowser.domain.video.VideoVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeVideoExtractorTest {

    @Test
    fun aNonYouTubePageDoesNotAskForStreams() = runBlocking {
        var called = false
        val extractor = YoutubeVideoExtractor(object : YoutubeCatalog {
            override suspend fun load(hints: PageVideoHints): List<VideoVariant> {
                called = true
                return emptyList()
            }
        })

        val variants = extractor.extract(hints("https://example.com/watch?v=1"))

        assertTrue(extractor.pageUrlSufficient)
        assertTrue(extractor.matches("https://www.youtube.com/watch?v=abc"))
        assertTrue(extractor.matches("https://www.youtube.com/shorts/abc"))
        assertTrue(variants.isEmpty())
        assertEquals(false, called)
    }

    @Test
    fun aWatchPageReturnsTheCatalogVariants() = runBlocking {
        val expected = VideoVariant(
            title = "Demo",
            qualityLabel = "360p",
            mimeType = "video/mp4",
            sizeBytes = null,
            source = VideoSource.Progressive("https://example.invalid/360.mp4"),
        )
        val extractor = YoutubeVideoExtractor(object : YoutubeCatalog {
            override suspend fun load(hints: PageVideoHints) = listOf(expected)
        })

        assertEquals("youtube", extractor.id)
        assertEquals(listOf(expected), extractor.extract(hints("https://youtu.be/dQw4w9WgXcQ")))
    }

    @Test
    fun aCatalogRefusalReachesTheCaller() = runBlocking {
        val extractor = YoutubeVideoExtractor(object : YoutubeCatalog {
            override suspend fun load(hints: PageVideoHints): List<VideoVariant> {
                throw VideoExtractException(VideoExtractFailure.MEMBERS_ONLY)
            }
        })

        val failure = runCatching { extractor.extract(hints("https://www.youtube.com/shorts/abc")) }.exceptionOrNull()

        assertTrue(failure is VideoExtractException)
        assertEquals(VideoExtractFailure.MEMBERS_ONLY, (failure as VideoExtractException).failure)
    }

    private fun hints(pageUrl: String) = PageVideoHints(pageUrl, null, emptyList(), null, null)
}
