package com.dantech.skimbrowser.ui.shell

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.awaitValue
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.waitUntil
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.SetupKey
import com.dantech.skimbrowser.ui.navigation.WelcomeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How the app starts: the first launch on Welcome (01), Get started to Setup (03), Start browsing
 * to the browser for good, and every later launch straight on the last tab. Buttons are tapped on
 * screen, Back is the system's.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppStartTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val skim = SkimAppRule()

    private val getStarted: String get() = skim.app.getString(R.string.welcome_get_started)
    private val startBrowsing: String get() = skim.app.getString(R.string.setup_start_browsing)

    @Test
    fun theFirstLaunchOpensOnWelcome() {
        skim.setOnboarded(false)
        val scenario = skim.launch()

        assertEquals(listOf(WelcomeKey), skim.keysOf(scenario))
        AppScreen.awaitNode(skim.activityOf(scenario), getStarted)
        assertTrue("Welcome opens no tab", skim.tabs.state.value.tabs.isEmpty())
    }

    @Test
    fun getStartedOpensSetupAndBackReturnsToWelcome() {
        skim.setOnboarded(false)
        val scenario = skim.launch()
        val activity = skim.activityOf(scenario)

        AppScreen.tap(activity, getStarted)
        waitUntil("Setup to open") { skim.keysOf(scenario) == listOf(WelcomeKey, SetupKey) }
        AppScreen.awaitNode(activity, startBrowsing)

        skim.pressBack()
        waitUntil("Welcome to be back") { skim.keysOf(scenario) == listOf(WelcomeKey) }
        waitUntil("Setup to be gone") { !AppScreen.shows(activity, startBrowsing) }
        AppScreen.awaitNode(activity, getStarted)
    }

    @Test
    fun startBrowsingLeavesOnboardingForGoodAndTheNextLaunchOpensOnTheSameTab() {
        skim.setOnboarded(false)
        val first = skim.launch()
        val activity = skim.activityOf(first)
        AppScreen.tap(activity, getStarted)
        waitUntil("Welcome to make way for Setup") { !AppScreen.shows(activity, getStarted) }
        AppScreen.tap(activity, startBrowsing)

        waitUntil("the browser to be all that is left") { skim.keysOf(first) == listOf(BrowserKey) }
        awaitValue(skim.graph.settings().settings, "onboarding to be saved as done") { it.onboardingCompleted }
        waitUntil("the browser to open a tab") { skim.tabs.state.value.currentTabId != null }
        val tab = checkNotNull(skim.tabs.state.value.currentTabId)
        onMain { skim.tabs.load(tab, skim.pages.url("/plain.html")) }
        first.close()

        val next = skim.launch()
        assertEquals(listOf(BrowserKey), skim.keysOf(next))
        assertEquals(tab, skim.tabs.state.value.currentTabId)
        assertEquals(1, skim.tabs.state.value.tabs.size)
    }

    @Test
    fun aLaterLaunchOpensOnTheLastTabUsed() {
        skim.setOnboarded(true)
        val last = skim.openTab(skim.pages.url("/plain.html"))
        skim.openTab(skim.pages.url("/article.html"))
        onMain { skim.tabs.select(last) }

        val scenario = skim.launch()

        assertEquals(listOf(BrowserKey), skim.keysOf(scenario))
        assertEquals(last, skim.tabs.state.value.currentTabId)
        assertEquals("no tab opened on the way", 2, skim.tabs.state.value.tabs.size)
    }

    @Test
    fun backOnWelcomeLeavesTheApp() {
        skim.setOnboarded(false)
        val scenario = skim.launch()
        AppScreen.awaitNode(skim.activityOf(scenario), getStarted)

        skim.pressBack()

        // Android 12 and later move a launcher task to the background; earlier versions finish it.
        waitUntil("Skim to leave") { scenario.state == Lifecycle.State.CREATED || scenario.state == Lifecycle.State.DESTROYED }
        if (scenario.state == Lifecycle.State.CREATED) assertEquals(listOf(WelcomeKey), skim.keysOf(scenario))
    }
}
