package com.dantech.skimbrowser

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.TestStorage
import com.dantech.skimbrowser.di.AppGraphEntryPoint
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.system.purgeOldShares
import dagger.hilt.android.EntryPointAccessors
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.BrowserHost
import com.dantech.skimbrowser.browser.DEVICE_TIMEOUT_MS
import com.dantech.skimbrowser.browser.DeviceDownloads
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TestPages
import com.dantech.skimbrowser.browser.WebViewSupport
import com.dantech.skimbrowser.browser.awaitValue
import com.dantech.skimbrowser.browser.downloads.DownloadEvent
import com.dantech.skimbrowser.browser.holdsFor
import com.dantech.skimbrowser.browser.jsValue
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.waitUntil
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivity's part in the browser's life, with the app's own container: it attaches its WebView
 * pool to the tab manager while it lives, pauses JavaScript timers while stopped, hears
 * DownloadManager finish Skim's downloads while started (and asks again about those that finished
 * while it was stopped), clears what an earlier incognito session shared once its first frame is
 * drawn, keeps a newer instance's pages running when an older one goes away after it started, and
 * when it finishes for good makes every close still waiting for Undo final. Each test closes the
 * tabs it opened and clears the history, site data and downloads it left.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val downloads = DeviceDownloads()

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()
    private lateinit var graph: ProcessGraph
    private val tabs: TabManager get() = graph.tabManager()
    private val pages = TestPages()
    private val collectors = CoroutineScope(Dispatchers.Default)

    @Before
    fun setUp() {
        TestStorage.current.set(AppStorage())
        graph = EntryPointAccessors.fromApplication(app, AppGraphEntryPoint::class.java).graph()
        pages.start()
        runBlocking {
            withTimeout(DEVICE_TIMEOUT_MS) {
                tabs.restore()
                purgeOldShares(app.cacheDir)
            }
        }
    }

    @After
    fun tearDown() {
        try {
            onMain {
                val open = tabs.state.value.tabs.map { it.id }
                if (open.isNotEmpty()) tabs.finalizeClose(tabs.close(open))
                tabs.finalizePendingCloses()
                if (graph.incognitoSession().state.value != IncognitoSession.State.None) graph.incognitoSession().end(engine = null)
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                WebStorage.getInstance().deleteAllData()
            }
            runBlocking {
                withTimeout(DEVICE_TIMEOUT_MS) {
                    tabs.awaitWrites()
                    graph.historyRepository().clearAll()
                    graph.tabStateStore().awaitIdle()
                    graph.thumbnails().awaitIdle()
                }
            }
        } finally {
            collectors.cancel()
            pages.close()
            if (::graph.isInitialized) {
                EntryPointAccessors.fromApplication(app, AppGraphEntryPoint::class.java).shutdown().close()
                TestStorage.current.set(null)
            }
        }
    }

    @Test
    fun theTabsHaveWebViewsWhileItLivesAndNoneOnceItIsGone() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val id = onMain { tabs.newTab(incognito = false, url = pages.url("/plain.html")) }

        assertTrue("its pool makes the tab a WebView", runBlocking { tabs.awaitPageReady(id) })
        scenario.close()

        assertFalse(tabs.runtime(id).value.pageLoaded)
        assertFalse("no pool, no page", runBlocking { tabs.awaitPageReady(id, timeoutMs = NO_POOL_WAIT_MS) })
    }

    @Test
    fun javaScriptTimersPauseWhileItIsStoppedAndRunAgainOnceItStarts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val id = onMain { tabs.newTab(incognito = false, url = pages.url("/timers.html")) }
            assertTrue(runBlocking { tabs.awaitPageReady(id) })
            val host = hostOf(scenario)
            val running = ticks(host, id)
            waitUntil("the timer to run") { ticks(host, id) > running }

            scenario.moveToState(Lifecycle.State.CREATED)
            val stopped = ticks(host, id)
            assertTrue("no tick while stopped", holdsFor(PAUSED_WATCH_MS) { ticks(host, id) == stopped })

            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil("the timer to run again") { ticks(host, id) > stopped }
        }
    }

    @Test
    fun javaScriptTimersRunInTheNextActivityAfterTheLastOneStoppedAndWentAway() {
        val id = onMain { tabs.newTab(incognito = false, url = pages.url("/timers.html")) }
        ActivityScenario.launch(MainActivity::class.java).use { first ->
            assertTrue(runBlocking { tabs.awaitPageReady(id) })
            first.moveToState(Lifecycle.State.CREATED)
        }

        ActivityScenario.launch(MainActivity::class.java).use { next ->
            assertTrue(runBlocking { tabs.awaitPageReady(id) })
            val host = hostOf(next)
            val started = ticks(host, id)
            waitUntil("the timer to run in the new Activity's WebView") { ticks(host, id) > started }
        }
    }

    @Test
    fun aNewerInstanceStartedOverAnOlderOneKeepsItsPagesRunningOnceTheOlderOneIsGone() {
        val instances = Instances()
        app.registerActivityLifecycleCallbacks(instances)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val id = onMain { tabs.newTab(incognito = false, url = pages.url("/timers.html")) }
                assertTrue(runBlocking { tabs.awaitPageReady(id) })
                val older = onMain { instances.resumed.single() }

                // As another app opening a link would: a new task over Skim's, while the older instance still runs.
                app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

                waitUntil("the newer instance to show and the older one to be gone") {
                    onMain { instances.resumed.any { it !== older } && older in instances.destroyed }
                }
                val newer = onMain { instances.resumed.single() }
                try {
                    val host = onMain { newer.browserHostForTests }
                    assertTrue("the newer instance's pool drives the tabs", onMain { tabs.isAttached(host.pool) })
                    assertTrue(runBlocking { tabs.awaitPageReady(id) })
                    val started = ticks(host, id)
                    waitUntil("the timer to run in the newer instance's page") { ticks(host, id) > started }
                    val next = pages.url("/plain.html")
                    onMain { tabs.load(id, next) }
                    awaitValue(tabs.runtime(id), "the tab to load another page") { it.pageLoaded && it.committedUrl == next }
                } finally {
                    onMain { newer.finish() }
                    waitUntil("the newer instance to be gone") { onMain { newer in instances.destroyed } }
                }
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(instances)
        }
    }

    @Test
    fun whileStartedItHearsDownloadManagerFinishADownloadAPageStarted() {
        val events = downloadEvents()
        ActivityScenario.launch(MainActivity::class.java).use {
            // The page clicks its download link as it loads: the one download a page may start on its own. The
            // link has no download attribute, so every WebView requests the file before handing it over.
            val id = onMain { tabs.newTab(incognito = false, url = pages.url("/download.html?plain&auto=1")) }
            assertTrue(runBlocking { tabs.awaitPageReady(id) })

            waitUntil("DownloadManager's notice to reach the tracker", DOWNLOAD_TIMEOUT_MS) { events.isNotEmpty() }
            val completed = events.single() as DownloadEvent.DownloadCompleted
            assertEquals(SAFE_REPORT_NAME, completed.fileName)
            assertEquals("application/pdf", completed.mime)
            assertArrayEquals(TestPages.REPORT_BYTES, downloads.read(completed.uri))
        }
    }

    @Test
    fun aDownloadThatEndsWhileItIsStoppedIsReportedOnceItStartsAgain() {
        val events = downloadEvents()
        val file = pages.url("/report.pdf")
        pages.holdAfterFirst("/report.pdf")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val id = onMain { tabs.newTab(incognito = false, url = pages.url("/download.html?plain&auto=1")) }
            assertTrue(runBlocking { tabs.awaitPageReady(id) })
            waitUntil("DownloadManager to ask for the file", DOWNLOAD_TIMEOUT_MS) { pages.requests("/report.pdf").size >= 2 }

            scenario.moveToState(Lifecycle.State.CREATED)
            pages.release("/report.pdf")
            assertEquals(DownloadManager.STATUS_SUCCESSFUL, downloads.awaitSystemDownload(file).status)
            assertTrue("unheard while stopped", holdsFor(PAUSED_WATCH_MS) { events.isEmpty() })

            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil("the tracker to ask again once started") { events.isNotEmpty() }
            val completed = events.single() as DownloadEvent.DownloadCompleted
            assertEquals(SAFE_REPORT_NAME, completed.fileName)
            assertArrayEquals(TestPages.REPORT_BYTES, downloads.read(completed.uri))
        }
    }

    @Test
    fun filesSharedFromAnEarlierIncognitoSessionAreDeletedOnceItStarts() {
        val shared = File(app.cacheDir, "shared/incognito").apply { mkdirs() }
        val left = File(shared, "left-by-an-earlier-session.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitUntil("the earlier session's shared files to be deleted") { !left.exists() }
        }
        assertEquals(IncognitoSession.State.None, graph.incognitoSession().state.value)
    }

    @Test
    fun finishingMakesEveryCloseStillWaitingForUndoFinalAndEndsTheIncognitoSession() {
        WebViewSupport.assumeProfiles()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val normal = onMain { tabs.newTab(incognito = false, url = pages.url("/plain.html")) }
        val incognito = onMain { tabs.newTab(incognito = true, url = pages.url("/plain.html")) }
        assertTrue(runBlocking { tabs.awaitPageReady(normal) && tabs.awaitPageReady(incognito) })
        val normalClose = onMain { tabs.close(listOf(normal)) }
        onMain { tabs.close(listOf(incognito)) }
        assertTrue(graph.incognitoSession().state.value is IncognitoSession.State.Ending)
        runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { tabs.awaitWrites() } }
        assertEquals("closed and still undoable", 1L, tabRows(normal))

        scenario.close()

        assertTrue(onMain { tabs.pendingCloseIds() }.isEmpty())
        assertEquals(IncognitoSession.State.None, graph.incognitoSession().state.value)
        onMain { tabs.undoClose(normalClose) }
        assertNull("Undo has nothing left to bring back", tabs.state.value.tab(normal))
        runBlocking { withTimeout(DEVICE_TIMEOUT_MS) { tabs.awaitWrites() } }
        assertEquals("deleted for good", 0L, tabRows(normal))
    }

    @Test
    fun beingRecreatedKeepsTheClosesThatCanStillBeUndone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val id = onMain { tabs.newTab(incognito = false, url = pages.url("/plain.html")) }
            assertTrue(runBlocking { tabs.awaitPageReady(id) })
            val token = onMain { tabs.close(listOf(id)) }

            scenario.recreate()

            assertTrue(id in onMain { tabs.pendingCloseIds() })
            onMain { tabs.undoClose(token) }
            assertNotNull("Undo still brings it back", tabs.state.value.tab(id))
            assertTrue("and the new Activity's pool shows its page", runBlocking { tabs.awaitPageReady(id) })
        }
    }

    private fun hostOf(scenario: ActivityScenario<MainActivity>): BrowserHost {
        var host: BrowserHost? = null
        scenario.onActivity { host = it.browserHostForTests }
        return checkNotNull(host)
    }

    private fun ticks(host: BrowserHost, tabId: String): Int = runBlocking {
        withTimeout(DEVICE_TIMEOUT_MS) {
            val result = withContext(Dispatchers.Main) { host.pool.evaluate(tabId, "window.ticks") }
            (jsValue(result) as Number).toInt()
        }
    }

    private fun tabRows(tabId: String): Long = runBlocking {
        withContext(Dispatchers.IO) {
            graph.database().openHelper.readableDatabase.query("SELECT COUNT(*) FROM tabs WHERE id = ?", arrayOf<Any>(tabId)).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }
    }

    /** How the downloads of the process end from now on, as they reach the tracker. */
    private fun downloadEvents(): List<DownloadEvent> {
        val events = CopyOnWriteArrayList<DownloadEvent>()
        collectors.launch(start = CoroutineStart.UNDISPATCHED) { graph.downloadTracker().events.collect { events += it } }
        return events
    }

    /** The app's MainActivity instances by stage of their life; read on the main thread. */
    private class Instances : Application.ActivityLifecycleCallbacks {
        val resumed = ArrayList<MainActivity>()
        val destroyed = ArrayList<Activity>()

        override fun onActivityResumed(activity: Activity) {
            if (activity is MainActivity) resumed += activity
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity is MainActivity) resumed -= activity
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is MainActivity) destroyed += activity
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private companion object {
        const val NO_POOL_WAIT_MS = 1_000L
        const val PAUSED_WATCH_MS = 1_000L

        /** DownloadManager schedules its work as a job; on a busy emulator it can take a while to start. */
        const val DOWNLOAD_TIMEOUT_MS = 30_000L

        /** The file name `/report.pdf` gives, as Skim saves it. */
        const val SAFE_REPORT_NAME = "Q3_ report.pdf"
    }
}
