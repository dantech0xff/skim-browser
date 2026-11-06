package com.dantech.skimbrowser.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Streaming replies drop thinking and stop when the collector stops. */
class ClaudeStreamingTest {

    @Test
    fun thinkingIsSkippedAndACitationFollowsTheText() = runBlocking {
        FakeClaudeServer().use { server ->
            server.enqueue("summary-with-citations.sse")
            val events = provider(server.baseUrl).client().streamEvents(probeParams()).toList()

            val text = events.filterIsInstance<AiEvent.Text>()
            val cites = events.filterIsInstance<AiEvent.Cite>()
            assertEquals(listOf("- Modern cold-climate heat pumps keep heating at minus 25 C."), text.map { it.delta })
            assertEquals(AiEvent.Cite(1, 2, "minus 25"), cites.single())
            assertEquals(AiEvent.Done("end_turn"), events.filterIsInstance<AiEvent.Done>().single())
            assertTrue(events.last() is AiEvent.Final)
            assertTrue(events.indexOf(text.single()) < events.indexOf(cites.single()))
        }
    }

    @Test
    fun aRefusalStopsWithThatReason() = runBlocking {
        FakeClaudeServer().use { server ->
            server.enqueue("refusal.sse")
            val events = provider(server.baseUrl).client().streamEvents(probeParams()).toList()
            assertEquals("refusal", events.filterIsInstance<AiEvent.Done>().single().stopReason)
        }
    }

    @Test
    fun cancellingClosesTheStreamBeforeTheBodyFinishes() {
        val server = FakeClaudeServer()
        val slow = server.serveSlowly("slow-stream.sse")
        try {
            // The server holds the rest of the body for 2s. The clock starts at the first text
            // event, so class loading is not part of the 1.5s budget. Finishing under 1.5s means
            // the close did not wait for the rest of the body.
            runBlocking {
                val client = provider(slow.baseUrl).client()
                val firstText = kotlinx.coroutines.CompletableDeferred<Unit>()
                val job = launch {
                    client.streamEvents(probeParams()).collect { event ->
                        if (event is AiEvent.Text) {
                            firstText.complete(Unit)
                            throw kotlinx.coroutines.CancellationException()
                        }
                    }
                }
                withTimeout(10_000) { firstText.await() }
                val started = System.nanoTime()
                withTimeout(1_500) { job.join() }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                assertTrue("stream took ${elapsedMs}ms", elapsedMs < 1_500)
                assertTrue("sent ${slow.written()} of ${slow.size}", slow.written() < slow.size)
            }
        } finally {
            slow.close()
            server.close()
        }
    }
}
