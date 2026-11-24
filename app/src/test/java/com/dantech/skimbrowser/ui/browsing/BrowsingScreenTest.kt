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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.certificate.CertificateFault
import com.dantech.skimbrowser.domain.certificate.CertificateWarning
import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.clickNearEnd
import com.dantech.skimbrowser.testing.clickNearStart
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.SiteIndicator
import com.dantech.skimbrowser.ui.shell.AiFabMode
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The browsing screen as its state draws it, with a stand-in page: which bar shows, the pill's
 * buttons and Ask Skim, the collapsed chip, the error over the page, and pull to refresh, driven
 * the way the tab's WebView drives it (nested scrolling, then a fling decides the refresh).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class BrowsingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val query = TextFieldState()
    private var state by mutableStateOf(Loaded)
    private var collapsed by mutableStateOf(false)

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
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").clickNearEnd()
        composeRule.onNodeWithContentDescription("Reader view").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()

        assertEquals(listOf("siteInfo", "editAddress", "reader", "more"), calls)
    }

    @Test
    fun theAddressSitsInTheBottomPillWithTheLockInsideItsField() {
        show()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val address = composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").getUnclippedBoundsInRoot()
        val lock = composeRule.onNodeWithContentDescription("Site information, connection is secure").getUnclippedBoundsInRoot()
        val tabs = composeRule.onNodeWithContentDescription("Tabs, 7 open").getUnclippedBoundsInRoot()
        assertTrue(address.top > (root.bottom - root.top) / 2)
        assertTrue("the lock is inside the field", lock.left >= address.left && lock.right <= address.right)
        assertTrue(address.right <= tabs.left)
        assertTrue(address.top < tabs.bottom && tabs.top < address.bottom)
    }

    @Test
    fun aTapJustPastTheLockOpensTheAddressNotSiteInformation() {
        show()

        // 6 dp past the lock's edge: inside the 48 dp touch area Compose gives a 28 dp button.
        composeRule.onNodeWithContentDescription("Site information, connection is secure")
            .performTouchInput { click(centerRight + Offset(6.dp.toPx(), 0f)) }

        assertEquals(listOf("editAddress"), calls)
    }

    // The pill's buttons

    @Test
    fun thePillHasNoBackOrForwardAndAskSkimAsksAboutThePage() {
        show()

        composeRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Forward").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Tabs, 7 open").performClick()
        composeRule.onNodeWithContentDescription("Ask Skim about this page").assertIsEnabled().performClick()

        assertEquals(listOf("tabs", "askPage"), calls)
    }

    @Test
    fun theButtonsOutsideTheFieldAre40DpSlotsSideBySide() {
        state = Loaded.copy(videoAvailable = true)
        show()

        val slots = listOf("Reader view", "Download video", "Tabs, 7 open", "More options", "Ask Skim about this page").map { label ->
            composeRule.onNode(hasContentDescription(label) and hasClickAction()).getUnclippedBoundsInRoot()
        }
        slots.forEach { assertEquals(40f, (it.right - it.left).value, 0.5f) }
        slots.zipWithNext().forEach { (before, after) -> assertEquals("no gap", before.right.value, after.left.value, 0.5f) }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun onANarrowScreenSaveVideoLeavesThePillToMoreOptions() {
        state = Loaded.copy(videoAvailable = true)
        show()

        composeRule.onAllNodesWithContentDescription("Download video").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Reader view").assertExists()
        composeRule.onNodeWithContentDescription("More options").performClick()

        assertEquals(listOf("more"), calls)
    }

    @Test
    fun anIncognitoTabsPillCountsIncognitoTabsAndItsAskSkimIsOff() {
        state = Loaded.copy(incognito = true, tabCount = 2, aiMode = AiFabMode.OffInIncognito)
        show(incognito = true)

        composeRule.onNodeWithContentDescription("Incognito tabs, 2 open").assertExists()
        composeRule.onNodeWithContentDescription("Ask Skim, off in incognito")
            .assertIsNotEnabled()
            .assertHasNoClickAction()
        composeRule.onAllNodesWithContentDescription("Ask Skim about this page").assertCountEquals(0)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun whileThePageFailedToLoadAskSkimIsUnavailable() {
        state = Failed
        show()

        composeRule.onNodeWithContentDescription("Ask Skim about this page, unavailable until the page loads")
            .assertIsNotEnabled()
            .assertHasNoClickAction()
        composeRule.onAllNodesWithContentDescription("Ask Skim about this page").assertCountEquals(0)
    }

    // The collapsed chip

    @Test
    fun scrolledDownThePillIsAChipWithTheHostThatBringsThePillBack() {
        collapsed = true
        show()

        composeRule.onAllNodesWithContentDescription("Edit address, daylightjournal.com").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Tabs, 7 open").assertCountEquals(0)
        composeRule.onNodeWithText("daylightjournal.com")
            .assert(
                SemanticsMatcher("labelled Show browser controls") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show browser controls"
                },
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .clickNearStart()

        assertEquals(listOf("expand"), calls)
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").assertExists()
    }

    @Test
    fun theChipsMarkAsksAboutThePage() {
        collapsed = true
        show()

        composeRule.onNodeWithContentDescription("Ask Skim about this page").performClick()

        assertEquals(listOf("askPage"), calls)
    }

    @Test
    fun aTapJustBeforeTheChipsMarkBringsThePillBackNotAskSkim() {
        collapsed = true
        show()

        // 4 dp before the mark: inside the 48 dp touch area Compose gives a 36 dp button.
        composeRule.onNodeWithContentDescription("Ask Skim about this page")
            .performTouchInput { click(centerLeft - Offset(4.dp.toPx(), 0f)) }

        assertEquals(listOf("expand"), calls)
    }

    @Test
    fun withSkimAiOffTheChipHasNoMark() {
        collapsed = true
        state = Loaded.copy(incognito = true, aiMode = AiFabMode.OffInIncognito)
        show(incognito = true)

        composeRule.onNodeWithText("daylightjournal.com").assertExists()
        composeRule.onAllNodesWithContentDescription("Ask Skim, off in incognito").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Ask Skim about this page").assertCountEquals(0)
    }

    @Test
    fun findInPageAndACoveredPageKeepTheFullPill() {
        collapsed = true
        state = Loaded.copy(find = FindUiState(countedText = ""))
        show()
        composeRule.onNodeWithContentDescription("Close find in page").assertExists()

        state = Failed
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Edit address, nightbus-kansai.jp/timetable").assertExists()
    }

    @Test
    fun theChipShowsOnlyWhileNothingNeedsTheFullPill() {
        assertTrue(showsChip(chromeCollapsed = true, Loaded, imeOpen = false))
        assertFalse("at the top of the page", showsChip(chromeCollapsed = false, Loaded, imeOpen = false))
        assertFalse("the keyboard is open", showsChip(chromeCollapsed = true, Loaded, imeOpen = true))
        assertFalse("find in page", showsChip(chromeCollapsed = true, Loaded.copy(find = FindUiState()), imeOpen = false))
        assertFalse("the page failed", showsChip(chromeCollapsed = true, Failed, imeOpen = false))
        val certificate = CertificateWarning("https://bad.example/", "bad.example", "abc", CertificateFault.Expired)
        assertFalse("a certificate warning", showsChip(chromeCollapsed = true, Loaded.copy(certificateWarning = certificate), imeOpen = false))
    }

    @Test
    fun thePillMorphsIntoTheChipOverSeveralFrames() {
        show()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { collapsed = true }
        advanceFrames()
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").assertExists()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Edit address, daylightjournal.com").assertCountEquals(0)
    }

    @Test
    fun withRemoveAnimationsThePillAndTheChipSwitchAtOnce() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { collapsed = true }
        advanceFrames()
        composeRule.onAllNodesWithContentDescription("Edit address, daylightjournal.com").assertCountEquals(0)
        composeRule.onNodeWithText("daylightjournal.com").assertExists()

        composeRule.runOnIdle { collapsed = false }
        advanceFrames()
        composeRule.onNodeWithContentDescription("Edit address, daylightjournal.com").assertExists()
        composeRule.mainClock.autoAdvance = true
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

    @Test
    fun aCertificateWarningHidesProceedUntilAdvanced() {
        state = Loaded.copy(
            address = "bad.example",
            indicator = SiteIndicator.Error,
            readerAvailable = false,
            aiMode = AiFabMode.UnavailableUntilLoaded,
            certificateWarning = CertificateWarning(
                url = "https://bad.example/login",
                host = "bad.example",
                fingerprint = "abc",
                fault = CertificateFault.Expired,
            ),
        )
        show()

        composeRule.onNodeWithText("Your connection is not private").assertExists()
        composeRule.onNodeWithText("The certificate for this site has expired.").assertExists()
        assertTrue(composeRule.onAllNodesWithText("bad.example").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Proceed anyway (unsafe)").assertDoesNotExist()
        composeRule.onNodeWithText("Advanced").performClick()
        composeRule.onNodeWithText("Proceed anyway (unsafe)").performClick()
        composeRule.onNodeWithText("Back to safety").performClick()

        assertEquals(listOf("proceed", "leave"), calls)
    }

    @Test
    fun eachCertificateFaultHasItsOwnSentence() {
        assertEquals(R.string.certificate_error_not_yet_valid, certificateExplanation(CertificateFault.NotYetValid))
        assertEquals(R.string.certificate_error_expired, certificateExplanation(CertificateFault.Expired))
        assertEquals(R.string.certificate_error_hostname, certificateExplanation(CertificateFault.HostnameMismatch))
        assertEquals(R.string.certificate_error_untrusted, certificateExplanation(CertificateFault.Untrusted))
        assertEquals(R.string.certificate_error_date, certificateExplanation(CertificateFault.DateInvalid))
        assertEquals(R.string.certificate_error_invalid, certificateExplanation(CertificateFault.Invalid))
    }

    // Helpers

    private fun show(incognito: Boolean = false, page: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag(PAGE)) }) {
        composeRule.setSkimContent(incognito = incognito) {
            BrowsingScreen(
                state,
                query,
                actions,
                page,
                chromeCollapsed = collapsed,
                onExpandChrome = {
                    calls += "expand"
                    collapsed = false
                },
            )
        }
        composeRule.waitForIdle()
    }

    /** Lets the state written by the test reach the composition, then runs [FRAMES] frames. */
    private fun advanceFrames() {
        composeRule.waitForIdle()
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
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
        onTabs = { calls += "tabs" },
        onMore = { calls += "more" },
        onAskPage = { calls += "askPage" },
        onLeaveUnsafeCertificate = { calls += "leave" },
        onProceedDespiteCertificate = { calls += "proceed" },
    )

    private companion object {
        const val PAGE = "page"

        /** A few frames: long enough for a snap, far shorter than the expressive springs. */
        const val FRAMES = 4
        const val FAILED_URL = "https://nightbus-kansai.jp/timetable"

        val Loaded = BrowsingUiState(
            address = "daylightjournal.com",
            indicator = SiteIndicator.Secure,
            readerAvailable = true,
            progress = null,
            loadError = null,
            tabCount = 7,
            incognito = false,
            aiMode = AiFabMode.Enabled,
            find = null,
            refreshing = false,
        )

        val Failed = Loaded.copy(
            address = "nightbus-kansai.jp/timetable",
            indicator = SiteIndicator.Error,
            readerAvailable = false,
            loadError = LoadError.Unreachable(FAILED_URL, "ERR_NAME_NOT_RESOLVED"),
            aiMode = AiFabMode.UnavailableUntilLoaded,
        )
    }
}
