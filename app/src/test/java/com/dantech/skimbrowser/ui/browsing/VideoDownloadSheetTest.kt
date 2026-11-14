package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.tab.RecordingTabs
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
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
                VideoDownloadSheet(VideoDownloadUiState(loading = false, protected = true), onSave = {})
                VideoDownloadSheet(VideoDownloadUiState(loading = true), onSave = {})
                VideoDownloadSheet(VideoDownloadUiState(loading = false), onSave = {})
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
            }
        }
        compose.onNodeWithText("This video is protected and can't be downloaded.").assertExists()
        compose.onNodeWithText("Looking for a video…").assertExists()
        compose.onNodeWithText("No downloadable video was found.").assertExists()
        compose.onNodeWithText("720p").performClick()
        assertEquals(0, saved)
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
        assertTrue(again.state.value.protected)
    }
}

private class RecordingCommands : VideoCommands {
    var saved: VideoVariant? = null
    var result: VideoSaveResult = VideoSaveResult.Started

    override suspend fun save(tabId: String, variant: VideoVariant): VideoSaveResult {
        saved = variant
        return result
    }
}
