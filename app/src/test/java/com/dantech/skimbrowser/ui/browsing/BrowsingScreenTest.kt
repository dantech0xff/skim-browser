package com.dantech.skimbrowser.ui.browsing

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.Velocity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.SiteIndicator
import com.dantech.skimbrowser.ui.shell.AiFabMode
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The browsing screen as its state draws it, with a stand-in page: which bar shows, the toolbar and
 * the AI FAB, the error over the page, and pull to refresh, driven the way the tab's WebView drives
 * it (nested scrolling, then a fling decides the refresh).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class BrowsingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val query = TextFieldState()
    private var state by mutableStateOf(Loaded)

    // The bars

    @Test
    fun findInPageTakesTheAddressBarsPlaceWhileItIsOpen() {
        show()
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").assertExists()
        composeRule.onAllNodesWithContentDescription("Find in page").assertCountEquals(0)

        state = Loaded.copy(find = FindUiState(countedText = ""))
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Find in page")))
        composeRule.onAllNodesWithContentDescription("Edit address, daylightjournal.com").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Site information, connection is secure").assertCountEquals(0)
    }

    @Test
    fun theCountShowsForTheTextTheWebViewSearchedAndIsAnnounced() {
        query.setTextAndPlaceCursorAtEnd("heat pump")
        state = Loaded.copy(find = FindUiState(countedText = "heat pump", activeMatch = 2, matchCount = 6))
        show()

        composeRule.onNodeWithText("2/6").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithContentDescription("Previous match").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Next match").assertIsEnabled()

        // Typed on: the count of the text before no longer applies.
        query.setTextAndPlaceCursorAtEnd("heat pumps")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("2/6").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Next match").assertIsNotEnabled()

        query.setTextAndPlaceCursorAtEnd("")
        state = Loaded.copy(find = FindUiState(countedText = ""))
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("0/0").assertCountEquals(0)
    }

    @Test
    fun withoutAMatchTheCountIsZeroOfZeroAndOnlyCloseIsOn() {
        query.setTextAndPlaceCursorAtEnd("glacier")
        state = Loaded.copy(find = FindUiState(countedText = "glacier"))
        show()

        composeRule.onNodeWithText("0/0").assertExists()
        composeRule.onNodeWithContentDescription("Previous match").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next match").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Close find in page").assertIsEnabled()
    }

    @Test
    fun theFindControlsAndTheKeyboardsSearchKeyDoWhatTheySay() {
        query.setTextAndPlaceCursorAtEnd("heat")
        state = Loaded.copy(find = FindUiState(countedText = "heat", activeMatch = 6, matchCount = 6))
        show()

        composeRule.onNodeWithContentDescription("Next match").performClick()
        composeRule.onNodeWithContentDescription("Previous match").performClick()
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.onNodeWithContentDescription("Close find in page").performClick()

        assertEquals(listOf("findNext", "findPrevious", "findNext", "closeFind"), calls)
    }

    @Test
    fun theAddressBarsControlsDoWhatTheySay() {
        show()

        composeRule.onNodeWithContentDescription("Site information, connection is secure").performClick()
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").performClick()
        composeRule.onNodeWithContentDescription("Reader view").performClick()

        assertEquals(listOf("siteInfo", "editAddress", "reader"), calls)
    }

    @Test
    fun theAddressSitsInTheBottomClusterAboveBack() {
        show()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val address = composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").getUnclippedBoundsInRoot()
        val back = composeRule.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
        assertTrue(address.top > (root.bottom - root.top) / 2)
        assertTrue(address.left > back.left)
        assertTrue(address.top < back.bottom && back.top < address.bottom)
    }

    // The toolbar

    @Test
    fun theToolbarFollowsTheTabAndDoesWhatItSays() {
        state = Loaded.copy(backEnabled = true, forwardEnabled = false, tabCount = 7)
        show()

        composeRule.onNodeWithContentDescription("Forward").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Tabs, 7 open").performClick()
        composeRule.onAllNodesWithContentDescription("Ask Skim about this page").assertCountEquals(0)

        state = Loaded.copy(backEnabled = false, forwardEnabled = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Forward").performClick()

        assertEquals(listOf("back", "tabs", "forward"), calls)
    }

    @Test
    fun anIncognitoTabsToolbarCountsIncognitoTabsAndItsAiFabIsOff() {
        state = Loaded.copy(incognito = true, tabCount = 2, aiFab = AiFabMode.OffInIncognito)
        show(incognito = true)

        composeRule.onNodeWithContentDescription("Incognito tabs, 2 open").assertExists()
        composeRule.onAllNodesWithContentDescription("Ask Skim, off in incognito").assertCountEquals(0)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun whileThePageFailedToLoadTheAiFabIsUnavailable() {
        state = Failed
        show()

        composeRule.onAllNodesWithContentDescription("Ask Skim about this page, unavailable until the page loads").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Ask Skim about this page").assertCountEquals(0)
    }

    // Page failed to load

    @Test
    fun anErrorCoversThePageWhichStaysWhereItIsBelowIt() {
        var built = 0
        var gone = 0
        show(page = {
            remember { ++built }
            DisposableEffect(Unit) { onDispose { gone++ } }
            Box(Modifier.fillMaxSize().testTag(PAGE))
        })
        composeRule.onAllNodesWithTag(PAGE).assertCountEquals(1)

        state = Failed
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Can't reach this site").assertExists()
        composeRule.onNodeWithContentDescription("Page didn't load").assertExists()
        composeRule.onAllNodesWithTag(PAGE).assertCountEquals(0)

        state = Loaded
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Can't reach this site").assertCountEquals(0)
        composeRule.onAllNodesWithTag(PAGE).assertCountEquals(1)
        assertEquals("the page was never built again", 1, built)
        assertEquals("nor taken away", 0, gone)
    }

    @Test
    fun theErrorsButtonsDoWhatTheySay() {
        state = Failed.copy(loadError = LoadError.NoInternet(FAILED_URL, "ERR_INTERNET_DISCONNECTED"))
        show()

        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onNodeWithText("Internet settings").performClick()

        assertEquals(listOf("tryAgain", "internetSettings"), calls)
    }

    // Pull to refresh

    @Test
    fun aPullAtTheTopOfThePageReloadsIt() {
        val page = NestedScrollDispatcher()
        show(page = { ScrollingPage(page) })

        pull(page)

        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun aPageThatFailedToLoadCannotBePulled() {
        val page = NestedScrollDispatcher()
        state = Failed
        show(page = { ScrollingPage(page) })

        pull(page)

        assertTrue(calls.isEmpty())
    }

    @Test
    fun withRemoveAnimationsTheRefreshIndicatorIsAStillFrame() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        state = Loaded.copy(refreshing = true)
        show()

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f))).assertExists()
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
    }

    @Test
    fun whileThePageReloadsTheRefreshIndicatorMoves() {
        state = Loaded.copy(refreshing = true)
        show()

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    // Helpers

    private fun show(incognito: Boolean = false, page: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag(PAGE)) }) {
        composeRule.setSkimContent(incognito = incognito) {
            BrowsingScreen(state, query, actions, page)
        }
        composeRule.waitForIdle()
    }

    /** A page that hands its drags to the screen, as the tab's WebView does at the top of a page. */
    @Composable
    private fun ScrollingPage(dispatcher: NestedScrollDispatcher) {
        Box(Modifier.fillMaxSize().testTag(PAGE).nestedScroll(object : NestedScrollConnection {}, dispatcher))
    }

    /** A pull down well past the threshold, then the release. */
    private fun pull(dispatcher: NestedScrollDispatcher) {
        composeRule.runOnIdle {
            repeat(5) { dispatcher.dispatchPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.UserInput) }
            dispatcher.coroutineScope.launch { dispatcher.dispatchPreFling(Velocity(0f, 1_000f)) }
        }
        composeRule.waitForIdle()
    }

    private val actions = BrowsingActions(
        onSiteInfo = { calls += "siteInfo" },
        onEditAddress = { calls += "editAddress" },
        onReader = { calls += "reader" },
        onRefresh = { calls += "refresh" },
        onTryAgain = { calls += "tryAgain" },
        onInternetSettings = { calls += "internetSettings" },
        onFindPrevious = { calls += "findPrevious" },
        onFindNext = { calls += "findNext" },
        onCloseFind = { calls += "closeFind" },
        onBack = { calls += "back" },
        onForward = { calls += "forward" },
        onTabs = { calls += "tabs" },
        onMore = { calls += "more" },
        onAskPage = { calls += "askPage" },
    )

    private companion object {
        const val PAGE = "page"
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

        val Failed = Loaded.copy(
            address = "nightbus-kansai.jp/timetable",
            indicator = SiteIndicator.Error,
            readerAvailable = false,
            loadError = LoadError.Unreachable(FAILED_URL, "ERR_NAME_NOT_RESOLVED"),
            aiFab = AiFabMode.UnavailableUntilLoaded,
        )
    }
}
