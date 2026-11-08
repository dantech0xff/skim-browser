package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.ai.FeedbackDelivery
import com.dantech.skimbrowser.domain.ai.FeedbackReport
import com.dantech.skimbrowser.domain.ai.FeedbackSurface
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rating post carries no page, no answer, and no API key. */
class ProxyFeedbackSenderTest {

    @Test
    fun aRatingPostsTheFiveFieldsAndReadsTheStoredRevision() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"applied":true,"storedRevision":2}"""))
            val sender = sender(server)
            val result = sender.send(report())
            val recorded = server.takeRequest()
            assertEquals("/v1/feedback", recorded.path)
            assertEquals("install-id", recorded.getHeader("X-Skim-Install"))
            assertNull(recorded.getHeader("x-api-key"))
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("\"rating\":\"poor\""))
            assertTrue(body.contains("\"revision\":2"))
            assertTrue(!body.contains("example.com"))
            assertTrue(!body.contains("summary"))
            assertTrue(result is FeedbackDelivery.Accepted)
            assertEquals(2, (result as FeedbackDelivery.Accepted).storedRevision)
        }
    }

    @Test
    fun anOfflineProxyIsAFailureAndABlankUrlDoesNotConnect() = runBlocking {
        MockWebServer().use { server ->
            server.shutdown()
            val down = sender(server).send(report())
            assertTrue(down is FeedbackDelivery.Failed)
        }
        val blank = ProxyFeedbackSender(ClaudeClientProvider("") { "install-id" })
        assertTrue(blank.send(report()) is FeedbackDelivery.Failed)
    }

    private fun sender(server: MockWebServer) =
        ProxyFeedbackSender(ClaudeClientProvider(server.url("/").toString().removeSuffix("/")) { "install-id" })

    private fun report() = FeedbackReport(
        answerId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
        revision = 2,
        rating = MessageFeedback.POOR,
        surface = FeedbackSurface.PAGE,
        appVersion = "1.0",
    )
}
