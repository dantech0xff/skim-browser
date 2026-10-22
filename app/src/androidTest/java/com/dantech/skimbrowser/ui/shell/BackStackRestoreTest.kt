package com.dantech.skimbrowser.ui.shell

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.HitTarget
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.waitUntil
import com.dantech.skimbrowser.ui.navigation.AiPermissionKey
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.LinkOptionsKey
import com.dantech.skimbrowser.ui.navigation.MoreMenuKey
import com.dantech.skimbrowser.ui.navigation.ReaderKey
import com.dantech.skimbrowser.ui.navigation.TabsKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The back stack across a recreated Activity: the system saves it with the Activity's state and
 * the new Activity shows it again, open sheets and dialogs included, once the keys of tabs that are
 * gone are dropped. MainActivity handles configuration changes itself, so `recreate` stands for
 * every time the system rebuilds it from saved state.
 */
@RunWith(AndroidJUnit4::class)
class BackStackRestoreTest {

    @get:Rule
    val skim = SkimAppRule()

    @Test
    fun openSheetsAndDialogsComeBackWithTheRecreatedActivity() {
        skim.setOnboarded(true)
        val page = skim.pages.url("/plain.html")
        val tab = skim.openTab(page)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val image = HitTarget.Image(src = skim.pages.url("/image.png"), alt = "A red square", linkUrl = page)
        onMain {
            navigator.openReader(tab)
            navigator.openLinkOptions(tab, image)
            navigator.openDialog(AiPermissionKey(tab, fromReader = true))
        }
        val shown = listOf(BrowserKey, ReaderKey(tab), LinkOptionsKey(tab, image), AiPermissionKey(tab, fromReader = true))
        assertEquals(shown, skim.keysOf(scenario))
        val before = skim.activityOf(scenario)

        scenario.recreate()

        assertNotSame("a new Activity", before, skim.activityOf(scenario))
        assertEquals("the recreated Activity shows the same back stack", shown, skim.keysOf(scenario))
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }

    @Test
    fun keysOfATabClosedMeanwhileAreDroppedAndTheBrowserShowsAnotherTab() {
        skim.setOnboarded(true)
        val kept = skim.openTab(skim.pages.url("/plain.html"))
        val closed = skim.openTab(skim.pages.url("/article.html"))
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        onMain {
            navigator.openMoreMenu(closed)
            navigator.openDialog(AiPermissionKey(closed))
        }
        assertEquals(listOf(BrowserKey, MoreMenuKey(closed), AiPermissionKey(closed)), skim.keysOf(scenario))
        // The tab goes while its sheet is open (a page can close its own tab), for good.
        onMain { skim.tabs.finalizeClose(skim.tabs.close(listOf(closed))) }

        scenario.recreate()

        assertEquals(listOf(BrowserKey), skim.keysOf(scenario))
        assertEquals(kept, skim.tabs.state.value.currentTabId)
        assertTrue(runBlocking { skim.tabs.awaitPageReady(kept) })
        val activity = skim.activityOf(scenario)
        waitUntil("the browser to show the tab that is left") { onMain { activity.webViewsOnScreen().isNotEmpty() } }
    }

    @Test
    fun incognitoTabsWithNoIncognitoTabLeftComeBackAsTabs() {
        skim.setOnboarded(true)
        skim.openTab(skim.pages.url("/plain.html"))
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        onMain { navigator.openTabs(incognito = true) }
        assertEquals(listOf(BrowserKey, TabsKey(incognito = true)), skim.keysOf(scenario))

        scenario.recreate()

        assertEquals(listOf(BrowserKey, TabsKey(incognito = false)), skim.keysOf(scenario))
        val activity = skim.activityOf(scenario)
        assertFalse("Tabs show nothing incognito", onMain { activity.isSecure() })
    }
}
