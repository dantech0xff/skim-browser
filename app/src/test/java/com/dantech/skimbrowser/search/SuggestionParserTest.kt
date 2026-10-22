package com.dantech.skimbrowser.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** OpenSearch suggestion bodies as the four engines send them, and every way a body can be wrong. */
class SuggestionParserTest {

    @Test
    fun googleGivesElementOneAndItsExtraElementsAreIgnored() {
        assertEquals(
            listOf(
                "heat pump vs furnace",
                "heat pump",
                "heat pump cost",
                "heat pump rebates 2026",
                "heat pump water heater",
                "heat pump dryer",
                "heat pump vs air conditioner",
                "heat pump hvac",
                "heat pump systems",
                "heat pumps explained",
            ),
            parseOpenSearchSuggestions(fixture("google")),
        )
    }

    @Test
    fun bingGivesItsFirstTenOfTwelve() {
        val parsed = parseOpenSearchSuggestions(fixture("bing"))

        assertEquals(MAX_SUGGESTIONS, parsed.size)
        assertEquals(listOf("heat pump", "heat pump water heater", "heat pump systems"), parsed.take(3))
        assertEquals("heat pump vs air conditioner", parsed.last())
    }

    @Test
    fun duckDuckGoAndEcosiaGiveTheirLists() {
        assertEquals(
            listOf(
                "heat pump",
                "heat pump vs furnace",
                "heat pump cost",
                "heat pump rebates 2026",
                "heat pump water heater",
                "heat pump systems",
                "heat pump dryer",
                "heat pump hvac",
            ),
            parseOpenSearchSuggestions(fixture("duckduckgo")),
        )
        assertEquals(
            listOf(
                "heat pump",
                "heat pumps explained",
                "heat pump hvac",
                "heat pump ac unit",
                "heat pump systems",
                "heat pump dryer",
                "heat pump water heater",
                "heat pumps for home heating",
            ),
            parseOpenSearchSuggestions(fixture("ecosia")),
        )
    }

    @Test
    fun anExtraArrayOfStringsAfterTheSuggestionsIsNotTakenForThem() {
        val body = """["heat",["heat pump"],["Heat pumps – Wikipedia"],["https://en.wikipedia.org/wiki/Heat_pump"]]"""

        assertEquals(listOf("heat pump"), parseOpenSearchSuggestions(body))
    }

    @Test
    fun aBodyThatIsNotSuggestionsGivesNone() {
        listOf(
            "",
            "   ",
            "not json",
            "<html><body>Sorry</body></html>",
            "[",
            """["heat",["heat pump"""",
            "null",
            "42",
            "\"heat\"",
            "{}",
            """{"query":"heat","suggestions":["heat pump"]}""",
            "[]",
            """["heat"]""",
            """["heat","heat pump"]""",
            """["heat",{"0":"heat pump"}]""",
            """["heat",null]""",
            """[["heat pump"],"heat"]""",
        ).forEach { body -> assertEquals(body, emptyList<String>(), parseOpenSearchSuggestions(body)) }
    }

    @Test
    fun onlyStringsAreSuggestions() {
        val body = """["heat",[1,"heat pump",null,true,{"a":"b"},["nested"],"heat pump cost",2.5]]"""

        assertEquals(listOf("heat pump", "heat pump cost"), parseOpenSearchSuggestions(body))
    }

    @Test
    fun atMostTenAreKept() {
        val body = suggestionsBody((1..50).map { "heat pump $it" })

        assertEquals((1..MAX_SUGGESTIONS).map { "heat pump $it" }, parseOpenSearchSuggestions(body))
    }

    @Test
    fun aSuggestionLongerThanTheLimitIsDroppedNotCut() {
        val longest = "a".repeat(MAX_SUGGESTION_LENGTH)
        val tooLong = "b".repeat(MAX_SUGGESTION_LENGTH + 1)

        assertEquals(listOf(longest, "heat pump"), parseOpenSearchSuggestions(suggestionsBody(listOf(longest, tooLong, "heat pump"))))
    }

    @Test
    fun suggestionsBecomePlainVisibleText() {
        val body = suggestionsBody(
            listOf(
                "\u202Eheat pump\u202C",
                "heat\u200B pump cost",
                "heat\npump\trebates",
                "  padded  ",
                "\u0007bell",
                "tag\uDB40\uDC41ged",
            ),
        )

        assertEquals(
            listOf("heat pump", "heat pump cost", "heat pump rebates", "padded", "bell", "tagged"),
            parseOpenSearchSuggestions(body),
        )
    }

    @Test
    fun blankAndRepeatedSuggestionsAreDropped() {
        val body = suggestionsBody(listOf("heat pump", "", "   ", "\u200B\u200D", "heat  pump", "heat pump", "heat pump cost"))

        assertEquals(listOf("heat pump", "heat pump cost"), parseOpenSearchSuggestions(body))
    }

    @Test
    fun jsonEscapesAreDecoded() {
        // Escapes as JSON writes them: the parser decodes them.
        val body = """["caf\u00e9",["caf\u00e9 au lait","\ud83d\ude00 smile","quote \" and \\ slash"]]"""

        assertEquals(listOf("café au lait", "\uD83D\uDE00 smile", "quote \" and \\ slash"), parseOpenSearchSuggestions(body))
    }

    @Test
    fun aByteOrderMarkBeforeTheBodyIsIgnored() {
        assertEquals(listOf("heat pump"), parseOpenSearchSuggestions("\uFEFF" + """["heat",["heat pump"]]"""))
    }

    @Test
    fun deeplyNestedJsonDoesNotOverflowTheStack() {
        val body = "[\"heat\"," + "[".repeat(20_000) + "]".repeat(20_000) + "]"

        assertTrue(parseOpenSearchSuggestions(body).isEmpty())
    }

    private fun suggestionsBody(suggestions: List<String>): String =
        suggestions.joinToString(prefix = "[\"heat\",[", postfix = "]]", separator = ",") { "\"" + jsonEscape(it) + "\"" }

    private fun jsonEscape(text: String): String = buildString {
        text.forEach { char ->
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char < ' ' || char.code in 0x7F..0x9F || !char.isDefined() || char.isSurrogate() || char.category == CharCategory.FORMAT ->
                    append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> append(char)
            }
        }
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("suggest/$name.json")) { "Missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
}
