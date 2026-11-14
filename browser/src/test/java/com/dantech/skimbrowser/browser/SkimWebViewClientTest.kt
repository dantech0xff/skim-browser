package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.tab.LoadError

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.TestRenderProcessGoneDetail
import android.webkit.TestSslErrorHandler
import android.webkit.TestWebResourceError
import android.webkit.TestWebResourceRequest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.RecordingEvents.Event
import com.dantech.skimbrowser.domain.certificate.CertificateAction
import com.dantech.skimbrowser.domain.certificate.CertificateFault
import com.dantech.skimbrowser.domain.shield.CosmeticFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.dantech.skimbrowser.domain.shield.HostBlocklist
import com.dantech.skimbrowser.domain.shield.ShieldChoices
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowLooper
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
    fun aMediaRequestIsObservedAndASegmentThumbnailOrTrackerIsNot() = runBlocking {
        val shield = RequestShield.of(HostBlocklist.of(blockAll = setOf("ads.example")))
        shield.setDisabled("daylightjournal.com", incognito = true, disabled = true)
        val events = RecordingEvents()
        val client = client(shield, events, incognito = true)
        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        assertEquals(null, client.shouldInterceptRequest(view, request("https://cdn.example/v.mp4", mainFrame = false)))
        assertEquals(null, client.shouldInterceptRequest(view, request("https://cdn.example/a.m3u8", mainFrame = false)))
        assertEquals(null, client.shouldInterceptRequest(view, request("https://cdn.example/seg.ts", mainFrame = false)))
        assertEquals(null, client.shouldInterceptRequest(view, request("https://cdn.example/thumbs/poster.jpg", mainFrame = false)))
        assertEquals(
            null,
            client.shouldInterceptRequest(
                view,
                TestWebResourceRequest(
                    "https://video.example/playback",
                    mainFrame = false,
                    headers = mapOf("Range" to "bytes=0-", "Accept" to "video/mp4"),
                ),
            ),
        )
        assertEquals(
            null,
            client.shouldInterceptRequest(
                view,
                TestWebResourceRequest(
                    "https://video.example/mixed",
                    mainFrame = false,
                    headers = mapOf("RANGE" to "bytes=0-", "ACCEPT" to "video/webm"),
                ),
            ),
        )
        val ad = client.shouldInterceptRequest(view, request("https://ads.example/ad.mp4", mainFrame = false))
        ShadowLooper.idleMainLooper()

        assertEquals(null, ad)
        assertEquals(
            listOf(
                "https://cdn.example/v.mp4",
                "https://cdn.example/a.m3u8",
                "https://video.example/playback",
                "https://video.example/mixed",
            ),
            events.eventsOf<Event.MediaUrl>().map { it.mediaUrl },
        )
    }

    @Test
    fun aTrackerRequestIsAnsweredEmptyAndTheMainFrameIsNot() {
        val shield = RequestShield.of(HostBlocklist.of(blockThird = setOf("google-analytics.com")))
        val events = RecordingEvents()
        val client = SkimWebViewClient(
            tabId = TAB,
            incognito = false,
            events = events,
            activityHost = host,
            loadInTab = { _, _ -> },
            shield = shield,
            onRendererGone = { _, _, _ -> },
        )
        val main = client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        val sameSite = client.shouldInterceptRequest(view, request("https://daylightjournal.com/app.js", mainFrame = false))
        val tracker = client.shouldInterceptRequest(
            view,
            request("https://www.google-analytics.com/g/collect", mainFrame = false),
        )
        client.onPageStarted(view, PAGE, null)

        assertEquals(null, main)
        assertEquals(null, sameSite)
        assertEquals(200, tracker?.statusCode)
        assertEquals(listOf(Event.ShieldCount(TAB, 1)), events.eventsOf<Event.ShieldCount>())
    }

    @Test
    fun aMainFrameInterceptIsTheDocumentBeforePageStarted() = runBlocking {
        val shield = RequestShield.of(HostBlocklist.of(blockThird = setOf("google-analytics.com")))
        shield.setDisabled("daylightjournal.com", incognito = true, disabled = true)
        val client = client(shield, incognito = true)
        val tracker = "https://www.google-analytics.com/g/collect"

        val early = client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        val onThisPage = client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        client.shouldInterceptRequest(view, request(NEXT, mainFrame = true))
        val onTheNextPage = client.shouldInterceptRequest(view, request(tracker, mainFrame = false))

        assertEquals("no document yet, so a third-party tracker is still blocked", 200, early?.statusCode)
        assertEquals("the shield is off for the page the main frame just named", null, onThisPage)
        assertEquals("leaving that page blocks the tracker before onPageStarted", 200, onTheNextPage?.statusCode)
    }

    @Test
    fun blocksBeforePageStartedStillReachTheCount() {
        val shield = RequestShield.of(HostBlocklist.of(blockAll = setOf("tracker.example")))
        val events = RecordingEvents()
        val client = client(shield, events)
        val tracker = "https://tracker.example/pixel"

        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        client.onPageStarted(view, PAGE, null)

        assertEquals(listOf(Event.ShieldCount(TAB, 1)), events.eventsOf<Event.ShieldCount>())
    }

    @Test
    fun aBlockedCountQueuedForThePreviousPageDoesNotDropTheNextPage() {
        val shield = RequestShield.of(HostBlocklist.of(blockAll = setOf("tracker.example")))
        val events = RecordingEvents()
        val client = client(shield, events)
        val tracker = "https://tracker.example/pixel"

        client.onPageStarted(view, PAGE, null)
        client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        client.onPageStarted(view, NEXT, null)
        client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        ShadowLooper.idleMainLooper()

        assertEquals(listOf(Event.ShieldCount(TAB, 1)), events.eventsOf<Event.ShieldCount>())
    }

    @Test
    fun aMainFrameThatAlreadyStartedDoesNotStickTheNextPage() {
        val shield = RequestShield.of(HostBlocklist.of(blockAll = setOf("tracker.example")))
        val events = RecordingEvents()
        val client = client(shield, events)
        val tracker = "https://tracker.example/pixel"

        client.onPageStarted(view, PAGE, null)
        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        client.onPageStarted(view, NEXT, null)
        client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        ShadowLooper.idleMainLooper()

        assertEquals(listOf(Event.ShieldCount(TAB, 1)), events.eventsOf<Event.ShieldCount>())
    }

    @Test
    fun aMainFrameThatFailsLeavesTheCommittedPage() = runBlocking {
        val shield = RequestShield.of(HostBlocklist.of(blockThird = setOf("google-analytics.com")))
        shield.setDisabled("daylightjournal.com", incognito = true, disabled = true)
        val client = client(shield, incognito = true)
        val download = "https://files.example/guide.pdf"
        val tracker = "https://www.google-analytics.com/g/collect"

        client.onPageStarted(view, PAGE, null)
        client.shouldInterceptRequest(view, request(download, mainFrame = true))
        client.onReceivedError(view, request(download, mainFrame = true), TestWebResourceError(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, null))
        val afterError = client.shouldInterceptRequest(view, request(tracker, mainFrame = false))

        client.shouldInterceptRequest(view, request(download, mainFrame = true))
        client.abandonLoad()
        val afterDownload = client.shouldInterceptRequest(view, request(tracker, mainFrame = false))

        assertEquals(null, afterError)
        assertEquals("a download is dropped even when its final URL differs", null, afterDownload)
    }

    @Test
    fun aReloadKeepsBlocksThatArriveBeforePageStarted() {
        val shield = RequestShield.of(HostBlocklist.of(blockAll = setOf("tracker.example")))
        val events = RecordingEvents()
        val client = client(shield, events)
        val tracker = "https://tracker.example/pixel"

        client.onPageStarted(view, PAGE, null)
        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        client.shouldInterceptRequest(view, request(tracker, mainFrame = false))
        client.onPageStarted(view, PAGE, null)

        assertEquals(listOf(Event.ShieldCount(TAB, 2)), events.eventsOf<Event.ShieldCount>())
    }

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
    fun anIdleCrossSiteJumpIsBlockedAndAClickIsNot() {
        client.onPageStarted(view, PAGE, null)

        assertTrue(client.shouldOverrideUrlLoading(view, request("https://ads.example/offer", mainFrame = true, gesture = false)))
        assertTrue(events.events.contains(Event.RedirectBlocked(TAB, "https://ads.example/offer")))
        assertTrue(host.started.isEmpty())

        assertFalse(client.shouldOverrideUrlLoading(view, request("https://daylightjournal.com/next", mainFrame = true, gesture = false)))
        assertFalse(client.shouldOverrideUrlLoading(view, request("https://ads.example/clicked", mainFrame = true, gesture = true)))

        client.noteInteraction()
        assertFalse(client.shouldOverrideUrlLoading(view, request("https://pay.example/checkout", mainFrame = true, gesture = false)))
    }

    @Test
    fun aServerRedirectIsNotAnOddRedirect() {
        client.onPageStarted(view, PAGE, null)

        assertFalse(
            client.shouldOverrideUrlLoading(
                view,
                request("https://ads.example/offer", mainFrame = true, gesture = false, redirect = true),
            ),
        )
        assertTrue(events.events.none { it is Event.RedirectBlocked })
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

    @Test
    fun aPageHidesLeftoverAdElementsWhenBlockingIsOn() = runBlocking {
        val cosmetic = CosmeticFilters.load("G\t.adsbox\n".reader().buffered())
        val shield = RequestShield.of(HostBlocklist.EMPTY, cosmetic)
        val client = client(shield)

        client.onPageStarted(view, PAGE, null)
        client.onPageFinished(view, PAGE)

        val script = shadowOf(view).lastEvaluatedJavascript
        assertTrue(script.contains(".adsbox"))
        assertTrue(script.contains("display:none !important"))
        assertTrue(script.contains("skim-hide"))
        assertFalse(script.contains("querySelector"))
    }

    @Test
    fun anAllowedSiteGetsNoHideSheet() = runBlocking {
        val cosmetic = CosmeticFilters.load("G\t.adsbox\n".reader().buffered())
        val shield = RequestShield.of(HostBlocklist.EMPTY, cosmetic)
        shield.setDisabled("daylightjournal.com", incognito = true, disabled = true)
        val client = client(shield, incognito = true)

        client.onPageStarted(view, PAGE, null)

        assertEquals(null, shadowOf(view).lastEvaluatedJavascript)
    }

    @Test
    fun aBlockedFrameIsCollapsedInOneStylesheet() {
        val shield = RequestShield.of(HostBlocklist.of(blockAll = setOf("ads.example")))
        val client = client(shield)
        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        client.shouldInterceptRequest(view, request("https://ads.example/banner", mainFrame = false))

        client.onPageStarted(view, PAGE, null)

        val script = shadowOf(view).lastEvaluatedJavascript
        assertTrue(script.contains("skim-collapse"))
        assertTrue(script.contains("iframe[src*="))
        assertTrue(script.contains("//ads.example/"))
        assertTrue(script.contains("display:none !important"))
        assertFalse(script.contains("querySelector"))
        assertFalse(script.contains("getElementsBy"))
    }

    @Test
    fun aBlockedFrameStillCollapsesWhenTheStartedUrlIsSpelledDifferently() {
        val shield = RequestShield.of(HostBlocklist.of(blockAll = setOf("ads.example")))
        val client = client(shield)
        client.shouldInterceptRequest(view, request("https://www.daylightjournal.com/energy/heat-pumps-cold", mainFrame = true))
        client.shouldInterceptRequest(view, request("https://ads.example/banner", mainFrame = false))

        client.onPageStarted(view, "$PAGE/", null)

        val script = shadowOf(view).lastEvaluatedJavascript
        assertTrue(script.contains("//ads.example/"))
    }

    @Test
    fun aDifferentPageDoesNotKeepThePreviousCollapseHosts() {
        val shield = RequestShield.of(HostBlocklist.of(blockAll = setOf("ads.example")))
        val client = client(shield)
        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        client.shouldInterceptRequest(view, request("https://ads.example/banner", mainFrame = false))

        client.onPageStarted(view, NEXT, null)

        val script = shadowOf(view).lastEvaluatedJavascript
        assertTrue(script == null || !script.contains("ads.example"))
    }

    @Test
    fun theHideSheetIsAppliedOnFinishEvenWhenDocumentStartIsSupported() {
        val cosmetic = CosmeticFilters.load("G\t.adsbox\n".reader().buffered())
        val shield = RequestShield.of(HostBlocklist.EMPTY, cosmetic)
        val client = client(shield, documentStart = true)

        client.onPageFinished(view, NEXT)

        val script = shadowOf(view).lastEvaluatedJavascript
        assertTrue(script.contains("skim-hide"))
        assertTrue(script.contains(".adsbox"))
        assertTrue(script.contains("example.com"))
    }

    @Test
    fun theHideSheetWaitsUntilTheRulesAreReady() {
        val shield = WaitingShield()
        val client = client(shield, documentStart = true)

        client.armCosmetic(view, PAGE)
        client.onPageStarted(view, PAGE, null)
        client.onPageFinished(view, PAGE)
        assertEquals(null, shadowOf(view).lastEvaluatedJavascript)

        shield.ready()
        ShadowLooper.idleMainLooper()

        val script = shadowOf(view).lastEvaluatedJavascript
        assertTrue(script.contains("skim-hide"))
        assertTrue(script.contains(".adsbox"))
    }

    @Test
    fun aDelayedArmUsesTheRequestedPageWhileTheDocumentIsBlank() {
        val shield = WaitingShield()
        val client = client(shield, documentStart = true)

        client.onPageStarted(view, "about:blank", null)
        client.armCosmetic(view, PAGE)
        shield.ready()
        ShadowLooper.idleMainLooper()

        assertTrue(shield.urls.contains(PAGE))
        assertTrue(shadowOf(view).lastEvaluatedJavascript == null || !shadowOf(view).lastEvaluatedJavascript.contains("about:blank"))
    }

    @Test
    fun aDelayedArmSurvivesASameHostRedirect() {
        val shield = WaitingShield()
        val client = client(shield, documentStart = true)

        client.armCosmetic(view, PAGE)
        client.shouldInterceptRequest(view, request("https://daylightjournal.com/other?q=1", mainFrame = true))
        shield.ready()
        ShadowLooper.idleMainLooper()

        assertTrue(shield.urls.contains(PAGE))
    }

    @Test
    fun anUnknownCertificateCancelsTheLoadAndShowsTheInterstitial() {
        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        val handler = TestSslErrorHandler()
        val error = sslError(SslError.SSL_EXPIRED, PAGE)

        client.onReceivedSslError(view, handler, error)

        assertTrue(handler.cancelled)
        assertFalse(handler.proceeded)
        val warning = events.eventsOf<Event.CertificateError>().single().warning
        assertEquals(PAGE, warning.url)
        assertEquals("daylightjournal.com", warning.host)
        assertEquals(CertificateFault.Expired, warning.fault)
        assertEquals(certificateFingerprintOf(error.certificate), warning.fingerprint)
    }

    @Test
    fun anAllowedCertificateProceedsAndDoesNotShowTheInterstitial() {
        events.certificateDecision = CertificateAction.Proceed
        val handler = TestSslErrorHandler()

        client.onReceivedSslError(view, handler, sslError(SslError.SSL_UNTRUSTED, PAGE))

        assertTrue(handler.proceeded)
        assertFalse(handler.cancelled)
        assertTrue(events.eventsOf<Event.CertificateError>().isEmpty())
    }

    @Test
    fun aSubresourceCertificateCancelsWithoutAnInterstitial() {
        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        val handler = TestSslErrorHandler()

        client.onReceivedSslError(view, handler, sslError(SslError.SSL_IDMISMATCH, "https://cdn.example/app.js"))

        assertTrue(handler.cancelled)
        assertFalse(handler.proceeded)
        assertTrue(events.eventsOf<Event.CertificateError>().isEmpty())
    }

    @Test
    fun aCancelledCertificateDropsTheInFlightPage() {
        val shield = RequestShield.of(HostBlocklist.of(blockThird = setOf("bad.example")))
        val client = client(shield)
        client.onPageStarted(view, PAGE, null)
        client.shouldInterceptRequest(view, request(BAD, mainFrame = true))

        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_UNTRUSTED, BAD))
        val stillOnThePreviousPage = client.shouldInterceptRequest(view, request("https://bad.example/app.js", mainFrame = false))
        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_UNTRUSTED, BAD))

        assertEquals(200, stillOnThePreviousPage?.statusCode)
        assertEquals(1, events.eventsOf<Event.CertificateError>().size)
    }

    @Test
    fun anAllowedCertificateKeepsTheInFlightPage() {
        events.certificateDecision = CertificateAction.Proceed
        val shield = RequestShield.of(HostBlocklist.of(blockThird = setOf("bad.example")))
        val client = client(shield)
        client.onPageStarted(view, PAGE, null)
        client.shouldInterceptRequest(view, request(BAD, mainFrame = true))

        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_UNTRUSTED, BAD))
        val onTheFailedNavigation = client.shouldInterceptRequest(view, request("https://bad.example/app.js", mainFrame = false))

        assertEquals(null, onTheFailedNavigation)
    }

    @Test
    fun aFollowUpCertificateErrorDoesNotReplaceTheFirstPage() {
        client.shouldInterceptRequest(view, request(BAD, mainFrame = true))
        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_UNTRUSTED, BAD))
        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_IDMISMATCH, "https://cdn.example/app.js"))

        val first = events.eventsOf<Event.CertificateError>().single().warning
        assertEquals(BAD, first.url)

        client.shouldInterceptRequest(view, request(PAGE, mainFrame = true))
        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_EXPIRED, PAGE))

        val warnings = events.eventsOf<Event.CertificateError>()
        assertEquals(listOf(BAD, PAGE), warnings.map { it.warning.url })
    }

    @Test
    fun retryingTheSameRefusedUrlShowsTheInterstitialAgain() {
        client.shouldInterceptRequest(view, request(BAD, mainFrame = true))
        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_UNTRUSTED, BAD))
        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_UNTRUSTED, BAD))

        assertEquals(listOf(BAD), events.eventsOf<Event.CertificateError>().map { it.warning.url })

        client.shouldInterceptRequest(view, request(BAD, mainFrame = true))
        client.onReceivedSslError(view, TestSslErrorHandler(), sslError(SslError.SSL_UNTRUSTED, BAD))

        assertEquals(listOf(BAD, BAD), events.eventsOf<Event.CertificateError>().map { it.warning.url })
    }

    @Test
    fun aDetachedClientCancelsACertificateAndReportsNothing() {
        client.detach()
        val handler = TestSslErrorHandler()

        client.onReceivedSslError(view, handler, sslError(SslError.SSL_UNTRUSTED, PAGE))

        assertTrue(handler.cancelled)
        assertFalse(handler.proceeded)
        assertTrue(events.events.isEmpty())
    }

    @Suppress("DEPRECATION")
    private fun sslError(primary: Int, url: String): SslError {
        val certificate = SslCertificate("CN=daylightjournal.com", "CN=Test CA", java.util.Date(0), java.util.Date(86_400_000L))
        return SslError(primary, certificate, url)
    }

    private fun request(
        url: String,
        mainFrame: Boolean,
        gesture: Boolean = true,
        redirect: Boolean = false,
    ): WebResourceRequest = TestWebResourceRequest(url, mainFrame, gesture, redirect)

    private fun client(
        shield: ShieldChoices,
        events: RecordingEvents = this.events,
        incognito: Boolean = false,
        documentStart: Boolean = false,
    ) = SkimWebViewClient(
        tabId = TAB,
        incognito = incognito,
        events = events,
        activityHost = host,
        loadInTab = { _, _ -> },
        shield = shield,
        documentStartSupported = documentStart,
        onRendererGone = { _, _, _ -> },
    )

    private class WaitingShield : ShieldChoices {
        private val pending = mutableListOf<() -> Unit>()
        private var loaded = false
        val urls = mutableListOf<String?>()
        private var record = false
        private val off = MutableStateFlow<Set<String>>(emptySet())
        override val disabledHosts: StateFlow<Set<String>> = off
        override fun incognitoDisabled(host: String) = false
        override suspend fun setDisabled(host: String, incognito: Boolean, disabled: Boolean) = Unit
        override fun blocks(documentUrl: String?, requestUrl: String, incognito: Boolean) = false
        override fun clearIncognito() = Unit
        override fun cosmeticCss(documentUrl: String?, incognito: Boolean): String? {
            if (record) urls += documentUrl
            if (!loaded) return null
            if (documentUrl == null || !documentUrl.startsWith("http")) return ""
            return ".adsbox{display:none !important}\n"
        }
        override fun whenCosmeticReady(action: () -> Unit) {
            if (loaded) action() else pending += action
        }
        fun ready() {
            loaded = true
            record = true
            urls.clear()
            val queued = pending.toList()
            pending.clear()
            queued.forEach { it() }
        }
    }

    private companion object {
        const val TAB = "tab-1"
        const val PAGE = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val NEXT = "https://example.com/next"
        const val BAD = "https://bad.example/login"
    }
}
