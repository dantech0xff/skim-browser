package com.dantech.skimbrowser.domain.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationExchangeTest {

    @Test
    fun aRequestIsOneIdAndTextPerLine() {
        val text = TranslationExchange.requestText(
            listOf(PageTextNode(2, "Hello\tthere", true), PageTextNode(4, "line\none", false)),
        )

        assertEquals("2\tHello there\n4\tline one", text)
    }

    @Test
    fun aReplyKeepsOnlyLinesForIdsThatWereSent() {
        val cases = listOf(
            ReplyCase(
                raw = "2\tXin chào\n4\tMột dòng",
                ids = setOf(2, 4),
                parsed = mapOf(2 to "Xin chào", 4 to "Một dòng"),
            ),
            ReplyCase(
                raw = "Here is the translation:\n2\tXin chào\n9\tnope\n\n```\n4\t\n2\tsecond",
                ids = setOf(2, 4),
                parsed = mapOf(2 to "Xin chào"),
            ),
            ReplyCase(
                raw = "not a row\n\tmissing\nabc\tnope",
                ids = setOf(1),
                parsed = emptyMap(),
            ),
        )

        cases.forEach { case ->
            assertEquals(case.parsed, TranslationExchange.parseReply(case.raw, case.ids))
        }
    }

    private data class ReplyCase(val raw: String, val ids: Set<Int>, val parsed: Map<Int, String>)
}
