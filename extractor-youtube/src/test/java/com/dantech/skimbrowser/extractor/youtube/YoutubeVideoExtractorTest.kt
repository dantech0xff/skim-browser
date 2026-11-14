package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoExtractException
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
                throw VideoExtractException(YoutubeFailures.MEMBERS)
            }
        })

        val failure = runCatching { extractor.extract(hints("https://www.youtube.com/shorts/abc")) }.exceptionOrNull()

        assertTrue(failure is VideoExtractException)
        assertEquals(YoutubeFailures.MEMBERS, failure?.message)
    }

    private fun hints(pageUrl: String) = PageVideoHints(pageUrl, null, emptyList(), null, null)
}
