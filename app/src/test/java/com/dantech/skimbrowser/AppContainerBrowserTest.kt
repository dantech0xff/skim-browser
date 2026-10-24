package com.dantech.skimbrowser

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.BrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.NetworkMonitor
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TabStateStore
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.browser.page.PageScriptSource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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
class AppContainerBrowserTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    @Inject lateinit var resources: ReleaseProcessResources
    @Inject lateinit var httpClient: Provider<OkHttpClient>
    @Inject lateinit var downloadTracker: Provider<DownloadTracker>
    @Inject lateinit var browserProfiles: Provider<BrowserProfiles>
    @Inject lateinit var tabManager: Provider<TabManager>
    @Inject lateinit var networkMonitor: Provider<NetworkMonitor>
    @Inject lateinit var incognitoSession: Provider<IncognitoSession>
    @Inject lateinit var tabStateStore: Provider<TabStateStore>
    @Inject lateinit var thumbnails: Provider<ThumbnailStore>
    @Inject lateinit var pageScripts: Provider<PageScriptSource>

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = folder.newFolder()
        TestStorage.current.set(AppStorage.temp(dir))
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        resources.release()
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
        val client = httpClient.get()

        assertSame(client, httpClient.get())
        assertSame(downloadTracker.get(), downloadTracker.get())
        assertSame(browserProfiles.get(), browserProfiles.get())
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
    }

    @Test
    fun closeLetsTheHttpClientsThreadsGo() {
        val client = httpClient.get()

        resources.release()

        assertTrue(client.dispatcher.executorService.isShutdown)
    }

    @Test
    fun theTabManagerBringsItsPartsAlongWithoutReadingAnything() {
        val connectivity = ApplicationProvider.getApplicationContext<android.app.Application>()
            .getSystemService(ConnectivityManager::class.java)
        val tabs = tabManager.get()

        assertSame(tabs, tabManager.get())
        assertEquals("it watches the network from now on", 1, shadowOf(connectivity).networkCallbacks.size)
        assertFalse("the saved tabs are read by restore, not by making the manager", File(dir, "skim.db").exists())
        assertSame(networkMonitor.get(), networkMonitor.get())
        assertSame(incognitoSession.get(), incognitoSession.get())
        assertSame(tabStateStore.get(), tabStateStore.get())
        assertSame(thumbnails.get(), thumbnails.get())
        assertSame(pageScripts.get(), pageScripts.get())
        assertEquals("one monitor for the manager and the container", 1, shadowOf(connectivity).networkCallbacks.size)
    }

    @Test
    fun closeStopsWatchingTheNetwork() {
        val connectivity = ApplicationProvider.getApplicationContext<android.app.Application>()
            .getSystemService(ConnectivityManager::class.java)
        networkMonitor.get()
        assertEquals(1, shadowOf(connectivity).networkCallbacks.size)

        resources.release()

        assertEquals(0, shadowOf(connectivity).networkCallbacks.size)
    }

    @Test
    fun theStoresKeepTheirFilesInTheContainersStorage() {
        tabStateStore.get().save("tab-1", Bundle().apply { putString("history", "a") }, incognito = false)
        thumbnails.get().put("tab-1", Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), incognito = false)
        runBlocking {
            tabStateStore.get().awaitIdle()
            thumbnails.get().awaitIdle()
        }

        assertTrue(File(dir, "tabstate/tab-1.bin").isFile)
        assertTrue(File(dir, "thumbs/tab-1.webp").isFile)
    }

    @Test
    fun thePageScriptsComeFromTheAppsAssets() {
        assertTrue(pageScripts.get().readerableScript().contains("function isProbablyReaderable("))
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
