package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.tab.RecordingTabs
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoExtractException
import com.dantech.skimbrowser.domain.video.VideoExtractFailure
import com.dantech.skimbrowser.domain.video.VideoExtractor
import com.dantech.skimbrowser.domain.video.GenericVideoExtractor
import com.dantech.skimbrowser.domain.video.PAGE_DOCUMENT
import com.dantech.skimbrowser.domain.video.TikTokVideoExtractor
import com.dantech.skimbrowser.domain.video.VideoCommands
import com.dantech.skimbrowser.domain.video.VideoSaveResult
import com.dantech.skimbrowser.domain.video.VideoSource
import com.dantech.skimbrowser.domain.video.VideoVariant
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoDownloadSheetTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun theSheetShowsAProtectedVideoAMissingVideoAndAChoice() {
        var saved = -1
        compose.setContent {
            Column {
                VideoDownloadSheet(
                    VideoDownloadUiState(
                        loading = false,
                        choices = listOf(
                            VideoChoice(0, "clip", "720p", 12),
                            VideoChoice(1, "clip", "1080p", 3_000_000),
                        ),
                    ),
                    onSave = { saved = it },
                )
                VideoDownloadSheet(VideoDownloadUiState(loading = false, notice = R.string.video_protected), onSave = {})
                VideoDownloadSheet(VideoDownloadUiState(loading = false, notice = R.string.video_age_restricted), onSave = {})
                VideoDownloadSheet(VideoDownloadUiState(loading = true), onSave = {})
                VideoDownloadSheet(VideoDownloadUiState(loading = false), onSave = {})
            }
        }
        compose.onNodeWithText("720p").performScrollTo().performClick()
        assertEquals(0, saved)
        compose.onNodeWithText("This video is protected and can't be downloaded.").assertExists()
        compose.onNodeWithText("This video is age-restricted and can't be downloaded.").assertExists()
        compose.onNodeWithText("Looking for a video…").assertExists()
        compose.onNodeWithText("No downloadable video was found.").assertExists()
    }

    @Test
    fun pickingATikTokFileStartsTheDownloadAndAProtectedResultStaysOnTheSheet() = runTest {
        val tabs = RecordingTabs()
        tabs.show(
            listOf(
                Tab(
                    id = "t",
                    incognito = false,
                    url = "https://www.tiktok.com/@a/video/1",
                    title = "clip",
                    groupId = null,
                    position = 0,
                    createdAt = 0,
                    lastAccessedAt = 0,
                ),
            ),
        )
        val json = """{"download_addr":{"url_list":["https://v.tiktok.com/a/download.mp4"]}}"""
        tabs.runtimes["t"] = TabRuntime(mediaUrls = listOf(PAGE_DOCUMENT + json))
        val commands = RecordingCommands()
        val model = VideoDownloadViewModel("t", tabs, setOf(TikTokVideoExtractor(), GenericVideoExtractor()), commands)
        assertTrue(model.state.value.choices.isNotEmpty())
        model.save(0)
        assertTrue(model.state.value.started)
        assertTrue(commands.saved?.source is VideoSource.Progressive)

        commands.result = VideoSaveResult.Protected
        val again = VideoDownloadViewModel("t", tabs, setOf(TikTokVideoExtractor()), commands)
        again.save(0)
        assertEquals(R.string.video_protected, again.state.value.notice)
    }

    @Test
    fun aYoutubeSeparatePairIsListedAndARefusalReplacesAnEmptyList() = runTest {
        val tabs = watchTab("https://www.youtube.com/watch?v=abc")
        val separate = VideoVariant(
            "Talk",
            "1080p separate audio",
            "video/mp4",
            50_000_000,
            VideoSource.SeparateAv("https://rr.example/video", "https://rr.example/audio"),
        )
        val commands = RecordingCommands()
        val model = VideoDownloadViewModel("t", tabs, setOf(youtube(separate), GenericVideoExtractor()), commands)
        assertEquals(listOf("1080p separate audio"), model.state.value.choices.map { it.quality })
        model.save(0)
        assertTrue(model.state.value.started)
        val saved = commands.saved?.source as VideoSource.SeparateAv
        assertEquals("https://rr.example/video", saved.videoUrl)
        assertEquals("https://rr.example/audio", saved.audioUrl)

        val notices = mapOf(
            VideoExtractFailure.DRM to R.string.video_protected,
            VideoExtractFailure.AGE_RESTRICTED to R.string.video_age_restricted,
            VideoExtractFailure.MEMBERS_ONLY to R.string.video_members_only,
            VideoExtractFailure.PREMIUM to R.string.video_premium,
            VideoExtractFailure.PAID to R.string.video_paid,
            VideoExtractFailure.PRIVATE to R.string.video_private,
            VideoExtractFailure.UNAVAILABLE to R.string.video_unavailable,
        )
        notices.forEach { (failure, notice) ->
            val refused = VideoDownloadViewModel("t", tabs, setOf(refusing(failure)), commands)
            assertEquals(failure.name, notice, refused.state.value.notice)
            assertTrue(refused.state.value.choices.isEmpty())
        }
    }
}

private fun watchTab(url: String): RecordingTabs = RecordingTabs().apply {
    show(
        listOf(
            Tab(
                id = "t",
                incognito = false,
                url = url,
                title = "Talk",
                groupId = null,
                position = 0,
                createdAt = 0,
                lastAccessedAt = 0,
            ),
        ),
    )
}

private fun youtube(variant: VideoVariant): VideoExtractor = object : VideoExtractor {
    override val id: String = "youtube"
    override fun matches(pageUrl: String): Boolean = pageUrl.contains("youtube.com")
    override suspend fun extract(hints: PageVideoHints): List<VideoVariant> = listOf(variant)
}

private fun refusing(failure: VideoExtractFailure): VideoExtractor = object : VideoExtractor {
    override val id: String = "youtube"
    override fun matches(pageUrl: String): Boolean = pageUrl.contains("youtube.com")
    override suspend fun extract(hints: PageVideoHints): List<VideoVariant> = throw VideoExtractException(failure)
}

private class RecordingCommands : VideoCommands {
    var saved: VideoVariant? = null
    var result: VideoSaveResult = VideoSaveResult.Started

    override suspend fun save(tabId: String, variant: VideoVariant): VideoSaveResult {
        saved = variant
        return result
    }
}
