package com.dantech.skimbrowser.macrobenchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameBenchmarks {

    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun warmStartup() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun scrollPage() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            // Home fits on a Pixel 7, so it has no scrollable node. A local page is tall enough
            // to fling, and it does not depend on a network.
            val page = serveLongPage()
            // The process is killed first, so every iteration delivers the URL to onCreate.
            // A live singleTask activity would only see onNewIntent and might keep Home.
            killProcess()
            pressHome()
            startActivityAndWait(
                Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$page/scroll"))
                    .setPackage(PACKAGE),
            )
            dismissOnboarding()
            val text = device.wait(Until.hasObject(By.textContains("Scroll line")), 15_000) == true
            if (!text) {
                check(device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 10_000) == true) {
                    "The local page never showed a WebView"
                }
                // The address can be up before the document is tall enough to fling.
                Thread.sleep(2_000)
            }
        },
    ) {
        val web = device.findObject(By.clazz("android.webkit.WebView"))
            ?: error("The page has no WebView")
        val bounds = web.visibleBounds
        val x = bounds.centerX()
        device.swipe(x, bounds.bottom - 280, x, bounds.top + 180, 50)
        device.waitForIdle()
    }

    @Test
    fun navigationAndTabsAndSheet() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Get started")), 20_000)
        device.findObject(By.text("Get started"))?.click()
        device.wait(Until.hasObject(By.text("Start browsing")), 20_000)
        device.findObject(By.text("Start browsing"))?.click()
        device.wait(Until.hasObject(By.text("Shortcuts")), 20_000)
        val tabs = device.findObject(By.descContains("Tabs"))
        tabs?.click()
        device.wait(Until.hasObject(By.text("New tab")), 20_000)
        device.pressBack()
        val ask = device.findObject(By.descContains("Ask Skim"))
        ask?.click()
        device.waitForIdle()
        device.pressBack()
    }

    /** Welcome and the setup screen. A later launch, or a page already open, has neither button. */
    private fun MacrobenchmarkScope.dismissOnboarding() {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            val welcome = device.hasObject(By.text("Get started"))
            val setup = device.hasObject(By.text("Start browsing"))
            if (!welcome && !setup) return
            // The label is not the clickable node. A tap on its center hits the button.
            if (welcome) tap("Get started")
            if (setup) tap("Start browsing")
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.tap(label: String) {
        val node = device.findObject(By.text(label)) ?: return
        val bounds = node.visibleBounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        device.click(bounds.centerX(), bounds.centerY())
    }

    private companion object {
        const val PACKAGE = "com.dantech.skimbrowser"

        /**
         * Serves one tall HTML page on loopback until the process ends. WebView may ask more than
         * once; each accept answers the same body.
         */
        fun serveLongPage(): Int {
            val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            val body = buildString {
                append("<!doctype html><html><body>")
                repeat(80) { append("<p style=\"font-size:18px;margin:24px 0\">Scroll line $it</p>") }
                append("</body></html>")
            }.toByteArray()
            val header = (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray()
            thread(isDaemon = true, name = "scroll-page") {
                while (!server.isClosed) {
                    try {
                        server.accept().use { socket ->
                            socket.soTimeout = 2_000
                            // Read the request first. Closing with unread bytes sends a TCP reset
                            // and WebView retries or fails the page.
                            try {
                                drainRequest(socket.getInputStream())
                            } catch (_: Exception) {
                                // The header may already have been consumed.
                            }
                            socket.getOutputStream().apply {
                                write(header)
                                write(body)
                                flush()
                            }
                        }
                    } catch (_: Exception) {
                        if (server.isClosed) break
                    }
                }
            }
            return server.localPort
        }

        private fun drainRequest(input: InputStream) {
            val buffer = ByteArray(2048)
            var total = 0
            while (total < buffer.size) {
                val read = try {
                    input.read(buffer, total, buffer.size - total)
                } catch (_: SocketTimeoutException) {
                    break
                }
                if (read < 0) break
                total += read
                if (headerEnded(buffer, total)) break
            }
        }

        private fun headerEnded(buffer: ByteArray, total: Int): Boolean {
            for (index in 0 until total - 3) {
                if (buffer[index] == '\r'.code.toByte() &&
                    buffer[index + 1] == '\n'.code.toByte() &&
                    buffer[index + 2] == '\r'.code.toByte() &&
                    buffer[index + 3] == '\n'.code.toByte()
                ) {
                    return true
                }
            }
            return false
        }
    }
}
