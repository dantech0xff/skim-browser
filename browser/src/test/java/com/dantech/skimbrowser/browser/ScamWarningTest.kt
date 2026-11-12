package com.dantech.skimbrowser.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebBackForwardList
import android.webkit.WebHistoryItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.SafeBrowsingResponseCompat
import androidx.webkit.WebResourceErrorCompat
import com.dantech.skimbrowser.browser.RecordingEvents.Event
import com.dantech.skimbrowser.domain.phishing.PhishingReason
import com.dantech.skimbrowser.domain.phishing.classifyPhishing
import com.dantech.skimbrowser.domain.phishing.SafeBrowsingThreat
import com.dantech.skimbrowser.domain.phishing.SafetyWarning
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScamWarningTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aLookalikeLoadShowsTheWarningAndDoesNotOpenIt() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)

            f.manager.load(id, "https://paypai.com/login")

            val warning = f.manager.runtime(id).value.safetyWarning
            assertTrue(warning is SafetyWarning.Heuristic)
            assertEquals(PhishingReason.LOOKALIKE, (warning as SafetyWarning.Heuristic).reason)
            assertEquals(emptyList<String>(), f.engine.calls.filterIsInstance<FakeTabEngine.Call.Load>().map { it.url })

            f.manager.proceedUnsafe(id)
            assertNull(f.manager.runtime(id).value.safetyWarning)
            assertEquals(listOf("https://paypai.com/login"), f.engine.calls.filterIsInstance<FakeTabEngine.Call.Load>().map { it.url })
            assertNull(gate.heuristic("https://paypai.com/login", incognito = false))
        } finally {
            f.close()
        }
    }

    @Test
    fun backToSafetyOnANewTabReturnsToTheNewTabPage() = runTest {
        val f = TabManagerFixture(this, folder.root, safety = MemoryGate())
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            f.manager.load(id, "https://paypai.com/")

            assertEquals("https://paypai.com/", f.tab(id)?.url)
            assertTrue(f.tab(id)?.fromNewTabPage == true)
            f.manager.leaveUnsafePage(id)

            assertNull(f.tab(id)?.url)
            assertNull(f.manager.runtime(id).value.safetyWarning)
        } finally {
            f.close()
        }
    }

    @Test
    fun aStartedLookalikeIsNotRecordedAsAVisit() {
        val events = RecordingEvents()
        val client = SkimWebViewClient(
            tabId = "tab",
            incognito = false,
            events = events,
            activityHost = FakeActivityHost(),
            loadInTab = { _, _ -> },
            safety = MemoryGate(),
            onRendererGone = { _, _, _ -> },
        )
        val view = WebView(ApplicationProvider.getApplicationContext())
        client.onPageStarted(view, "https://paypai.com/", null)
        client.doUpdateVisitedHistory(view, "https://paypai.com/", false)

        assertEquals(listOf("https://paypai.com/"), events.events.filterIsInstance<Event.PageStarted>().map { it.url })
        assertTrue(events.events.filterIsInstance<Event.VisitedHistory>().isEmpty())
        assertEquals(PhishingReason.LOOKALIKE, (events.events.filterIsInstance<Event.SafetyWarningEvent>().single().warning as SafetyWarning.Heuristic).reason)
    }

    @Test
    fun aStartedLookalikeStepsBackToThePreviousPage() {
        val events = RecordingEvents()
        val client = SkimWebViewClient(
            tabId = "tab",
            incognito = false,
            events = events,
            activityHost = FakeActivityHost(),
            loadInTab = { _, _ -> },
            safety = MemoryGate(),
            onRendererGone = { _, _, _ -> },
        )
        var steppedBack = false
        val view = object : WebView(ApplicationProvider.getApplicationContext()) {
            override fun canGoBack(): Boolean = true
            override fun goBack() {
                steppedBack = true
            }
        }
        client.onPageStarted(view, "https://paypai.com/", null)
        client.doUpdateVisitedHistory(view, "https://paypai.com/", false)

        assertTrue(steppedBack)
        assertTrue(events.events.filterIsInstance<Event.PageStarted>().isEmpty())
        assertTrue(events.events.filterIsInstance<Event.VisitedHistory>().isEmpty())
        assertEquals(PhishingReason.LOOKALIKE, (events.events.filterIsInstance<Event.SafetyWarningEvent>().single().warning as SafetyWarning.Heuristic).reason)
    }

    @Test
    fun backAfterARedirectShowsThePreviousPage() = runTest {
        val f = TabManagerFixture(this, folder.root, safety = MemoryGate())
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            f.manager.load(id, "https://example.com/")
            f.engine.openPage(id, "https://example.com/")
            f.engine.reportHistory(id, canGoBack = true, canGoForward = false)
            val warning = classifyPhishing("https://paypai.com/")
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)
            f.manager.events.onPageStarted(id, "https://example.com/")
            f.manager.events.onVisitedHistory(id, "https://paypai.com/")

            assertEquals("https://example.com/", f.tab(id)?.url)
            assertTrue(f.manager.runtime(id).value.safetyWarning is SafetyWarning.Heuristic)
            f.manager.leaveUnsafePage(id)

            assertEquals("https://example.com/", f.tab(id)?.url)
            assertNull(f.manager.runtime(id).value.safetyWarning)
            assertTrue(f.engine.calls.filterIsInstance<FakeTabEngine.Call.GoBack>().isEmpty())
        } finally {
            f.close()
        }
    }

    @Test
    fun aFlaggedForwardEntryIsDropped() {
        val events = RecordingEvents()
        val client = SkimWebViewClient(
            tabId = "tab",
            incognito = false,
            events = events,
            activityHost = FakeActivityHost(),
            loadInTab = { _, _ -> },
            safety = MemoryGate(),
            onRendererGone = { _, _, _ -> },
        )
        var steppedBack = false
        val view = object : WebView(ApplicationProvider.getApplicationContext()) {
            override fun getUrl(): String = "https://example.com/"
            override fun canGoBack(): Boolean = true
            override fun canGoForward(): Boolean = true
            override fun goBack() {
                steppedBack = true
            }

            override fun copyBackForwardList(): WebBackForwardList = historyList(
                "https://example.com/",
                "https://paypai.com/",
            )

            override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
                assertTrue(script.contains("pushState"))
                resultCallback?.onReceiveValue("\"ok\"")
            }
        }
        client.onPageFinished(view, "https://example.com/")

        assertTrue(steppedBack)
        val forward = events.events.filterIsInstance<Event.NavigationState>().last()
        assertTrue(forward.canGoBack)
        assertFalse(forward.canGoForward)
    }

    @Test
    fun aSafeForwardEntryIsKept() {
        val events = RecordingEvents()
        val client = SkimWebViewClient(
            tabId = "tab",
            incognito = false,
            events = events,
            activityHost = FakeActivityHost(),
            loadInTab = { _, _ -> },
            safety = MemoryGate(),
            onRendererGone = { _, _, _ -> },
        )
        var dropped = false
        val view = object : WebView(ApplicationProvider.getApplicationContext()) {
            override fun canGoBack(): Boolean = true
            override fun canGoForward(): Boolean = true
            override fun copyBackForwardList(): WebBackForwardList = historyList(
                "https://example.com/",
                "https://example.org/next",
            )

            override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
                dropped = true
            }
        }
        client.onPageFinished(view, "https://example.com/")

        assertFalse(dropped)
        assertTrue(events.events.filterIsInstance<Event.NavigationState>().single().canGoForward)
    }

    @Test
    fun forwardIsRefusedWhileAWarningIsShowing() = runTest {
        val f = TabManagerFixture(this, folder.root, safety = MemoryGate())
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            f.manager.load(id, "https://paypai.com/")
            f.engine.reportHistory(id, canGoBack = true, canGoForward = true)

            f.manager.goForward(id)

            assertTrue(f.engine.calls.filterIsInstance<FakeTabEngine.Call.GoForward>().isEmpty())
        } finally {
            f.close()
        }
    }

    @Test
    fun aLookalikeTypedOverAnOpenPageLeavesThatPage() = runTest {
        val f = TabManagerFixture(this, folder.root, safety = MemoryGate())
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            f.manager.load(id, "https://example.com/")
            f.engine.openPage(id, "https://example.com/")

            f.manager.load(id, "https://paypai.com/")

            assertEquals("https://example.com/", f.tab(id)?.url)
            assertTrue(f.manager.runtime(id).value.safetyWarning is SafetyWarning.Heuristic)
            f.manager.leaveUnsafePage(id)

            assertEquals("https://example.com/", f.tab(id)?.url)
            assertNull(f.manager.runtime(id).value.safetyWarning)
            assertEquals(listOf("https://example.com/"), f.engine.calls.filterIsInstance<FakeTabEngine.Call.Load>().map { it.url })
        } finally {
            f.close()
        }
    }

    @Test
    fun backFromACommittedLookalikeGoesBack() = runTest {
        val f = TabManagerFixture(this, folder.root, safety = MemoryGate())
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            f.manager.load(id, "https://example.com/")
            f.engine.openPage(id, "https://example.com/")
            f.engine.reportHistory(id, canGoBack = true, canGoForward = false)
            val warning = classifyPhishing("https://paypai.com/")
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)
            f.manager.events.onPageStarted(id, "https://paypai.com/")

            f.manager.leaveUnsafePage(id)

            assertEquals(listOf(id), f.engine.calls.filterIsInstance<FakeTabEngine.Call.GoBack>().map { it.tabId })
            assertEquals("https://paypai.com/", f.tab(id)?.url)
        } finally {
            f.close()
        }
    }

    @Test
    fun backFromSafeBrowsingOnANewTabReturnsToTheNewTabPage() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            f.manager.load(id, "https://evil.example/phish")
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)

            f.manager.leaveUnsafePage(id)

            assertNull(f.tab(id)?.url)
            assertNull(f.manager.runtime(id).value.safetyWarning)
        } finally {
            f.close()
        }
    }

    @Test
    fun safeBrowsingShowsSkimsPageAndProceedsOnlyForThatHost() {
        val gate = MemoryGate()
        val events = RecordingEvents()
        val view = WebView(ApplicationProvider.getApplicationContext())
        val client = SkimWebViewClient(
            tabId = "tab",
            incognito = false,
            events = events,
            activityHost = FakeActivityHost(),
            loadInTab = { _, _ -> },
            safety = gate,
            onRendererGone = { _, _, _ -> },
        )
        val response = RecordingSafeBrowsing()
        client.onSafeBrowsingHit(view, request("https://evil.example/phish"), THREAT_PHISHING, response)

        val shown = events.events.filterIsInstance<Event.SafetyWarningEvent>().single().warning
        assertTrue(shown is SafetyWarning.SafeBrowsing)
        assertEquals(SafeBrowsingThreat.PHISHING, (shown as SafetyWarning.SafeBrowsing).threat)
        assertNull(response.proceeded)

        gate.proceedSafeBrowsing("tab", "evil.example", incognito = false)
        assertEquals(true, response.proceeded)
        assertFalse(response.reported)
        assertTrue(gate.allows("evil.example", incognito = false))
        assertFalse(gate.allows("evil.example", incognito = true))
    }

    @Test
    fun aSubresourceSafeBrowsingHitDoesNotWarnOrLeaveThePage() {
        val events = RecordingEvents()
        val client = SkimWebViewClient(
            tabId = "tab",
            incognito = false,
            events = events,
            activityHost = FakeActivityHost(),
            loadInTab = { _, _ -> },
            safety = MemoryGate(),
            onRendererGone = { _, _, _ -> },
        )
        val response = RecordingSafeBrowsing()
        client.onSafeBrowsingHit(WebView(ApplicationProvider.getApplicationContext()), request("https://evil.example/pixel.gif", mainFrame = false), THREAT_PHISHING, response)

        assertEquals(false, response.proceeded)
        assertFalse(response.reported)
        assertTrue(events.events.filterIsInstance<Event.SafetyWarningEvent>().isEmpty())
    }

    @Test
    fun proceedAfterTheWebViewIsGoneLoadsTheAddress() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)
            gate.cancelSafeBrowsing(id)

            f.manager.proceedUnsafe(id)

            assertNull(f.manager.runtime(id).value.safetyWarning)
            assertEquals(listOf("https://evil.example/phish"), f.engine.calls.filterIsInstance<FakeTabEngine.Call.Load>().map { it.url })
        } finally {
            f.close()
        }
    }

    @Test
    fun aNewAddressCancelsThePausedSafeBrowsingHit() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            var answer: Boolean? = null
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { answer = it }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)

            f.manager.load(id, "https://example.com/")

            assertEquals(false, answer)
            assertNull(f.manager.runtime(id).value.safetyWarning)
            assertEquals("https://example.com/", f.tab(id)?.url)
        } finally {
            f.close()
        }
    }

    @Test
    fun aCancelledSafeBrowsingHitDoesNotReplaceTheNewAddress() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)
            f.manager.load(id, "https://example.com/")

            f.manager.events.onPageStarted(id, "https://evil.example/phish")
            f.manager.events.onVisitedHistory(id, "https://evil.example/phish")
            f.manager.events.onLoadError(id, -2, "aborted", "https://evil.example/phish")

            assertEquals("https://example.com/", f.tab(id)?.url)
            assertNull(f.manager.runtime(id).value.loadError)
            assertNull(f.manager.runtime(id).value.committedUrl?.let { if (it.contains("evil.example")) it else null })

            f.manager.load(id, "https://evil.example/phish")
            f.manager.events.onLoadError(id, -2, "aborted", "https://evil.example/phish")
            assertEquals("https://evil.example/phish", f.tab(id)?.url)
            assertNotNull(f.manager.runtime(id).value.loadError)
            f.manager.events.onPageStarted(id, "https://evil.example/phish")
            assertEquals("https://evil.example/phish", f.tab(id)?.url)
            assertNull(f.manager.runtime(id).value.loadError)
        } finally {
            f.close()
        }
    }

    @Test
    fun aLaterAddressClearsTheRetrySoTheCancelledHitCannotReplaceIt() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)
            f.manager.load(id, "https://example.com/")
            f.manager.load(id, "https://evil.example/phish")
            f.manager.load(id, "https://news.example/")

            f.manager.events.onPageStarted(id, "https://evil.example/phish")

            assertEquals("https://news.example/", f.tab(id)?.url)
        } finally {
            f.close()
        }
    }

    @Test
    fun aLateStartOfThePreviousPageDoesNotCancelTheRetry() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)
            f.manager.load(id, "https://example.com/")
            f.manager.load(id, "https://evil.example/phish")

            f.manager.events.onPageStarted(id, "https://example.com/")
            f.manager.events.onPageStarted(id, "https://evil.example/phish")

            assertEquals("https://evil.example/phish", f.tab(id)?.url)
            assertNull(f.manager.runtime(id).value.loadError)
        } finally {
            f.close()
        }
    }

    @Test
    fun turningWarningsOffLetsACancelledAddressLoad() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)
            f.manager.load(id, "https://example.com/")
            f.manager.clearSafetyWarnings()
            f.manager.events.onPageStarted(id, "https://evil.example/phish")
            assertEquals("https://example.com/", f.tab(id)?.url)

            f.manager.load(id, "https://evil.example/phish")
            f.manager.events.onPageStarted(id, "https://evil.example/phish")

            assertEquals("https://evil.example/phish", f.tab(id)?.url)
        } finally {
            f.close()
        }
    }

    @Test
    fun turningWarningsOffReleasesThePausedHitAndClearsTheOverlay() = runTest {
        val gate = ProcessSafetyGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            var answer: Boolean? = null
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { answer = it }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)

            gate.setEnabled(false)
            f.manager.clearSafetyWarnings()

            assertEquals(true, answer)
            assertNull(f.manager.runtime(id).value.safetyWarning)
        } finally {
            f.close()
        }
    }

    @Test
    fun aRejectedStartDoesNotStopTheLoadInFlight() {
        val navigations = TabNavigations()
        navigations.begin("tab", "https://evil.example/phish")
        navigations.refuse("tab", "https://evil.example/phish")
        navigations.begin("tab", "https://example.com/")
        val events = RecordingEvents()
        val client = SkimWebViewClient(
            tabId = "tab",
            incognito = false,
            events = events,
            activityHost = FakeActivityHost(),
            loadInTab = { _, _ -> },
            safety = MemoryGate(),
            navigations = navigations,
            onRendererGone = { _, _, _ -> },
        )
        var stopped = false
        val view = object : WebView(ApplicationProvider.getApplicationContext()) {
            override fun stopLoading() {
                stopped = true
            }
        }
        client.onPageStarted(view, "https://evil.example/phish", null)

        assertFalse(stopped)
        assertTrue(events.events.filterIsInstance<Event.PageStarted>().isEmpty())
    }

    @Test
    fun aSafeBrowsingForwardEntryIsDropped() {
        val events = RecordingEvents()
        val client = SkimWebViewClient(
            tabId = "tab",
            incognito = false,
            events = events,
            activityHost = FakeActivityHost(),
            loadInTab = { _, _ -> },
            safety = MemoryGate(),
            onRendererGone = { _, _, _ -> },
        )
        val hitView = WebView(ApplicationProvider.getApplicationContext())
        client.onSafeBrowsingHit(hitView, request("https://evil.example/phish"), THREAT_PHISHING, RecordingSafeBrowsing())
        client.doUpdateVisitedHistory(hitView, "https://evil.example/phish", false)
        client.onReceivedError(hitView, request("https://evil.example/phish"), object : WebResourceErrorCompat() {
            override fun getErrorCode(): Int = -2
            override fun getDescription(): CharSequence = "aborted"
            override fun getDebugCode(): Int = -2
        })
        assertTrue(events.events.filterIsInstance<Event.VisitedHistory>().isEmpty())
        assertTrue(events.events.filterIsInstance<Event.LoadError>().isEmpty())
        var steppedBack = false
        val view = object : WebView(ApplicationProvider.getApplicationContext()) {
            override fun getUrl(): String = "https://example.com/"
            override fun canGoBack(): Boolean = true
            override fun canGoForward(): Boolean = true
            override fun goBack() {
                steppedBack = true
            }

            override fun copyBackForwardList(): WebBackForwardList = historyList(
                "https://example.com/",
                "https://evil.example/phish",
            )

            override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
                resultCallback?.onReceiveValue("\"ok\"")
            }
        }
        client.onPageFinished(view, "https://example.com/")

        assertTrue(steppedBack)
        assertFalse(events.events.filterIsInstance<Event.NavigationState>().last().canGoForward)
    }

    @Test
    fun turningWarningsOffLetsTheAddressLoad() = runTest {
        val gate = MemoryGate(startEnabled = false)
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            f.manager.load(id, "https://paypai.com/")
            assertNull(f.manager.runtime(id).value.safetyWarning)
            assertEquals(listOf("https://paypai.com/"), f.engine.calls.filterIsInstance<FakeTabEngine.Call.Load>().map { it.url })
        } finally {
            f.close()
        }
    }

    @Test
    fun backToSafetyDropsALaterStartOfTheThreat() = runTest {
        val gate = MemoryGate()
        val f = TabManagerFixture(this, folder.root, safety = gate)
        try {
            val id = f.manager.newTab(incognito = false)
            f.engines.attach(f.engine)
            f.manager.load(id, "https://example.com/")
            f.engine.openPage(id, "https://example.com/")
            f.engine.reportHistory(id, canGoBack = true, canGoForward = false)
            f.manager.load(id, "https://evil.example/phish")
            val warning = gate.offerSafeBrowsing(id, "https://evil.example/phish", "evil.example", SafeBrowsingThreat.PHISHING, false) { }
            check(warning != null)
            f.manager.events.onSafetyWarning(id, warning)
            f.manager.events.onPageStarted(id, "https://evil.example/phish")

            f.manager.leaveUnsafePage(id)
            f.manager.events.onPageStarted(id, "https://example.com/")
            f.manager.events.onPageStarted(id, "https://evil.example/phish")

            assertEquals("https://example.com/", f.tab(id)?.url)
            f.manager.load(id, "https://news.example/")
            f.engine.openPage(id, "https://news.example/")
            f.manager.events.onPageStarted(id, "https://evil.example/phish")
            assertEquals("https://news.example/", f.tab(id)?.url)
        } finally {
            f.close()
        }
    }

    @Test
    fun aNavigationGenerationDropsReportsFromAnEarlierAddress() = runTest {
        val nav = TabNavigations()
        nav.begin("tab", "https://example.com/start")
        nav.follow("tab", "https://login.example/session")
        assertTrue(nav.acceptStart("tab", "https://login.example/session"))
        nav.begin("tab", "https://kotlinlang.org/")
        assertTrue(nav.acceptStart("tab", "https://kotlinlang.org/docs/"))
        nav.begin("tab", "https://daylightjournal.com/energy/heat-pumps-cold")
        assertTrue(nav.acceptStart("tab", "https://nightbus-kansai.jp/timetable"))
        nav.begin("tab", "https://evil.example/phish")
        nav.refuse("tab", "https://evil.example/phish")
        nav.begin("tab", "https://example.com/")
        nav.begin("tab", "https://news.example/")
        assertFalse(nav.let { it.follow("tab", "https://evil.example/phish"); it.acceptStart("tab", "https://evil.example/phish") })
        assertTrue(nav.acceptStart("tab", "https://news.example/"))

        val evil = "https://evil.example/phish"
        val example = "https://example.com/"
        val news = "https://news.example/"
        val races = listOf(
            Race(
                "a new address ignores a cancelled hit's start, visit, error, finish, and title",
                listOf(Step.Load(example), Step.Start(evil), Step.Visit(evil), Step.Error(evil), Step.Finish(evil), Step.Title("Stale")),
                url = example,
            ),
            Race(
                "a later address keeps the cancelled hit from replacing it",
                listOf(Step.Load(example), Step.Load(evil), Step.Load(news), Step.Start(evil), Step.Visit(evil), Step.Error(evil)),
                url = news,
            ),
            Race(
                "a late start of the previous page does not cancel the reopen",
                listOf(Step.Load(example), Step.Load(evil), Step.Start(example), Step.Start(evil)),
                url = evil,
            ),
            Race(
                "an error for the address just opened belongs to that navigation",
                listOf(Step.Load(example), Step.Load(evil), Step.Error(evil)),
                url = evil,
                error = true,
            ),
            Race(
                "the reopened page's start clears the error from that navigation",
                listOf(Step.Load(example), Step.Load(evil), Step.Error(evil), Step.Start(evil)),
                url = evil,
            ),
            Race(
                "warnings off do not apply a stale start",
                listOf(Step.Load(example), Step.WarningsOff, Step.Start(evil)),
                url = example,
            ),
            Race(
                "warnings off still let a new load of the address start",
                listOf(Step.Load(example), Step.WarningsOff, Step.Load(evil), Step.Start(evil)),
                url = evil,
            ),
            Race(
                "proceed ignores a late start of the page that was left",
                listOf(Step.Proceed, Step.Start(example), Step.Start(evil)),
                url = evil,
            ),
        )
        races.forEach { race ->
            val gate = MemoryGate()
            val f = TabManagerFixture(this, folder.root, safety = gate)
            try {
                val id = f.manager.newTab(incognito = false)
                f.engines.attach(f.engine)
                f.manager.load(id, example)
                f.manager.load(id, evil)
                val warning = gate.offerSafeBrowsing(id, evil, "evil.example", SafeBrowsingThreat.PHISHING, false) { }
                check(warning != null)
                f.manager.events.onSafetyWarning(id, warning)
                race.steps.forEach { step ->
                    when (step) {
                        is Step.Load -> f.manager.load(id, step.url)
                        is Step.Start -> f.manager.events.onPageStarted(id, step.url)
                        is Step.Visit -> f.manager.events.onVisitedHistory(id, step.url)
                        is Step.Error -> f.manager.events.onLoadError(id, -2, "aborted", step.url)
                        is Step.Finish -> f.manager.events.onPageFinished(id, step.url)
                        is Step.Title -> f.manager.events.onTitle(id, step.text)
                        Step.WarningsOff -> {
                            gate.setEnabled(false)
                            f.manager.clearSafetyWarnings()
                        }
                        Step.Proceed -> f.manager.proceedUnsafe(id)
                    }
                }
                assertEquals(race.name, race.url, f.tab(id)?.url)
                assertEquals(race.name, race.error, f.manager.runtime(id).value.loadError != null)
                assertEquals(race.name, race.warning, f.manager.runtime(id).value.safetyWarning != null)
                assertEquals(race.name, race.title, f.tab(id)?.title.orEmpty())
                assertEquals(race.name, race.pageLoaded, f.manager.runtime(id).value.pageLoaded)
            } finally {
                f.close()
            }
        }
    }

    private sealed interface Step {
        data class Load(val url: String) : Step
        data class Start(val url: String) : Step
        data class Visit(val url: String) : Step
        data class Error(val url: String) : Step
        data class Finish(val url: String) : Step
        data class Title(val text: String) : Step
        data object WarningsOff : Step
        data object Proceed : Step
    }

    private data class Race(
        val name: String,
        val steps: List<Step>,
        val url: String?,
        val error: Boolean = false,
        val warning: Boolean = false,
        val title: String = "",
        val pageLoaded: Boolean = false,
    )

    private fun historyList(current: String, forward: String): WebBackForwardList {
        val items = listOf(historyItem(current), historyItem(forward))
        return object : WebBackForwardList() {
            override fun getCurrentItem(): WebHistoryItem = items[0]
            override fun getCurrentIndex(): Int = 0
            override fun getItemAtIndex(index: Int): WebHistoryItem? = items.getOrNull(index)
            override fun getSize(): Int = items.size
            override fun clone(): WebBackForwardList = this
        }
    }

    private fun historyItem(url: String): WebHistoryItem = object : WebHistoryItem() {
        override fun getUrl(): String = url
        override fun getOriginalUrl(): String = url
        override fun getTitle(): String = ""
        override fun getFavicon(): Bitmap? = null
        override fun clone(): WebHistoryItem = this
    }

    private fun request(url: String, mainFrame: Boolean = true): WebResourceRequest = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame(): Boolean = mainFrame
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = true
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private class RecordingSafeBrowsing : SafeBrowsingResponseCompat() {
        var proceeded: Boolean? = null
        var reported: Boolean = true
        override fun showInterstitial(allowReporting: Boolean) = Unit
        override fun proceed(report: Boolean) {
            proceeded = true
            reported = report
        }

        override fun backToSafety(report: Boolean) {
            proceeded = false
            reported = report
        }
    }

    private class MemoryGate(startEnabled: Boolean = true) : SafetyGate {
        private val session = com.dantech.skimbrowser.domain.phishing.PhishingSession()
        override var enabled: Boolean = startEnabled
            private set

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        override fun heuristic(url: String, incognito: Boolean) = session.heuristic(url, incognito, enabled)
        override fun offerSafeBrowsing(
            tabId: String,
            url: String,
            host: String,
            threat: SafeBrowsingThreat,
            incognito: Boolean,
            respond: (Boolean) -> Unit,
        ): SafetyWarning.SafeBrowsing? {
            if (!enabled || session.allowsSafeBrowsing(host, incognito)) {
                respond(true)
                return null
            }
            pending = respond
            return SafetyWarning.SafeBrowsing(url, host, threat)
        }

        private var pending: ((Boolean) -> Unit)? = null
        override fun allowHeuristic(host: String, incognito: Boolean) = session.allowHeuristic(host, incognito)
        override fun proceedSafeBrowsing(tabId: String, host: String, incognito: Boolean): Boolean {
            session.allowSafeBrowsing(host, incognito)
            val respond = pending ?: return false
            pending = null
            respond(true)
            return true
        }

        override fun cancelSafeBrowsing(tabId: String) {
            pending?.invoke(false)
            pending = null
        }

        fun allows(host: String, incognito: Boolean) = session.allowsSafeBrowsing(host, incognito)
    }

    private companion object {
        const val THREAT_PHISHING = 2
    }
}
