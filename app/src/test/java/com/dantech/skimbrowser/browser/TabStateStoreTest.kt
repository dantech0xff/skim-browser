package com.dantech.skimbrowser.browser

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabStateStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(folder.root, "tabstate")
    }

    @Test
    fun aSavedStateComesBackFromItsFile() = runTest {
        val store = store()

        store.save("tab-1", state(), incognito = false)
        val loaded = store.load("tab-1", incognito = false)

        assertState(loaded)
        assertTrue(File(dir, "tab-1.bin").isFile)
    }

    @Test
    fun anotherStoreOnTheSameDirectoryReadsTheStateBack() = runTest {
        store().apply {
            save("tab-1", state(), incognito = false)
            awaitIdle()
        }

        assertState(store().load("tab-1", incognito = false))
    }

    @Test
    fun theCallerMayChangeTheBundleAfterSaving() = runTest {
        val store = store()
        val bundle = state()

        store.save("tab-1", bundle, incognito = false)
        bundle.putString("url", "https://changed.example/")

        assertEquals("https://kotlinlang.org/docs/", store.load("tab-1", incognito = false)?.getString("url"))
    }

    @Test
    fun incognitoStateNeverReachesTheDisk() = runTest {
        val store = store()

        store.save("tab-9", state(), incognito = true)
        store.awaitIdle()

        assertFalse(dir.exists() && dir.list().orEmpty().isNotEmpty())
        assertState(store.load("tab-9", incognito = true))
        assertNull("an incognito state is not a file", store.load("tab-9", incognito = false))

        store.delete("tab-9")
        assertNull(store.load("tab-9", incognito = true))
        assertFalse(dir.exists() && dir.list().orEmpty().isNotEmpty())
    }

    @Test
    fun aStateOverTheSizeLimitIsNotSavedAnywhere() = runTest {
        val store = store()
        val huge = state().apply { putByteArray("huge", ByteArray(3 * 1024 * 1024)) }

        store.save("tab-1", huge, incognito = false)
        store.save("tab-2", huge, incognito = true)
        store.awaitIdle()

        assertFalse(dir.exists() && dir.list().orEmpty().isNotEmpty())
        assertNull(store.load("tab-1", incognito = false))
        assertNull(store.load("tab-2", incognito = true))
    }

    @Test
    fun aStateWrittenByAnotherWebViewIsDeleted() = runTest {
        store(webView = "com.google.android.webview/140.0.1").apply {
            save("tab-1", state(), incognito = false)
            awaitIdle()
        }

        assertNull(store(webView = "com.google.android.webview/141.0.2").load("tab-1", incognito = false))
        assertFalse(File(dir, "tab-1.bin").exists())
    }

    @Test
    fun aStateWrittenOnAnotherAndroidVersionIsDeleted() = runTest {
        store(sdk = 35).apply {
            save("tab-1", state(), incognito = false)
            awaitIdle()
        }

        assertNull(store(sdk = 36).load("tab-1", incognito = false))
        assertFalse(File(dir, "tab-1.bin").exists())
    }

    @Test
    fun aFileOfAnotherFormatVersionIsDeleted() = runTest {
        writeStateFile("tab-1", formatVersion = 2)

        assertNull(store().load("tab-1", incognito = false))
        assertFalse(File(dir, "tab-1.bin").exists())
    }

    @Test
    fun aHandWrittenFileInTheExpectedFormatIsRead() = runTest {
        writeStateFile("tab-1")

        assertState(store().load("tab-1", incognito = false))
    }

    @Test
    fun aWrongChecksumIsDeleted() = runTest {
        writeStateFile("tab-1", crc = { it + 1 })

        assertNull(store().load("tab-1", incognito = false))
        assertFalse(File(dir, "tab-1.bin").exists())
    }

    @Test
    fun aChangedPayloadByteIsDeleted() = runTest {
        store().apply {
            save("tab-1", state(), incognito = false)
            awaitIdle()
        }
        val file = File(dir, "tab-1.bin")
        val bytes = file.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0x55).toByte()
        file.writeBytes(bytes)

        assertNull(store().load("tab-1", incognito = false))
        assertFalse(file.exists())
    }

    @Test
    fun aFileCutShortIsDeleted() = runTest {
        store().apply {
            save("tab-1", state(), incognito = false)
            awaitIdle()
        }
        val file = File(dir, "tab-1.bin")
        val bytes = file.readBytes()

        listOf(bytes.size - 1, bytes.size / 2, 10, 0).forEach { length ->
            file.writeBytes(bytes.copyOf(length))
            assertNull("cut to $length bytes", store().load("tab-1", incognito = false))
            assertFalse("cut to $length bytes", file.exists())
        }
    }

    @Test
    fun aFileWithBytesAfterThePayloadIsDeleted() = runTest {
        writeStateFile("tab-1", trailing = byteArrayOf(1, 2, 3))

        assertNull(store().load("tab-1", incognito = false))
        assertFalse(File(dir, "tab-1.bin").exists())
    }

    @Test
    fun aParcelThatDoesNotReadBackIsDeletedWithoutThrowing() = runTest {
        val garbage = ByteArray(512) { (it * 31 + 7).toByte() }
        writeStateFile("tab-1", payload = garbage)

        assertNull(store().load("tab-1", incognito = false))
        assertFalse(File(dir, "tab-1.bin").exists())
    }

    @Test
    fun aForeignFileIsDeleted() = runTest {
        dir.mkdirs()
        File(dir, "tab-1.bin").writeText("not a tab state at all")

        assertNull(store().load("tab-1", incognito = false))
        assertFalse(File(dir, "tab-1.bin").exists())
    }

    @Test
    fun aStateNeverSavedIsNull() = runTest {
        assertNull(store().load("tab-1", incognito = false))
        assertNull(store().load("tab-1", incognito = true))
    }

    @Test
    fun deleteRemovesTheFile() = runTest {
        val store = store()
        store.save("tab-1", state(), incognito = false)

        store.delete("tab-1")
        store.awaitIdle()

        assertFalse(File(dir, "tab-1.bin").exists())
        assertNull(store.load("tab-1", incognito = false))
    }

    @Test
    fun saveDeleteAndLoadRunInTheOrderOfTheCalls() = runTest {
        val store = store()

        store.save("tab-1", state(url = "https://first.example/"), incognito = false)
        store.delete("tab-1")
        store.save("tab-1", state(url = "https://second.example/"), incognito = false)

        assertEquals("https://second.example/", store.load("tab-1", incognito = false)?.getString("url"))
        store.delete("tab-1")
        assertNull(store.load("tab-1", incognito = false))
    }

    @Test
    fun purgeKeepsOnlyTheListedTabsAndRemovesLeftovers() = runTest {
        val store = store()
        listOf("keep", "drop-1", "drop-2").forEach { store.save(it, state(), incognito = false) }
        store.save("incognito-drop", state(), incognito = true)
        store.save("incognito-keep", state(), incognito = true)
        store.awaitIdle()
        File(dir, "keep.bin.new").writeText("interrupted write")
        File(dir, "drop-1.bin.bak").writeText("old backup")
        File(dir, "notes.txt").writeText("junk")

        store.purgeExcept(setOf("keep", "incognito-keep"))
        store.awaitIdle()

        assertEquals(setOf("keep.bin", "keep.bin.new"), dir.list().orEmpty().toSet())
        assertState(store.load("keep", incognito = false))
        assertState(store.load("incognito-keep", incognito = true))
        assertNull(store.load("incognito-drop", incognito = true))
    }

    @Test
    fun anIdThatIsNotAFileNameIsRefused() = runTest {
        val store = store()

        listOf("../escape", "a/b", "", "x".repeat(65), "tab 1").forEach { id ->
            store.save(id, state(), incognito = false)
            assertNull(id, store.load(id, incognito = false))
        }
        store.awaitIdle()

        assertFalse(File(folder.root, "escape.bin").exists())
        assertFalse(dir.exists() && dir.list().orEmpty().isNotEmpty())
    }

    private fun TestScope.store(webView: String = WEBVIEW, sdk: Int = SDK) =
        TabStateStore(dir, { webView }, backgroundScope, StandardTestDispatcher(testScheduler), sdk)

    private fun state(url: String = "https://kotlinlang.org/docs/") = Bundle().apply {
        putString("url", url)
        putInt("scroll", 1_234)
        putByteArray("blob", BLOB)
    }

    private fun assertState(loaded: Bundle?) {
        assertEquals("https://kotlinlang.org/docs/", loaded?.getString("url"))
        assertEquals(1_234, loaded?.getInt("scroll"))
        assertArrayEquals(BLOB, loaded?.getByteArray("blob"))
    }

    /**
     * Writes a state file in the store's format: magic, format version, WebView version, SDK_INT,
     * payload length, CRC32 of the payload, then the payload.
     */
    private fun writeStateFile(
        tabId: String,
        formatVersion: Int = 1,
        payload: ByteArray = marshall(state()),
        crc: (Long) -> Long = { it },
        trailing: ByteArray = ByteArray(0),
    ) {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(0x534B5453)
                out.writeInt(formatVersion)
                out.writeUTF(WEBVIEW)
                out.writeInt(SDK)
                out.writeInt(payload.size)
                out.writeLong(crc(CRC32().apply { update(payload) }.value))
                out.write(payload)
                out.write(trailing)
            }
        }.toByteArray()
        dir.mkdirs()
        File(dir, "$tabId.bin").writeBytes(bytes)
    }

    private fun marshall(bundle: Bundle): ByteArray {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            return parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        const val WEBVIEW = "com.google.android.webview/141.0.7390.122"
        const val SDK = 36
        val BLOB = ByteArray(300) { it.toByte() }
    }
}
