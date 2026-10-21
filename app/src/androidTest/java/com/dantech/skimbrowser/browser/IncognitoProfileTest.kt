package com.dantech.skimbrowser.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Incognito on the device's WebView profiles: what a session's tabs share and keep from normal
 * tabs, which profile every WebView of a session gets, and what is left once the session ends.
 */
@RunWith(AndroidJUnit4::class)
class IncognitoProfileTest {

    @get:Rule
    val rig = BrowserRig()

    @Before
    fun profilesAreSupported() {
        // Skipped only where the WebView has no profiles; IncognitoUnavailableTest covers that.
        WebViewSupport.assumeProfiles()
    }

    @Test
    fun normalAndIncognitoTabsNeverSeeEachOthersCookies() {
        val setNormal = rig.pages.url("/set-cookie?value=normal")
        val setIncognito = rig.pages.url("/set-cookie?value=incognito")
        val check = rig.pages.url("/cookie-check")
        rig.showCurrentTab()
        val normal = rig.newTab(setNormal)
        rig.awaitLoaded(normal, setNormal)

        val incognito = rig.newTab(check, incognito = true)
        rig.awaitLoaded(incognito, check)
        assertNull("the incognito tab sent none of the normal tab's cookies", lastCookieHeader("/cookie-check"))
        rig.loadIn(incognito, setIncognito)

        rig.loadIn(normal, check)
        assertEquals("skim=normal", lastCookieHeader("/cookie-check"))
        rig.loadIn(incognito, check)
        assertEquals("skim=incognito", lastCookieHeader("/cookie-check"))

        val profile = sessionProfile()
        assertEquals("skim=normal", onMain { CookieManager.getInstance().getCookie(check) })
        assertEquals("skim=incognito", onMain { profileStore().getProfile(profile)!!.cookieManager.getCookie(check) })
        assertEquals(profile, onMain { WebViewCompat.getProfile(rig.webViewOf(incognito)).name })
        assertEquals(Profile.DEFAULT_PROFILE_NAME, onMain { WebViewCompat.getProfile(rig.webViewOf(normal)).name })
    }

    @Test
    fun aWindowOpenedByAnIncognitoPageIsInTheSessionsProfile() {
        val opener = rig.newTab(rig.pages.url("/opener.html"), incognito = true)
        rig.showCurrentTab()
        rig.awaitLoaded(opener)
        val profile = sessionProfile()

        Finger.tap(rig.shownWebView())
        val popup = awaitValue(rig.tabs.state, "the popup tab") { state -> state.tabs.any { it.openerTabId == opener } }
            .tabs.single { it.openerTabId == opener }
        rig.awaitLoaded(popup.id, rig.pages.url("/popup.html"))

        assertTrue(popup.incognito)
        assertEquals(profile, onMain { WebViewCompat.getProfile(rig.webViewOf(popup.id)).name })
        rig.evaluate(popup.id, "document.cookie = 'skimpopup=1; path=/'")
        val page = rig.pages.url("/popup.html")
        waitUntil("the popup's cookie in the session's store") {
            onMain { profileStore().getProfile(profile)!!.cookieManager.getCookie(page) }?.contains("skimpopup=1") == true
        }
        assertNull("and never in the normal tabs' store", onMain { CookieManager.getInstance().getCookie(page) })
    }

    @Test
    fun aTemporaryWebViewHasItsProfileBeforeItIsUsedAndIsDestroyedAfter() {
        rig.newTab(rig.pages.url("/plain.html"), incognito = true)
        val profile = sessionProfile()
        var used: WebView? = null
        var seen: String? = null

        onMain {
            rig.pool.withTemporaryWebView(profile) { view ->
                used = view
                seen = WebViewCompat.getProfile(view).name
            }
        }

        assertEquals(profile, seen)
        assertThrows("destroyed once the work is done", IllegalStateException::class.java) {
            onMain { WebViewCompat.getProfile(checkNotNull(used)) }
        }
    }

    @Test
    fun anIncognitoTabOpenedWhileTheLastOneCanStillBeUndoneStartsAnotherSession() {
        val first = rig.newTab(rig.pages.url("/set-cookie?value=earlier"), incognito = true)
        rig.showCurrentTab()
        rig.awaitLoaded(first)
        val earlier = sessionProfile()
        val token = onMain { rig.tabs.close(listOf(first)) }
        assertEquals(IncognitoSession.State.Ending(earlier, token), rig.incognito.state.value)

        val check = rig.pages.url("/cookie-check")
        val second = rig.newTab(check, incognito = true)
        val later = sessionProfile()
        assertNotEquals(earlier, later)
        onMain { rig.tabs.undoClose(token) }
        assertNull("the earlier session cannot come back", rig.tab(first))

        rig.awaitLoaded(second, check)
        assertNull("nothing of the earlier session reaches the new one", lastCookieHeader("/cookie-check"))
        assertEquals(later, onMain { WebViewCompat.getProfile(rig.webViewOf(second)).name })
        waitUntil("the earlier session's cookies to go") {
            onMain { profileStore().getProfile(earlier)!!.cookieManager.getCookie(check) } == null
        }
    }

