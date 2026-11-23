package com.dantech.skimbrowser.browser

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SiteIconStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(folder.root, "site-icons")
    }

    @Test
    fun aNormalIconShowsAtOnceForItsHostAndIsWrittenAsPng() = runTest {
        val store = store()
        val icon = solid(Color.RED, 32, 32)

        store.put("https://www.Daylightjournal.com/energy?x=1", icon, incognito = false)

        assertSame(icon, store.observe("daylightjournal.com").value?.asAndroidBitmap())
        assertSame("the host is read like the address bar", icon, store.observe("WWW.daylightjournal.com").value?.asAndroidBitmap())
        store.awaitIdle()
        val file = File(dir, "daylightjournal.com.png")
        assertTrue(file.isFile)
        assertArrayEquals(PNG_SIGNATURE, file.readBytes().copyOf(PNG_SIGNATURE.size))
        assertEquals(setOf("daylightjournal.com.png"), dir.list().orEmpty().toSet())
    }

    @Test
    fun aLargeIconIsScaledDownAndASmallOneKeptAsItIs() = runTest {
        val store = store()
        val small = solid(Color.BLUE, 16, 16)

        store.put("https://tabi-note.jp/", solid(Color.RED, 192, 96), incognito = false)
        store.put("https://kotlinlang.org/", small, incognito = false)
        store.put("https://example.com/", solid(Color.GREEN, 96, 96), incognito = false)

        val large = store.observe("tabi-note.jp").value?.asAndroidBitmap() ?: throw AssertionError("no icon")
        assertEquals(96, large.width)
        assertEquals(48, large.height)
        assertSame(small, store.observe("kotlinlang.org").value?.asAndroidBitmap())
        assertEquals(96, store.observe("example.com").value?.asAndroidBitmap()?.width)
    }

    @Test
    fun anIncognitoIconNeverReachesTheDiskAndOnlyAnIncognitoObserverSeesIt() = runTest {
        val store = store()

        store.put("https://daylightjournal.com/energy", solid(Color.BLACK, 16, 16), incognito = true)
        store.awaitIdle()

        assertNotNull(store.observe("daylightjournal.com", incognito = true).value)
        val normal = store.observe("daylightjournal.com")
        store.awaitIdle()
        assertNull(normal.value)
        assertFalse(dir.exists() && dir.list().orEmpty().isNotEmpty())
        val restored = store()
        val flow = restored.observe("daylightjournal.com", incognito = true)
        restored.awaitIdle()
        assertNull(flow.value)
    }

    @Test
    fun anIncognitoObserverFallsBackOnTheNormalIcon() = runTest {
        val store = store()
        val normal = solid(Color.RED, 16, 16)
        store.put("https://daylightjournal.com/", normal, incognito = false)

        val incognito = store.observe("daylightjournal.com", incognito = true)
        assertSame(normal, incognito.value?.asAndroidBitmap())

        val own = solid(Color.BLUE, 16, 16)
        store.put("https://daylightjournal.com/", own, incognito = true)
        assertSame(own, incognito.value?.asAndroidBitmap())
        assertSame(normal, store.observe("daylightjournal.com").value?.asAndroidBitmap())
    }

    @Test
    fun anIncognitoObserverGetsTheNormalIconReadFromDisk() = runTest {
        store().apply {
            put("https://daylightjournal.com/", solid(Color.RED, 16, 16), incognito = false)
            awaitIdle()
        }

        val restored = store()
        val flow = restored.observe("daylightjournal.com", incognito = true)
        restored.awaitIdle()

        assertNotNull(flow.value)
    }

    @Test
    fun clearAllForgetsEveryIconInMemoryAndOnDisk() = runTest {
        val store = store()
        store.put("https://daylightjournal.com/", solid(Color.RED, 16, 16), incognito = false)
        store.put("https://tabi-note.jp/", solid(Color.RED, 16, 16), incognito = true)
        val normal = store.observe("daylightjournal.com")
        val incognito = store.observe("tabi-note.jp", incognito = true)
        store.awaitIdle()
        File(dir, "daylightjournal.com.png.tmp").writeText("interrupted write")

        store.clearAll()
        store.awaitIdle()

        assertNull(normal.value)
        assertNull(incognito.value)
        assertTrue(dir.list().orEmpty().isEmpty())
        val again = store.observe("daylightjournal.com")
        store.awaitIdle()
        assertNull(again.value)
    }

    @Test
    fun clearIncognitoKeepsTheNormalIcons() = runTest {
        val store = store()
        val normal = solid(Color.RED, 16, 16)
        store.put("https://daylightjournal.com/", normal, incognito = false)
        store.put("https://daylightjournal.com/", solid(Color.BLUE, 16, 16), incognito = true)
        store.put("https://tabi-note.jp/", solid(Color.BLUE, 16, 16), incognito = true)
        val sharedHost = store.observe("daylightjournal.com", incognito = true)
        val incognitoOnly = store.observe("tabi-note.jp", incognito = true)
        store.awaitIdle()

        store.clearIncognito()
        store.awaitIdle()

        assertSame("the normal icon stands in again", normal, sharedHost.value?.asAndroidBitmap())
        assertNull(incognitoOnly.value)
        assertNull(store.observe("tabi-note.jp", incognito = true).value)
        assertSame(normal, store.observe("daylightjournal.com").value?.asAndroidBitmap())
        assertTrue(File(dir, "daylightjournal.com.png").isFile)
    }

    @Test
    fun aPageWithoutAStorableHostWritesNoFile() = runTest {
        val store = store()
        listOf(
            null,
            "about:blank",
            "data:text/html,hi",
            "file:///sdcard/page.html",
            "https://[::1]/",
            "https://${"a".repeat(254)}/",
        ).forEach { url -> store.put(url, solid(Color.RED, 16, 16), incognito = false) }
        store.awaitIdle()

        assertFalse(dir.exists())
        assertNull(store.observe("../escape").value)
        assertNull(store.observe("a/b").value)
        assertNull(store.observe("").value)
        assertNull(store.observe(null).value)
        store.awaitIdle()
        assertFalse(File(folder.root, "escape.png").exists())
        assertFalse(dir.exists())
    }

    @Test
    fun aNewStoreOnTheSameDirectoryReadsTheSavedIcon() = runTest {
        store().apply {
            put("https://daylightjournal.com/", solid(Color.BLUE, 24, 24), incognito = false)
            awaitIdle()
        }

        val restored = store()
        val flow = restored.observe("daylightjournal.com")
        assertNull("read in the background", flow.value)
        restored.awaitIdle()

        val bitmap = flow.value?.asAndroidBitmap() ?: throw AssertionError("not read back")
        assertEquals(24, bitmap.width)
        assertEquals(Color.BLUE, bitmap.getPixel(4, 4))
    }

    @Test
    fun aNewerPutWinsOverASlowerRead() = runTest {
        store().apply {
            put("https://daylightjournal.com/", solid(Color.BLUE, 16, 16), incognito = false)
            awaitIdle()
        }
        val restored = store()
        val flow = restored.observe("daylightjournal.com")
        val newer = solid(Color.GREEN, 16, 16)

        restored.put("https://daylightjournal.com/", newer, incognito = false)
        restored.awaitIdle()

        assertSame(newer, flow.value?.asAndroidBitmap())
    }

    @Test
    fun aCorruptFileIsDeletedAndReadsAsNoIcon() = runTest {
        dir.mkdirs()
        File(dir, "daylightjournal.com.png").writeText("not an image")
        val store = store()

        val flow = store.observe("daylightjournal.com")
        store.awaitIdle()

        assertNull(flow.value)
        assertFalse(File(dir, "daylightjournal.com.png").exists())
    }

    @Test
    fun anEvictedIconStaysInTheFlowAlreadyHandedOutAndIsReadAgain() = runTest {
        val oneIcon = solid(Color.WHITE, 16, 16).allocationByteCount
        // Room for one icon and a few hosts still without one.
        val store = store(cacheBytes = oneIcon + 8)
        store.put("https://daylightjournal.com/", solid(Color.RED, 16, 16), incognito = false)
        val first = store.observe("daylightjournal.com")
        store.put("https://tabi-note.jp/", solid(Color.RED, 16, 16), incognito = false)
        store.awaitIdle()

        assertNotNull("a flow handed out keeps its icon", first.value)
        val again = store.observe("daylightjournal.com")
        store.awaitIdle()
        assertNotNull("read from disk again", again.value)
    }

    @Test
    fun incognitoIconsBeyondTheirCacheAreForgotten() = runTest {
        val oneIcon = solid(Color.WHITE, 16, 16).allocationByteCount
        // Room for two icons and a few hosts still without one.
        val store = store(incognitoCacheBytes = oneIcon * 2 + 8)

        store.put("https://daylightjournal.com/", solid(Color.RED, 16, 16), incognito = true)
        store.put("https://tabi-note.jp/", solid(Color.RED, 16, 16), incognito = true)
        store.put("https://kotlinlang.org/", solid(Color.RED, 16, 16), incognito = true)

        assertNotNull(store.observe("kotlinlang.org", incognito = true).value)
        assertNotNull(store.observe("tabi-note.jp", incognito = true).value)
        assertNull("put least recently", store.observe("daylightjournal.com", incognito = true).value)
        store.awaitIdle()
        assertFalse(dir.exists() && dir.list().orEmpty().isNotEmpty())
    }

    private fun TestScope.store(
        cacheBytes: Int = 4 * 1024 * 1024,
        incognitoCacheBytes: Int = 1024 * 1024,
    ) = SiteIconStore(dir, backgroundScope, StandardTestDispatcher(testScheduler), cacheBytes, incognitoCacheBytes)

    private fun solid(color: Int, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    }
}
