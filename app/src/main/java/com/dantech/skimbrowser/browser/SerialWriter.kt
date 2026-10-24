package com.dantech.skimbrowser.browser

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs blocks one at a time on [dispatcher], each to its end (suspensions included) before the
 * next starts, in the order they were handed in. A dispatcher limited to one thread alone would let
 * a block that suspends in Room be overtaken by the next one; here a later write can never land
 * before an earlier one. The mutex is fair and [post] takes its place in line before returning.
 */
internal class SerialWriter(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val tag: String,
) {
    private val mutex = Mutex()

    /** Queues [block]. A failure is logged and does not stop the blocks queued after it. */
    fun post(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                try {
                    withContext(dispatcher) { block() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(tag, "A queued write failed", e)
                }
            }
        }
    }

    /** Queues [block] behind everything posted before and returns its result; failures are thrown. */
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { withContext(dispatcher) { block() } }

    /** Returns once every block queued before this call has finished. */
    suspend fun awaitIdle() {
        mutex.withLock { }
    }
}
