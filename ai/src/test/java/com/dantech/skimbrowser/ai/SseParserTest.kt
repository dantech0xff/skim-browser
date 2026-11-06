package com.dantech.skimbrowser.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The event-stream parser, without a socket. */
class SseParserTest {

    @Test
    fun aSplitChunkStillYieldsTextThenACitationThenTheFinalText() {
        val fixture = javaClass.classLoader.getResource("sse/summary-with-citations.sse")!!.readText()
        val parser = SseParser()
        val events = mutableListOf<AiEvent>()
        fixture.chunked(7).forEach { piece -> events += parser.push(piece) }
        events += parser.push("\n")

        val text = events.filterIsInstance<AiEvent.Text>()
        assertEquals(listOf("- Modern cold-climate heat pumps keep heating at minus 25 C."), text.map { it.delta })
        assertEquals(AiEvent.Cite(1, 2, "minus 25"), events.filterIsInstance<AiEvent.Cite>().single())
        assertEquals(AiEvent.Done("end_turn"), events.filterIsInstance<AiEvent.Done>().single())
        val final = events.filterIsInstance<AiEvent.Final>().single()
        assertEquals(text.single().delta, final.text)
        assertTrue(events.none { it is AiEvent.Text && it.delta.contains("The page is about") })
    }

    @Test
    fun anErrorEventFailsTheParse() {
        val parser = SseParser()
        val body = "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"overloaded\"}}\n\n"
        try {
            parser.push(body)
            fail("An error event was treated as text.")
        } catch (error: ProxyStreamException) {
            assertTrue(error.message!!.contains("overloaded"))
        }
    }

    @Test
    fun aCitationOutsideIntRangeIsDropped() {
        val parser = SseParser()
        val body = """
            event: content_block_delta
            data: {"type":"content_block_delta","delta":{"type":"citations_delta","citation":{"type":"content_block_location","cited_text":"x","start_block_index":-1,"end_block_index":1}}}

        """.trimIndent() + "\n"
        val events = parser.push(body)
        assertTrue(events.none { it is AiEvent.Cite })
    }

    @Test
    fun aStreamErrorIsAServerFailure() {
        assertTrue(ProxyStreamException("overloaded").toAiFailure() is com.dantech.skimbrowser.domain.ai.AiFailure.Server)
        assertTrue(ProxyHttpException(500).toAiFailure() is com.dantech.skimbrowser.domain.ai.AiFailure.Server)
        assertTrue(ProxyLimited().toAiFailure() is com.dantech.skimbrowser.domain.ai.AiFailure.DailyLimit)
        assertTrue(java.io.IOException("down").toAiFailure() is com.dantech.skimbrowser.domain.ai.AiFailure.Offline)
    }
}
