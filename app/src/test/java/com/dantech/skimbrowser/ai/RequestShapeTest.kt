package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The bytes sent to the model: one shape, no fallback, and a stable first turn. */
@RunWith(AndroidJUnit4::class)
class RequestShapeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val databases = mutableListOf<SkimDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun aSummaryThenAQuestionKeepTheOpeningAndNameTheModel() = runBlocking {
        FakeClaudeServer().use { server ->
            server.enqueue("summary-with-citations.sse")
            server.enqueue("answer-plain.sse")
            val session = allowedSession()
            val service = service(server.baseUrl, apiKey = "test-key")
            service.summarize(session).toList()
            val summary = server.take()
            val summaryBody = summary.body.readUtf8()
            service.ask(session, "Key numbers").toList()
            val question = server.take()
            val questionBody = question.body.readUtf8()

            listOf(summary to summaryBody, question to questionBody).forEach { (request, body) ->
                assertTrue(request.path!!.startsWith("/v1/messages"))
                assertTrue("path ${request.path}", !request.path!!.contains("beta"))
                assertNull(request.getHeader("anthropic-beta"))
                val root = Json.parseToJsonElement(body).jsonObject
                assertEquals("claude-haiku-5-5", root["model"]!!.jsonPrimitive.content)
                assertEquals("medium", root["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
                assertNull(root["fallbacks"])
                assertNull(root["thinking"])
                assertNull(root["temperature"])
                assertNull(root["top_p"])
                assertNull(root["top_k"])
                assertEquals(Prompts.SYSTEM, root.systemText())
                val document = root.messageContent(0)
                assertEquals("document", document["type"]!!.jsonPrimitive.content)
                val source = document["source"]!!.jsonObject
                assertEquals("content", source["type"]!!.jsonPrimitive.content)
                assertEquals(2, source["content"]!!.jsonArray.size)
                assertEquals(true, document["citations"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
                assertEquals("ephemeral", document["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            }
            assertEquals("test-key", summary.getHeader("x-api-key"))
            assertNull(summary.getHeader("X-Skim-Install"))
            assertEquals(firstMessage(summaryBody), firstMessage(questionBody))
        }
    }

    @Test
    fun aProxySendsTheInstallIdAndNoKey() = runBlocking {
        FakeClaudeServer().use { server ->
            server.enqueue("answer-plain.sse")
            service(server.baseUrl, apiKey = null, installId = "install-1").summarize(allowedSession()).toList()
            val request = server.take()
            assertNull(request.getHeader("x-api-key"))
            assertEquals("install-1", request.getHeader("X-Skim-Install"))
        }
    }

    private suspend fun service(baseUrl: String, apiKey: String?, installId: String = "install-id"): SkimAiService {
        val db = inMemorySkimDatabase().also { databases += it }
        val settings = SettingsRepository(
            SettingsRepository.createDataStore(CoroutineScope(Dispatchers.IO)) {
                folder.newFile("settings-${databases.size}.preferences_pb")
            },
        )
        val sites = SitePermissionRepository(db.sitePermissionDao(), db)
        settings.setAskBeforeSending(false)
        sites.setSkimAi(PAGE_HOST, SkimAiState.ALLOWED)
        return SkimAiService(ClaudeClientProvider(baseUrl, apiKey) { installId }, sites, settings)
    }
}

internal fun allowedSession(
    incognito: Boolean = false,
    host: String = PAGE_HOST,
    approvedHost: String = PAGE_HOST,
) = ChatSession(
    chatId = -1,
    tabId = "tab-1",
    pageUrl = PAGE_URL,
    approvedHost = approvedHost,
    sources = listOf(AiSource("tab-1", incognito, host)),
    document = articlePage().let { page ->
        if (host == PAGE_HOST) page else page.copy(url = "https://$host/moved")
    },
    languageTag = "en",
)

private fun JsonObject.systemText(): String {
    val system = get("system") ?: return ""
    return if (system is JsonPrimitive) system.content else system.toString()
}

private fun JsonObject.messageContent(index: Int) =
    get("messages")!!.jsonArray[0].jsonObject["content"]!!.jsonArray[index].jsonObject

private fun firstMessage(body: String): String {
    val start = body.indexOf('{', body.indexOf("\"messages\""))
    var depth = 0
    var index = start
    do {
        when (body[index]) {
            '{' -> depth++
            '}' -> depth--
        }
        index++
    } while (depth > 0)
    return body.substring(start, index)
}
