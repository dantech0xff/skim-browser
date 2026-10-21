package com.dantech.skimbrowser.ui.snackbar

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UndoRegistryTest {

    private val calls = mutableListOf<String>()

    @Test
    fun undoRunsOnceOnTheAppScope() = runTest {
        val registry = UndoRegistry(appScope())
        val token = registry.register(UndoKind.HISTORY, undo = { calls += "undo" }, finalize = { calls += "finalize" })

        registry.undo(token)
        assertEquals("the undo is launched, not run in the caller", emptyList<String>(), calls)
        runCurrent()
        registry.undo(token)
        registry.finalize(token)
        runCurrent()

        assertEquals(listOf("undo"), calls)
    }

    @Test
    fun undoStillRunsAfterTheScreenThatRegisteredItIsGone() = runTest {
        val registry = UndoRegistry(appScope())
        val screenScope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        var token: UndoToken? = null
        screenScope.launch { token = registry.register(UndoKind.BOOKMARK, undo = { calls += "restore" }) }
        runCurrent()

        screenScope.cancel()
        registry.undo(checkNotNull(token))
        runCurrent()

        assertEquals(listOf("restore"), calls)
    }

    @Test
    fun finalizeRunsOnceAndUndoIsIgnoredAfterIt() = runTest {
        val registry = UndoRegistry(appScope())
        val token = registry.register(UndoKind.TAB, undo = { calls += "undo" }, finalize = { calls += "finalize" })

        registry.finalize(token)
        registry.finalize(token)
        registry.undo(token)
        runCurrent()

        assertEquals(listOf("finalize"), calls)
    }

    @Test
    fun cancelAllFinalizesThatKindOnlyAndItsUndoNoLongerRuns() = runTest {
        val registry = UndoRegistry(appScope())
        val first = registry.register(UndoKind.SKIM_CHAT, undo = { calls += "undo 1" }, finalize = { calls += "finalize 1" })
        val second = registry.register(UndoKind.SKIM_CHAT, undo = { calls += "undo 2" }, finalize = { calls += "finalize 2" })
        val other = registry.register(UndoKind.HISTORY, undo = { calls += "undo other" }, finalize = { calls += "finalize other" })

        registry.cancelAll(UndoKind.SKIM_CHAT)
        runCurrent()
        registry.undo(first)
        registry.undo(second)
        registry.undo(other)
        runCurrent()

        assertEquals(listOf("finalize 1", "finalize 2", "undo other"), calls)
    }

    @Test
    fun unknownTokensAreIgnored() = runTest {
        val registry = UndoRegistry(appScope())
        val issued = registry.register(UndoKind.TAB, undo = { calls += "undo" }, finalize = { calls += "finalize" })

        registry.undo(UndoToken(issued.id + 1))
        registry.finalize(UndoToken(-1))
        registry.cancelAll(UndoKind.HISTORY)
        runCurrent()

        assertEquals(emptyList<String>(), calls)
        assertNull(registry.kindOf(UndoToken(999)))
    }

    @Test
    fun kindOfIsKnownOnlyWhilePending() = runTest {
        val registry = UndoRegistry(appScope())
        val token = registry.register(UndoKind.SHORTCUT, undo = {})

        assertEquals(UndoKind.SHORTCUT, registry.kindOf(token))
        registry.finalize(token)
        assertNull(registry.kindOf(token))
    }

    @Test
    fun aFailingUndoIsReportedToTheAppScopeAndOthersStillRun() = runTest {
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> failures += e },
        )
        val registry = UndoRegistry(scope)
        val failing = registry.register(UndoKind.BOOKMARK, undo = { error("database closed") })
        val working = registry.register(UndoKind.BOOKMARK, undo = { calls += "undo" })

        registry.undo(failing)
        registry.undo(working)
        runCurrent()

        assertEquals(listOf("database closed"), failures.map { it.message })
        assertEquals(listOf("undo"), calls)
    }

    @Test
    fun eachTokenResolvesExactlyOnceUnderConcurrentCalls() {
        // Unconfined runs each block in the calling thread, so the race is between real threads.
        val registry = UndoRegistry(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val resolutions = List(500) { AtomicInteger() }
        val tokens = resolutions.mapIndexed { index, count ->
            registry.register(
                kind = if (index % 2 == 0) UndoKind.TAB else UndoKind.HISTORY,
                undo = { count.incrementAndGet() },
                finalize = { count.incrementAndGet() },
            )
        }
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        repeat(threads) { worker ->
            pool.execute {
                start.await()
                tokens.forEach { token ->
                    when (worker % 3) {
                        0 -> registry.undo(token)
                        1 -> registry.finalize(token)
                        else -> registry.cancelAll(UndoKind.TAB)
                    }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))

        assertEquals(List(tokens.size) { 1 }, resolutions.map { it.get() })
    }

    private fun TestScope.appScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
}
