package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
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
import kotlinx.serialization.json.booleanOrNull
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

/** The bytes of an across-tabs request: one document per tab, JSON when the answer is structured. */
@RunWith(AndroidJUnit4::class)
class TabsRequestShapeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val databases = mutableListOf<SkimDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun anOverviewSendsOneDocumentPerTabWithoutCitations() = runBlocking {
        FakeClaudeServer().use { server ->
            server.enqueue("tabs-overview.sse")
            val service = service(server.baseUrl)
            val session = allowedSession()
            val context = listOf(
                tabContext("1", "daylightjournal.com", title = "Heat pumps", text = "Cold models."),
                tabContext("2", "tabi-note.jp", title = "Night buses", text = null),
            )
            service.overviewTabs(session, context, "Summarize my 2 open tabs").toList()
            val root = Json.parseToJsonElement(server.take().body.readUtf8()).jsonObject
            assertShape(root, effort = "medium")
            val format = root["output_config"]!!.jsonObject["format"]!!.jsonObject
            assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
            assertClosedObjects(format["schema"]!!.jsonObject)
            val content = root.messageContent()
            assertEquals(3, content.size)
            assertDocument(content[0], title = "Heat pumps", text = "Cold models.", cached = false)
            assertDocument(content[1], title = "Night buses", text = NO_PAGE_TEXT, cached = true)
            assertEquals("Summarize my 2 open tabs", content[2].jsonObject["text"]!!.jsonPrimitive.content)
            assertNull(server.takeOrNull())
        }
    }

    @Test
    fun aFollowUpStaysPlainTextAndKeepsTheDocumentsOnTheFirstTurn() = runBlocking {
        FakeClaudeServer().use { server ->
            server.enqueue("tabs-overview.sse")
            server.enqueue("answer-plain.sse")
            val service = service(server.baseUrl)
            val session = allowedSession()
            val context = listOf(tabContext("1", PAGE_HOST, title = "Heat pumps", text = "Cold models."))
            service.overviewTabs(session, context, "Summarize").toList()
            server.take()
            service.askTabs(session, "What changed?").toList()
            val root = Json.parseToJsonElement(server.take().body.readUtf8()).jsonObject
            assertShape(root, effort = "medium")
            assertNull(root["output_config"]!!.jsonObject["format"])
            val messages = root["messages"]!!.jsonArray
            assertEquals(2, messages.size)
            assertEquals("document", messages[0].jsonObject["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
            assertTrue(messages[1].toString().contains("What changed?"))
        }
    }

    @Test
    fun groupingSendsTitlesAndHostsAndNoPageText() = runBlocking {
        FakeClaudeServer().use { server ->
            server.enqueue("tab-grouping.sse")
            val service = service(server.baseUrl)
            val context = listOf(
                tabContext("1", "daylightjournal.com", title = "Heat pumps", text = "PAGE_TEXT_SHOULD_NOT_APPEAR"),
                tabContext("2", "tabi-note.jp", title = "Night buses", text = "ALSO_HIDDEN"),
            )
            service.groupTabs(context)
            val request = server.take()
            assertNull(request.getHeader("anthropic-beta"))
            val body = request.body.readUtf8()
            val root = Json.parseToJsonElement(body).jsonObject
            assertShape(root, effort = "low")
            val content = root.messageContent()
            assertTrue(body.contains("Heat pumps"))
            assertTrue(body.contains("daylightjournal.com"))
            assertTrue(body.contains(NO_PAGE_TEXT))
            assertTrue(!body.contains("PAGE_TEXT_SHOULD_NOT_APPEAR"))
            content.dropLast(1).forEach { block ->
                val document = block.jsonObject
                assertNull(document["cache_control"])
                assertCitationsOff(document)
            }
            assertEquals(GROUPING_INSTRUCTION, content.last().jsonObject["text"]!!.jsonPrimitive.content)
        }
    }

    private fun assertShape(root: JsonObject, effort: String) {
        assertEquals("claude-haiku-5-5", root["model"]!!.jsonPrimitive.content)
        assertEquals(effort, root["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertNull(root["fallbacks"])
        assertNull(root["thinking"])
        assertNull(root["temperature"])
        assertNull(root["top_p"])
        assertNull(root["top_k"])
    }

    private fun assertDocument(block: kotlinx.serialization.json.JsonElement, title: String, text: String, cached: Boolean) {
        val document = block.jsonObject
        assertEquals("document", document["type"]!!.jsonPrimitive.content)
        assertEquals(title, document["title"]!!.jsonPrimitive.content)
        val source = document["source"]!!.jsonObject["content"]!!.jsonArray
        assertEquals(text, source.single().jsonObject["text"]!!.jsonPrimitive.content)
        assertCitationsOff(document)
        if (cached) {
            assertEquals("ephemeral", document["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        } else {
            assertNull(document["cache_control"])
        }
    }

    private fun assertCitationsOff(document: JsonObject) {
        val enabled = document["citations"]?.jsonObject?.get("enabled")?.jsonPrimitive?.booleanOrNull
        assertTrue(enabled != true)
    }

    private fun assertClosedObjects(node: JsonObject) {
        if (node["type"]?.jsonPrimitive?.content == "object") {
            assertEquals(false, node["additionalProperties"]?.jsonPrimitive?.booleanOrNull)
        }
        node.values.forEach { value ->
            when (value) {
                is JsonObject -> assertClosedObjects(value)
                else -> Unit
            }
        }
    }

    private fun JsonObject.messageContent() =
        get("messages")!!.jsonArray[0].jsonObject["content"]!!.jsonArray

    private fun service(baseUrl: String): SkimAiService {
        val db = inMemorySkimDatabase().also { databases += it }
        val settings = SettingsRepository(
            SettingsRepository.createDataStore(CoroutineScope(Dispatchers.IO)) {
                folder.newFile("settings-${databases.size}.preferences_pb")
            },
        )
        return SkimAiService(
            provider(baseUrl),
            SitePermissionRepository(db.sitePermissionDao(), db),
            settings,
        )
    }

    private fun FakeClaudeServer.takeOrNull() = try {
        server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        null
    }
}
