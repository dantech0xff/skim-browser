package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.tab.HitTarget

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.webkit.WebView.HitTestResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowWebView

@RunWith(AndroidJUnit4::class)
class SkimWebViewTest {

    private val view = SkimWebView(ApplicationProvider.getApplicationContext<Context>())
    private val hits = mutableListOf<HitTarget>()

    @Test
    fun scrollPaddingIsDocumentSpaceThatScrollsAway() {
        val script = SkimWebView.contentScrollPaddingScript(48, 160)

        assertTrue(script.contains("padding-top:48px"))
        assertTrue(script.contains("padding-bottom:160px"))
        assertFalse(script.contains("position:fixed"))

        view.setContentScrollPadding(48, 160)
        assertEquals(48, view.contentPadTop)
        assertEquals(160, view.contentPadBottom)
        view.setContentScrollPadding(48, 160)
    }

    @Test
    fun itTakesPartInNestedScrolling() {
        assertTrue("pull to refresh only hears views that scroll nested", view.isNestedScrollingEnabled)
    }

    @Test
    fun aTapCountsAsAGestureForAWhile() {
        assertFalse("never touched", view.consumeGesture(withinMs = 5_000))

        tap()

        ShadowSystemClock.advanceBy(Duration.ofMillis(5_000))
        assertTrue("up to the limit", view.consumeGesture(withinMs = 5_000))
        tap()
        ShadowSystemClock.advanceBy(Duration.ofMillis(5_001))
        assertFalse(view.consumeGesture(withinMs = 5_000))
    }

    @Test
    fun aTapAllowsOneActionOnly() {
        tap()

        assertTrue(view.consumeGesture(withinMs = 5_000))
        assertFalse("used up", view.consumeGesture(withinMs = 5_000))
    }

    @Test
    fun aScrollIsNoTap() {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop

        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, y = 400f))
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, y = 400f - slop - 1))
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, y = 400f))
        view.onTouchEvent(event(MotionEvent.ACTION_UP, y = 400f))
        assertFalse("a finger that moved away and back scrolled", view.consumeGesture(withinMs = 5_000))

        // A fast swipe may report no move before its finger lifts.
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, y = 400f))
        view.onTouchEvent(event(MotionEvent.ACTION_UP, y = 400f - slop - 1))
        assertFalse(view.consumeGesture(withinMs = 5_000))

        // A finger that only went down, without lifting, is no tap either.
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN))
        assertFalse(view.consumeGesture(withinMs = 5_000))
    }

    @Test
    fun aPinchIsNoTap() {
        val secondFinger = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT

        view.onTouchEvent(event(MotionEvent.ACTION_DOWN))
        view.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN or secondFinger))
        view.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP or secondFinger))
        view.onTouchEvent(event(MotionEvent.ACTION_UP))

        assertFalse(view.consumeGesture(withinMs = 5_000))
    }

    @Test
    fun aTouchThatEndsWithoutAnUpIsNoTap() {
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN))
        view.onTouchEvent(event(MotionEvent.ACTION_CANCEL))
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE))
        view.onTouchEvent(event(MotionEvent.ACTION_UP))

        assertFalse(view.consumeGesture(withinMs = 5_000))
    }

    @Test
    fun anIncognitoPageKeepsTheKeyboardFromLearningAndTheAutofillServiceAway() {
        val incognito = SkimWebView(ApplicationProvider.getApplicationContext<Context>(), incognito = true)
        val normalInput = EditorInfo()
        val incognitoInput = EditorInfo()

        view.onCreateInputConnection(normalInput)
        incognito.onCreateInputConnection(incognitoInput)

        assertTrue(incognitoInput.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        assertEquals(0, normalInput.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, incognito.importantForAutofill)
        assertNotEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, view.importantForAutofill)
    }

    @Test
    fun aLongPressOnAnImageIsReportedAtOnce() {
        view.onHitTarget = { hits += it }
        shadowOf(view).setHitTestResult(ShadowWebView.createHitTestResult(HitTestResult.IMAGE_TYPE, IMAGE))

        assertTrue(longPress())

        assertEquals(listOf<HitTarget>(HitTarget.Image(src = IMAGE, alt = null, linkUrl = null)), hits)
    }

    @Test
    fun aLongPressOnALinkIsTakenWhileTheWebViewLooksUpItsText() {
        view.onHitTarget = { hits += it }

        listOf(HitTestResult.SRC_ANCHOR_TYPE, HitTestResult.SRC_IMAGE_ANCHOR_TYPE).forEach { type ->
            shadowOf(view).setHitTestResult(ShadowWebView.createHitTestResult(type, LINK))
            assertTrue("type $type", longPress())
        }
    }

    @Test
    fun anyOtherLongPressIsLeftToTheWebView() {
        view.onHitTarget = { hits += it }

        listOf(HitTestResult.UNKNOWN_TYPE, HitTestResult.EDIT_TEXT_TYPE, HitTestResult.PHONE_TYPE).forEach { type ->
            shadowOf(view).setHitTestResult(ShadowWebView.createHitTestResult(type, "tel:+6561234567"))
            assertFalse("type $type", longPress())
        }
        assertTrue(hits.isEmpty())
    }

    @Test
    fun withoutAListenerTheWebViewKeepsTheLongPress() {
        shadowOf(view).setHitTestResult(ShadowWebView.createHitTestResult(HitTestResult.IMAGE_TYPE, IMAGE))

        assertFalse(longPress())
    }

    /** The long-click listener the view set on itself, as a long-press calls it. */
    private fun longPress(): Boolean = shadowOf(view).onLongClickListener.onLongClick(view)

    private fun event(action: Int, x: Float = 10f, y: Float = 10f): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, x, y, 0)
    }

    private fun tap() {
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN))
        view.onTouchEvent(event(MotionEvent.ACTION_UP))
    }

    private companion object {
        const val LINK = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val IMAGE = "https://daylightjournal.com/img/pump.jpg"
    }
}
