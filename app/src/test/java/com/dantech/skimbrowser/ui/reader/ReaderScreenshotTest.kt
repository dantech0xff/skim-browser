package com.dantech.skimbrowser.ui.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.page.PageBlock
import com.dantech.skimbrowser.data.settings.ReaderFont
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.AiFab
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reader view (08) and its text settings (08b). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ReaderScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reader() {
        show(sample = fullArticle(), percent = 100, font = ReaderFont.SANS_SERIF, panel = false)
        captureBoard("08-reader-view")
        composeRule.assertA11y()
    }

    @Test
    fun textSettings() {
        show(sample = settingsArticle(), percent = 110, font = ReaderFont.SANS_SERIF, panel = true)
        captureBoard("08b-reader-text-settings")
        composeRule.assertA11y()
    }

    @Test
    fun textSettingsSerif() {
        show(sample = settingsArticle(), percent = 110, font = ReaderFont.SERIF, panel = true)
        captureExtra("08b-reader-text-settings--serif")
        composeRule.assertA11y()
    }

    @Test
    fun textSettingsDark() {
        composeRule.setSkimContent(dark = true) {
            article(sample = settingsArticle(), percent = 110, font = ReaderFont.SERIF, panel = true)
        }
        composeRule.waitForIdle()
        captureExtra("08b-reader-text-settings--dark")
        composeRule.assertA11y()
    }

    private fun show(sample: ReaderArticle, percent: Int, font: ReaderFont, panel: Boolean) {
        composeRule.setSkimContent { article(sample, percent, font, panel) }
        composeRule.waitForIdle()
    }

    @Composable
    private fun article(sample: ReaderArticle, percent: Int, font: ReaderFont, panel: Boolean) {
        ReaderScreen(
            article = sample,
            scalePercent = percent,
            font = font,
            panelOpen = panel,
            canGoBack = true,
            canGoForward = false,
            tabCount = 7,
            incognito = false,
            actions = idleActions(),
            listState = LazyListState(),
            highlightIndex = null,
            highlightNonce = 0,
            aiFab = {
                AiFab(
                    enabled = true,
                    contentDescription = stringResource(R.string.cd_ask_skim_about_page),
                    onClick = {},
                )
            },
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureExtra(name: String) {
        captureScreenRoboImage("src/test/screenshots/boards/$name.png")
    }

    /** 08 draws the whole article. 08b draws only the opening, cut off by the panel. */
    private fun fullArticle() = article(
        PageBlock("1", "p", "For decades the advice was simple: if your winters drop well below freezing, a heat pump is not for you. That advice is now out of date, and the numbers coming out of Norway, Finland and the northern United States show how quickly it has aged."),
        PageBlock("2", "p", "Cold-climate models from several manufacturers keep delivering useful heat at minus 25 °C. The trick is a variable-speed compressor paired with refrigerants that stay efficient when the air outside has little warmth left to give."),
        PageBlock("3", "p", "Installers in Maine report that the question has shifted from whether a heat pump will cope to how large a unit a house really needs. Oversizing, once a safety margin, now mostly means paying for capacity that sits idle."),
        PageBlock("4", "p", "The economics follow the engineering. A typical home switching from oil heating saves between $300 and $900 a year, according to the study."),
    )

    private fun settingsArticle() = article(
        PageBlock("1", "p", "For decades the advice was simple: if your winters drop well below freezing, a heat pump is not for you. That advice is now out of date, and the numbers coming out of Norway, Finland and the northern United States show how quickly it has aged."),
        PageBlock("2", "p", "Cold-climate models from several manufacturers keep delivering useful heat at minus 25 °C."),
    )

    private fun article(vararg blocks: PageBlock) = ReaderArticle(
        host = "daylightjournal.com",
        title = "Why heat pumps are winning in cold climates",
        byline = "Marta Lindqvist",
        blocks = blocks.toList(),
    )

    private fun idleActions() = ReaderActions(
        onExit = {},
        onToggleTextSize = {},
        onClosePanel = {},
        onScale = {},
        onFont = {},
        onBack = {},
        onForward = {},
        onTabs = {},
        onMore = {},
    )
}
