package com.dantech.skimbrowser.ui.browsing

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Velocity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.ProvideHaptics
import com.dantech.skimbrowser.testing.RecordingHapticFeedback
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

/** Pull to refresh asks for one haptic when the pull reaches the point where a release reloads. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class PullToRefreshHapticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val haptics = RecordingHapticFeedback()
    private val page = NestedScrollDispatcher()
    private var refreshes = 0

    @Test
    fun aPullPastTheThresholdAsksForOneThresholdHaptic() {
        show(Loaded)

        pull()

        assertEquals(1, refreshes)
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics.performed)
    }

    @Test
    fun aPullThatStopsShortAsksForNothing() {
        show(Loaded)

        composeRule.runOnIdle {
            page.dispatchPostScroll(Offset.Zero, Offset(0f, 20f), NestedScrollSource.UserInput)
            page.coroutineScope.launch { page.dispatchPreFling(Velocity.Zero) }
        }
        composeRule.waitForIdle()

        assertEquals(0, refreshes)
        assertTrue(haptics.performed.isEmpty())
    }

    @Test
    fun aPullWhileThePageReloadsAsksForNothing() {
        show(Loaded.copy(refreshing = true))

        pull()

        assertTrue(haptics.performed.isEmpty())
    }

    @Test
    fun aPageThatFailedToLoadCannotBePulledAndAsksForNothing() {
        show(Failed)

        pull()

        assertEquals(0, refreshes)
        assertTrue(haptics.performed.isEmpty())
    }

    @Test
    fun withRemoveAnimationsTheHapticStays() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show(Loaded)

        pull()

        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics.performed)
    }

    private fun show(state: BrowsingUiState) {
        composeRule.setSkimContent {
            ProvideHaptics(haptics) {
                BrowsingScreen(state, TextFieldState(), actions, page = { ScrollingPage() })
            }
        }
        composeRule.waitForIdle()
    }

    /** A page that hands its drags to the screen, as the tab's WebView does at the top of a page. */
    @Composable
    private fun ScrollingPage() {
        Box(Modifier.fillMaxSize().nestedScroll(object : NestedScrollConnection {}, page))
    }

    /** A pull down well past the threshold, then the release. */
    private fun pull() {
        composeRule.runOnIdle {
            repeat(5) { page.dispatchPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.UserInput) }
            page.coroutineScope.launch { page.dispatchPreFling(Velocity(0f, 1_000f)) }
        }
        composeRule.waitForIdle()
    }

    private val actions = BrowsingActions(
        onSiteInfo = {},
        onEditAddress = {},
        onReader = {},
        onRefresh = { refreshes++ },
        onTryAgain = {},
        onInternetSettings = {},
        onFindPrevious = {},
        onFindNext = {},
        onCloseFind = {},
        onTabs = {},
        onMore = {},
        onAskPage = {},
    )

    private companion object {
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
            loadError = LoadError.Unreachable("https://nightbus-kansai.jp/timetable", "ERR_NAME_NOT_RESOLVED"),
            aiMode = AiFabMode.UnavailableUntilLoaded,
        )
    }
}
