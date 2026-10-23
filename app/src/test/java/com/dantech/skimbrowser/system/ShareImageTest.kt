package com.dantech.skimbrowser.system

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.FakeBrowserProfiles
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.downloads.BlobBridge
import com.dantech.skimbrowser.browser.downloads.MAX_INLINE_DOWNLOAD_BYTES
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Sharing an image: cookies, the size cap, a data URL and an incognito file. */
@RunWith(AndroidJUnit4::class)
class ShareImageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearFileProviderCache() {
        // FileProvider remembers the first test's cache directory for the process. Each test has
        // its own Robolectric data directory, so the remembered root would reject later files.
        val cache = FileProvider::class.java.getDeclaredField("sCache")
        cache.isAccessible = true
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    @Test
    fun anHttpImageSendsThePageCookiesAndIsWrittenUnderShared() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("png-bytes").setHeader("Content-Type", "image/png"))
        server.start()
        try {
            val cache = appCache()
            val share = share(cache) { "sid=abc" }
            val shared = share.prepare(context, tab(incognito = false), server.url("/pump.png").toString(), BlobBridge(context.assets), null)
            assertEquals("sid=abc", server.takeRequest().getHeader("Cookie"))
            assertEquals("image/png", shared?.mime)
            assertTrue(File(cache, "shared").list()?.isNotEmpty() == true)
            assertTrue(File(cache, "shared/incognito").list().isNullOrEmpty())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aBodyPastTheCapIsDropped() {
        val body = "x".repeat(32).toResponseBody("image/png".toMediaType())
        assertNull(body.readAtMost(16))
        val small = "png".toResponseBody("image/png".toMediaType())
        assertEquals(3, small.readAtMost(MAX_INLINE_DOWNLOAD_BYTES)?.size)
    }

    @Test
    fun aDataUrlIsDecodedWithoutARequest() = runTest {
        val cache = appCache()
        val share = share(cache) { error("no network") }
        val shared = share.prepare(
            context,
            tab(false),
            "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
            BlobBridge(context.assets),
            null,
        )
        assertEquals("image/gif", shared?.mime)
        assertTrue(File(cache, "shared").list()?.any { it.endsWith(".gif") } == true)
    }

    @Test
    fun anIncognitoTabWritesUnderTheIncognitoDirectoryAndSendsProfileCookies() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("png").setHeader("Content-Type", "image/png"))
        server.start()
        try {
            val cache = appCache()
            val profiles = FakeBrowserProfiles()
            val session = IncognitoSession(profiles, File(cache, "shared/incognito"), backgroundScope)
            session.profileForNewTab { }
            val name = (session.state.value as IncognitoSession.State.Active).profile
            val url = server.url("/secret.png").toString()
            profiles.profile(name).cookies[url] = "sid=hidden"
            val share = ShareImage(OkHttpClient(), cache, session, profiles) { error("default cookies") }
            val shared = share.prepare(context, tab(incognito = true), url, BlobBridge(context.assets), null)
            assertEquals("sid=hidden", server.takeRequest().getHeader("Cookie"))
            assertEquals("image/png", shared?.mime)
            assertTrue(File(cache, "shared/incognito").list()?.isNotEmpty() == true)
        } finally {
            server.shutdown()
        }
    }

    /**
     * FileProvider only exposes the app cache. Clearing the files keeps the `shared` directory:
     * deleting and recreating it makes later shares miss the provider's root.
     */
    private fun appCache(): File {
        val cache = context.cacheDir
        val shared = File(cache, "shared")
        shared.mkdirs()
        shared.listFiles()?.forEach { it.deleteRecursively() }
        return cache
    }

    private fun TestScope.share(cache: File, cookies: (String) -> String?): ShareImage {
        val profiles = FakeBrowserProfiles()
        val session = IncognitoSession(profiles, File(cache, "shared/incognito"), backgroundScope)
        return ShareImage(OkHttpClient(), cache, session, profiles, cookies)
    }

    private fun tab(incognito: Boolean) = Tab(
        id = "tab-1",
        incognito = incognito,
        url = "https://daylightjournal.com/",
        title = "Daylight",
        groupId = null,
        position = 0,
        createdAt = 0,
        lastAccessedAt = 0,
    )
}
