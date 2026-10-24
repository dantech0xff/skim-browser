package com.dantech.skimbrowser.browser

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
class ThumbnailStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(folder.root, "thumbs")
    }

    @Test
    fun aNormalTabsThumbnailShowsAtOnceAndIsWrittenAsWebp() = runTest {
        val store = store()
        val bitmap = solid(Color.RED)

        store.put("tab-1", bitmap, incognito = false)

        assertSame(bitmap, store.observe("tab-1").value?.asAndroidBitmap())
        store.awaitIdle()
        val file = File(dir, "tab-1.webp")
        assertTrue(file.isFile)
        val header = file.readBytes()
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(header, 8, 4, Charsets.US_ASCII))
    }

    @Test
    fun aSavedThumbnailIsReadBackAfterProcessDeath() = runTest {
        store().apply {
            put("tab-1", solid(Color.BLUE), incognito = false)
            awaitIdle()
        }

        val restored = store()
        val flow = restored.observe("tab-1")
        assertNull("read in the background", flow.value)
        restored.awaitIdle()

        val bitmap = flow.value?.asAndroidBitmap() ?: throw AssertionError("not read back")
        assertEquals(WIDTH, bitmap.width)
        assertEquals(HEIGHT, bitmap.height)
        assertTrue(Color.blue(bitmap.getPixel(10, 10)) > 200)
    }

    @Test
    fun aNewerPutWinsOverASlowerRead() = runTest {
        store().apply {
            put("tab-1", solid(Color.BLUE), incognito = false)
            awaitIdle()
        }
        val restored = store()
        val flow = restored.observe("tab-1")
        val newer = solid(Color.GREEN)

        restored.put("tab-1", newer, incognito = false)
        restored.awaitIdle()

        assertSame(newer, flow.value?.asAndroidBitmap())
    }

    @Test
    fun anIncognitoThumbnailNeverReachesTheDisk() = runTest {
        val store = store()

        store.put("tab-9", solid(Color.BLACK), incognito = true)
        store.awaitIdle()

        assertNotNull(store.observe("tab-9").value)
        assertFalse(dir.exists() && dir.list().orEmpty().isNotEmpty())
        val restored = store()
        val flow = restored.observe("tab-9")
        restored.awaitIdle()
        assertNull(flow.value)
    }

    @Test
    fun incognitoThumbnailsBeyondTheCacheAreForgottenOldestFirst() = runTest {
        val oneImage = solid(Color.WHITE).allocationByteCount
        val store = store(incognitoCacheBytes = oneImage * 2)

        store.put("tab-1", solid(Color.WHITE), incognito = true)
        store.put("tab-2", solid(Color.WHITE), incognito = true)
        store.observe("tab-1").value
        store.put("tab-3", solid(Color.WHITE), incognito = true)

        assertNotNull("used most recently", store.observe("tab-1").value)
        assertNull("used least recently", store.observe("tab-2").value)
        assertNotNull(store.observe("tab-3").value)
        store.awaitIdle()
        assertFalse(dir.exists() && dir.list().orEmpty().isNotEmpty())
    }

    @Test
    fun deleteForgetsTheThumbnailAndItsFile() = runTest {
        val store = store()
        val flow = store.observe("tab-1")
        store.put("tab-1", solid(Color.RED), incognito = false)
        store.awaitIdle()

        store.delete("tab-1")
        store.awaitIdle()

        assertNull(flow.value)
        assertNull(store.observe("tab-1").value)
        assertFalse(File(dir, "tab-1.webp").exists())
    }

    @Test
    fun clearAllForgetsEveryThumbnail() = runTest {
        val store = store()
        store.put("tab-1", solid(Color.RED), incognito = false)
        store.put("tab-2", solid(Color.RED), incognito = true)
        val normal = store.observe("tab-1")
        val incognito = store.observe("tab-2")
        store.awaitIdle()

        store.clearAll()
        store.awaitIdle()

        assertNull(normal.value)
        assertNull(incognito.value)
        assertTrue(dir.list().orEmpty().isEmpty())
    }

    @Test
    fun purgeKeepsOnlyTheListedTabs() = runTest {
        val store = store()
        listOf("keep", "drop").forEach { store.put(it, solid(Color.RED), incognito = false) }
        store.put("incognito-drop", solid(Color.RED), incognito = true)
        store.awaitIdle()
        File(dir, "keep.webp.tmp").writeText("interrupted write")
        File(dir, "stray.png").writeText("junk")
        val dropped = store.observe("drop")

        store.purgeExcept(setOf("keep"))
        store.awaitIdle()

        assertEquals(setOf("keep.webp"), dir.list().orEmpty().toSet())
        assertNull(dropped.value)
        assertNull(store.observe("incognito-drop").value)
        assertNotNull(store.observe("keep").value)
    }

    @Test
    fun aCorruptFileIsDeletedAndReadsAsNoThumbnail() = runTest {
        dir.mkdirs()
        File(dir, "tab-1.webp").writeText("not an image")
        val store = store()

        val flow = store.observe("tab-1")
        store.awaitIdle()

        assertNull(flow.value)
        assertFalse(File(dir, "tab-1.webp").exists())
    }

    @Test
    fun anIdThatIsNotAFileNameIsIgnored() = runTest {
        val store = store()

        store.put("../escape", solid(Color.RED), incognito = false)
        store.awaitIdle()

        assertNull(store.observe("../escape").value)
        assertFalse(File(folder.root, "escape.webp").exists())
        assertFalse(dir.exists())
    }

    private fun TestScope.store(incognitoCacheBytes: Int = 16 * 1024 * 1024) =
        ThumbnailStore(dir, backgroundScope, StandardTestDispatcher(testScheduler), incognitoCacheBytes)

    private fun solid(color: Int): Bitmap =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 66
    }
}
