package com.dantech.skimbrowser.browser

import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class WindowSecureControllerTest {

    private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity = controller.get()
    private val recents = mutableListOf<Boolean>()
    private val secure = WindowSecureController(activity.window) { enabled -> recents += enabled }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun theFlagIsSetAtOnce() {
        secure.setSecure("incognito-tab", true)

        assertTrue(flagSet())
        assertTrue(secure.isSecure)
    }

    @Test
    fun theFlagClearsOnlyOnceTheCurrentFrameIsDone() {
        secure.setSecure("incognito-tab", true)

        secure.setSecure("incognito-tab", false)

        assertTrue("still set while the frame that showed the tab is on screen", flagSet())
        idle()
        assertFalse(flagSet())
    }

    @Test
    fun theFlagStaysWhileAnyOwnerHoldsIt() {
        secure.setSecure("incognito-tab", true)
        secure.setSecure("incognito-tabs-screen", true)

        secure.setSecure("incognito-tab", false)
        idle()
        assertTrue(flagSet())

        secure.setSecure("incognito-tabs-screen", false)
        idle()
        assertFalse(flagSet())
    }

    @Test
    fun anOwnerTakingTheFlagAgainBeforeTheFrameEndsKeepsIt() {
        secure.setSecure("incognito-tab", true)
        secure.setSecure("incognito-tab", false)

        secure.setSecure("incognito-tabs-screen", true)
        idle()

        assertTrue(flagSet())
    }

    @Test
    fun anOwnerThatNeverHeldTheFlagCannotClearIt() {
        secure.setSecure("incognito-tab", true)

        secure.setSecure("someone-else", false)
        idle()

        assertTrue(flagSet())
    }

    @Test
    fun recentsHideAtOnceAndShowAgainAfterTheFrame() {
        secure.setRecentsHidden("incognito-tabs", true)
        assertEquals(listOf(false), recents)

        secure.setRecentsHidden("incognito-tabs", false)
        assertEquals("not before the frame is done", listOf(false), recents)
        idle()
        assertEquals(listOf(false, true), recents)
    }

    @Test
    fun recentsStayHiddenWhileAnyOwnerHoldsThem() {
        secure.setRecentsHidden("incognito-tabs", true)
        secure.setRecentsHidden("incognito-screen", true)

        secure.setRecentsHidden("incognito-tabs", false)
        idle()
        assertEquals("one call for both owners", listOf(false), recents)

        secure.setRecentsHidden("incognito-screen", false)
        idle()
        assertEquals(listOf(false, true), recents)
    }

    @Test
    fun recentsAndTheFlagAreIndependent() {
        secure.setRecentsHidden("incognito-tabs", true)
        assertFalse(flagSet())

        secure.setSecure("incognito-tab", true)
        secure.setSecure("incognito-tab", false)
        idle()

        assertFalse(flagSet())
        assertEquals("recents stay hidden", listOf(false), recents)
    }

    @Test
    fun beforeApi33RecentsCannotBeHiddenAlone() {
        val older = WindowSecureController(activity.window, setRecentsScreenshotEnabled = null)

        older.setRecentsHidden("incognito-tabs", true)
        older.setRecentsHidden("incognito-tabs", false)
        idle()

        assertFalse("nothing else changes", flagSet())
    }

    private fun flagSet(): Boolean = (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
