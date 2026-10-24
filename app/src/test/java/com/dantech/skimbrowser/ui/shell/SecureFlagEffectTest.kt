package com.dantech.skimbrowser.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabsState
import com.dantech.skimbrowser.browser.WindowSecureController
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.ReaderKey
import com.dantech.skimbrowser.ui.navigation.TabsKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FLAG_SECURE and the Recents preview on a real window, driven by [SecureFlagEffect] and by the
 * entries of a real NavDisplay: on before incognito content shows, off only once the last frame of
 * it is gone, including while it animates away.
 */
@RunWith(AndroidJUnit4::class)
class SecureFlagEffectTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val recents = mutableListOf<Boolean>()
    private lateinit var secure: WindowSecureController

    private val normal = tab("normal")
    private val incognito = tab("incognito", incognito = true)

    @Before
    fun setUp() {
        secure = WindowSecureController(composeRule.activity.window) { enabled -> recents += enabled }
    }

    @Test
    fun theFlagFollowsWhetherIncognitoContentIsShown() {
        var target by mutableStateOf(false)
        composeRule.setContent { SecureFlagEffect(target, hasIncognitoTabs = false, armed = 0, secure = secure) }
        assertFalse(secure.isSecure)

        target = true
        composeRule.waitForIdle()
        assertTrue(secure.isSecure)

        target = false
        composeRule.waitForIdle()
        assertFalse(secure.isSecure)
    }

    @Test
    fun aChangeTheNavigatorSecuredAheadThatShowsNothingIncognitoLetsTheFlagGo() {
        var armed by mutableIntStateOf(0)
        composeRule.setContent { SecureFlagEffect(target = false, hasIncognitoTabs = false, armed = armed, secure = secure) }

        composeRule.runOnIdle {
            // What the navigator does right before a change, which here ends on normal content.
            secure.setSecure(INCOGNITO_SURFACE_OWNER, true)
            armed++
        }
        composeRule.waitForIdle()

        assertFalse(secure.isSecure)
    }

    @Test
    fun theRecentsPreviewStaysHiddenWhileAnIncognitoTabIsOpen() {
        var hasIncognitoTabs by mutableStateOf(true)
        composeRule.setContent { SecureFlagEffect(target = false, hasIncognitoTabs = hasIncognitoTabs, armed = 0, secure = secure) }
        assertEquals("hidden", listOf(false), recents)

        hasIncognitoTabs = false
        composeRule.waitForIdle()

        assertEquals("shown again", listOf(false, true), recents)
    }

    @Test
    fun leavingTheScreenLetsBothGo() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            if (shown) SecureFlagEffect(target = true, hasIncognitoTabs = true, armed = 0, secure = secure)
        }
        assertTrue(secure.isSecure)

        shown = false
        composeRule.waitForIdle()

        assertFalse(secure.isSecure)
        assertEquals(listOf(false, true), recents)
    }

    @Test
    fun incognitoTabsFadingOutKeepTheFlagUntilTheyAreGone() {
        val backStack = NavBackStack<NavKey>(BrowserKey, TabsKey(incognito = true))
        showNavDisplay(backStack, TabsState(listOf(normal, incognito), currentTabId = normal.id))
        assertTrue("Incognito tabs show", secure.isSecure)

        composeRule.mainClock.autoAdvance = false
        backStack[backStack.lastIndex] = TabsKey(incognito = false)
        fadeHalfway()
        assertShown(TabsKey(incognito = true), TabsKey(incognito = false))
        assertTrue("still on while they fade out", secure.isSecure)

        fadeToTheEnd()
        assertNotShown(TabsKey(incognito = true))
        assertFalse("off once they are gone", secure.isSecure)
    }

    @Test
    fun aReaderLeavingWithItsTabClosedKeepsTheFlagWhileItFadesOut() {
        val backStack = NavBackStack<NavKey>(BrowserKey, ReaderKey(incognito.id))
        var tabs by mutableStateOf(TabsState(listOf(normal, incognito), currentTabId = normal.id))
        showNavDisplay(backStack) { tabs }
        assertTrue(secure.isSecure)

        composeRule.mainClock.autoAdvance = false
        tabs = TabsState(listOf(normal), currentTabId = normal.id)
        backStack.removeAt(backStack.lastIndex)
        fadeHalfway()
        assertShown(ReaderKey(incognito.id), BrowserKey)
        assertTrue("the reader still shows the tab's page", secure.isSecure)

        fadeToTheEnd()
        assertNotShown(ReaderKey(incognito.id))
        assertFalse(secure.isSecure)
    }

    @Test
    fun aReaderThatStaysWhileItsTabClosesKeepsTheFlagUntilItLeaves() {
        val backStack = NavBackStack<NavKey>(BrowserKey, ReaderKey(incognito.id))
        var tabs by mutableStateOf(TabsState(listOf(normal, incognito), currentTabId = normal.id))
        showNavDisplay(backStack) { tabs }
        assertTrue(secure.isSecure)

        // A page closed its own pop-up while the reader showed it: the reader's key is still in the back stack.
        tabs = TabsState(listOf(normal), currentTabId = normal.id)
        composeRule.waitForIdle()
        assertTrue("what the reader shows may still be the tab's page", secure.isSecure)

        composeRule.mainClock.autoAdvance = false
        backStack.removeAt(backStack.lastIndex)
        fadeHalfway()
        assertTrue("still on while it fades out", secure.isSecure)

        fadeToTheEnd()
        assertNotShown(ReaderKey(incognito.id))
        assertFalse(secure.isSecure)
    }

    @Test
    fun theBrowserLetsTheFlagGoOnceItShowsANormalTab() {
        val backStack = NavBackStack<NavKey>(BrowserKey)
        var tabs by mutableStateOf(TabsState(listOf(normal, incognito), currentTabId = incognito.id))
        showNavDisplay(backStack) { tabs }
        assertTrue(secure.isSecure)

        tabs = tabs.copy(currentTabId = normal.id)
        composeRule.waitForIdle()

        assertFalse(secure.isSecure)
    }

    @Test
    fun aScreenShowingNothingIncognitoNeverTurnsTheFlagOn() {
        showNavDisplay(NavBackStack(BrowserKey, TabsKey(incognito = false)), TabsState(listOf(normal, incognito), currentTabId = incognito.id))

        assertFalse("the incognito tab is under the tabs screen", secure.isSecure)
    }

    private fun showNavDisplay(backStack: NavBackStack<NavKey>, tabs: TabsState) = showNavDisplay(backStack) { tabs }

    /** A NavDisplay with plain screens tagged with their keys, its entries holding the flag as the app's do. */
    private fun showNavDisplay(backStack: NavBackStack<NavKey>, tabs: () -> TabsState) {
        composeRule.setContent {
            val entries = remember {
                entryProvider<NavKey>(fallback = { key -> NavEntry(key) { Box(Modifier.fillMaxSize().testTag(key.toString())) } }) {}
            }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeAt(backStack.lastIndex) },
                entryDecorators = listOf(rememberSecureEntryDecorator(backStack.toList(), { entries(it).contentKey }, tabs(), secure)),
                entryProvider = entries,
            )
        }
        composeRule.waitForIdle()
    }

    /** Lets the screen change start (its first frame composes both screens), then runs half the fade. */
    private fun fadeHalfway() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(FADE_MIDWAY_MS)
        composeRule.waitForIdle()
    }

    private fun fadeToTheEnd() {
        composeRule.mainClock.advanceTimeBy(FADE_DONE_MS)
        composeRule.waitForIdle()
    }

    private fun assertShown(vararg keys: NavKey) {
        keys.forEach { composeRule.onNodeWithTag(it.toString()).assertExists() }
    }

    private fun assertNotShown(key: NavKey) {
        composeRule.onNodeWithTag(key.toString()).assertDoesNotExist()
    }

    private fun tab(id: String, incognito: Boolean = false) = Tab(
        id = id,
        incognito = incognito,
        url = "https://example.com/$id",
        title = id,
        groupId = null,
        position = 0,
        createdAt = 1L,
        lastAccessedAt = 1L,
    )

    private companion object {
        /** NavDisplay cross-fades screens over 700 ms. */
        const val FADE_MIDWAY_MS = 300L
        const val FADE_DONE_MS = 1_000L
    }
}
