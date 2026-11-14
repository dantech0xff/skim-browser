package com.dantech.skimbrowser.ui.shield

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.shield.BlockedHost
import com.dantech.skimbrowser.domain.shield.ShieldDay
import com.dantech.skimbrowser.domain.shield.shieldSnapshot
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Blocking stats. There is no design board for this screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ShieldStatsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statsLight() {
        show(dark = false)
        capture("shield-stats")
    }

    @Test
    fun statsDark() {
        show(dark = true)
        capture("shield-stats-dark")
    }

    @Test
    fun resetAsksBeforeItClears() {
        var resets = 0
        composeRule.setSkimContent {
            ShieldStatsScreen(state = sample().toUi(), onBack = {}, onReset = { resets++ })
        }
        composeRule.onNodeWithText("Reset stats").performClick()
        composeRule.onNodeWithText("Reset blocking stats?").assertExists()
        composeRule.onNodeWithText("Reset").performClick()
        composeRule.waitForIdle()
        check(resets == 1)
    }

    private fun show(dark: Boolean) {
        composeRule.setSkimContent(dark = dark) {
            ShieldStatsScreen(state = sample().toUi(), onBack = {}, onReset = {})
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        captureScreenRoboImage("src/test/screenshots/components/$name.png")
    }

    private fun sample() = shieldSnapshot(
        days = listOf(
            ShieldDay(epochDay = 10, requests = 12, popups = 1, redirects = 0, cosmetic = 4, bytes = 200_000),
            ShieldDay(epochDay = 9, requests = 3, popups = 0, redirects = 1, cosmetic = 2, bytes = 40_000),
            ShieldDay(epochDay = 8, requests = 8, popups = 0, redirects = 0, cosmetic = 1, bytes = 90_000),
        ),
        topHosts = listOf(
            BlockedHost("doubleclick.net", 8),
            BlockedHost("google-analytics.com", 4),
        ),
        todayEpochDay = 10,
    )
}
