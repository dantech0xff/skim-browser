package com.dantech.skimbrowser.domain.video

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExtractorContractTest {

    @Test
    fun extractReturnsTheVariantsTheExtractorProduces() = runBlocking {
        val variant = VideoVariant(
            title = "Demo",
            qualityLabel = "360p",
            mimeType = "video/mp4",
            sizeBytes = 10,
            source = VideoSource.Progressive("https://example.invalid/v.mp4"),
        )
        val extractor = object : VideoExtractor {
            override val id = "fake"
            override fun matches(pageUrl: String) = pageUrl.startsWith("https://example.invalid/")
            override suspend fun extract(hints: PageVideoHints) = listOf(variant)
        }
        val hints = PageVideoHints(
            pageUrl = "https://example.invalid/watch",
            pageTitle = null,
            mediaUrls = emptyList(),
            userAgent = null,
            cookieHeader = null,
        )

        assertTrue(extractor.matches(hints.pageUrl))
        assertFalse(extractor.pageUrlSufficient)
        assertEquals(listOf(variant), extractor.extract(hints))
        assertEquals(variant, variant.copy())
    }

    @Test
    fun aPageUrlIsEnoughWhenTheExtractorSaysSo() {
        val page = object : VideoExtractor {
            override val id = "page"
            override val pageUrlSufficient = true
            override fun matches(pageUrl: String) = pageUrl.contains("/shorts/") || pageUrl.contains("watch?v=")
            override suspend fun extract(hints: PageVideoHints) = emptyList<VideoVariant>()
        }
        val watch = "https://www.youtube.com/watch?v=abc"
        val shorts = "https://www.youtube.com/shorts/abc"
        assertTrue(videoCanBeSaved(watch, emptyList(), listOf(page)))
        assertTrue(videoCanBeSaved(shorts, emptyList(), listOf(page)))
        assertFalse(videoCanBeSaved("https://example.com/", emptyList(), listOf(page)))
        assertTrue(videoCanBeSaved("https://example.com/", listOf("https://cdn.example/a.mp4"), emptyList()))
        assertFalse(videoCanBeSaved(null, emptyList(), listOf(page)))
    }

    @Test
    fun aRefusalNamesTheReasonTheUiCanMap() {
        val failure = VideoExtractException(VideoExtractFailure.AGE_RESTRICTED)

        assertEquals(VideoExtractFailure.AGE_RESTRICTED, failure.failure)
        assertEquals(VideoExtractFailure.AGE_RESTRICTED.name, failure.message)
    }
}
