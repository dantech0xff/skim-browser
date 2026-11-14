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
        assertEquals(listOf(variant), extractor.extract(hints))
        assertEquals(variant, variant.copy())
    }

    @Test
    fun aRefusalCarriesTheMessageACallerCanShow() {
        val failure = VideoExtractException("This video is age-restricted. Skim does not download it.")

        assertEquals("This video is age-restricted. Skim does not download it.", failure.message)
        assertFalse(failure.message!!.isBlank())
    }
}
