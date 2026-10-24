package com.dantech.skimbrowser.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabRuntime
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiFabModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val page = tab(url = "https://example.com/")
    private val newTabPage = tab(url = null)
    private val loadErrors = listOf(
        LoadError.Unreachable("https://example.com/", "ERR_NAME_NOT_RESOLVED"),
        LoadError.NoInternet("https://example.com/", "ERR_INTERNET_DISCONNECTED"),
        LoadError.TimedOut("https://example.com/", "ERR_CONNECTION_TIMED_OUT"),
        LoadError.Crashed("https://example.com/", "RENDERER_CRASHED"),
    )

    @Test
    fun aPageAndTheNewTabPageHaveTheFabOn() {
        assertEquals(AiFabMode.Enabled, aiFabMode(page, TabRuntime(pageLoaded = true)))
        assertEquals("while it loads", AiFabMode.Enabled, aiFabMode(page, TabRuntime(progress = 0.4f)))
        assertEquals(AiFabMode.Enabled, aiFabMode(newTabPage, TabRuntime()))
    }

    @Test
    fun incognitoTurnsTheFabOff() {
        assertEquals(AiFabMode.OffInIncognito, aiFabMode(page.copy(incognito = true), TabRuntime(pageLoaded = true)))
        assertEquals("09c", AiFabMode.OffInIncognito, aiFabMode(newTabPage.copy(incognito = true), TabRuntime()))
        assertEquals(AiFabMode.OffInIncognito, aiFabMode(page.copy(incognito = true), TabRuntime(loadError = loadErrors.first())))
    }

    @Test
    fun aPageThatFailedToLoadHasNothingToReadUntilItLoads() {
        for (error in loadErrors) {
            assertEquals(error.toString(), AiFabMode.UnavailableUntilLoaded, aiFabMode(page, TabRuntime(loadError = error)))
        }
    }

    @Test
    fun theLabelSaysWhatTheFabOpensOrWhyItIsOff() {
        assertEquals(R.string.ask_skim, aiFabLabel(AiFabMode.Enabled, isNewTabPage = true))
        assertEquals(R.string.cd_ask_skim_about_page, aiFabLabel(AiFabMode.Enabled, isNewTabPage = false))
        assertEquals(R.string.cd_ask_skim_off_incognito, aiFabLabel(AiFabMode.OffInIncognito, isNewTabPage = true))
        assertEquals(R.string.cd_ask_skim_off_incognito, aiFabLabel(AiFabMode.OffInIncognito, isNewTabPage = false))
        assertEquals(R.string.cd_ask_skim_unavailable, aiFabLabel(AiFabMode.UnavailableUntilLoaded, isNewTabPage = false))
    }

    @Test
    fun theFabOfAPageAsksAboutIt() {
        var asked = 0
        composeRule.setSkimContent {
            Box(Modifier.size(56.dp)) { TabAiFab(page, TabRuntime(pageLoaded = true), onClick = { asked++ }) }
        }

        composeRule.onNodeWithContentDescription("Ask Skim about this page").performClick()

        assertEquals(1, asked)
    }

    @Test
    fun theFabOfAnIncognitoTabCannotBeTapped() {
        var asked = 0
        composeRule.setSkimContent(incognito = true) {
            Box(Modifier.size(56.dp)) { TabAiFab(newTabPage.copy(incognito = true), TabRuntime(), onClick = { asked++ }) }
        }

        composeRule.onNodeWithContentDescription("Ask Skim, off in incognito")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        assertEquals(0, asked)
    }

    @Test
    fun theFabOfAPageThatFailedToLoadSaysWhy() {
        composeRule.setSkimContent {
            Box(Modifier.size(56.dp)) { TabAiFab(page, TabRuntime(loadError = loadErrors.first()), onClick = {}) }
        }

        composeRule.onNodeWithContentDescription("Ask Skim about this page, unavailable until the page loads").assertIsNotEnabled()
    }

    private fun tab(url: String?) = Tab(
        id = "tab-1",
        incognito = false,
        url = url,
        title = "",
        groupId = null,
        position = 0,
        createdAt = 1L,
        lastAccessedAt = 1L,
    )
}
