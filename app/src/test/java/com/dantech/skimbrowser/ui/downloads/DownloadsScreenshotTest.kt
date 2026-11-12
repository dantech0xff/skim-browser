package com.dantech.skimbrowser.ui.downloads

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.download.DownloadItem
import com.dantech.skimbrowser.domain.download.DownloadPhase
import com.dantech.skimbrowser.domain.download.DownloadRetry
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The download list. There is no design board for this screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class DownloadsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun runningDoneAndFailed() {
        val retry = DownloadRetry("tab-1", "https://example.com/notes.txt", null, null, "text/plain", true, false)
        composeRule.setSkimContent {
            DownloadsScreen(
                items = listOf(
                    row(1, "guide.pdf", DownloadPhase.Running, soFar = 25_000, total = 100_000, uri = null, retry = null),
                    row(2, "photo.png", DownloadPhase.Done, soFar = 2048, total = 2048, uri = "content://downloads/2", retry = null),
                    row(3, "notes.txt", DownloadPhase.Failed, soFar = 0, total = -1, uri = null, retry = retry),
                ),
                onBack = {},
                onOpen = {},
                onRetry = {},
                onRemove = { _, _ -> },
            )
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/components/downloads-screen.png")
    }

    private fun row(
        id: Long,
        name: String,
        phase: DownloadPhase,
        soFar: Long,
        total: Long,
        uri: String?,
        retry: DownloadRetry?,
    ) = DownloadItem(id, name, phase, soFar, total, uri, "application/octet-stream", false, retry, systemId = null)
}
