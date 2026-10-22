package com.dantech.skimbrowser.ui.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home (04) as its board draws it, with the board's data: N = 7, seven shortcuts and two Continue
 * cards; and the first Home, which the boards do not draw: no hero card, no Continue with Skim, only
 * the Add tile.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home() {
        setHome(HomeFixtures.Board, tabCount = 7)

        captureBoard("04-home-new-tab")
        composeRule.assertA11y()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun theFirstHome() {
        setHome(HomeFixtures.FirstHome, tabCount = 1)

        captureScreenRoboImage("$COMPONENT_GOLDENS/home-04-first.png")
        composeRule.assertA11y()
    }

    private fun setHome(state: HomeUiState, tabCount: Int) {
        val tab = HomeFixtures.newTabPage()
        composeRule.setSkimContent {
            HomeScreen(state, HomeFixtures.Recorder().actions, toolbar = { HomeFixtures.Toolbar(tab, tabCount) })
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val COMPONENT_GOLDENS = "src/test/screenshots/components"
    }
}
