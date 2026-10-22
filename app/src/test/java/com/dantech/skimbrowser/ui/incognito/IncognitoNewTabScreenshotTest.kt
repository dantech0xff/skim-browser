package com.dantech.skimbrowser.ui.incognito

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.home.HomeFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The incognito New tab page (09c) as its board draws it: two incognito tabs, the mic shown. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class IncognitoNewTabScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun incognitoNewTab() {
        val tab = HomeFixtures.newTabPage(incognito = true)
        composeRule.setSkimContent(incognito = true) {
            IncognitoNewTabScreen(
                voiceAvailable = true,
                onSearch = {},
                onVoice = {},
                toolbar = { HomeFixtures.Toolbar(tab, tabCount = 2) },
            )
        }
        composeRule.waitForIdle()

        captureBoard("09c-incognito-new-tab")
        composeRule.assertA11y()
    }
}
