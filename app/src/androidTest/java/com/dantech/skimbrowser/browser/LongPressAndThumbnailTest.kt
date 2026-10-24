package com.dantech.skimbrowser.browser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import com.dantech.skimbrowser.domain.tab.HitTarget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a tab's WebView reports about its page on the device: the link or image a long press hit
 * (with the link's address and text from `requestFocusNodeHref`), and the thumbnail drawn from it,
 * kept on disk for a normal tab and only in memory for an incognito one.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LongPressAndThumbnailTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rig = BrowserRig()

    @Test
    fun aLongPressOnALinkAnImageOrAnImageInALinkReportsWhatItHit() {
        val url = rig.pages.url("/longpress.html")
        val id = rig.newTab(url)
        rig.showCurrentTab()
        rig.awaitLoaded(id, url)
        waitUntil("both images to load") { jsValue(rig.evaluate(id, "Array.prototype.every.call(document.images, function (i) { return i.complete && i.naturalWidth > 0; })")) == true }
        val view = shownWebView()

        assertEquals(HitTarget.Link(url = rig.pages.url("/guide.html"), text = "Read the guide"), longPress(id, view, LINK_BAND))
        assertEquals(HitTarget.Image(src = rig.pages.url("/image.png?plain"), alt = null, linkUrl = null), longPress(id, view, IMAGE_BAND))
        assertEquals(
            HitTarget.Image(src = rig.pages.url("/image.png?linked"), alt = null, linkUrl = rig.pages.url("/gallery.html")),
            longPress(id, view, LINKED_IMAGE_BAND),
        )

        assertTrue("a long press opens nothing", holdsFor(SETTLE_MS) { rig.runtime(id).committedUrl == url })
        assertTrue(rig.pages.requests("/guide.html").isEmpty() && rig.pages.requests("/gallery.html").isEmpty())
    }

    @Test
    fun aShownPageGetsAThumbnailDrawnFromItsWebViewKeptOnDiskOnlyForANormalTab() {
        val url = rig.pages.url("/article.html")
        val normal = rig.newTab(url)
        rig.showCurrentTab()
        rig.awaitLoaded(normal, url)
        val normalThumbnail = awaitThumbnail(normal)
        val view = shownWebView()

        val (width, height) = checkNotNull(onMain { thumbnailSize(view.width, view.height) })
        assertEquals(width, normalThumbnail.width)
        assertEquals(height, normalThumbnail.height)
        assertTrue("the page's text shows in it", darkPixelShare(normalThumbnail) > MIN_TEXT_SHARE)
        runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { rig.access.thumbnails().awaitIdle() } }
        assertTrue("saved for after process death", File(thumbnailDir(), "$normal.webp").isFile)

        // A normal tab's thumbnail is checked on every WebView; an incognito tab needs profiles.
        WebViewSupport.assumeProfiles()
        val incognito = rig.newTab(url, incognito = true)
        rig.awaitLoaded(incognito, url)
        val incognitoThumbnail = awaitThumbnail(incognito)
        assertTrue(darkPixelShare(incognitoThumbnail) > MIN_TEXT_SHARE)
        runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { rig.access.thumbnails().awaitIdle() } }
        assertFalse("never on disk", File(thumbnailDir(), "$incognito.webp").exists())
        assertEquals(listOf("$normal.webp"), thumbnailDir().list().orEmpty().toList())
    }

    /** Long-presses [view] at the vertical middle of [band] and returns the target [tabId] reported. */
    private fun longPress(tabId: String, view: SkimWebView, band: Float): HitTarget {
        val before = rig.hits.size
        assertTrue("the long press at $band was noticed", Finger.longPress(view, 0.5f, band) { rig.hits.size > before })
        val (reportedTab, target) = rig.hits.last()
        assertEquals(tabId, reportedTab)
        rig.evaluate(tabId, "window.getSelection().removeAllRanges()")
        return target
    }

    private fun awaitThumbnail(tabId: String): Bitmap =
        checkNotNull(awaitValue(rig.access.thumbnails().observe(tabId), "the thumbnail of $tabId") { it != null }).asAndroidBitmap()

    private fun thumbnailDir() = File(rig.storageDir, "thumbs")

    private fun shownWebView(): SkimWebView {
        waitUntil("one WebView on screen") { rig.webViewsInWindow().size == 1 }
        return rig.webViewsInWindow().single()
    }

    /** The share of [bitmap]'s pixels dark enough to be text. */
    private fun darkPixelShare(bitmap: Bitmap): Double {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val dark = pixels.count { pixel -> Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) < DARK_SUM }
        return dark.toDouble() / pixels.size
    }

    private companion object {
        /** The middle of each band of longpress.html, as a share of the viewport's height. */
        const val LINK_BAND = 0.15f
        const val IMAGE_BAND = 0.48f
        const val LINKED_IMAGE_BAND = 0.81f
        const val SETTLE_MS = 1_000L

        /** A pixel whose channels add up to less than this is dark. */
        const val DARK_SUM = 3 * 128

        /** Black text on white covers well over 1% of a page's top. */
        const val MIN_TEXT_SHARE = 0.01
    }
}
