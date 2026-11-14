package com.dantech.skimbrowser.ui.downloads

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.download.DownloadItem
import com.dantech.skimbrowser.domain.download.DownloadPhase
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadsFailureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aDroppedConnectionSaysTheNetworkFailed() {
        compose.setSkimContent {
            DownloadsScreen(
                items = listOf(
                    DownloadItem(
                        id = 1,
                        fileName = "clip.mp4",
                        phase = DownloadPhase.Failed,
                        bytesSoFar = 0,
                        bytesTotal = 34L * 1024 * 1024,
                        uri = null,
                        mime = "video/mp4",
                        incognito = false,
                        retry = null,
                        systemId = null,
                        failureReason = "network",
                    ),
                ),
                onBack = {},
                onOpen = {},
                onRetry = {},
                onRemove = { _, _ -> },
            )
        }
        compose.onNodeWithText("Couldn't download. The network failed.").assertExists()
    }
}
