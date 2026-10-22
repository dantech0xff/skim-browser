package com.dantech.skimbrowser.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Search & suggestions (04b) as its board draws it, "heat pump" typed: Ask Skim, three of the
 * engine's suggestions and one history entry; and the same field in an incognito tab, which the
 * boards do not draw: dark, the engine's suggestions only. Both are whole screens with the layer's
 * window over the screen that opened it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SearchScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rig = SearchRig()

    @After
    fun tearDown() = rig.close()

    @Test
    fun searchAndSuggestions() {
        rig.seedHistory()
        rig.suggested["heat pump"] = SearchRig.HeatPumpSuggestions
        typeHeatPump(incognito = false)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Why heat pumps are winning in cold climates").fetchSemanticsNodes().isNotEmpty()
        }

        captureBoard("04b-search-suggestions")
        composeRule.assertA11y()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun searchAndSuggestionsInAnIncognitoTab() {
        rig.seedHistory()
        rig.suggested["heat pump"] = SearchRig.HeatPumpSuggestions
        typeHeatPump(incognito = true)

        captureScreenRoboImage("$COMPONENT_GOLDENS/search-04b-incognito.png")
        composeRule.assertA11y()
    }

    private fun typeHeatPump(incognito: Boolean) {
        rig.search.open(incognito = incognito)
        composeRule.setSkimContent(incognito = incognito) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            SearchOverlay(rig.search, incognito, onOpenUrl = {}, onAskSkim = {}, onClose = {})
        }
        composeRule.giveDialogAStatusBar()
        composeRule.onNode(hasSetTextAction()).performTextInput("heat pump")
        composeRule.pass(150)
    }

    private companion object {
        const val COMPONENT_GOLDENS = "src/test/screenshots/components"
    }
}
