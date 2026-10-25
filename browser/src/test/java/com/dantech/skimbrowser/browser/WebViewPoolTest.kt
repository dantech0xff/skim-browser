package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.tab.LoadError

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.TestRenderProcessGoneDetail
import android.webkit.TestWebResourceError
import android.webkit.GeolocationPermissions
import android.webkit.TestWebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * The pool over the real tab manager and stores, with Robolectric's WebView: what it keeps alive,
 * when it saves, restores and destroys, and what it answers. What only Chromium does (loading
 * pages, restoring a real back history, profiles) is covered by the device tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebViewPoolTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity = activityController.get()
    private val host = FakeActivityHost()
    private val fullscreen = FullscreenController(activity)
    private val fixtures = mutableListOf<TabManagerFixture>()
    private val pools = mutableListOf<WebViewPool>()
    private val secondActivities = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()

    /** The WebViews whose thumbnails were drawn: Robolectric's WebView has no size to draw. */
    private val drawn = mutableListOf<View>()

    private lateinit var parts: TestBrowserParts

    @After
    fun tearDown() = runBlocking {
        pools.forEach { it.destroyAll() }
        fixtures.forEach { it.close() }
        secondActivities.forEach { it.pause().stop().destroy() }
        activityController.pause().stop().destroy()
        Unit
    }

    // Restoring

    @Test
    fun aTabWithoutSavedStateLoadsItsPageOnceTheStateWasLookedFor() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)

        val view = pool.obtain(f.tab(id)!!)

        assertNull("nothing loads before the saved state is read", shadowOf(view).lastLoadedUrl)
        runCurrent()
        assertEquals(PAGE, shadowOf(view).lastLoadedUrl)
        assertSame("the same WebView while it lives", view, pool.obtain(f.tab(id)!!))
    }

    @Test
    fun aTabOnTheNewTabPageLoadsNothing() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false)

        val view = pool.obtain(f.tab(id)!!)
        runCurrent()

        assertNull(shadowOf(view).lastLoadedUrl)
    }

    @Test
    fun aSavedStateComesBackBeforeALoadAskedForMeanwhile() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = OTHER)
        f.stateStore.save(id, historyOf(PAGE, OTHER), incognito = false)

        val view = pool.obtain(f.tab(id)!!)
        f.manager.load(id, NEXT)
        assertNull("the load waits for the restore", shadowOf(view).lastLoadedUrl)
        runCurrent()

        assertEquals(listOf(PAGE, OTHER), historyOf(view))
        assertEquals(NEXT, shadowOf(view).lastLoadedUrl)
    }

    @Test
    fun aRestoredTabShowsItsRestoredPageNotItsUrlAgain() = runTest {
        val (f, pool) = attached()
        // The tab's stored URL may lag behind its history; the restored history decides what shows.
        val id = f.manager.newTab(incognito = false, url = NEXT)
        f.stateStore.save(id, historyOf(PAGE, OTHER), incognito = false)

        val view = pool.obtain(f.tab(id)!!)
        runCurrent()

        assertEquals(listOf(PAGE, OTHER), historyOf(view))
        assertEquals("the restored entry, with no load of the tab's URL after it", OTHER, shadowOf(view).lastLoadedUrl)
    }

    @Test
    fun loadsAndReloadsForATabWithoutAWebViewReachTheNextOne() = runTest {
        val (f, pool) = attached()
        val restored = f.manager.newTab(incognito = false, url = PAGE, background = true)
        f.stateStore.save(restored, historyOf(PAGE), incognito = false)
        val fresh = f.manager.newTab(incognito = false, url = OTHER, background = true)
        val moved = f.manager.newTab(incognito = false, url = PAGE, background = true)

        f.manager.reload(restored)
        f.manager.reload(fresh)
        f.manager.load(moved, NEXT)
        val restoredView = pool.obtain(f.tab(restored)!!)
        val freshView = pool.obtain(f.tab(fresh)!!)
        val movedView = pool.obtain(f.tab(moved)!!)
        runCurrent()

        assertEquals("the restored page reloads", 1, shadowOf(restoredView).reloadInvocations)
        assertEquals("a page loaded afresh needs no reload", 0, shadowOf(freshView).reloadInvocations)
        assertEquals(OTHER, shadowOf(freshView).lastLoadedUrl)
        assertEquals(NEXT, shadowOf(movedView).lastLoadedUrl)
    }

    @Test
    fun goingBackWhileTheHistoryIsStillRestoringWaitsForIt() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = OTHER)
        f.stateStore.save(id, historyOf(PAGE, OTHER), incognito = false)
        val view = pool.obtain(f.tab(id)!!)

        assertTrue("the tab is not left for want of a history", f.manager.goBack(id))
        assertEquals(0, shadowOf(view).goBackInvocations)
        runCurrent()

        assertEquals(1, shadowOf(view).goBackInvocations)
        assertFalse("nothing more to go back to", f.manager.goBack(id))
    }

    @Test
    fun aStateThatCannotBeRestoredIsDeletedAndThePageLoads() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        f.stateStore.save(id, Bundle().apply { putString("not", "a webview state") }, incognito = false)

        val view = pool.obtain(f.tab(id)!!)
        runCurrent()

        assertEquals(PAGE, shadowOf(view).lastLoadedUrl)
        assertNull(f.stateStore.load(id, incognito = false))
    }

    // Keeping WebViews alive

    @Test
    fun theFourthWebViewPushesOutTheLeastRecentlyUsedButNeverTheCurrentTab() = runTest {
        val (f, pool) = attached()
        val ids = (1..4).map { f.manager.newTab(incognito = false, url = "https://site$it.example/", background = true) }
        assertEquals("the first tab is current", ids[0], f.manager.state.value.currentTabId)
        val views = ids.take(3).map { id -> livePage(f, pool, id) }
        val generation = pool.generation(ids[1])
        val before = generation.value

        livePage(f, pool, ids[3])

        assertFalse("the current tab stays though it is the oldest", shadowOf(views[0]).wasDestroyCalled())
        assertTrue(shadowOf(views[1]).wasDestroyCalled())
        assertFalse(shadowOf(views[2]).wasDestroyCalled())
        assertEquals("its state was handed over first", listOf("https://site2.example/"), historyOf(f.stateStore.load(ids[1], false)!!))
        assertNotEquals("its host builds again", before, generation.value)
        assertNotSame(views[1], pool.obtain(f.tab(ids[1])!!))
    }

    @Test
    fun usingAWebViewAgainKeepsItAlive() = runTest {
        val (f, pool) = attached()
        val ids = (1..4).map { f.manager.newTab(incognito = false, url = "https://site$it.example/", background = true) }
        val views = ids.take(3).map { id -> livePage(f, pool, id) }

        pool.obtain(f.tab(ids[1])!!)
        livePage(f, pool, ids[3])

        assertFalse(shadowOf(views[1]).wasDestroyCalled())
        assertTrue(shadowOf(views[2]).wasDestroyCalled())
    }

    @Test
    fun aPopupAndItsOpenerOutliveTheCapacityUntilThePopupCloses() = runTest {
        val (f, pool) = attached()
        val opener = f.manager.newTab(incognito = false, url = PAGE)
        val openerView = livePage(f, pool, opener)
        val popup = f.manager.events.onPopupRequested(opener)!!
        val popupView = pool.obtainForPopup(popup)!!
        runCurrent()
        assertNull("a popup's content comes from its opener", shadowOf(popupView).lastLoadedUrl)

        val others = (3..5).map { n -> livePage(f, pool, f.manager.newTab(incognito = false, url = "https://site$n.example/")) }

        assertFalse(shadowOf(openerView).wasDestroyCalled())
        assertFalse(shadowOf(popupView).wasDestroyCalled())
        assertEquals(listOf(true, true, false), others.map { shadowOf(it).wasDestroyCalled() })

        f.manager.events.onPopupClosed(popup)
        assertTrue(shadowOf(popupView).wasDestroyCalled())
        livePage(f, pool, f.manager.newTab(incognito = false, url = "https://site6.example/"))
        livePage(f, pool, f.manager.newTab(incognito = false, url = "https://site7.example/"))
        assertTrue("an opener without a popup is like any tab", shadowOf(openerView).wasDestroyCalled())
    }

    @Test
    fun beingInTheBackgroundOnlySavesStateWhileACriticalWarningLeavesTheCurrentTab() = runTest {
        val (f, pool) = attached()
        val ids = (1..3).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        val views = ids.map { id -> livePage(f, pool, id) }

        pool.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        runCurrent()

        assertEquals(listOf(false, false, false), views.map { shadowOf(it).wasDestroyCalled() })
        ids.forEach { id -> assertNotNull("state of $id", f.stateStore.load(id, false)) }

        @Suppress("DEPRECATION")
        pool.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        assertEquals(listOf(true, true, false), views.map { shadowOf(it).wasDestroyCalled() })

        pool.onStart()
        val more = (4..6).map { n -> livePage(f, pool, f.manager.newTab(incognito = false, url = "https://site$n.example/")) }
        assertEquals("three again once the Activity starts", listOf(true, false, false), (listOf(views[2]) + more.dropLast(1)).map { shadowOf(it).wasDestroyCalled() })
    }

    @Test
    fun aLowRamDeviceKeepsOneWebViewBesideTheCurrentTab() = runTest {
        val (f, pool) = attached(lowRam = true)
        val current = f.manager.newTab(incognito = false, url = PAGE)
        val currentView = livePage(f, pool, current)
        val background = f.manager.newTab(incognito = false, url = OTHER, background = true)
        val backgroundView = livePage(f, pool, background)

        val another = livePage(f, pool, f.manager.newTab(incognito = false, url = NEXT, background = true))

        assertFalse(shadowOf(currentView).wasDestroyCalled())
        assertTrue(shadowOf(backgroundView).wasDestroyCalled())
        assertFalse("the WebView just made stays", shadowOf(another).wasDestroyCalled())
    }

    // Saving

    @Test
    fun aFinishedPageGetsItsThumbnailThenItsState() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = shownPage(f, pool, id)

        finishLoading(view, PAGE)
        advanceTimeBy(CAPTURE_DELAY_MS - 1)
        assertNull(f.thumbnails.observe(id).value)
        advanceTimeBy(2)
        assertNotNull("the thumbnail, half a second after", f.thumbnails.observe(id).value)
        assertEquals(listOf<View>(view), drawn)
        assertNull(f.stateStore.load(id, false))

        advanceTimeBy(SAVE_DELAY_MS)
        assertEquals("the state, a second after", listOf(PAGE), historyOf(f.stateStore.load(id, false)!!))
    }

    @Test
    fun aPageThatFailedToLoadGetsNoThumbnail() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = shownPage(f, pool, id)

        client(view).onPageStarted(view, PAGE, null)
        client(view).onReceivedError(
            view,
            TestWebResourceRequest(PAGE, mainFrame = true),
            TestWebResourceError(WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED"),
        )
        chrome(view).onProgressChanged(view, 100)
        client(view).onPageFinished(view, PAGE)
        advanceTimeBy(SAVE_DELAY_MS * 2)

        assertTrue("the error screen is drawn over it, not in it", drawn.isEmpty())
        assertNull(f.thumbnails.observe(id).value)
    }

    @Test
    fun aLoadedPageLeavingItsHostOrItsWebViewLeavesItsThumbnail() = runTest {
        val (f, pool) = attached()
        val shown = f.manager.newTab(incognito = false, url = PAGE)
        val shownView = shownPage(f, pool, shown)
        finishLoading(shownView, PAGE)

        pool.releaseFromHost(shown, shownView)
        assertEquals(listOf<View>(shownView), drawn)
        assertNotNull(f.thumbnails.observe(shown).value)

        val evicted = f.manager.newTab(incognito = false, url = OTHER, background = true)
        val evictedView = livePage(f, pool, evicted)
        finishLoading(evictedView, OTHER)
        livePage(f, pool, f.manager.newTab(incognito = false, url = "https://site1.example/", background = true))
        assertFalse(shadowOf(evictedView).wasDestroyCalled())
        livePage(f, pool, f.manager.newTab(incognito = false, url = NEXT, background = true))

        assertTrue(shadowOf(evictedView).wasDestroyCalled())
        assertEquals("drawn before it went", listOf<View>(shownView, evictedView), drawn)
        assertNotNull(f.thumbnails.observe(evicted).value)
    }

    @Test
    fun aNewPageStartingCancelsTheWorkForTheLastOne() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = shownPage(f, pool, id)

        finishLoading(view, PAGE)
        client(view).onPageStarted(view, OTHER, null)
        advanceTimeBy(SAVE_DELAY_MS * 2)

        assertNull(f.thumbnails.observe(id).value)
        assertTrue(drawn.isEmpty())
        assertNull(f.stateStore.load(id, false))
    }

    @Test
    fun aClosedTabHandsOverItsStateBeforeItsWebViewGoesSoUndoRestoresIt() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = OTHER)
        val view = livePage(f, pool, id)
        shadowOf(view).pushEntryToHistory(NEXT)

        val token = f.manager.close(listOf(id))
        assertTrue(shadowOf(view).wasDestroyCalled())
        f.manager.undoClose(token)
        val again = pool.obtain(f.tab(id)!!)
        runCurrent()

        assertNotSame(view, again)
        assertEquals(listOf(OTHER, NEXT), historyOf(again))
    }

    @Test
    fun aWebViewWithoutAPageHandsOverNoState() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        f.stateStore.save(id, historyOf(PAGE, OTHER), incognito = false)
        pool.obtain(f.tab(id)!!)

        pool.saveAll()
        f.manager.close(listOf(id)).also { f.manager.undoClose(it) }
        runCurrent()

        assertEquals("the state saved before is still there", listOf(PAGE, OTHER), historyOf(f.stateStore.load(id, false)!!))
    }

    @Test
    fun stoppingSavesEveryStateAndKeepsTheWebViews() = runTest {
        val (f, pool) = attached()
        val ids = (1..2).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        val views = ids.map { id -> livePage(f, pool, id) }

        pool.onStop()
        runCurrent()

        ids.forEach { id -> assertNotNull(f.stateStore.load(id, false)) }
        assertEquals(listOf(false, false), views.map { shadowOf(it).wasDestroyCalled() })
        pool.onStart()
    }

    // Page scripts

    @Test
    fun aScriptRunsBeforeTheCallerSuspendsAndItsAnswerComesBack() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = livePage(f, pool, id)

        val answer = async(start = CoroutineStart.UNDISPATCHED) { pool.evaluate(id, "document.title") }

        assertEquals("already running", "document.title", shadowOf(view).lastEvaluatedJavascript)
        shadowOf(view).lastEvaluatedJavascriptCallback.onReceiveValue("\"Heat pumps\"")
        assertEquals("\"Heat pumps\"", answer.await())
    }

    @Test
    fun aScriptWaitingWhenTheWebViewGoesIsAnsweredWithNothing() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = livePage(f, pool, id)
        val answer = async(start = CoroutineStart.UNDISPATCHED) { pool.evaluate(id, "slow()") }

        f.manager.close(listOf(id))

        assertNull(answer.await())
        assertTrue(shadowOf(view).wasDestroyCalled())
        shadowOf(view).lastEvaluatedJavascriptCallback.onReceiveValue("late")
    }

    @Test
    fun aTabWithoutAReadyWebViewRunsNoScript() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)

        assertNull(pool.evaluate(id, "1"))
        val view = pool.obtain(f.tab(id)!!)
        assertNull("not while it restores", pool.evaluate(id, "1"))
        assertNull(shadowOf(view).lastEvaluatedJavascript)
    }

    // Renderer gone

    @Test
    fun aRendererLostTwiceRecoversAndAThirdTimeShowsTheErrorUntilTryAgain() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val generation = pool.generation(id)

        repeat(2) { round ->
            val view = livePage(f, pool, id)
            val before = generation.value
            assertTrue(client(view).onRenderProcessGone(view, TestRenderProcessGoneDetail(crashed = true)))
            assertTrue("round $round", shadowOf(view).wasDestroyCalled())
            assertNotNull("round $round: it comes back", generation.value)
            assertNotEquals(before, generation.value)
        }
        val third = livePage(f, pool, id)
        client(third).onRenderProcessGone(third, TestRenderProcessGoneDetail(crashed = true))

        assertNull("the error shows instead of a page", generation.value)
        assertTrue(f.manager.runtime(id).value.loadError is LoadError.Crashed)
        assertNull(pool.showInHost(id))
        pool.ensureWebView(id, offscreen = true)
        assertNull(pool.evaluate(id, "1"))

        f.manager.reload(id)
        assertNotNull("Try again brings the page back", generation.value)
        val again = pool.showInHost(id)!!
        runCurrent()
        assertEquals(PAGE, shadowOf(again).lastLoadedUrl)
    }

    @Test
    fun aWebViewGoingAwayEndsTheFullScreenItShows() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = livePage(f, pool, id)
        var hidden = 0
        chrome(view).onShowCustomView(View(activity), object : WebChromeClient.CustomViewCallback {
            override fun onCustomViewHidden() {
                hidden++
            }
        })
        assertTrue(fullscreen.isFullscreen.value)

        f.manager.close(listOf(id))

        assertFalse(fullscreen.isFullscreen.value)
        assertEquals("the page is told once", 1, hidden)
    }

    @Test
    fun aRendererKilledForMemoryLeavesOneWebView() = runTest {
        val (f, pool) = attached()
        val current = f.manager.newTab(incognito = false, url = PAGE)
        val killed = livePage(f, pool, f.manager.newTab(incognito = false, url = OTHER, background = true))
        val kept = livePage(f, pool, f.manager.newTab(incognito = false, url = NEXT, background = true))
        livePage(f, pool, current)

        client(killed).onRenderProcessGone(killed, TestRenderProcessGoneDetail(crashed = false))
        val newest = livePage(f, pool, f.manager.newTab(incognito = false, url = "https://site4.example/", background = true))

        assertTrue(shadowOf(kept).wasDestroyCalled())
        assertFalse(shadowOf(newest).wasDestroyCalled())
    }

    // Off screen, hosts, temporary WebViews

    @Test
    fun aTabNotShownLoadsOffScreenInTheWindow() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE, background = true)

        pool.ensureWebView(id, offscreen = true)
        val view = pool.obtain(f.tab(id)!!)
        runCurrent()

        val offscreen = view.parent as FrameLayout
        assertSame(activity.findViewById<ViewGroup>(android.R.id.content), offscreen.parent)
        assertTrue("drawn beside the screen", offscreen.translationX >= activity.resources.displayMetrics.widthPixels)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, offscreen.importantForAccessibility)
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, offscreen.descendantFocusability)
        assertEquals(PAGE, shadowOf(view).lastLoadedUrl)

        pool.ensureWebView(id, offscreen = true)
        assertSame(view, pool.obtain(f.tab(id)!!))
        assertEquals(1, offscreen.childCount)
    }

    @Test
    fun aHostResumesItsWebViewAndPausesItWhenItLetsGo() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = pool.showInHost(id)!!
        assertTrue(shadowOf(view).wasOnResumeCalled())
        val frame = FrameLayout(activity).also { it.addView(view) }

        pool.releaseFromHost(id, view)

        assertTrue(shadowOf(view).wasOnPauseCalled())
        assertNull(view.parent)
        assertEquals(0, frame.childCount)
        assertFalse("the pool still owns it", shadowOf(view).wasDestroyCalled())
    }

    @Test
    fun whileTwoHostsShowATabTheNewerOneKeepsItsWebView() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val leaving = FrameLayout(activity)
        val arriving = FrameLayout(activity)
        val view = pool.showInHost(id)!!.also { leaving.addView(it) }
        assertSame(view, pool.showInHost(id)!!.also { arriving.addView(it) })

        pool.releaseFromHost(id, view)

        assertSame("the screen that is arriving keeps it", arriving, view.parent)
        assertFalse(shadowOf(view).wasOnPauseCalled())
        pool.releaseFromHost(id, view)
        assertNull(view.parent)
        assertTrue(shadowOf(view).wasOnPauseCalled())
    }

    @Test
    fun aHostLettingGoOfAReplacedWebViewLeavesTheNewOneAlone() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val old = livePage(f, pool, id)
        client(old).onRenderProcessGone(old, TestRenderProcessGoneDetail(crashed = true))
        val replacement = livePage(f, pool, id)

        pool.releaseFromHost(id, old)

        assertFalse(shadowOf(replacement).wasOnPauseCalled())
        assertFalse(shadowOf(replacement).wasDestroyCalled())
    }

    @Test
    fun desktopSiteAppliesToTheLiveWebViewAndReloads() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = livePage(f, pool, id)

        f.manager.setDesktopSite(id, true)

        assertTrue(view.settings.useWideViewPort)
        assertEquals(1, shadowOf(view).reloadInvocations)
    }

    @Test
    fun aTemporaryWebViewIsDestroyedEvenWhenItsWorkFails() = runTest {
        val (_, pool) = attached()
        var used: WebView? = null

        assertThrows(IllegalArgumentException::class.java) {
            pool.withTemporaryWebView(profile = null) { webView ->
                used = webView
                throw IllegalArgumentException("failed")
            }
        }

        assertTrue(shadowOf(used!!).wasDestroyCalled())
    }

    @Test
    fun withoutProfilesNothingRunsOnATemporaryWebViewMeantForOne() = runTest {
        val (_, pool) = attached()
        var ran = false

        assertThrows(IllegalStateException::class.java) { pool.withTemporaryWebView(profile = "incognito-1") { ran = true } }

        assertFalse("never on the default profile instead", ran)
    }

    @Test
    fun anIncognitoTabGetsNoWebViewWithoutItsProfile() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = true, url = PAGE)

        val refused = assertThrows(IllegalStateException::class.java) { pool.obtain(f.tab(id)!!) }
        assertEquals("this WebView has no profiles, so none can be set", "WebView profiles are not supported", refused.message)
        assertNull(pool.showInHost(id))
        pool.ensureWebView(id, offscreen = true)

        assertNull(pool.evaluate(id, "1"))
        assertNull(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? FrameLayout)
    }

    @Test
    fun anIncognitoTabWhoseSessionEndedNeverGetsTheDefaultProfile() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = true, url = PAGE)
        f.incognito.end(engine = null)

        val refused = assertThrows(IllegalStateException::class.java) { pool.obtain(f.tab(id)!!) }

        assertEquals("the missing session refuses, before any profile is tried", "An incognito tab has no incognito session", refused.message)
        assertNull(pool.showInHost(id))
        pool.ensureWebView(id, offscreen = true)
        assertNull(pool.evaluate(id, "1"))
    }

    @Test
    fun destroyingThePoolEndsEverything() = runTest {
        val (f, pool) = attached()
        val ids = (1..2).map { f.manager.newTab(incognito = false, url = "https://site$it.example/") }
        val views = ids.map { id -> livePage(f, pool, id) }
        val answer = async(start = CoroutineStart.UNDISPATCHED) { pool.evaluate(ids[1], "slow()") }

        pool.destroyAll()

        assertEquals(listOf(true, true), views.map { shadowOf(it).wasDestroyCalled() })
        assertNull(answer.await())
        ids.forEach { id -> assertNotNull("saved on the way out", f.stateStore.load(id, false)) }
        assertThrows(IllegalStateException::class.java) { pool.obtain(f.tab(ids[0])!!) }
        assertNull(pool.showInHost(ids[0]))
        var ran = false
        pool.withTemporaryWebView(null) { ran = true }
        assertFalse(ran)
    }

    @Test
    fun aPagesDownloadReachesTheDownloadsAndItsListenerGoesWithTheWebView() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = pool.obtain(f.tab(id)!!) as SkimWebView
        runCurrent()
        val now = SystemClock.uptimeMillis()
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0))

        shadowOf(view).downloadListener.onDownloadStart(DOWNLOAD, USER_AGENT, "attachment; filename=\"guide.pdf\"", "application/pdf", 1_024)
        runCurrent()

        val request = parts.systemDownloads.enqueued.single()
        assertEquals(DOWNLOAD, request.url)
        assertEquals("guide.pdf", request.fileName)
        assertEquals(USER_AGENT, request.userAgent)
        f.manager.close(listOf(id))
        assertNull("no download reaches a destroyed WebView's tab", shadowOf(view).downloadListener)
    }

    @Test
    fun closingATabDeniesThePermissionRequestsItsPageLeftWaiting() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = pool.obtain(f.tab(id)!!)
        runCurrent()
        val answers = mutableListOf<Boolean>()

        chrome(view).onGeolocationPermissionsShowPrompt(
            "https://daylightjournal.com/",
            GeolocationPermissions.Callback { _, allow, _ -> answers += allow },
        )
        f.manager.close(listOf(id))

        assertEquals(listOf(false), answers)
    }

    // Two Activities at once: a new one starts before the old one stopped

    @Test
    fun anActivityThatStopsAfterANewerOneStartedLeavesItsPagesRunning() = runTest {
        val timers = WebViewTimers()
        val (f, old) = attached(timers = timers)
        val id = f.manager.newTab(incognito = false, url = PAGE)
        livePage(f, old, id)
        old.onStart()
        val newer = secondPool(f, timers)
        f.engines.attach(newer)
        newer.onStart()
        val newView = livePage(f, newer, id)
        finishLoading(newView, PAGE)
        assertTrue(f.manager.runtime(id).value.pageLoaded)

        old.onStop()
        assertFalse("the newer Activity still shows pages", timers.paused)
        old.destroyAll()
        f.engines.detach(old)

        assertTrue("the old pool does not speak for the tab any more", f.manager.runtime(id).value.pageLoaded)
        assertTrue(f.engines.isAttached(newer))
        f.manager.load(id, NEXT)
        assertEquals("commands reach the newer pool", NEXT, shadowOf(newView).lastLoadedUrl)
        newer.onStop()
        assertTrue("the last pool to stop pauses the timers", timers.paused)
        newer.onStart()
        assertFalse(timers.paused)
    }

    @Test
    fun timersPausedWhileNoPoolRanResumeWithTheNextPoolsFirstWebView() = runTest {
        val timers = WebViewTimers()
        val (f, first) = attached(timers = timers)
        val id = f.manager.newTab(incognito = false, url = PAGE)
        first.onStart()
        livePage(f, first, id)
        first.onStop()
        first.destroyAll()
        f.engines.detach(first)
        assertTrue(timers.paused)

        val next = secondPool(f, timers)
        f.engines.attach(next)
        next.onStart()
        assertTrue("nothing to resume them through yet", timers.paused)
        next.obtain(f.tab(id)!!)

        assertFalse(timers.paused)
    }

    // Page scripts and dialogs

    @Test
    fun aScriptThatNeverAnswersEndsWithNothingAfterTenSeconds() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = livePage(f, pool, id)

        val answer = async(start = CoroutineStart.UNDISPATCHED) { pool.evaluate(id, "while (true) {}") }
        advanceTimeBy(9_999)
        runCurrent()
        assertFalse(answer.isCompleted)
        advanceTimeBy(2)
        runCurrent()

        assertTrue("over at ten seconds", answer.isCompleted)
        assertNull(answer.await())
        // A late answer from the page reaches nobody.
        shadowOf(view).lastEvaluatedJavascriptCallback.onReceiveValue("1")
    }

    @Test
    fun onlyTheTabOnScreenShowsThePagesDialogs() = runTest {
        val (f, pool) = attached()
        val other = f.manager.newTab(incognito = false, url = OTHER)
        val current = f.manager.newTab(incognito = false, url = PAGE)
        val dialogs = RecordingJsResults()
        fun alert(): Boolean {
            val view = pool.obtain(f.tab(current)!!)
            return chrome(view).onJsAlert(view, PAGE, "Your session expired", dialogs.alert())
        }

        // Loading off-screen, as behind the tab grid: in the window, but in no host.
        pool.ensureWebView(current, offscreen = true)
        runCurrent()
        assertTrue(pool.obtain(f.tab(current)!!).isAttachedToWindow)
        assertTrue(alert())
        // Given to a host that is not in the window yet.
        val view = pool.showInHost(current)!!
        assertFalse(view.isAttachedToWindow)
        assertTrue(alert())
        // On screen.
        activity.findViewById<ViewGroup>(android.R.id.content).addView(view, ViewGroup.LayoutParams(1080, 2200))
        assertFalse("the WebView shows this one", alert())
        // Still in its host while another tab became the current one.
        f.manager.select(other)
        runCurrent()
        assertTrue(alert())

        assertEquals(listOf("cancel", "cancel", "cancel"), dialogs.answers)
    }

    @Test
    fun aPageOnScreenShowsThreeDialogsPerDocument() = runTest {
        val (f, pool) = attached()
        val id = f.manager.newTab(incognito = false, url = PAGE)
        val view = shownPage(f, pool, id)
        finishLoading(view, PAGE)
        val dialogs = RecordingJsResults()

        val shown = (1..4).map { chrome(view).onJsAlert(view, PAGE, "Wait", dialogs.alert()) }
        finishLoading(view, NEXT)

        assertEquals(listOf(false, false, false, true), shown)
        assertFalse("the next document may show its own", chrome(view).onJsAlert(view, NEXT, "Hello", dialogs.alert()))
        assertEquals(listOf("cancel"), dialogs.answers)
    }

    private fun TestScope.secondPool(f: TabManagerFixture, timers: WebViewTimers): WebViewPool {
        val secondActivity = Robolectric.buildActivity(ComponentActivity::class.java).setup().also { secondActivities += it }.get()
        val secondParts = TestBrowserParts(f, this, secondActivity, host)
        return WebViewPool(
            activity = secondActivity,
            tabs = f.manager,
            tabEvents = f.manager.events,
            engines = f.engines,
            incognito = f.incognito,
            stateStore = f.stateStore,
            thumbnails = f.thumbnails,
            activityHost = host,
            fullscreen = FullscreenController(secondActivity),
            sitePermissions = secondParts.sitePermissions,
            downloads = secondParts.downloads,
            isLowRamDevice = false,
            mainDispatcher = f.main,
            timers = timers,
            drawThumbnail = { null },
        ).also { pools += it }
    }

    private fun TestScope.attached(lowRam: Boolean = false, timers: WebViewTimers = WebViewTimers()): Pair<TabManagerFixture, WebViewPool> {
        val f = TabManagerFixture(this, folder.root).also { fixtures += it }
        parts = TestBrowserParts(f, this, activity, host)
        val pool = WebViewPool(
            activity = activity,
            tabs = f.manager,
            tabEvents = f.manager.events,
            engines = f.engines,
            incognito = f.incognito,
            stateStore = f.stateStore,
            thumbnails = f.thumbnails,
            activityHost = host,
            fullscreen = fullscreen,
            sitePermissions = parts.sitePermissions,
            downloads = parts.downloads,
            isLowRamDevice = lowRam,
            mainDispatcher = f.main,
            timers = timers,
            drawThumbnail = { view ->
                drawn += view
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            },
        ).also { pools += it }
        f.engines.attach(pool)
        return f to pool
    }

    /** [tabId]'s WebView, restored, with its page committed so it has a state to hand over. */
    private fun TestScope.livePage(f: TabManagerFixture, pool: WebViewPool, tabId: String): WebView {
        val view = pool.obtain(f.tab(tabId)!!)
        runCurrent()
        if (view.copyBackForwardList().size == 0) shadowOf(view).pushEntryToHistory(f.tab(tabId)!!.url!!)
        return view
    }

    /** [tabId]'s WebView, shown in the window and laid out like a page on screen. */
    private fun TestScope.shownPage(f: TabManagerFixture, pool: WebViewPool, tabId: String): WebView {
        val view = pool.showInHost(tabId)!!
        activity.findViewById<ViewGroup>(android.R.id.content).addView(view, ViewGroup.LayoutParams(1080, 2200))
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2200)
        runCurrent()
        shadowOf(view).pushEntryToHistory(f.tab(tabId)!!.url!!)
        return view
    }

    /** The reports of a page load, as the WebView makes them. */
    private fun finishLoading(view: WebView, url: String) {
        client(view).onPageStarted(view, url, null)
        client(view).doUpdateVisitedHistory(view, url, false)
        chrome(view).onProgressChanged(view, 100)
        client(view).onPageFinished(view, url)
    }

    private fun client(view: WebView) = shadowOf(view).webViewClient as SkimWebViewClient

    private fun chrome(view: WebView) = shadowOf(view).webChromeClient as SkimChromeClient

    /** A state as Robolectric's WebView saves it, with [urls] as its back history. */
    private fun historyOf(vararg urls: String): Bundle {
        val source = WebView(activity)
        urls.forEach { shadowOf(source).pushEntryToHistory(it) }
        return Bundle().also { source.saveState(it) }
    }

    private fun historyOf(view: WebView): List<String> {
        val list = view.copyBackForwardList()
        return (0 until list.size).map { list.getItemAtIndex(it).url }
    }

    private fun historyOf(state: Bundle): List<String> {
        val view = WebView(activity)
        view.restoreState(state)
        return historyOf(view)
    }

    private companion object {
        const val PAGE = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val OTHER = "https://daylightjournal.com/energy/batteries"
        const val NEXT = "https://kotlinlang.org/docs/"
        const val DOWNLOAD = "https://daylightjournal.com/files/guide.pdf"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) Skim"
        const val CAPTURE_DELAY_MS = 500L
        const val SAVE_DELAY_MS = 1_000L
    }
}
