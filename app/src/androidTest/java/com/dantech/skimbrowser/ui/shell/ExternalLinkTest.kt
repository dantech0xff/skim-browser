package com.dantech.skimbrowser.ui.shell

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.WebViewSupport
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.waitUntil
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.TabsKey
import com.dantech.skimbrowser.ui.navigation.WelcomeKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Web links other apps send: each opens once, in a new normal tab shown at once, and Back on its
 * first page closes that tab for good and returns to the app that sent it. A recreated Activity,
 * Skim reopened from the launcher or from Recents never opens an earlier link again. Only http and
 * https links open, never in incognito.
 *
 * Whether a link opened again shows only once the app had the chance to open it: another link
 * sent afterwards opens after any earlier one, so once its tab is there the tabs are counted.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExternalLinkTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val skim = SkimAppRule()

    private val link: String get() = skim.pages.url("/plain.html")

    @Test
    fun aLinkFromAnotherAppOpensInANewNormalTabInFront() {
        skim.setOnboarded(true)
        val earlier = skim.openTab(skim.pages.url("/article.html"))
        val scenario = skim.launch(skim.linkIntent(link))

        val tab = awaitTabShowing(link)
        assertTrue(tab.openedByExternalApp)
        assertFalse(tab.incognito)
        assertEquals(tab.id, skim.tabs.state.value.currentTabId)
        assertEquals(listOf(earlier, tab.id), skim.tabs.state.value.tabs.map { it.id })
        assertEquals(listOf(BrowserKey), skim.keysOf(scenario))
        assertTrue(runBlocking { skim.tabs.awaitPageReady(tab.id) })
        val activity = skim.activityOf(scenario)
        waitUntil("the link's page to be on screen") { onMain { activity.webViewsOnScreen().isNotEmpty() } }
    }

    @Test
    fun backOnTheLinksFirstPageClosesItsTabForGoodAndLeavesSkim() {
        skim.setOnboarded(true)
        val scenario = skim.launch(skim.linkIntent(link))
        val tab = awaitTabShowing(link)
        assertTrue(runBlocking { skim.tabs.awaitPageReady(tab.id) })

        skim.pressBack()

        waitUntil("Skim to move to the background") { scenario.state == Lifecycle.State.CREATED }
        assertTrue("the link's tab is closed", skim.tabsShowing(link).isEmpty())
        assertFalse("for good: there is no Undo", tab.id in onMain { skim.tabs.pendingCloseIds() })
        assertTrue("no other page opened", skim.tabs.state.value.tabs.all { it.url == null })
    }

    @Test
    fun onAFirstLaunchTheLinkShowsOverWelcomeAndBackStillClosesItAndLeaves() {
        skim.setOnboarded(false)
        val scenario = skim.launch(skim.linkIntent(link))
        val tab = awaitTabShowing(link)
        assertEquals(listOf(WelcomeKey, BrowserKey), skim.keysOf(scenario))
        assertTrue(runBlocking { skim.tabs.awaitPageReady(tab.id) })

        skim.pressBack()

        waitUntil("Skim to move to the background") { scenario.state == Lifecycle.State.CREATED }
        assertTrue(skim.tabsShowing(link).isEmpty())
        assertFalse(tab.id in onMain { skim.tabs.pendingCloseIds() })
    }

    @Test
    fun aLinkIsNeverOpenedAgainByARecreatedOrReopenedActivity() {
        skim.setOnboarded(true)
        val first = skim.launch(skim.linkIntent(link))
        awaitTabShowing(link)

        first.recreate()
        skim.navigatorOf(first)
        awaitLinkOpenedAfterwards("/plain.html?after=recreate")
        assertEquals("after recreate", 1, skim.tabsShowing(link).size)

        first.close()
        val fromLauncher = skim.launch()
        skim.navigatorOf(fromLauncher)
        awaitLinkOpenedAfterwards("/plain.html?after=launcher")
        assertEquals("after a start from the launcher", 1, skim.tabsShowing(link).size)

        fromLauncher.close()
        // Recents starts a task again with the intent that first started it, marked as coming from history.
        val fromRecents = skim.launch(skim.linkIntent(link).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        skim.navigatorOf(fromRecents)
        awaitLinkOpenedAfterwards("/plain.html?after=recents")
        assertEquals("after a start from Recents", 1, skim.tabsShowing(link).size)
        assertEquals("one tab per link", 4, skim.tabs.state.value.tabs.size)
    }

    @Test
    fun aLinkSentWhileSkimRunsOpensAnotherTabInFront() {
        skim.setOnboarded(true)
        val scenario = skim.launch(skim.linkIntent(link))
        val first = awaitTabShowing(link)
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        onMain { navigator.openTabs(incognito = false) }

        val second = skim.pages.url("/article.html")
        skim.send(skim.linkIntent(second))

        val tab = awaitTabShowing(second)
        assertTrue(tab.openedByExternalApp)
        assertFalse(tab.incognito)
        assertEquals(tab.id, skim.tabs.state.value.currentTabId)
        assertEquals(listOf(first.id, tab.id), skim.tabs.state.value.tabs.map { it.id })
        assertEquals("the browser shows it, over what was open", listOf(BrowserKey), skim.keysOf(scenario))
        assertSame("the running Activity received it", activity, skim.activityOf(scenario))
    }

    @Test
    fun onlyWebLinksOpen() {
        skim.setOnboarded(true)
        val earlier = skim.openTab(link)
        // Started for a link that is not a web page, then sent more of them while it runs.
        val scenario = skim.launch(skim.linkIntent("javascript:document.title='opened'"))
        skim.navigatorOf(scenario)
        listOf("file:///sdcard/Download/notes.html", "intent://scan/#Intent;scheme=zxing;end", "javascript:alert(1)")
            .forEach { skim.send(skim.linkIntent(it)) }

        val web = awaitLinkOpenedAfterwards("/article.html")

        assertEquals(listOf(earlier, web.id), skim.tabs.state.value.tabs.map { it.id })
    }

    @Test
    fun aLinkNeverOpensInIncognitoEvenOverIncognitoScreens() {
        WebViewSupport.assumeProfiles()
        skim.setOnboarded(true)
        val incognito = skim.openTab(skim.pages.url("/article.html"), incognito = true)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val activity = skim.activityOf(scenario)
        onMain { navigator.openTabs(incognito = true) }
        assertTrue(onMain { activity.isSecure() })

        skim.send(skim.linkIntent(link))

        val tab = awaitTabShowing(link)
        assertFalse(tab.incognito)
        assertEquals(tab.id, skim.tabs.state.value.currentTabId)
        assertEquals(listOf(BrowserKey), skim.keysOf(scenario))
        assertTrue("the incognito tab stays as it was", skim.tabs.state.value.tab(incognito)?.incognito == true)
        waitUntil("the window to let FLAG_SECURE go once the normal tab shows") { onMain { !activity.isSecure() } }
        assertFalse(TabsKey(incognito = true) in skim.keysOf(scenario))
    }

    /**
     * The one tab showing [url], once it is open. The app opens a link's tab, makes it current and
     * shows the browser in one task of the main thread, and the tab is in the tab list before it is
     * current: the state is read once that task is over.
     */
    private fun awaitTabShowing(url: String): Tab {
        waitUntil("a tab to show the link") { skim.tabsShowing(url).isNotEmpty() }
        return onMain { skim.tabsShowing(url).single() }
    }

    /** Sends a link to the page at [path] to the running Skim and returns its tab, once open. */
    private fun awaitLinkOpenedAfterwards(path: String): Tab {
        val url = skim.pages.url(path)
        skim.send(skim.linkIntent(url))
        return awaitTabShowing(url)
    }
}
