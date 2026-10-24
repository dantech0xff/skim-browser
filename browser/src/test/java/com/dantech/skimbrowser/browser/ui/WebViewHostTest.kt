package com.dantech.skimbrowser.browser.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.TestRenderProcessGoneDetail
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.BrowserHost
import com.dantech.skimbrowser.browser.FakeActivityHost
import com.dantech.skimbrowser.browser.FullscreenController
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.browser.SkimWebView
import com.dantech.skimbrowser.browser.SkimWebViewClient
import com.dantech.skimbrowser.browser.TabManagerFixture
import com.dantech.skimbrowser.browser.TestBrowserParts
import com.dantech.skimbrowser.browser.WebViewPool
import com.dantech.skimbrowser.browser.WebViewTimers
import com.dantech.skimbrowser.browser.WindowSecureController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WebViewHostTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val fixtures = mutableListOf<TabManagerFixture>()
    private val pools = mutableListOf<WebViewPool>()
    private lateinit var parts: TestBrowserParts

    @After
    fun tearDown() = runBlocking {
        compose.activityRule.scenario.onActivity { pools.forEach { it.destroyAll() } }
        fixtures.forEach { it.close() }
    }

    @Test
    fun theTabsWebViewShowsResumedAndLeavesPausedWithoutBeingDestroyed() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        var shown by mutableStateOf(true)
        show(pool) { if (shown) WebViewHost(id, Modifier.fillMaxSize()) }

        val view = webViewsInWindow().single()
        assertSame("the pool's WebView of the tab", view, pool.obtain(f.tab(id)!!))
        assertTrue(shadowOf(view).wasOnResumeCalled())

        shown = false
        compose.waitForIdle()

        assertTrue(webViewsInWindow().isEmpty())
        assertTrue(shadowOf(view).wasOnPauseCalled())
        assertFalse("the pool keeps it alive", shadowOf(view).wasDestroyCalled())
        assertSame(view, pool.obtain(f.tab(id)!!))
    }

    @Test
    fun theTabsWebViewFillsItsHostSoPagesSizedToTheScreenKeepTheirHeight() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        show(pool) { WebViewHost(id, Modifier.fillMaxSize()) }

        val params = webViewsInWindow().single().layoutParams

        // AndroidView adds its view as wrap_content, which makes WebView lay pages out 0 pixels high.
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height)
    }

    @Test
    fun aWebViewThatLostItsRendererIsReplacedInTheHost() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        show(pool) { WebViewHost(id, Modifier.fillMaxSize()) }
        val first = webViewsInWindow().single()

        loseRenderer(first)
        compose.waitForIdle()

        val second = webViewsInWindow().single()
        assertNotSame(first, second)
        assertTrue(shadowOf(first).wasDestroyCalled())
        assertFalse(first.isAttachedToWindow)
        runCurrent()
        assertEquals("the new WebView loads the tab again", PAGE, shadowOf(second).lastLoadedUrl)
    }

    @Test
    fun aTabThatCrashedTooOftenShowsNothingUntilTryAgain() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        show(pool) { WebViewHost(id, Modifier.fillMaxSize()) }

        repeat(3) {
            loseRenderer(webViewsInWindow().single())
            compose.waitForIdle()
        }

        assertTrue("the error screen above shows instead", webViewsInWindow().isEmpty())
        f.manager.reload(id)
        compose.waitForIdle()
        assertEquals(1, webViewsInWindow().size)
    }

    private fun TestScope.attached(): Pair<TabManagerFixture, WebViewPool> {
        val f = TabManagerFixture(this, folder.root).also { fixtures += it }
        val activity = compose.activity
        val activityHost = FakeActivityHost()
        parts = TestBrowserParts(f, this, activity, activityHost)
        val pool = WebViewPool(
            activity = activity,
            tabs = f.manager,
            incognito = f.incognito,
            stateStore = f.stateStore,
            thumbnails = f.thumbnails,
            activityHost = activityHost,
            fullscreen = FullscreenController(activity),
            sitePermissions = parts.sitePermissions,
            downloads = parts.downloads,
            isLowRamDevice = false,
            mainDispatcher = f.main,
            // Timers of its own, so no other test's pools count as started.
            timers = WebViewTimers(),
            drawThumbnail = { null },
        ).also { pools += it }
        f.manager.attach(pool)
        return f to pool
    }

    private fun show(pool: WebViewPool, content: @Composable () -> Unit) {
        val activity = compose.activity
        val host = BrowserHost(
            pool,
            parts.sitePermissions,
            parts.downloads,
            parts.blob,
            FakeActivityHost(),
            FullscreenController(activity),
            WindowSecureController(activity),
        )
        compose.setContent { CompositionLocalProvider(LocalBrowserHost provides host) { content() } }
        compose.waitForIdle()
    }

    private fun loseRenderer(view: SkimWebView) {
        val client = shadowOf(view).webViewClient as SkimWebViewClient
        client.onRenderProcessGone(view, TestRenderProcessGoneDetail(crashed = true))
    }

    /** The tabs' WebViews attached anywhere in the Activity's window. */
    private fun webViewsInWindow(): List<SkimWebView> {
        val found = mutableListOf<SkimWebView>()
        fun visit(view: View) {
            if (view is SkimWebView) found += view
            if (view is ViewGroup) (0 until view.childCount).forEach { visit(view.getChildAt(it)) }
        }
        visit(compose.activity.window.decorView)
        return found
    }

    private companion object {
        const val PAGE = "https://daylightjournal.com/energy/heat-pumps-cold"
    }
}
