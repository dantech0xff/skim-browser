package com.dantech.skimbrowser

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppContainerBrowserTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    @Test
    fun noBrowserPartIsMadeBeforeFirstUse() {
        val dir = folder.newFolder()

        AppContainer(app, AppStorage.temp(dir)).use { container ->
            assertTrue("no network callback yet", networkCallbacks() == 0)
            assertEquals("nothing written", emptyList<String>(), dir.list()!!.toList())
            assertEquals(
                "the HTTP client, the WebView profiles and the download tracker wait for their first use",
                listOf(false, false, false),
                listOf("httpClientHolder", "browserProfiles\$delegate", "downloadTracker\$delegate").map { made(container, it) },
            )
        }
    }

    @Test
    fun theDownloadPartsAreMadeOnceAndShared() {
        AppContainer(app, AppStorage.temp(folder.newFolder())).use { container ->
            val client = container.httpClient

            assertSame(client, container.httpClient)
            assertSame(container.downloadTracker, container.downloadTracker)
            assertSame(container.browserProfiles, container.browserProfiles)
            assertEquals(15_000, client.connectTimeoutMillis)
            assertEquals(15_000, client.readTimeoutMillis)
            assertTrue(made(container, "httpClientHolder"))
        }
    }

    @Test
    fun closeLetsTheHttpClientsThreadsGo() {
        val container = AppContainer(app, AppStorage.temp(folder.newFolder()))
        val client = container.httpClient

        container.close()

        assertTrue(client.dispatcher.executorService.isShutdown)
    }

    @Test
    fun theTabManagerBringsItsPartsAlongWithoutReadingAnything() {
        val dir = folder.newFolder()

        AppContainer(app, AppStorage.temp(dir)).use { container ->
            val tabs = container.tabManager

            assertSame(tabs, container.tabManager)
            assertEquals("it watches the network from now on", 1, networkCallbacks())
            assertFalse("the saved tabs are read by restore, not by making the manager", File(dir, "skim.db").exists())
            assertSame(container.networkMonitor, container.networkMonitor)
            assertSame(container.incognitoSession, container.incognitoSession)
            assertSame(container.tabStateStore, container.tabStateStore)
            assertSame(container.thumbnails, container.thumbnails)
            assertSame(container.pageScripts, container.pageScripts)
            assertEquals("one monitor for the manager and the container", 1, networkCallbacks())
        }
    }

    @Test
    fun closeStopsWatchingTheNetwork() {
        val container = AppContainer(app, AppStorage.temp(folder.newFolder()))
        container.networkMonitor
        assertEquals(1, networkCallbacks())

        container.close()

        assertEquals(0, networkCallbacks())
    }

    @Test
    fun theStoresKeepTheirFilesInTheContainersStorage() {
        val dir = folder.newFolder()

        AppContainer(app, AppStorage.temp(dir)).use { container ->
            container.tabStateStore.save("tab-1", Bundle().apply { putString("history", "a") }, incognito = false)
            container.thumbnails.put("tab-1", Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), incognito = false)
            runBlocking {
                container.tabStateStore.awaitIdle()
                container.thumbnails.awaitIdle()
            }

            assertTrue(File(dir, "tabstate/tab-1.bin").isFile)
            assertTrue(File(dir, "thumbs/tab-1.webp").isFile)
        }
    }

    @Test
    fun thePageScriptsComeFromTheAppsAssets() {
        AppContainer(app, AppStorage.temp(folder.newFolder())).use { container ->
            assertTrue(container.pageScripts.readerableScript().contains("function isProbablyReaderable("))
        }
    }

    @Test
    fun theDefaultStorageIsTheAppsPrivateStorage() {
        val storage = AppStorage()

        assertEquals(File(app.filesDir, "tabstate"), storage.tabStateDir(app))
        assertEquals(File(app.cacheDir, "thumbs"), storage.thumbnailDir(app))
        assertEquals(File(app.cacheDir, "shared/incognito"), storage.incognitoSharedDir(app))
    }

    private fun networkCallbacks(): Int = shadowOf(connectivity).networkCallbacks.size

    /** Whether the container's lazy part behind field [name] was made. */
    private fun made(container: AppContainer, name: String): Boolean {
        val field = AppContainer::class.java.getDeclaredField(name).apply { isAccessible = true }
        return (field.get(container) as Lazy<*>).isInitialized()
    }
}
