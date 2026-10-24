package com.dantech.skimbrowser.browser

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class FullscreenControllerTest {

    private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity = controller.get()
    private val fullscreen = FullscreenController(activity)
    private val decor get() = activity.window.decorView as ViewGroup

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun thePagesViewCoversTheWindow() {
        val video = View(activity)

        fullscreen.enter("tab-1", video, Callback())

        assertTrue(fullscreen.isFullscreen.value)
        val frame = video.parent as ViewGroup
        assertSame("on a frame right on the decor view", decor, frame.parent)
        assertSame("above everything else", frame, decor.getChildAt(decor.childCount - 1))
    }

    @Test
    fun backLeavesFullScreenAndTellsThePageOnce() {
        val callback = Callback()
        fullscreen.enter("tab-1", View(activity), callback)

        activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(fullscreen.isFullscreen.value)
        assertEquals(1, callback.hidden)
        assertFalse("Back is free again", activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun backReachesFullScreenBeforeTheScreensOwnHandler() {
        var screenBack = 0
        val screen = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                screenBack++
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, screen)
        val callback = Callback()
        fullscreen.enter("tab-1", View(activity), callback)

        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, callback.hidden)
        assertEquals(0, screenBack)

        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, screenBack)
    }

    @Test
    fun leavingRemovesTheViewFromTheWindow() {
        val video = View(activity)
        fullscreen.enter("tab-1", video, Callback())
        val frame = video.parent as ViewGroup

        fullscreen.exit()

        assertNull(frame.parent)
        assertNull(video.parent)
    }

    @Test
    fun onlyTheTabThatShowsFullScreenCanEndIt() {
        val callback = Callback()
        fullscreen.enter("tab-1", View(activity), callback)

        fullscreen.exit("tab-2")
        assertTrue(fullscreen.isFullscreen.value)

        fullscreen.exit("tab-1")
        assertFalse(fullscreen.isFullscreen.value)
        assertEquals(1, callback.hidden)
    }

    @Test
    fun aSecondRequestEndsTheFirst() {
        val first = Callback()
        val second = Callback()
        fullscreen.enter("tab-1", View(activity), first)

        fullscreen.enter("tab-2", View(activity), second)

        assertEquals(1, first.hidden)
        assertEquals(0, second.hidden)
        assertTrue(fullscreen.isFullscreen.value)
    }

    @Test
    fun aPageThatAsksToLeaveWhileLeavingIsToldOnce() {
        val callback = Callback { fullscreen.exit("tab-1") }
        fullscreen.enter("tab-1", View(activity), callback)

        fullscreen.exit()

        assertEquals(1, callback.hidden)
        assertFalse(fullscreen.isFullscreen.value)
    }

    @Test
    fun destroyingTheActivityEndsFullScreenForGood() {
        val shown = Callback()
        fullscreen.enter("tab-1", View(activity), shown)

        fullscreen.destroy()
        val late = Callback()
        fullscreen.enter("tab-1", View(activity), late)

        assertEquals(1, shown.hidden)
        assertEquals("refused at once", 1, late.hidden)
        assertFalse(fullscreen.isFullscreen.value)
    }

    @Test
    fun leavingWhenNothingShowsDoesNothing() {
        fullscreen.exit()
        fullscreen.exit("tab-1")

        assertFalse(fullscreen.isFullscreen.value)
    }

    private class Callback(private val onHidden: () -> Unit = {}) : WebChromeClient.CustomViewCallback {
        var hidden = 0

        override fun onCustomViewHidden() {
            hidden++
            onHidden()
        }
    }
}
