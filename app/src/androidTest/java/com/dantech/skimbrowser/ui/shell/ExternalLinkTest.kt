package com.dantech.skimbrowser.ui.shell

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.waitUntil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A web link another app sends: Back on its first page closes that tab for good and returns to the
 * app that sent it.
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

    /**
     * The one tab showing [url], once it is open. The app opens a link's tab, makes it current and
     * shows the browser in one task of the main thread, and the tab is in the tab list before it is
     * current: the state is read once that task is over.
     */
    private fun awaitTabShowing(url: String): Tab {
        waitUntil("a tab to show the link") { skim.tabsShowing(url).isNotEmpty() }
        return onMain { skim.tabsShowing(url).single() }
    }
}
