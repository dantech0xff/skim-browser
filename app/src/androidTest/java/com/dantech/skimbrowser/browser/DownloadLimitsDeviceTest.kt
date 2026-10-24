package com.dantech.skimbrowser.browser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.downloads.DownloadEvent
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a page may download and what the WebView hands to Skim for it, on the device's WebView.
 * A page starts one download of its own; a tap on the page lets one more through, a scroll none.
 * A `data:` link is saved byte for byte. A download the WebView requested itself reaches the
 * download listener at the address the server's redirects lead to, with the response's type: the
 * premise of which downloads may carry cookies. An older WebView hands a link with a `download`
 * attribute over unrequested, at the link's own address and with no type
 * ([WebViewSupport.readsDownloadLinksFirst]); Skim gives such a download no cookies.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadLimitsDeviceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val downloads = DeviceDownloads()

    @get:Rule(order = 2)
    val rig = BrowserRig()

    /** One call of the WebView's download listener. */
    private data class ListenerCall(val url: String, val userAgent: String?, val contentDisposition: String?, val mimeType: String?, val contentLength: Long)

    @Test
    fun aPageThatStartsThreeDownloadsOnItsOwnGetsOneAndTheOtherTwoFail() {
        rig.showCurrentTab()
        rig.newTab(rig.pages.url("/download.html?href=" + Uri.encode(DATA_URL) + "&auto=3"))

        val events = rig.awaitDownloadEvents(3)

        assertEquals("$events", 1, events.count { it is DownloadEvent.DownloadCompleted })
        assertEquals("$events", 2, events.count { it is DownloadEvent.DownloadFailed })
        assertTrue("and no more", holdsFor(SETTLE_MS) { rig.downloadEvents.size == 3 })
        assertArrayEquals(DATA_BYTES, downloads.read(downloads.savedFiles().single().uri))
    }

    @Test
    fun aTapLetsThePageStartOneMoreDownloadAndOnlyOne() {
        val (id, view) = showDownloadPage()
        assertEquals("the page's own download", listOf(COMPLETED), kinds(start(id, times = 1, ending = 1)))
        assertEquals("without a tap, no other", listOf(FAILED), kinds(start(id, times = 1, ending = 1)))

        Finger.tap(view, fractionY = REST_Y)
        assertEquals("a tap anywhere on the page allows one", listOf(COMPLETED, FAILED), kinds(start(id, times = 2, ending = 2)).sorted())

        val before = rig.downloadEvents.size
        Finger.tap(view, fractionY = LINK_Y)
        assertEquals("a tap on the link is that link's download", listOf(COMPLETED), kinds(eventsAfter(before, 1)))
        assertEquals("and allows nothing more", listOf(FAILED), kinds(start(id, times = 1, ending = 1)))

        assertTrue(holdsFor(SETTLE_MS) { rig.downloadEvents.size == 6 })
        assertEquals(3, downloads.savedFiles().size)
    }

    @Test
    fun aScrollLetsThePageStartNoDownload() {
        val (id, view) = showDownloadPage()
        assertEquals("the page's own download", listOf(COMPLETED), kinds(start(id, times = 1, ending = 1)))

        scrollThePage(id, view)
        assertEquals("a scroll is no tap", listOf(FAILED), kinds(start(id, times = 1, ending = 1)))

        Finger.tap(view, fractionY = REST_Y)
        assertEquals("while a tap would have allowed one", listOf(COMPLETED), kinds(start(id, times = 1, ending = 1)))
        assertEquals(2, downloads.savedFiles().size)
    }

    @Test
    fun aDataLinkIsSavedByteForByteNamedFromItsOwnTypeAndTheListenerGetsNoName() {
        val (id, view) = showDownloadPage("&name=" + Uri.encode("bytes.txt"))
        val heard = listenTo(id)

        Finger.tap(view, fractionY = LINK_Y)

        val completed = rig.awaitDownloadEvents(1).single() as DownloadEvent.DownloadCompleted
        val call = heard.single()
        assertEquals(DATA_URL, call.url)
        // Skim reads the type from the URL itself, as an older WebView reports none.
        val reported = if (WebViewSupport.readsDownloadLinksFirst) "text/plain" else ""
        assertEquals("the type the WebView reports for the link", reported, call.mimeType.orEmpty())
        assertTrue("no Content-Disposition, so the link's download name never reaches Skim: $call", call.contentDisposition.isNullOrEmpty())
        assertEquals("named from its type", "download.txt", completed.fileName)
        assertEquals("text/plain", completed.mime)
        assertArrayEquals(DATA_BYTES, downloads.read(completed.uri))
    }

    @Test
    fun aDownloadTheWebViewRequestedReachesTheListenerAtTheAddressTheServersRedirectsLeadTo() {
        val linked = rig.pages.url("/report.pdf?name=linked.pdf")
        val navigated = rig.pages.url("/report.pdf?name=navigated.pdf")
        val redirecting = rig.pages.url("/redirect?to=" + Uri.encode(linked))
        val (id, view) = showDownloadPage(href = redirecting)
        val heard = listenTo(id)

        // A download link whose address redirects, then a navigation of the page whose address redirects.
        Finger.tap(view, fractionY = LINK_Y)
        waitUntil("the link's download") { heard.size == 1 }
        rig.evaluate(id, "location.href = '/redirect?to=' + encodeURIComponent('$navigated')")
        waitUntil("the navigation's download") { heard.size == 2 }

        val (link, navigation) = heard
        assertEquals("a navigation's download: the address after the redirects", navigated, navigation.url)
        assertEquals("with the final response's Content-Disposition", "attachment; filename=\"navigated.pdf\"", navigation.contentDisposition)
        assertEquals("and its type", "application/pdf", navigation.mimeType)
        if (WebViewSupport.readsDownloadLinksFirst) {
            assertEquals("a link's download: the address after the redirects", linked, link.url)
            assertEquals("with the final response's Content-Disposition", "attachment; filename=\"linked.pdf\"", link.contentDisposition)
            assertEquals("and its type", "application/pdf", link.mimeType)
        } else {
            assertEquals("a link handed over unrequested: its own address", redirecting, link.url)
            assertEquals("with no type", "", link.mimeType.orEmpty())
            assertEquals("and no length", UNKNOWN_LENGTH, link.contentLength)
            assertTrue(link.contentDisposition.isNullOrEmpty())
        }
        listOf(link, navigation).forEach { call -> downloads.awaitSystemDownload(call.url) }
        assertEquals("DownloadManager was given the addresses the listener got", setOf(link.url, navigation.url), downloads.systemDownloads().map { it.url }.toSet())
        // The WebView followed both redirects, or (a link handed over unrequested) DownloadManager followed the link's.
        assertEquals(2, rig.pages.requests("/redirect").size)
    }

    /** Shows the download page in a new current tab, its link to [href] and [query] added; returns the tab and its WebView. */
    private fun showDownloadPage(query: String = "", href: String = DATA_URL): Pair<String, SkimWebView> {
        rig.showCurrentTab()
        val url = rig.pages.url("/download.html?href=" + Uri.encode(href) + query)
        val id = rig.newTab(url)
        rig.awaitLoaded(id, url)
        return id to rig.shownWebView()
    }

    /** Clicks the page's link [times] times from script, with no tap, and returns the [ending] downloads that end from it. */
    private fun start(tabId: String, times: Int, ending: Int): List<DownloadEvent> {
        val before = rig.downloadEvents.size
        rig.evaluate(tabId, "skimClick($times)")
        return eventsAfter(before, ending)
    }

    /**
     * Drags up the page until it scrolled. Now and then WebView 66 takes no part of a drag on a busy
     * device: the page gets no touch event at all and does not move. That drag was no tap either, so
     * nothing about the download limit hangs on it, and the page is dragged again.
     */
    private fun scrollThePage(tabId: String, view: SkimWebView) {
        repeat(MAX_DRAGS) {
            Finger.drag(view, fromY = DRAG_FROM, toY = DRAG_TO)
            if (!holdsFor(SCROLL_WATCH_MS) { scrollY(tabId) == 0.0 }) return
        }
        throw AssertionError("The page did not scroll after $MAX_DRAGS drags")
    }

    private fun scrollY(tabId: String): Double = (jsValue(rig.evaluate(tabId, "window.scrollY")) as? Number)?.toDouble() ?: 0.0

    /** The [count] download ends that follow the first [before]. */
    private fun eventsAfter(before: Int, count: Int): List<DownloadEvent> = rig.awaitDownloadEvents(before + count).subList(before, before + count)

    private fun kinds(events: List<DownloadEvent>): List<String> = events.map { if (it is DownloadEvent.DownloadCompleted) COMPLETED else FAILED }

    /** Records what the WebView's download listener gets, handing each call on to the router exactly as the pool's listener does. */
    private fun listenTo(tabId: String): List<ListenerCall> {
        val heard = CopyOnWriteArrayList<ListenerCall>()
        val view = rig.webViewOf(tabId)
        onMain {
            view.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                heard += ListenerCall(url, userAgent, contentDisposition, mimeType, contentLength)
                rig.host.downloads.start(tabId, url, userAgent, contentDisposition, mimeType, view)
            }
        }
        return heard
    }

    private companion object {
        /** Inside the page's download link, the band over its top 45%; the rest of the page is below it. */
        const val LINK_Y = 0.2f
        const val REST_Y = 0.75f

        /** A drag up the page, which scrolls it down. */
        const val DRAG_FROM = 0.85f
        const val DRAG_TO = 0.3f
        const val SETTLE_MS = 1_000L

        /** How many drags a page gets to scroll, and how long after each one it is watched for moving. */
        const val MAX_DRAGS = 3
        const val SCROLL_WATCH_MS = 2_000L
        const val COMPLETED = "completed"
        const val FAILED = "failed"

        /** The length a download listener gets when the WebView does not know one. */
        const val UNKNOWN_LENGTH = -1L

        /** Every byte value once, so a byte changed on the way shows. */
        val DATA_BYTES = ByteArray(256) { it.toByte() }
        val DATA_URL = "data:text/plain;base64," + Base64.encodeToString(DATA_BYTES, Base64.NO_WRAP)
    }
}
