package com.dantech.skimbrowser.ui.browsing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.translate.PageTranslation
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageTranslationBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anIdlePageDrawsNoTranslationBar() {
        composeRule.setSkimContent {
            PageTranslationBar(PageTranslation.Idle, onShowOriginal = {}, onDismiss = {}, onRetry = {})
        }

        composeRule.onNodeWithText("Translated").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Close translation").assertDoesNotExist()
    }

    @Test
    fun translatingReportsProgressAndCloseLeavesIt() {
        var state: PageTranslation by mutableStateOf(PageTranslation.Translating(done = 0, total = 0))
        val closed = mutableListOf<Unit>()
        composeRule.setSkimContent {
            PageTranslationBar(state, onShowOriginal = {}, onDismiss = { closed += Unit }, onRetry = {})
        }
        composeRule.onNodeWithText("Translating this page").assertExists()

        state = PageTranslation.Translating(done = 1, total = 4)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1/4").assertExists()
        composeRule.onNodeWithContentDescription("Close translation").performClick()

        assertEquals(1, closed.size)
    }

    @Test
    fun aFinishedTranslationCanShowTheOriginalAgain() {
        var state: PageTranslation by mutableStateOf(PageTranslation.Ready(showingOriginal = false, note = null))
        val shown = mutableListOf<Boolean>()
        composeRule.setSkimContent {
            PageTranslationBar(state, onShowOriginal = { shown += it }, onDismiss = {}, onRetry = {})
        }

        composeRule.onNodeWithText("Translated").assertExists()
        composeRule.onNodeWithText("Show original").performClick()
        assertEquals(listOf(true), shown)

        state = PageTranslation.Ready(showingOriginal = true, note = PageTranslation.Note.Incomplete)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("The rest of this page was not translated.").assertExists()
        composeRule.onNodeWithText("Show translation").performClick()
        assertEquals(listOf(true, false), shown)
    }

    @Test
    fun aFailedTranslationOffersRetryOnlyWhenTryingAgainCanWork() {
        var state: PageTranslation by mutableStateOf(
            PageTranslation.Failed(PageTranslation.Note.Offline, host = "daylightjournal.com"),
        )
        val retried = mutableListOf<Unit>()
        composeRule.setSkimContent {
            PageTranslationBar(state, onShowOriginal = {}, onDismiss = {}, onRetry = { retried += Unit })
        }
        composeRule.onNodeWithText("You're offline. This page was not translated.").assertExists()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried.size)

        state = PageTranslation.Failed(PageTranslation.Note.Limit, host = "daylightjournal.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Daily limit reached. Try again later.").assertExists()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()

        state = PageTranslation.Failed(PageTranslation.Note.NotAllowed, host = "daylightjournal.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Close translation").assertDoesNotExist()
    }

    @Test
    fun notesThatAreNotAFailureStillExplainThePage() {
        var state: PageTranslation by mutableStateOf(
            PageTranslation.Ready(showingOriginal = false, note = PageTranslation.Note.Empty),
        )
        composeRule.setSkimContent {
            PageTranslationBar(state, onShowOriginal = {}, onDismiss = {}, onRetry = {})
        }
        composeRule.onNodeWithText("This page has no text to translate.").assertExists()

        state = PageTranslation.Ready(showingOriginal = false, note = PageTranslation.Note.Incognito)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Incognito pages are not sent for translation.").assertExists()

        state = PageTranslation.Ready(showingOriginal = false, note = PageTranslation.Note.Offline)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You're offline. This page was not translated.").assertExists()

        state = PageTranslation.Ready(showingOriginal = false, note = PageTranslation.Note.Limit)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Daily limit reached. Try again later.").assertExists()

        state = PageTranslation.Ready(showingOriginal = false, note = PageTranslation.Note.Unavailable)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Translation didn't finish. Try again.").assertExists()

        state = PageTranslation.Failed(PageTranslation.Note.Unavailable, host = "daylightjournal.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Try again").assertExists()

        state = PageTranslation.Failed(PageTranslation.Note.Empty, host = "daylightjournal.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("This page has no text to translate.").assertExists()

        state = PageTranslation.Failed(PageTranslation.Note.Incognito, host = "daylightjournal.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Incognito pages are not sent for translation.").assertExists()
    }
}
