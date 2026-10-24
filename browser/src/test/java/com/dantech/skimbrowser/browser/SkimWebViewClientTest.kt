package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.tab.LoadError

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.TestRenderProcessGoneDetail
import android.webkit.TestWebResourceError
import android.webkit.TestWebResourceRequest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.RecordingEvents.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SkimWebViewClientTest {

    private val view = WebView(ApplicationProvider.getApplicationContext<Context>())
    private val events = RecordingEvents()
    private val host = FakeActivityHost()
    private val loads = mutableListOf<Pair<String, String>>()
    private val gone = mutableListOf<Triple<WebView, RendererGoneAction, Boolean>>()
    private val client = SkimWebViewClient(
        tabId = TAB,
        incognito = false,
        events = events,
        activityHost = host,
        loadInTab = { tabId, url -> loads += tabId to url },
        onRendererGone = { webView, action, didCrash -> gone += Triple(webView, action, didCrash) },
    )

    @Test
    fun theMainFramesMilestonesReachTheTabInTheirOrder() {
        shadowOf(view).pushEntryToHistory("https://daylightjournal.com/")
        shadowOf(view).pushEntryToHistory(PAGE)

        client.onPageStarted(view, PAGE, null)
        client.doUpdateVisitedHistory(view, PAGE, false)
        client.onPageFinished(view, PAGE)

        assertEquals(
            listOf(
                Event.PageStarted(TAB, PAGE),
                Event.VisitedHistory(TAB, PAGE),
                Event.NavigationState(TAB, canGoBack = true, canGoForward = false),
                Event.PageFinished(TAB, PAGE),
                Event.NavigationState(TAB, canGoBack = true, canGoForward = false),
            ),
            events.events,
        )
    }

    @Test
    fun aMainFrameErrorReachesTheTabWithItsCodeDescriptionAndUrl() {
        client.onReceivedError(
            view,
            request(PAGE, mainFrame = true),
            TestWebResourceError(WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED"),
        )

        assertEquals(
            listOf(Event.LoadError(TAB, WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED", PAGE)),
            events.events,
        )
    }

    @Test
    fun aFailedImageOrFrameIsNoPageError() {
        client.onReceivedError(
            view,
            request("https://cdn.example/pump.jpg", mainFrame = false),
            TestWebResourceError(WebViewClient.ERROR_CONNECT, "net::ERR_CONNECTION_REFUSED"),
        )

        assertTrue(events.events.isEmpty())
    }

    @Test
    fun anErrorWithoutADescriptionStillReachesTheTab() {
        client.onReceivedError(view, request(PAGE, mainFrame = true), TestWebResourceError(WebViewClient.ERROR_TIMEOUT, null))

        assertEquals(listOf(Event.LoadError(TAB, WebViewClient.ERROR_TIMEOUT, null, PAGE)), events.events)
    }

    @Test
    fun webPagesLoadInTheWebView() {
        assertFalse(client.shouldOverrideUrlLoading(view, request("https://example.com/next", mainFrame = true, gesture = false)))
        assertFalse(client.shouldOverrideUrlLoading(view, request("about:blank", mainFrame = true, gesture = false)))
        assertTrue(host.started.isEmpty())
    }

    @Test
    fun aFrameInsideThePageNavigatesOnItsOwn() {
        assertFalse(client.shouldOverrideUrlLoading(view, request("tel:+6561234567", mainFrame = false, gesture = true)))
        assertFalse(client.shouldOverrideUrlLoading(view, request("intent://scan/#Intent;scheme=zxing;end", mainFrame = false)))
        assertTrue(host.started.isEmpty())
    }

    @Test
    fun fileContentAndJavascriptUrlsAreBlocked() {
        listOf("file:///sdcard/secret.txt", "content://media/external/1", "javascript:alert(1)").forEach { url ->
            assertTrue(url, client.shouldOverrideUrlLoading(view, request(url, mainFrame = true, gesture = true)))
        }
        assertTrue(host.started.isEmpty())
    }

    @Test
    fun anotherAppOpensOnlyAfterAGesture() {
        assertTrue(client.shouldOverrideUrlLoading(view, request("tel:+6561234567", mainFrame = true, gesture = false)))
        assertTrue("blocked without a gesture", host.started.isEmpty())

        assertTrue(client.shouldOverrideUrlLoading(view, request("tel:+6561234567", mainFrame = true, gesture = true)))

        val intent = host.started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("tel:+6561234567"), intent.data)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(loads.isEmpty())
    }

    @Test
    fun anIncognitoTabHandsOnlyCallsEmailsAndTextsToOtherApps() {
        val incognito = SkimWebViewClient(TAB, incognito = true, events, host, { tabId, url -> loads += tabId to url }) { _, _, _ -> }

        assertTrue(incognito.shouldOverrideUrlLoading(view, request("intent://evil.example/t#Intent;scheme=https;end", mainFrame = true, gesture = true)))
        assertTrue(incognito.shouldOverrideUrlLoading(view, request("market://details?id=x", mainFrame = true, gesture = true)))
        assertTrue("nothing left the tab", host.started.isEmpty())
        assertTrue(incognito.shouldOverrideUrlLoading(view, request("mailto:ana@example.com", mainFrame = true, gesture = true)))

        assertEquals(Uri.parse("mailto:ana@example.com"), host.started.single().data)
    }

    @Test
    fun anIntentAimedAtSkimItselfNeverStarts() {
        val url = "intent://daylightjournal.com/#Intent;scheme=https;package=${view.context.packageName};launchFlags=0x10008000;end"

        assertTrue(client.shouldOverrideUrlLoading(view, request(url, mainFrame = true, gesture = true)))

        assertTrue(host.started.isEmpty())
    }

    @Test
    fun withoutAnAppTheFallbackPageLoadsInTheTabThroughTheTabManager() {
        host.startFailure = ActivityNotFoundException()

        val handled = client.shouldOverrideUrlLoading(
            view,
            request(
                "intent://scan/#Intent;scheme=zxing;package=com.example.scanner;" +
                    "S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fscanner;end",
                mainFrame = true,
                gesture = true,
            ),
        )

        assertTrue(handled)
        assertEquals(listOf(TAB to "https://example.com/scanner"), loads)
        assertEquals("the WebView itself loads nothing", null, shadowOf(view).lastLoadedUrl)
    }

    @Test
    fun withoutAnAppOrAFallbackNothingHappens() {
        host.startFailure = ActivityNotFoundException()

        assertTrue(client.shouldOverrideUrlLoading(view, request("mailto:ana@example.com", mainFrame = true, gesture = true)))
        assertTrue(loads.isEmpty())
    }

    @Test
    fun anAppThatRefusesSkimBlocksTheNavigation() {
        host.startFailure = SecurityException("Permission Denial")

        val handled = client.shouldOverrideUrlLoading(
            view,
            request("intent://x/#Intent;scheme=x;S.browser_fallback_url=https%3A%2F%2Fexample.com%2F;end", mainFrame = true, gesture = true),
        )

        assertTrue(handled)
        assertTrue("a refusal is not a missing app: no fallback", loads.isEmpty())
    }

    @Test
    fun aGoneRendererIsReportedThenTheWebViewGoesBackToThePool() {
        events.rendererGoneAction = RendererGoneAction.SHOW_ERROR

        val handled = client.onRenderProcessGone(view, TestRenderProcessGoneDetail(crashed = false))

        assertTrue("handled, so the app does not go down with the renderer", handled)
        assertEquals(listOf(Event.RendererGone(TAB, didCrash = false)), events.events)
        val (webView, action, didCrash) = gone.single()
        assertSame(view, webView)
        assertEquals(RendererGoneAction.SHOW_ERROR, action)
        assertFalse(didCrash)
    }

    @Test
    fun aDetachedClientReportsNothingAndBlocksNavigation() {
        client.detach()

        client.onPageStarted(view, PAGE, null)
        client.doUpdateVisitedHistory(view, PAGE, false)
        client.onPageFinished(view, PAGE)
        client.onReceivedError(view, request(PAGE, mainFrame = true), TestWebResourceError(WebViewClient.ERROR_CONNECT, null))
        val blocked = client.shouldOverrideUrlLoading(view, request("https://example.com/", mainFrame = true, gesture = true))
        val handled = client.onRenderProcessGone(view, TestRenderProcessGoneDetail(crashed = true))

        assertTrue(blocked)
        assertTrue(handled)
        assertTrue(events.events.isEmpty())
        assertTrue(gone.isEmpty())
    }

    @Test
    fun aRendererIsHandedBackOnlyOnce() {
        client.onRenderProcessGone(view, TestRenderProcessGoneDetail(crashed = true))
        client.onRenderProcessGone(view, TestRenderProcessGoneDetail(crashed = true))

        assertEquals(1, gone.size)
        assertEquals(1, events.eventsOf<Event.RendererGone>().size)
    }

    private fun request(url: String, mainFrame: Boolean, gesture: Boolean = true): WebResourceRequest =
        TestWebResourceRequest(url, mainFrame, gesture)

    private companion object {
        const val TAB = "tab-1"
        const val PAGE = "https://daylightjournal.com/energy/heat-pumps-cold"
    }
}
