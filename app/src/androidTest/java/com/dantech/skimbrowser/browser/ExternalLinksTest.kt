package com.dantech.skimbrowser.browser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import com.dantech.skimbrowser.domain.tab.PageEvent

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Links that leave the browser, on the device's WebView: a page's `tel:`, `mailto:`, `intent:`
 * links reach another app only after a tap, as a browsable intent with no launch flag and never
 * aimed at Skim; an incognito tab hands over only calls, emails and text messages; an `intent:`
 * link no app takes loads its web fallback; and `file:`, `content:` and `javascript:` addresses
 * never load in a tab. Apps are only recorded ([TestActivityHost.launched]), never started,
 * except where a launch must fail as it does in the app.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExternalLinksTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rig = BrowserRig()

    @Test
    fun callAndEmailLinksOpenTheirAppOnlyAfterATap() {
        rig.showCurrentTab()
        listOf("tel:+15551234", "mailto:ana@example.com").forEachIndexed { index, link ->
            val (id, view) = showLink(link)

            followWithoutATap(id)
            assertTrue("$link: no app opens without a tap", holdsFor(NOTHING_MS) { rig.activityHost.launched.size == index })

            Finger.tap(view)
            waitUntil("$link: its app to be asked for") { rig.activityHost.launched.size == index + 1 }
            val intent = rig.activityHost.launched.last()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(Uri.parse(link), intent.data)
            assertSafe(intent)
            assertEquals("the page stays", linkPage(link), rig.runtime(id).committedUrl)
        }
    }

    @Test
    fun anIntentLinkLosesItsLaunchFlagsAndComponentAndCannotAimAtSkim() {
        rig.showCurrentTab()
        val (id, view) = showLink(OTHER_APP_INTENT)
        followWithoutATap(id)
        assertTrue("no app opens without a tap", holdsFor(NOTHING_MS) { rig.activityHost.launched.isEmpty() })

        Finger.tap(view)

        waitUntil("the other app to be asked for") { rig.activityHost.launched.size == 1 }
        val intent = rig.activityHost.launched.single()
        assertEquals("com.example.skimtest", intent.`package`)
        assertEquals(Uri.parse("skimtest://skim.example/open"), intent.data)
        assertSafe(intent)

        val (_, aimedAtSkim) = showLink("intent://skim.example/open#Intent;scheme=skimtest;package=com.dantech.skimbrowser;end")
        Finger.tap(aimedAtSkim)
        assertTrue("a link aimed at Skim is refused", holdsFor(NOTHING_MS) { rig.activityHost.launched.size == 1 })
    }

    @Test
    fun anIncognitoTabHandsOnlyCallsEmailsAndTextMessagesToOtherApps() {
        WebViewSupport.assumeProfiles()
        val handed = listOf("tel:+15551234", "mailto:ana@example.com", "sms:+15551234", "smsto:+15551234")
        val kept = listOf(OTHER_APP_INTENT, "market://details?id=com.example.skimtest", "geo:0,0?q=harbour")
        rig.showCurrentTab()
        val id = rig.newTab(rig.pages.url("/plain.html"), incognito = true)
        rig.awaitLoaded(id)

        handed.forEachIndexed { index, link ->
            tapLinkIn(id, link)
            waitUntil("$link to be handed over") { rig.activityHost.launched.size == index + 1 }
            assertEquals(Uri.parse(link), rig.activityHost.launched.last().data)
            assertSafe(rig.activityHost.launched.last())
        }
        kept.forEach { link ->
            tapLinkIn(id, link)
            assertTrue("$link stays in the incognito tab", holdsFor(NOTHING_MS) { rig.activityHost.launched.size == handed.size })
        }
    }

    @Test
    fun anIntentLinkThatNoAppTakesLoadsItsWebFallbackAndNoOtherKind() {
        // Launched for real, as the app's own host does, so the missing app fails as it would there.
        rig.activityHost.startExternalWith = { intent -> rig.activity.startActivity(intent) }
        rig.showCurrentTab()
        val fallback = rig.pages.url("/fallback.html")
        val (id, view) = showLink("$MISSING_APP_INTENT;S.browser_fallback_url=${Uri.encode(fallback)};end")

        Finger.tap(view)

        rig.awaitLoaded(id, fallback)
        assertEquals("the app was asked for first", 1, rig.activityHost.launched.size)

        val local = "$MISSING_APP_INTENT;S.browser_fallback_url=${Uri.encode("file:///sdcard/Download/page.html")};end"
        val (other, otherView) = showLink(local)
        Finger.tap(otherView)
        waitUntil("the app to be asked for") { rig.activityHost.launched.size == 2 }
        assertTrue("a fallback that is not a web page never loads", holdsFor(NOTHING_MS) { rig.runtime(other).committedUrl == linkPage(local) })
    }

    @Test
    fun fileContentAndJavascriptAddressesNeverLoadInATab() {
        rig.showCurrentTab()
        listOf("file:///sdcard/Download/page.html", "content://com.dantech.skimbrowser.fileprovider/shared/page.html").forEach { link ->
            val (id, view) = showLink(link)
            Finger.tap(view)
            assertTrue("$link: the tab stays on its page", holdsFor(NOTHING_MS) { rig.runtime(id).committedUrl == linkPage(link) })
            assertTrue("$link: nothing started loading it", rig.pageEvents.none { it is PageEvent.PageStarted && it.url.startsWith(link.substringBefore(':')) })
        }
        assertTrue("and no app was asked for them", rig.activityHost.launched.isEmpty())

        val (id, _) = showLink("tel:+15551234")
        onMain { rig.tabs.load(id, "javascript:window.skimRan = true") }
        assertTrue("a javascript: address never loads in a tab", holdsFor(NOTHING_MS) { jsValue(rig.evaluate(id, "window.skimRan === true")) == false })
        assertEquals(linkPage("tel:+15551234"), rig.runtime(id).committedUrl)
        val opened = rig.newTab("javascript:window.skimRan = true")
        assertNull("a new tab given one shows the New tab page", rig.tab(opened)?.url)
    }

    /** Opens the link page for [link] in a new current tab and returns the tab and its WebView on screen. */
    private fun showLink(link: String): Pair<String, SkimWebView> {
        val url = linkPage(link)
        val before = rig.webViewsInWindow().singleOrNull()
        val id = rig.newTab(url)
        rig.awaitLoaded(id, url)
        return id to rig.shownWebView(other = before)
    }

    /** Loads the link page for [link] in [tabId], the tab on screen, and taps its link. */
    private fun tapLinkIn(tabId: String, link: String) {
        rig.loadIn(tabId, linkPage(link))
        Finger.tap(rig.shownWebView())
    }

    /** The page follows its link from script, as a page may try at any time: no tap since it loaded. */
    private fun followWithoutATap(tabId: String) {
        rig.evaluate(tabId, "location.href = document.getElementById('link').href")
    }

    private fun linkPage(link: String): String = rig.pages.url("/link.html?href=" + Uri.encode(link))

    /** Only an activity that accepts browsable intents may get it, picked by Android, with no launch flag. */
    private fun assertSafe(intent: Intent) {
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("no launch flag", 0, intent.flags)
        assertNull(intent.component)
        assertNull(intent.selector)
    }

    private companion object {
        /** Long enough for a navigation or an app launch to have shown if it was going to. */
        const val NOTHING_MS = 1_500L

        /** An `intent:` link with every launch flag and component a page might try to set. */
        const val OTHER_APP_INTENT =
            "intent://skim.example/open#Intent;scheme=skimtest;package=com.example.skimtest;component=com.example.skimtest/.Main;launchFlags=0x10008000;end"

        /** An `intent:` link for an app that is not installed, without its end, so a fallback can be added. */
        const val MISSING_APP_INTENT = "intent://skim.example/open#Intent;scheme=skimtest;package=com.dantech.skimbrowser.missing"
    }
}
