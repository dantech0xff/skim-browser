package com.dantech.skimbrowser.browser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.Manifest
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.skimbrowser.browser.permissions.PendingSitePermission
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.domain.permission.SitePermission
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Site permissions on the device's WebView: real `getUserMedia` and geolocation requests from
 * pages served on `http://127.0.0.1` and `http://localhost` (secure contexts), through Skim's
 * request ([SitePermissionBridge.pending]) to the page's promise. Android's runtime permissions
 * are granted first, so only Skim's own decisions are at play.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SitePermissionDeviceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rig = BrowserRig()

    private val bridge: SitePermissionBridge get() = rig.host.sitePermissions

    @Before
    fun grantRuntimePermissions() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        RUNTIME_PERMISSIONS.forEach { shellAutomation().grantRuntimePermission(app, it) }
    }

    @Test
    fun anAllowThatIsRememberedGrantsThePageAndAnswersItsNextRequestWithoutAsking() {
        val id = showMediaPage()

        rig.evaluate(id, "skimAskMedia()")

        val pending = awaitRequest()
        assertEquals(PendingSitePermission(id, PAGE_HOST, MEDIA, incognito = false), pending)
        onMain { bridge.allow(pending, remember = true) }
        assertEquals(GRANTED, answer(id))
        awaitStored(PAGE_HOST) { it.camera == PermissionState.ALLOWED && it.microphone == PermissionState.ALLOWED }

        rig.evaluate(id, "skimStopMedia()")
        val (again, shown) = shownWhile {
            rig.evaluate(id, "skimAskMedia()")
            answer(id)
        }
        assertEquals("granted again", GRANTED, again)
        assertTrue("without asking: $shown", shown.isEmpty())
        rig.evaluate(id, "skimStopMedia()")
    }

    @Test
    fun aBlockedSiteIsDeniedAndNeverAskedAgain() {
        val id = showMediaPage()
        rig.evaluate(id, "skimAskMedia()")
        val pending = awaitRequest()

        onMain { bridge.block(pending) }

        assertEquals(DENIED, answer(id))
        awaitStored(PAGE_HOST) { it.camera == PermissionState.BLOCKED && it.microphone == PermissionState.BLOCKED }
        val (again, shown) = shownWhile {
            rig.evaluate(id, "skimAskMedia()")
            answer(id)
        }
        assertEquals(DENIED, again)
        assertTrue("without asking: $shown", shown.isEmpty())
    }

    @Test
    fun aDismissedRequestIsDeniedThisTimeOnlyAndAskedAgainNextTime() {
        val id = showMediaPage()
        rig.evaluate(id, "skimAskMedia()")
        val pending = awaitRequest()

        onMain { bridge.dismiss(pending) }

        assertEquals(DENIED, answer(id))
        assertNull("nothing kept for the site", runBlocking { rig.access.sitePermissionRepository().get(PAGE_HOST) })
        rig.evaluate(id, "skimAskMedia()")
        val next = awaitRequest()
        assertEquals(PendingSitePermission(id, PAGE_HOST, MEDIA, incognito = false), next)
        onMain { bridge.dismiss(next) }
        assertEquals(DENIED, answer(id))
    }

    @Test
    fun aFrameOfAnotherSiteGetsNoneOfThatSitesDecisionsAndTheRequestNamesThePage() {
        runBlocking {
            MEDIA.forEach { rig.access.sitePermissionRepository().setPermission(TestPages.LOCALHOST, it, PermissionState.ALLOWED) }
        }
        rig.showCurrentTab()
        // On a page of its own the other site has its decision.
        val own = rig.newTab(rig.pages.url("/media.html", TestPages.LOCALHOST))
        rig.awaitLoaded(own)
        val (granted, askedOnItsOwnPage) = shownWhile {
            rig.evaluate(own, "skimAskMedia()")
            answer(own)
        }
        assertEquals(GRANTED, granted)
        assertTrue(askedOnItsOwnPage.isEmpty())
        rig.evaluate(own, "skimStopMedia()")

        // Embedded in this site's page, it asks as this page, which has no decision.
        val frame = rig.pages.url("/media.html?ask", TestPages.LOCALHOST)
        val embedding = rig.newTab(rig.pages.url("/frame.html?src=" + Uri.encode(frame)))
        val pending = awaitRequest()
        assertEquals(PendingSitePermission(embedding, PAGE_HOST, MEDIA, incognito = false), pending)
        onMain { bridge.dismiss(pending) }
        waitUntil("the frame's answer") { jsValue(rig.evaluate(embedding, "window.skimMedia")) == DENIED }
    }

    @Test
    fun anIncognitoTabsDecisionsLiveOnlyInItsSessionAndGoWithIt() {
        WebViewSupport.assumeProfiles()
        val id = showMediaPage(incognito = true)
        rig.evaluate(id, "skimAskMedia()")

        val pending = awaitRequest()
        assertEquals("the incognito tab's own client asks", PendingSitePermission(id, PAGE_HOST, MEDIA, incognito = true), pending)
        onMain { bridge.allow(pending, remember = true) }

        assertEquals(GRANTED, answer(id))
        assertEquals(PermissionState.ALLOWED, rig.incognito.decision(PAGE_HOST, SitePermissionKind.CAMERA))
        assertEquals(PermissionState.ALLOWED, rig.incognito.decision(PAGE_HOST, SitePermissionKind.MICROPHONE))
        rig.evaluate(id, "skimStopMedia()")
        val (again, shown) = shownWhile {
            rig.evaluate(id, "skimAskMedia()")
            answer(id)
        }
        assertEquals(GRANTED, again)
        assertTrue(shown.isEmpty())
        rig.evaluate(id, "skimStopMedia()")
        assertTrue("never in the normal tabs' decisions", holdsFor(WAIT_MS) { runBlocking { rig.access.sitePermissionRepository().get(PAGE_HOST) } == null })

        onMain { rig.tabs.finalizeClose(rig.tabs.close(listOf(id))) }
        assertEquals(IncognitoSession.State.None, rig.incognito.state.value)
        assertEquals(PermissionState.UNSET, rig.incognito.decision(PAGE_HOST, SitePermissionKind.CAMERA))
        val next = showMediaPage(incognito = true)
        rig.evaluate(next, "skimAskMedia()")
        val asked = awaitRequest()
        assertEquals("a new session asks again", PendingSitePermission(next, PAGE_HOST, MEDIA, incognito = true), asked)
        onMain { bridge.dismiss(asked) }
        assertEquals(DENIED, answer(next))
    }

    @Test
    fun locationForAnHttpPageIsDeniedAtOnceWithoutAsking() {
        val id = showMediaPage()

        val (location, shown) = shownWhile {
            rig.evaluate(id, "skimAskLocation()")
            answer(id, "skimLocation")
        }

        assertEquals("PERMISSION_DENIED", "denied 1", location)
        assertTrue(shown.isEmpty())
    }

    @Test
    fun aRequestOfATabInTheBackgroundWaitsUntilThatTabIsShown() {
        rig.showCurrentTab()
        val shownTab = rig.newTab(rig.pages.url("/plain.html"))
        rig.awaitLoaded(shownTab)
        val background = rig.newTab(rig.pages.url("/media.html"), background = true)
        assertTrue(runBlocking { rig.tabs.awaitPageReady(background) })

        rig.evaluate(background, "skimAskMedia()")

        assertTrue("nobody is asked about a tab that is not shown", holdsFor(WAIT_MS) { bridge.pending.value == null })
        assertEquals("waiting", jsValue(rig.evaluate(background, "window.skimMedia")))
        onMain { rig.tabs.select(background) }
        val pending = awaitRequest()
        assertEquals(PendingSitePermission(background, PAGE_HOST, MEDIA, incognito = false), pending)
        onMain { bridge.allow(pending, remember = false) }
        assertEquals(GRANTED, answer(background))
        rig.evaluate(background, "skimStopMedia()")
    }

    /** Shows the media page in a new current tab and returns the tab. */
    private fun showMediaPage(incognito: Boolean = false): String {
        rig.showCurrentTab()
        val url = rig.pages.url("/media.html")
        val id = rig.newTab(url, incognito = incognito)
        rig.awaitLoaded(id, url)
        return id
    }

    /** The request put to the user, once there is one. */
    private fun awaitRequest(): PendingSitePermission = checkNotNull(awaitValue(bridge.pending, "a request for the user") { it != null })

    /** The page's answer in `window.[name]`, once it is no longer waiting. */
    private fun answer(tabId: String, name: String = "skimMedia"): String {
        var value: String? = null
        waitUntil("the page's answer in $name", ANSWER_TIMEOUT_MS) {
            value = jsValue(rig.evaluate(tabId, "window.$name")) as? String
            value != null && value != "waiting"
        }
        return checkNotNull(value)
    }

    /** Waits until the normal tabs' decisions for [host] satisfy [check]. */
    private fun awaitStored(host: String, check: (SitePermission) -> Boolean) {
        waitUntil("the site's decisions to be kept") {
            runBlocking { rig.access.sitePermissionRepository().get(host) }?.let(check) == true
        }
    }

    /** Runs [block] and returns its result with every request put to the user meanwhile. */
    private fun <T> shownWhile(block: () -> T): Pair<T, List<PendingSitePermission>> {
        val shown = CopyOnWriteArrayList<PendingSitePermission>()
        val watcher = CoroutineScope(Dispatchers.Default)
        watcher.launch(start = CoroutineStart.UNDISPATCHED) { bridge.pending.collect { request -> request?.let(shown::add) } }
        return try {
            block() to shown.toList()
        } finally {
            watcher.cancel()
        }
    }

    private companion object {
        const val PAGE_HOST = "127.0.0.1"
        const val GRANTED = "granted audio,video"
        const val DENIED = "denied NotAllowedError"
        const val WAIT_MS = 1_500L

        /** Opening the emulator's camera can take a while. */
        const val ANSWER_TIMEOUT_MS = 20_000L
        val MEDIA = setOf(SitePermissionKind.CAMERA, SitePermissionKind.MICROPHONE)
        val RUNTIME_PERMISSIONS = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
