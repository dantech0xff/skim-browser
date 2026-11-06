package com.dantech.skimbrowser.testing

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fails a unit test that stops making progress, and prints every thread before the worker dies.
 *
 * <p>A paused-looper deadlock never returns to JUnit, so a {@code @Test(timeout)} does not fire.
 * GitHub's step timeout then waits out the whole job. This watch runs inside the test process.
 */
object HungTestWatch {
    /** Longer than any waitUntil in these tests. Short enough that CI does not sit for half an hour. */
    private const val STALL_MS = 20_000L

    private val installed = AtomicBoolean(false)

    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        Thread({
            var last = ""
            var since = System.nanoTime()
            while (true) {
                Thread.sleep(1_000)
                val stuck = Thread.getAllStackTraces().entries.firstOrNull { (thread, frames) ->
                    thread != Thread.currentThread() && isLooperWait(frames)
                }
                val trace = stuck?.value?.joinToString("\n") ?: ""
                if (trace.isNotEmpty() && trace == last) {
                    val stalledMs = (System.nanoTime() - since) / 1_000_000
                    if (stalledMs >= STALL_MS) {
                        dump(stuck!!.key, stalledMs)
                        Runtime.getRuntime().halt(42)
                    }
                } else {
                    last = trace
                    since = System.nanoTime()
                }
            }
        }, "hung-test-watch").apply { isDaemon = true }.start()
    }

    /**
     * The paused looper's idle wait. An idle worker that happens to be named "Main Thread" parks
     * in [java.util.concurrent.ThreadPoolExecutor] and is not this.
     */
    private fun isLooperWait(frames: Array<StackTraceElement>): Boolean {
        val text = frames.joinToString("\n")
        if (text.contains("ThreadPoolExecutor.getTask")) return false
        return text.contains("nativePollOnce") ||
            text.contains("Espresso.onIdle") ||
            text.contains("loopMainThreadUntilIdle") ||
            text.contains("RobolectricIdlingStrategy.runUntilIdle") ||
            text.contains("IdlingRunnable.run")
    }

    private fun dump(stuck: Thread, stalledMs: Long) {
        val text = buildString {
            appendLine("HUNG TEST: ${stuck.name} made no progress for ${stalledMs}ms")
            Thread.getAllStackTraces().entries.sortedBy { it.key.name }.forEach { (thread, frames) ->
                appendLine("\"${thread.name}\" ${thread.state}")
                frames.forEach { frame -> appendLine("\tat $frame") }
            }
        }
        System.err.println(text)
        System.err.flush()
        File("/tmp/hung-test.txt").writeText(text)
    }
}
