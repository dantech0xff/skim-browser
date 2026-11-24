package com.dantech.skimbrowser.ui.browsing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** More options offers Download video only when the pill had no room for Save video. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class MoreMenuSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    @Test
    fun withAVideoThePillHadNoRoomForThePageSectionOffersDownloadVideo() {
        show(onDownloadVideo = { calls += "video" })

        composeRule.onNodeWithText("Translate page").assertExists()
        composeRule.onNodeWithText("Download video").performClick()

        assertEquals(listOf("video"), calls)
    }

    @Test
    fun otherwiseTheMenuDoesNotOfferIt() {
        show(onDownloadVideo = null)

        composeRule.onNodeWithText("Translate page").assertExists()
        composeRule.onAllNodesWithText("Download video").assertCountEquals(0)
    }

    @Test
    fun inIncognitoDownloadVideoStandsWithoutTranslatePage() {
        show(onDownloadVideo = { calls += "video" }, incognito = true)

        composeRule.onAllNodesWithText("Translate page").assertCountEquals(0)
        composeRule.onNodeWithText("Download video").performClick()

        assertEquals(listOf("video"), calls)
    }

    private fun show(onDownloadVideo: (() -> Unit)?, incognito: Boolean = false) {
        composeRule.setSkimContent(incognito = incognito) {
            MoreMenuSheet(
                state = MoreMenuUiState(
                    page = true,
                    title = "Why heat pumps are winning in cold climates",
                    host = "daylightjournal.com",
                    url = "https://daylightjournal.com/energy",
                    letter = "D",
                    bookmarkKnown = true,
                    incognito = incognito,
                ),
                onStar = {},
                onShare = {},
                onFind = {},
                onDesktop = {},
                onNewTab = {},
                onNewIncognito = {},
                onBookmarks = {},
                onHistory = {},
                onSettings = {},
                onDownloadVideo = onDownloadVideo,
            )
        }
        composeRule.waitForIdle()
    }
}
