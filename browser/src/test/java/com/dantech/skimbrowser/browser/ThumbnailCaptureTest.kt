package com.dantech.skimbrowser.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailCaptureTest {

    private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity = controller.get()

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun theThumbnailIsTheTopOfThePageAtThePreviewsShape() {
        // A 412 dp wide phone page at 2.625 px per dp, scaled to 0.35 and cut at 168 × 92.
        assertEquals(378 to 207, thumbnailSize(1080, 2200))
    }

    @Test
    fun aShortViewIsTakenWhole() {
        assertEquals(378 to 105, thumbnailSize(1080, 300))
    }

    @Test
    fun aViewWithoutASizeHasNoThumbnail() {
        assertNull(thumbnailSize(0, 2200))
        assertNull(thumbnailSize(1080, 0))
        assertEquals("never an empty bitmap", 1 to 1, thumbnailSize(1, 1))
    }

    @Test
    fun aViewOutsideAWindowOrNotLaidOutIsSkipped() {
        val page = StripedPage(activity)
        assertNull("not in a window", captureThumbnail(page))

        val unlaid = StripedPage(activity)
        (activity.window.decorView as ViewGroup).addView(unlaid)
        assertNull("not laid out yet", captureThumbnail(unlaid))
    }

    @Test
    fun theCaptureShowsWhatIsOnScreen() {
        val page = attached()

        val top = captureThumbnail(page)
        assertNotNull(top)
        assertEquals(378, top!!.width)
        assertEquals(207, top.height)
        assertEquals(Color.RED, top.getPixel(top.width / 2, top.height / 2))

        page.scrollTo(0, StripedPage.STRIPE)
        val scrolled = captureThumbnail(page)!!
        assertEquals("the scrolled part, not the top of the document", Color.BLUE, scrolled.getPixel(scrolled.width / 2, 2))
    }

    private fun attached(): StripedPage {
        val page = StripedPage(activity)
        (activity.window.decorView as ViewGroup).addView(page, ViewGroup.LayoutParams(1080, 2200))
        page.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY))
        page.layout(0, 0, 1080, 2200)
        return page
    }

    /** A page whose first [STRIPE] pixels are red and the rest blue, drawn at its own coordinates. */
    private class StripedPage(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.BLUE)
            canvas.clipRect(0, 0, width, STRIPE)
            canvas.drawColor(Color.RED)
        }

        companion object {
            const val STRIPE = 1_000
        }
    }
}
