package com.dantech.skimbrowser

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.di.ProcessGraph
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProcessGraphBrowserTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    @Inject lateinit var graph: ProcessGraph

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = folder.newFolder()
        TestStorage.current.set(AppStorage.temp(dir))
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        graph.close()
        TestStorage.current.set(null)
    }

    @Test
    fun noBrowserPartIsMadeBeforeFirstUse() {
        val connectivity = ApplicationProvider.getApplicationContext<android.app.Application>()
            .getSystemService(ConnectivityManager::class.java)
        assertEquals("no network callback yet", 0, shadowOf(connectivity).networkCallbacks.size)
        assertEquals("nothing written", emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun theDownloadPartsAreMadeOnceAndShared() {
        val client = graph.httpClient()

        assertSame(client, graph.httpClient())
        assertSame(graph.downloadTracker(), graph.downloadTracker())
        assertSame(graph.browserProfiles(), graph.browserProfiles())
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
    }

    @Test
    fun closeLetsTheHttpClientsThreadsGo() {
        val client = graph.httpClient()

        graph.close()

        assertTrue(client.dispatcher.executorService.isShutdown)
    }

    @Test
    fun theTabManagerBringsItsPartsAlongWithoutReadingAnything() {
        val connectivity = ApplicationProvider.getApplicationContext<android.app.Application>()
            .getSystemService(ConnectivityManager::class.java)
        val tabs = graph.tabManager()

        assertSame(tabs, graph.tabManager())
        assertEquals("it watches the network from now on", 1, shadowOf(connectivity).networkCallbacks.size)
        assertFalse("the saved tabs are read by restore, not by making the manager", File(dir, "skim.db").exists())
        assertSame(graph.networkMonitor(), graph.networkMonitor())
        assertSame(graph.incognitoSession(), graph.incognitoSession())
        assertSame(graph.tabStateStore(), graph.tabStateStore())
        assertSame(graph.thumbnails(), graph.thumbnails())
        assertSame(graph.pageScripts(), graph.pageScripts())
        assertEquals("one monitor for the manager and the container", 1, shadowOf(connectivity).networkCallbacks.size)
    }

    @Test
    fun closeStopsWatchingTheNetwork() {
        val connectivity = ApplicationProvider.getApplicationContext<android.app.Application>()
            .getSystemService(ConnectivityManager::class.java)
        graph.networkMonitor()
        assertEquals(1, shadowOf(connectivity).networkCallbacks.size)

        graph.close()

        assertEquals(0, shadowOf(connectivity).networkCallbacks.size)
    }

    @Test
    fun theStoresKeepTheirFilesInTheContainersStorage() {
        graph.tabStateStore().save("tab-1", Bundle().apply { putString("history", "a") }, incognito = false)
        graph.thumbnails().put("tab-1", Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), incognito = false)
        runBlocking {
            graph.tabStateStore().awaitIdle()
            graph.thumbnails().awaitIdle()
        }

        assertTrue(File(dir, "tabstate/tab-1.bin").isFile)
        assertTrue(File(dir, "thumbs/tab-1.webp").isFile)
    }

    @Test
    fun thePageScriptsComeFromTheAppsAssets() {
        assertTrue(graph.pageScripts().readerableScript().contains("function isProbablyReaderable("))
    }

    @Test
    fun theDefaultStorageIsTheAppsPrivateStorage() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val storage = AppStorage()

        assertEquals(File(app.filesDir, "tabstate"), storage.tabStateDir(app))
        assertEquals(File(app.cacheDir, "thumbs"), storage.thumbnailDir(app))
        assertEquals(File(app.cacheDir, "shared/incognito"), storage.incognitoSharedDir(app))
    }
}
