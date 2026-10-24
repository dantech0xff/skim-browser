package com.dantech.skimbrowser.browser.downloads

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage.Companion.chunk
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage.Companion.end
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage.Companion.error
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage.Companion.gone
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage.Companion.start
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage.Companion.started
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage.Companion.unavailable
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage.Companion.waiting
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The blob bridge against a fake page that answers the blob script's runs and keeps a read's state
 * from its start until its drop. What the real script does in a page is checked in Chrome by a
 * harness outside the app; here every answer is the page's to make up, and the bridge must check it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BlobBridgeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val view = WebView(context)
    private val page = FakeBlobPage(SCRIPT)
    private var nonces = 0

    @Test
    fun theOriginOfABlobIsThatOfThePageThatMadeIt() {
        assertEquals("https://daylightjournal.com", blobOriginOf("blob:https://daylightjournal.com/5f0c-41"))
        assertEquals("https://daylightjournal.com", blobOriginOf("BLOB:HTTPS://DaylightJournal.COM:443/5f0c-41"))
        assertEquals("https://daylightjournal.com:8443", blobOriginOf("blob:https://daylightjournal.com:8443/5f0c-41"))
        assertEquals("http://localhost:8080", blobOriginOf("blob:http://localhost:8080/5f0c-41"))
        assertEquals("http://daylightjournal.com", blobOriginOf("blob:http://daylightjournal.com:80/5f0c-41"))
        assertEquals("https://[::1]:8443", blobOriginOf("blob:https://[::1]:8443/5f0c-41"))
    }

    @Test
    fun aBlobWithoutAWebOriginHasNone() {
        listOf(
            "blob:null/5f0c-41",
            "blob:file:///sdcard/5f0c-41",
            "blob:data:text/plain,5f0c",
            "blob:",
            "https://daylightjournal.com/5f0c-41",
            "data:application/pdf;base64,JVBERi0=",
        ).forEach { url -> assertNull(url, blobOriginOf(url)) }
    }

    @Test
    fun aReadStartsForTheBlobsOriginTakesEveryStepAndDropsItsState() = runTest {
        val bytes = "%PDF-1.4 a small report".toByteArray()
        page.serve(bytes, "application/pdf", sliceBytes = 9)

        val blob = bridge().fetch(view, BLOB_URL)

        assertArrayEquals(bytes, blob!!.bytes)
        assertEquals("application/pdf", blob.mimeType)
        val start = page.calls.first()
        assertEquals(BLOB_URL, start.text("url"))
        assertEquals(ORIGIN, start.text("origin"))
        assertEquals(MAX_INLINE_DOWNLOAD_BYTES.toLong(), start.number("maxBytes"))
        assertEquals(BlobBridge.CHUNK_CHARS.toLong(), start.number("chunkChars"))
        assertEquals("start, its size, 3 chunks, the end, then the drop", listOf("start") + List(5) { "take" } + "drop", page.ops)
        assertEquals("one nonce for the whole read", 1, page.calls.map { it.nonce }.distinct().size)
        assertFalse("the drop does not wait for the page", page.calls.last().awaited)
        assertTrue("nothing is left in the page", page.live.isEmpty())
        assertEquals("data is taken without waiting", 0, currentTime)
    }

    @Test
    fun whileThePageReadsItIsAskedAgainEvery25Milliseconds() = runTest {
        repeat(4) { page.answers += ::waiting }
        page.serve("abc".toByteArray(), null)

        val blob = bridge().fetch(view, BLOB_URL)

        assertArrayEquals("abc".toByteArray(), blob!!.bytes)
        assertEquals(4 * BlobBridge.POLL_INTERVAL_MS, currentTime)
        assertEquals(4 + 3, page.ops.count { it == "take" })
    }

    @Test
    fun theScriptGetsItsArgumentsAsOneJsonValueThatNoUrlCanBreakOutOf() = runTest {
        val url = "blob:$ORIGIN/5f0c-41#'\"</script>\u2028\${x}`)"
        page.onStart = { unavailable(it.nonce) }
        val scripts = mutableListOf<String>()
        val recording = object : ScriptRunner by page {
            override suspend fun evaluate(webView: WebView, script: String): String? = page.evaluate(webView, script).also { scripts += script }

            override fun run(webView: WebView, script: String) {
                scripts += script
                page.run(webView, script)
            }
        }

        BlobBridge(recording, { SCRIPT }, { "nonce-1" }, BlobBridge.FETCH_TIMEOUT_MS, StandardTestDispatcher(testScheduler)).fetch(view, url, maxBytes = 1234)

        val start = scripts.first()
        assertTrue(start.startsWith("($SCRIPT)("))
        assertTrue(start.endsWith(")"))
        val json = start.removePrefix("($SCRIPT)(").removeSuffix(")")
        assertFalse(json.contains("</script>"))
        assertFalse(json.contains('\u2028'))
        assertEquals(
            BlobScriptArgs(op = "start", nonce = "nonce-1", url = url, origin = ORIGIN, maxBytes = 1234, chunkChars = BlobBridge.CHUNK_CHARS),
            Json.decodeFromString(BlobScriptArgs.serializer(), json),
        )
        assertEquals("drop", Json.decodeFromString(BlobScriptArgs.serializer(), scripts.last().removePrefix("($SCRIPT)(").removeSuffix(")")).op)
    }

    @Test
    fun aPageOfAnotherOriginIsUnavailableAndNothingIsTaken() = runTest {
        page.origin = "https://ads.example"
        page.serve("abc".toByteArray(), null)

        assertNull(bridge().fetch(view, BLOB_URL))
        assertEquals(listOf("start", "drop"), page.ops)
        assertTrue(page.live.isEmpty())
    }

    @Test
    fun aStartThatReportsAnotherOriginFails() = runTest {
        page.onStart = { started(it.nonce, "https://ads.example") }
        page.serve("abc".toByteArray(), null)

        assertNull(bridge().fetch(view, BLOB_URL))
        assertEquals("the page's word is not taken", listOf("start", "drop"), page.ops)
    }

    @Test
    fun aStartThatDoesNotCheckOutFails() = runTest {
        listOf<(String) -> String?>(
            { null },
            { "not json" },
            { "\"started\"" },
            { """{"status":"started","origin":"$ORIGIN","nonce":"guessed"}""" },
            { """{"status":"started","nonce":"$it"}""" },
        ).forEach { answer ->
            page.onStart = { answer(it.nonce) }
            page.serve("abc".toByteArray(), null)
            assertNull(bridge().fetch(view, BLOB_URL))
            page.answers.clear()
        }

        assertEquals(0, page.ops.count { it == "take" })
        assertTrue(page.live.isEmpty())
    }

    @Test
    fun answersForAnotherNonceOrNotJsonFailTheRead() = runTest {
        listOf<(String) -> String?>(
            { start("guessed", 3, null) },
            { null },
            { "not json" },
            { "[]" },
            { """{"status":"start","size":3}""" },
        ).forEach { answer ->
            page.answers += answer
            page.serve("abc".toByteArray(), null)
            assertNull(bridge().fetch(view, BLOB_URL))
            page.answers.clear()
        }

        assertEquals("each failed at its first answer", 5, page.ops.count { it == "take" })
        assertTrue(page.live.isEmpty())
    }

    @Test
    fun anErrorOrAReadThePageNoLongerHoldsEndsTheReadAtOnce() = runTest {
        listOf<(String) -> String?>({ error(it, "size") }, { error(it, "fetch") }, { gone(it) }, { unavailable(it) }).forEach { answer ->
            page.answers += answer
            assertNull(bridge().fetch(view, BLOB_URL))
        }

        assertEquals(0, currentTime)
        assertTrue(page.live.isEmpty())
    }

    @Test
    fun aBlobLargerThanTheLimitIsRefusedWhenAnnounced() = runTest {
        page.serve(ByteArray(101), null)

        assertNull(bridge().fetch(view, BLOB_URL, maxBytes = 100))
        assertEquals("nothing taken after the size", 1, page.ops.count { it == "take" })
        assertEquals(0, currentTime)
    }

    @Test
    fun moreBytesThanTheBlobAnnouncedStopTheReadAtOnce() = runTest {
        page.answers += { start(it, 4, null) }
        page.answers += { chunk(it, 0, ByteArray(3)) }
        // The page sends past the size it announced, and never ends.
        page.answers += { chunk(it, 1, ByteArray(2)) }

        assertNull(bridge().fetch(view, BLOB_URL))
        assertEquals(3, page.ops.count { it == "take" })
        assertEquals("stopped at the limit, not by the timeout", 0, currentTime)
    }

    @Test
    fun aChunkOutOfOrderFailsTheRead() = runTest {
        // Taken in, it would make a whole blob.
        page.answers += { start(it, 2, null) }
        page.answers += { chunk(it, 1, ByteArray(2)) }
        page.answers += { end(it, 1) }

        assertNull(bridge().fetch(view, BLOB_URL))
    }

    @Test
    fun aChunkThatIsNotBase64OrHasNoNumberFailsTheRead() = runTest {
        page.answers += { start(it, 0, null) }
        page.answers += { """{"status":"chunk","index":0,"data":"%%%","nonce":"$it"}""" }
        page.answers += { end(it, 1) }
        assertNull(bridge().fetch(view, BLOB_URL))

        page.answers.clear()
        page.answers += { start(it, 2, null) }
        page.answers += { """{"status":"chunk","index":"0","data":"AAA=","nonce":"$it"}""" }
        page.answers += { end(it, 1) }
        assertNull(bridge().fetch(view, BLOB_URL))
    }

    @Test
    fun aChunkLongerThanTheScriptSendsFailsTheRead() = runTest {
        val tooLong = "A".repeat(BlobBridge.CHUNK_CHARS + 1028)
        page.answers += { start(it, tooLong.length / 4 * 3L, null) }
        page.answers += { """{"status":"chunk","index":0,"data":"$tooLong","nonce":"$it"}""" }
        page.answers += { end(it, 1) }

        assertNull(bridge().fetch(view, BLOB_URL))
    }

    @Test
    fun anEndThatDoesNotMatchWhatCameFailsTheRead() = runTest {
        listOf(
            listOf<(String) -> String?>({ start(it, 4, null) }, { chunk(it, 0, ByteArray(2)) }, { end(it, 1) }),
            listOf<(String) -> String?>({ start(it, 2, null) }, { chunk(it, 0, ByteArray(2)) }, { end(it, 2) }),
        ).forEach { answers ->
            page.answers += answers
            assertNull(bridge().fetch(view, BLOB_URL))
        }
    }

    @Test
    fun numbersWrittenAsDecimalsAreWholeNumbersStill() = runTest {
        page.answers += { """{"status":"start","size":2.0,"mime":"","nonce":"$it"}""" }
        page.answers += { """{"status":"chunk","index":0.0,"data":"AAA=","nonce":"$it"}""" }
        page.answers += { """{"status":"end","chunks":1e0,"nonce":"$it"}""" }

        assertArrayEquals(ByteArray(2), bridge().fetch(view, BLOB_URL)!!.bytes)
    }

    @Test
    fun anEmptyBlobIsRead() = runTest {
        page.serve(ByteArray(0), "text/plain")

        val blob = bridge().fetch(view, BLOB_URL)

        assertEquals(0, blob!!.bytes.size)
        assertEquals("text/plain", blob.mimeType)
    }

    @Test
    fun theLargestChunkTheScriptSendsIsAccepted() = runTest {
        val bytes = ByteArray(BlobBridge.CHUNK_CHARS / 4 * 3) { it.toByte() }
        page.serve(bytes, null)

        assertArrayEquals(bytes, bridge().fetch(view, BLOB_URL)!!.bytes)
    }

    @Test
    fun aPageThatNeverFinishesTimesOutAndItsStateIsDropped() = runTest {
        page.answers += { start(it, 3, null) }

        assertNull(bridge().fetch(view, BLOB_URL))

        assertEquals(BlobBridge.FETCH_TIMEOUT_MS, currentTime)
        assertTrue(page.live.isEmpty())
        assertTrue("asked about every 25 ms", page.ops.count { it == "take" } <= BlobBridge.FETCH_TIMEOUT_MS / BlobBridge.POLL_INTERVAL_MS + 2)
    }

    @Test
    fun aWebViewThatNeverAnswersTimesOutAndItsStateIsDropped() = runTest {
        page.silent = true

        assertNull(bridge().fetch(view, BLOB_URL))

        assertEquals(BlobBridge.FETCH_TIMEOUT_MS, currentTime)
        assertEquals(listOf("start", "drop"), page.ops)
        assertTrue(page.live.isEmpty())
    }

    @Test
    fun aCancelledReadDropsItsStateWithoutWaiting() = runTest {
        val read = async(start = CoroutineStart.UNDISPATCHED) { bridge().fetch(view, BLOB_URL) }
        runCurrent()
        assertEquals(1, page.live.size)

        read.cancel()
        runCurrent()

        assertTrue(page.live.isEmpty())
        assertFalse(page.calls.last().awaited)
    }

    @Test
    fun destroyEndsARunningReadAndDropsItsStateAtOnce() = runTest {
        val bridge = bridge()
        val read = async(start = CoroutineStart.UNDISPATCHED) { bridge.fetch(view, BLOB_URL) }
        runCurrent()

        bridge.destroy()

        assertTrue("dropped while the WebView still lives", page.live.isEmpty())
        runCurrent()
        assertNull(read.await())
        assertEquals("dropped once", 1, page.ops.count { it == "drop" })
        assertNull(bridge.fetch(view, BLOB_URL))
        assertEquals("nothing runs after destroy", 1, page.ops.count { it == "start" })
    }

    @Test
    fun destroyEndsAReadWhoseWebViewNoLongerAnswers() = runTest {
        page.silent = true
        val bridge = bridge()
        val read = async(start = CoroutineStart.UNDISPATCHED) { bridge.fetch(view, BLOB_URL) }
        runCurrent()

        bridge.destroy()
        runCurrent()

        assertTrue(read.isCompleted)
        assertNull(read.await())
        assertEquals(0, currentTime)
    }

    @Test
    fun aReadWaitingItsTurnWhenTheActivityGoesNeverStarts() = runTest {
        val bridge = bridge()
        val first = async(start = CoroutineStart.UNDISPATCHED) { bridge.fetch(view, BLOB_URL) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { bridge.fetch(view, BLOB_URL) }
        runCurrent()

        bridge.destroy()
        runCurrent()

        assertNull(first.await())
        assertNull(second.await())
        assertEquals(1, page.ops.count { it == "start" })
        assertTrue(page.live.isEmpty())
    }

    @Test
    fun readsRunOneAtATimeEachWithANewNonce() = runTest {
        val bridge = BlobBridge(page, { SCRIPT }, mainDispatcher = StandardTestDispatcher(testScheduler))
        val first = async(start = CoroutineStart.UNDISPATCHED) { bridge.fetch(view, BLOB_URL) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { bridge.fetch(view, BLOB_URL) }
        runCurrent()
        assertEquals("the second waits for the first", 1, page.ops.count { it == "start" })

        advanceTimeBy(BlobBridge.FETCH_TIMEOUT_MS + 1)
        runCurrent()
        assertNull(first.await())
        assertEquals(listOf("start", "drop"), page.ops.filter { it != "take" }.take(2))
        assertEquals("the first is gone before the second starts", 1, page.live.size)
        advanceTimeBy(BlobBridge.FETCH_TIMEOUT_MS + 1)
        runCurrent()
        assertNull(second.await())

        val (one, two) = page.calls.filter { it.op == "start" }.map { it.nonce }
        assertNotEquals(one, two)
        assertTrue(page.live.isEmpty())
    }

    @Test
    fun aScriptThatCannotBeReadFailsTheReadWithNothingRun() = runTest {
        val bridge = BlobBridge(page, { throw IOException("no asset") }, mainDispatcher = StandardTestDispatcher(testScheduler))

        assertNull(bridge.fetch(view, BLOB_URL))
        assertTrue(page.calls.isEmpty())
    }

    @Test
    fun aBlobWithoutAWebOriginRunsNothing() = runTest {
        assertNull(bridge().fetch(view, "blob:null/5f0c-41"))
        assertTrue(page.calls.isEmpty())
    }

    @Test
    fun theAppsScriptChecksTheOriginAndTheSizeBeforeItReads() {
        val script = context.assets.open(BlobBridge.SCRIPT).bufferedReader().use { it.readText() }

        assertTrue(script.lines().first { !it.startsWith("//") }.startsWith("function (args)"))
        listOf("'start'", "'take'", "'drop'", "enumerable: false").forEach { assertTrue(it, script.contains(it)) }
        assertTrue(script.indexOf("location.origin") < script.indexOf("fetch(args.url)"))
        assertTrue(script.indexOf("blob.size > args.maxBytes") < script.indexOf("state.blob = blob"))
    }

    private fun TestScope.bridge() =
        BlobBridge(page, { SCRIPT }, { "nonce-${++nonces}" }, BlobBridge.FETCH_TIMEOUT_MS, StandardTestDispatcher(testScheduler))

    private companion object {
        const val ORIGIN = "https://daylightjournal.com"
        const val BLOB_URL = "blob:https://daylightjournal.com/5f0c-41"
        const val SCRIPT = "function (args) { return null; }"
    }
}
