package com.dantech.skimbrowser.ui.browsing

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.phishing.PhishingReason
import com.dantech.skimbrowser.domain.phishing.SafeBrowsingThreat
import com.dantech.skimbrowser.domain.phishing.SafetyWarning
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Scam warnings have no board. The strong page is a Safe Browsing hit. The soft page is the
 * on-device lookalike check. Proceed stays behind Advanced, so these frames show Back to safety.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SafetyWarningScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun dangerousSite() {
        composeRule.setSkimContent {
            SafetyWarningContent(
                warning = SafetyWarning.SafeBrowsing(
                    url = "https://evil.example/login",
                    host = "evil.example",
                    threat = SafeBrowsingThreat.PHISHING,
                ),
                onBackToSafety = {},
                onProceed = {},
            )
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("$GOLDENS/safety-warning-dangerous.png")
        composeRule.assertA11y()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun lookalikeAddress() {
        composeRule.setSkimContent {
            SafetyWarningContent(
                warning = SafetyWarning.Heuristic(
                    url = "https://paypai.com/login",
                    host = "paypai.com",
                    reason = PhishingReason.LOOKALIKE,
                    brand = "PayPal",
                ),
                onBackToSafety = {},
                onProceed = {},
            )
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("$GOLDENS/safety-warning-lookalike.png")
        composeRule.assertA11y()
    }

    private companion object {
        const val GOLDENS = "src/test/screenshots/components"
    }
}
