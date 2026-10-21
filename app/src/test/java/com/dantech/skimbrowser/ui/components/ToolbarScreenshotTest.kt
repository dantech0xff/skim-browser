package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureComponent
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The floating toolbars at the bottom of their boards: each golden is the bottom 120 dp of a
 * 412 dp wide screen, so it lines up with the same strip of the board PNG (toolbar 64 high, 24 above
 * the bottom edge, FAB 56 and 8 to the right of the bar, the pair centered).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ToolbarScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browserToolbarOnAPage() = captureToolbar("browser-toolbar-05") { modifier ->
        PageToolbar(canGoBack = true, tabCount = 7, aiEnabled = true, aiLabel = R.string.cd_ask_skim_about_page, modifier = modifier)
    }

    @Test
    fun browserToolbarOnANewTab() = captureToolbar("browser-toolbar-04") { modifier ->
        PageToolbar(canGoBack = false, tabCount = 7, aiEnabled = true, aiLabel = R.string.ask_skim, modifier = modifier)
    }

    @Test
    fun browserToolbarWhenThePageFailedToLoad() = captureToolbar("browser-toolbar-11") { modifier ->
        PageToolbar(canGoBack = true, tabCount = 7, aiEnabled = false, aiLabel = R.string.cd_ask_skim_unavailable, modifier = modifier)
    }

    @Test
    fun browserToolbarInIncognito() = captureToolbar("browser-toolbar-09c", incognito = true) { modifier ->
        PageToolbar(
            canGoBack = false,
            tabCount = 2,
            aiEnabled = false,
            aiLabel = R.string.cd_ask_skim_off_incognito,
            incognito = true,
            modifier = modifier,
        )
    }

    @Test
    fun browserToolbarInTheDarkTheme() = captureToolbar("browser-toolbar-05-dark", dark = true) { modifier ->
        PageToolbar(canGoBack = true, tabCount = 7, aiEnabled = true, aiLabel = R.string.cd_ask_skim_about_page, modifier = modifier)
    }

    @Test
    fun browserToolbarWithAHundredTabs() = captureToolbar("browser-toolbar-tab-count-overflow") { modifier ->
        PageToolbar(canGoBack = true, tabCount = 120, aiEnabled = true, aiLabel = R.string.cd_ask_skim_about_page, modifier = modifier)
    }

    @Test
    fun tabSwitcherToolbar() = captureToolbar("tab-switcher-toolbar-09") { modifier ->
        TabSwitcherToolbar(
            incognito = false,
            aiEnabled = true,
            onAskSkim = {},
            onNewIncognitoTab = {},
            onCloseAll = {},
            onNewTab = {},
            modifier = modifier,
        )
    }

    @Test
    fun tabSwitcherToolbarInIncognito() = captureToolbar("tab-switcher-toolbar-09b", incognito = true) { modifier ->
        TabSwitcherToolbar(
            incognito = true,
            aiEnabled = false,
            onAskSkim = {},
            onNewIncognitoTab = null,
            onCloseAll = {},
            onNewTab = {},
            modifier = modifier,
        )
    }

    @Composable
    private fun PageToolbar(
        canGoBack: Boolean,
        tabCount: Int,
        aiEnabled: Boolean,
        aiLabel: Int,
        modifier: Modifier,
        incognito: Boolean = false,
    ) {
        val label = stringResource(aiLabel)
        BrowserToolbar(
            canGoBack = canGoBack,
            canGoForward = false,
            tabCount = tabCount,
            incognito = incognito,
            onBack = {},
            onForward = {},
            onTabs = {},
            onMore = {},
            aiFab = { AiFab(enabled = aiEnabled, contentDescription = label, onClick = {}) },
            modifier = modifier,
        )
    }

    /** Lays the toolbar out the way screens place it, at the bottom of a board-wide strip. */
    private fun captureToolbar(
        name: String,
        dark: Boolean = false,
        incognito: Boolean = false,
        toolbar: @Composable (Modifier) -> Unit,
    ) {
        composeRule.setSkimContent(dark = dark, incognito = incognito) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag(FRAME),
            ) {
                toolbar(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = ToolbarMetrics.BottomOffset),
                )
            }
        }
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    private companion object {
        const val FRAME = "frame"
    }
}
