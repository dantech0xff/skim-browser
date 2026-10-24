package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** An across-tabs answer is checked before it is shown or saved. */
@RunWith(AndroidJUnit4::class)
class TabsOverviewTest {

    private val refs = (1..7).map { it.toString() }.toSet()

    @Test
    fun everyObjectRefusesExtraFields() {
        assertClosed(TABS_OVERVIEW_SCHEMA_MAP)
        assertClosed(GROUPING_SCHEMA_MAP)
        assertTrue(TABS_OVERVIEW_FORMAT.schema().toString().isNotEmpty())
        assertTrue(GROUPING_FORMAT.schema().toString().isNotEmpty())
    }

    @Test
    fun aFinishedAnswerKeepsKnownRefsAndDropsUnsafeLines() {
        val overview = overviewFromModel("end_turn", VALID, refs)
        assertEquals("Your tabs cover three topics.", overview?.intro)
        assertEquals(listOf("1", "2", "3", "4"), overview?.groups?.get(0)?.tabIds)
        assertEquals(listOf("Which installer looked best?", "What is left to book in Osaka?"), overview?.followUps)
    }

    @Test
    fun refusalAndTheTokenLimitAreNotParsed() {
        assertNull(overviewFromModel("refusal", VALID, refs))
        assertNull(overviewFromModel("max_tokens", VALID, refs))
        assertNull(groupingFromModel("refusal", GROUPING, setOf("1", "2", "3", "4")))
        assertNull(groupingFromModel("max_tokens", GROUPING, setOf("1", "2", "3", "4")))
    }

    @Test
    fun unsafeNamesUnknownRefsAndShortGroupsAreDropped() {
        val overview = parseTabsOverview(INJECTION, refs)
        assertEquals(listOf("Other", "Kept"), overview?.groups?.map { it.name })
        assertEquals(listOf("5"), overview?.groups?.get(0)?.tabIds)
        assertEquals(listOf("6"), overview?.groups?.get(1)?.tabIds)
        assertEquals(listOf("What changed?"), overview?.followUps)

        val grouping = groupingFromModel("end_turn", GROUPING_UNSAFE, setOf("1", "2", "3", "4", "5"))
        assertEquals(listOf("Energy"), grouping?.groups?.map { it.name })
        assertEquals(listOf("1", "2"), grouping?.groups?.single()?.tabIds)
    }

    @Test
    fun aBlankGroupNameBecomesOtherAndStoredJsonDoesNotNeedTheOriginalRefs() {
        val parsed = parseTabsOverview("""{"intro":"Hi","groups":[{"name":"  ","summary":"One tab.","tabIds":["1"]}],"followUps":[]}""", setOf("1"))
        assertEquals("Other", parsed?.groups?.single()?.name)
        val stored = decodeStoredOverview(encodeOverview(parsed!!))
        assertEquals("One tab.", stored?.groups?.single()?.summary)
        assertEquals("Hi", overviewSnippet(encodeOverview(parsed)))
    }

    private fun assertClosed(schema: Map<String, Any>) {
        fun walk(node: Any?) {
            if (node !is Map<*, *>) return
            if (node["type"] == "object") {
                assertEquals(false, node["additionalProperties"])
                assertFalse((node["required"] as List<*>).isEmpty())
            }
            node.values.forEach(::walk)
        }
        walk(schema)
    }

    private companion object {
        val VALID = javaClass.getResource("/sse/tabs-overview.sse")!!.readText().let(::textOf)
        val INJECTION = javaClass.getResource("/sse/tabs-overview-injection.sse")!!.readText().let(::textOf)
        const val GROUPING = """{"groups":[{"name":"Energy","tabIds":["1","2"]},{"name":"News","tabIds":["3","4"]}]}"""
        const val GROUPING_UNSAFE =
            """{"groups":[{"name":"Energy","tabIds":["1","2"]},{"name":"https://news.example","tabIds":["3","4"]},{"name":"Solo","tabIds":["5"]},{"name":"","tabIds":["1"]}]}"""
    }
}

/** The text of the single text delta in an SSE fixture. */
private fun textOf(sse: String): String {
    val line = sse.lineSequence().first { it.contains("text_delta") }.removePrefix("data: ")
    return Json.parseToJsonElement(line).jsonObject["delta"]!!.jsonObject["text"]!!.jsonPrimitive.content
}
