package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.SiteIndicator
import com.dantech.skimbrowser.ui.shell.AiFabMode
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Browsing (05), Find in page (05c) and Page failed to load (11). The page slot is an empty box:
 * a WebView does not draw under Robolectric, so the article on the boards is absent.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class BrowsingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browsing() {
        show(Loaded, TextFieldState())
        captureBoard("05-browsing")
        composeRule.assertA11y()
    }

    @Test
    fun browsingWhileThePageLoads() {
        show(Loaded.copy(progress = 0.62f, readerAvailable = true), TextFieldState())
        captureBoard("05-browsing--loading")
        composeRule.assertA11y()
    }

    @Test
    fun findInPage() {
        val query = TextFieldState("heat pump")
        show(Loaded.copy(find = FindUiState(countedText = "heat pump", activeMatch = 2, matchCount = 6)), query)
        captureBoard("05c-find-in-page")
        composeRule.assertA11y()
    }

    @Test
    fun pageUnreachable() {
        show(failed(LoadError.Unreachable(FAILED_URL, "ERR_NAME_NOT_RESOLVED")), TextFieldState())
        captureBoard("11-page-failed-to-load")
        composeRule.assertA11y()
    }

    @Test
    fun noInternet() {
        show(failed(LoadError.NoInternet(FAILED_URL, "ERR_INTERNET_DISCONNECTED")), TextFieldState())
        captureBoard("11-page-failed-to-load--no-internet")
        composeRule.assertA11y()
    }

    @Test
    fun timedOut() {
        show(failed(LoadError.TimedOut(FAILED_URL, "ERR_CONNECTION_TIMED_OUT")), TextFieldState())
        captureBoard("11-page-failed-to-load--timed-out")
        composeRule.assertA11y()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun rendererCrashed() {
        show(failed(LoadError.Crashed(FAILED_URL, "RENDERER_CRASHED")), TextFieldState())
        composeRule.waitForIdle()
        captureScreenRoboImage("$COMPONENT_GOLDENS/browsing-11-crashed.png")
        composeRule.assertA11y()
    }

    private fun show(state: BrowsingUiState, query: TextFieldState) {
        composeRule.setSkimContent {
            BrowsingScreen(state, query, actions, page = {
                Box(Modifier.fillMaxSize().background(Color.White))
            })
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val COMPONENT_GOLDENS = "src/test/screenshots/components"
        const val FAILED_URL = "https://nightbus-kansai.jp/timetable"

        val Loaded = BrowsingUiState(
            address = "daylightjournal.com",
            indicator = SiteIndicator.Secure,
            readerAvailable = true,
            progress = null,
            loadError = null,
            backEnabled = true,
            forwardEnabled = false,
            tabCount = 7,
            incognito = false,
            aiFab = AiFabMode.Enabled,
            find = null,
            refreshing = false,
        )

        fun failed(error: LoadError) = Loaded.copy(
            address = "nightbus-kansai.jp/timetable",
            indicator = SiteIndicator.Error,
            readerAvailable = false,
            loadError = error,
            aiFab = AiFabMode.UnavailableUntilLoaded,
        )

        val actions = BrowsingActions(
            onSiteInfo = {},
            onEditAddress = {},
            onReader = {},
            onRefresh = {},
            onTryAgain = {},
            onInternetSettings = {},
            onFindPrevious = {},
            onFindNext = {},
            onCloseFind = {},
            onBack = {},
            onForward = {},
            onTabs = {},
            onMore = {},
            onAskPage = {},
        )
    }
}