    @Test
    fun endingTheSessionLeavesNoCookiesStorageOrCachedFilesInItsProfile() {
        val caches = File(rig.activity.cacheDir, WEBVIEW_CACHE_DIR)
        val cachesBefore = caches.list().orEmpty().toSet()
        val page = rig.pages.url("/storage.html")
        val id = rig.newTab(page, incognito = true)
        rig.showCurrentTab()
        rig.awaitLoaded(id, page)
        rig.evaluate(id, "skimWrite()")
        waitUntil("the page to store its data") { jsValue(rig.evaluate(id, "window.skimStorage")) == "written" }
        val profile = sessionProfile()
        val profileCache = caches.listFiles().orEmpty().single { it.name !in cachesBefore && File(it, HTTP_CACHE_DIR).isDirectory }
        waitUntil("the page's resources in the profile's HTTP cache") { cachedEntries(profileCache).isNotEmpty() }
        assertTrue(onMain { profileStore().getProfile(profile)!!.cookieManager.getCookie(page) }.orEmpty().contains("skimstore=kept"))
        if (rig.incognito.hasHttpCacheManager) {
            val cache = onMain { profileStore().getProfile(profile)!!.httpCache }
            assertTrue("the cache is held to its smallest size", onMain { !cache.isUsingDefaultQuota && cache.quotaBytes < cache.defaultQuotaBytes })
        }

        onMain { rig.tabs.finalizeClose(rig.tabs.close(listOf(id))) }

        assertEquals(IncognitoSession.State.None, rig.incognito.state.value)
        waitUntil("the profile's HTTP cache to be emptied") { cachedEntries(profileCache).isEmpty() }
        waitUntil("the profile's cookies to go") { onMain { profileStore().getProfile(profile)!!.cookieManager.getCookie(page) } == null }
        val left = readSiteData(profile, page)
        assertEquals("cookie", "", left.getString("cookie"))
        assertTrue("local storage: $left", left.isNull("local"))
        assertTrue("IndexedDB: $left", left.isNull("idb"))
        assertEquals("service workers: $left", 0, left.getInt("workers"))
    }

    private fun sessionProfile(): String = when (val session = rig.incognito.state.value) {
        is IncognitoSession.State.Active -> session.profile
        else -> throw AssertionError("No active incognito session: $session")
    }

    private fun profileStore(): ProfileStore = ProfileStore.getInstance()

    private fun lastCookieHeader(path: String): String? = rig.pages.requests(path).last().getHeader("Cookie")

    /** The HTTP cache entries under a profile's cache directory: files besides the cache's own index. */
    private fun cachedEntries(profileCache: File): List<File> =
        profileCache.walkTopDown().filter { it.isFile && CACHE_ENTRY.matches(it.name) }.toList()

    /**
     * What a page of [url]'s site finds of its earlier data in [profile]: a new WebView of that
     * profile loads the page and reads it back, and the data that reading made is cleared again.
     */
    // Only the fixture's script runs in this WebView.
    @SuppressLint("SetJavaScriptEnabled")
    private fun readSiteData(profile: String, url: String): JSONObject {
        var finished = false
        val view = onMain {
            WebView(rig.activity).apply {
                WebViewCompat.setProfile(this, profile)
                // As the tabs' WebViews have them, so the page reaches the same stores.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        finished = true
                    }
                }
                rig.activity.addContentView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                loadUrl(url)
            }
        }
        try {
            waitUntil("the read-back page to load") { onMain { finished } }
            rig.evaluateIn(view, "skimRead()")
            var found: String? = null
            waitUntil("the read-back to finish") {
                found = jsValue(rig.evaluateIn(view, "window.skimStorage")) as? String
                found != null
            }
            val text = checkNotNull(found)
            check(text.startsWith("{")) { "The read-back failed: $text" }
            return JSONObject(text)
        } finally {
            onMain {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                rig.pool.withTemporaryWebView(profile) { it.clearCache(true) }
                profileStore().getProfile(profile)?.apply {
                    cookieManager.removeAllCookies(null)
                    webStorage.deleteAllData()
                }
            }
        }
    }

    private companion object {
        /** Where WebView keeps each profile's HTTP cache, one directory per profile. */
        const val WEBVIEW_CACHE_DIR = "WebView"
        const val HTTP_CACHE_DIR = "HTTP Cache"

        /** A Chromium simple-cache entry file, such as `ea60897c371adaaa_0`. */
        val CACHE_ENTRY = Regex("[0-9a-f]{16}_[0-9s]")
    }
}
