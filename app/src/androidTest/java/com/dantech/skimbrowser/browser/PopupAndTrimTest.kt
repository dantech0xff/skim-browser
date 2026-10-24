package com.dantech.skimbrowser.browser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.content.ComponentCallbacks2
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Windows that pages open (`window.open`, sign-in and payment popups) on the device's WebView:
 * only after a tap, as a foreground tab that stays linked to its opener, kept alive with it when the
 * app goes to the background, and closed back to the opener by `window.close()`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PopupAndTrimTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rig = BrowserRig()

    @Test
    fun aWindowOpenedAfterATapIsAForegroundTabThatClosesBackToItsOpener() {
        val (opener, openerView) = showOpener()

        val popup = tapToOpenPopup(opener, openerView)

        assertEquals("the popup is the current tab", popup.id, rig.tabs.state.value.currentTabId)
        assertEquals(true, jsValue(rig.evaluate(popup.id, "window.skimHasOpener")))
        val popupView = rig.shownWebView(other = openerView)
        val popupGeneration = onMain { rig.pool.generation(popup.id) }
        val before = popupGeneration.value

        Finger.tap(popupView)

        awaitValue(rig.tabs.state, "the popup to close") { it.tab(popup.id) == null }
        assertEquals("back on the opener", opener, rig.tabs.state.value.currentTabId)
        assertFalse("closed for good, without Undo", popup.id in onMain { rig.tabs.pendingCloseIds() })
        assertNotEquals("its WebView is gone", before, popupGeneration.value)
        waitUntil("the opener's own WebView back on screen") { rig.webViewsInWindow().singleOrNull() === openerView }
        assertTrue(rig.runtime(opener).pageLoaded)
    }

    @Test
    fun goingToTheBackgroundKeepsAPopupAndItsOpenerAlive() {
        val (opener, openerView) = showOpener()
        val popup = tapToOpenPopup(opener, openerView)
        val openerGeneration = onMain { rig.pool.generation(opener) }
        val popupGeneration = onMain { rig.pool.generation(popup.id) }
        val before = openerGeneration.value to popupGeneration.value

        onMain {
            rig.pool.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            rig.pool.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        }

        assertEquals("neither WebView was replaced", before, openerGeneration.value to popupGeneration.value)
        assertTrue(rig.runtime(opener).pageLoaded && rig.runtime(popup.id).pageLoaded)
        assertEquals("the popup still talks to its opener", true, jsValue(rig.evaluate(popup.id, "window.opener !== null")))
        assertNotNull("their state was saved", runBlocking { rig.graph.tabStateStore().load(opener, incognito = false) })
    }

    @Test
    fun aPageCannotOpenAWindowWithoutATap() {
        val (opener, _) = showOpener()

        val opened = rig.evaluate(opener, "String(window.open('/popup.html?unasked', 'skim-unasked'))")

        assertEquals("the page gets no window", "null", jsValue(opened))
        assertTrue("and no tab opens", holdsFor(NO_WINDOW_MS) { rig.tabs.state.value.tabs.size == 1 })
        assertTrue(rig.pages.requests("/popup.html").isEmpty())
    }

    /** Opens the opener page in the current tab and returns the tab and its WebView on screen. */
    private fun showOpener(): Pair<String, SkimWebView> {
        val url = rig.pages.url("/opener.html")
        val opener = rig.newTab(url)
        rig.showCurrentTab()
        rig.awaitLoaded(opener, url)
        return opener to rig.shownWebView()
    }

    /** Taps the opener page's button and returns the popup tab once its page loaded. */
    private fun tapToOpenPopup(opener: String, openerView: SkimWebView): Tab {
        Finger.tap(openerView)
        val popup = awaitValue(rig.tabs.state, "a popup tab") { state -> state.tabs.any { it.openerTabId == opener } }
            .tabs.single { it.openerTabId == opener }
        rig.awaitLoaded(popup.id, rig.pages.url("/popup.html"))
        return popup
    }

    private companion object {
        const val NO_WINDOW_MS = 1_500L
    }
}
