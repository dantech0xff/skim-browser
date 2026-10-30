package com.dantech.skimbrowser.ui.onboarding

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Welcome (01) and Default browser and import (03) as their boards draw them, with the brand
 * scheme. The goldens of 01 and 03 compare with the board PNGs directly; 03 once Skim is the default
 * browser (Done, disabled) is not drawn and is its own golden.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class OnboardingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun restoreAnimations() = setAnimatorScale(1f)

    @Test
    fun welcome() {
        composeRule.setSkimContent { Welcome() }
        composeRule.waitForIdle()

        captureBoard("01-welcome")
        composeRule.assertA11y()
    }

    @Test
    fun setupBeforeSkimIsTheDefaultBrowser() {
        composeRule.setSkimContent { SetupScreen(SetupUiState(isDefault = false), onSetDefault = {}, onChooseFile = {}, onStartBrowsing = {}) }
        composeRule.waitForIdle()

        captureBoard("03-default-browser-import")
        composeRule.assertA11y()
        composeRule.onNodeWithText("Set as default").assertIsEnabled()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun setupOnceSkimIsTheDefaultBrowser() {
        composeRule.setSkimContent { SetupScreen(SetupUiState(isDefault = true), onSetDefault = {}, onChooseFile = {}, onStartBrowsing = {}) }
        composeRule.waitForIdle()

        captureScreenRoboImage("$COMPONENT_GOLDENS/setup-done-03.png")
        composeRule.assertA11y()
        composeRule.onNodeWithText("Done").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Set as default").assertCountEquals(0)
    }

    @Test
    fun welcomeActions() {
        val opened = mutableListOf<String>()
        var started = 0
        composeRule.setSkimContent {
            WelcomeScreen(onGetStarted = { started++ }, onOpenLegal = { opened += it }, termsUrl = TERMS_URL, privacyUrl = PRIVACY_URL)
        }

        composeRule.onNodeWithText("Get started").performClick()
        legalLine().performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == TERMS_URL }
        legalLine().performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == PRIVACY_URL }

        assertEquals(1, started)
        assertEquals(listOf(TERMS_URL, PRIVACY_URL), opened)
    }

    @Test
    fun withoutLegalAddressesTheLegalLineHasNoLinks() {
        composeRule.setSkimContent {
            WelcomeScreen(onGetStarted = {}, onOpenLegal = {}, termsUrl = "", privacyUrl = "")
        }

        legalLine().assert(
            SemanticsMatcher("has no link") { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.all { it.getLinkAnnotations(0, it.length).isEmpty() }
            },
        )
        composeRule.onNodeWithText("By continuing you agree to the Terms of Service and Privacy Policy.").assertExists()
    }

    @Test
    fun setupActions() {
        val calls = mutableListOf<String>()
        composeRule.setSkimContent {
            SetupScreen(
                SetupUiState(isDefault = false),
                onSetDefault = { calls += "default" },
                onChooseFile = { calls += "file" },
                onStartBrowsing = { calls += "start" },
            )
        }

        composeRule.onNodeWithText("Set as default").performClick()
        composeRule.onNodeWithText("Choose file").performClick()
        composeRule.onNodeWithText("Start browsing").performClick()

        assertEquals(listOf("default", "file", "start"), calls)
    }

    @Test
    fun welcomeWithRemoveAnimationsMatchesTheBoard() {
        setAnimatorScale(0f)
        composeRule.setSkimContent { Welcome() }
        composeRule.waitForIdle()

        captureBoard("01-welcome")
        composeRule.assertA11y()
    }

    @Test
    fun setupWithRemoveAnimationsMatchesTheBoard() {
        setAnimatorScale(0f)
        composeRule.setSkimContent {
            SetupScreen(SetupUiState(isDefault = false), onSetDefault = {}, onChooseFile = {}, onStartBrowsing = {})
        }
        composeRule.waitForIdle()

        captureBoard("03-default-browser-import")
        composeRule.assertA11y()
    }

    @Test
    fun aFinishedCrossfadeRestsOnDone() {
        val isDefault = mutableStateOf(false)
        composeRule.setSkimContent {
            val default by isDefault
            SetupScreen(
                SetupUiState(isDefault = default),
                onSetDefault = {},
                onChooseFile = {},
                onStartBrowsing = {},
            )
        }
        isDefault.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Done").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Set as default").assertCountEquals(0)
    }

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Composable
    private fun Welcome() {
        WelcomeScreen(onGetStarted = {}, onOpenLegal = {}, termsUrl = TERMS_URL, privacyUrl = PRIVACY_URL)
    }

    private fun legalLine() = composeRule.onNodeWithText("By continuing", substring = true)

    private companion object {
        const val COMPONENT_GOLDENS = "src/test/screenshots/components"
        const val TERMS_URL = "https://example.com/terms"
        const val PRIVACY_URL = "https://example.com/privacy"
    }
}
