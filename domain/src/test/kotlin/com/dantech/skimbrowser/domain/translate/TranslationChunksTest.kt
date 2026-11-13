package com.dantech.skimbrowser.domain.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationChunksTest {

    @Test
    fun onScreenNodesArePackedBeforeTheRest() {
        val nodes = listOf(
            node(1, "below", inViewport = false),
            node(2, "here", inViewport = true),
            node(3, "also", inViewport = true),
        )

        val chunks = TranslationChunks.plan(nodes, maxChars = 100)

        assertEquals(listOf(listOf(2, 3, 1)), ids(chunks))
    }

    @Test
    fun aChunkStopsBeforeItPassesTheCharacterBudget() {
        val cases = listOf(
            Case(maxChars = 10, nodes = listOf(node(1, "12345"), node(2, "12345"), node(3, "123456")), ids = listOf(listOf(1, 2), listOf(3))),
            Case(maxChars = 4, nodes = listOf(node(1, "12345"), node(2, "ab")), ids = listOf(listOf(1), listOf(2))),
            Case(maxChars = 20, nodes = listOf(node(1, "short"), node(2, "also")), ids = listOf(listOf(1, 2))),
        )

        cases.forEach { case ->
            assertEquals(case.ids, ids(TranslationChunks.plan(case.nodes, maxChars = case.maxChars)))
        }
    }

    @Test
    fun blankTextAndDuplicateIdsAreDroppedAndOnScreenNodesWinTheCap() {
        val nodes = listOf(
            node(1, "   ", inViewport = true),
            node(2, "seen", inViewport = true),
            node(2, "again", inViewport = false),
            node(3, "later", inViewport = false),
        )

        val chunks = TranslationChunks.plan(nodes, maxChars = 100, maxNodes = 1)

        assertEquals(listOf(listOf(2)), ids(chunks))
    }

    private data class Case(val maxChars: Int, val nodes: List<PageTextNode>, val ids: List<List<Int>>)

    private fun node(id: Int, text: String, inViewport: Boolean = true) = PageTextNode(id, text, inViewport)

    private fun ids(chunks: List<List<PageTextNode>>) = chunks.map { chunk -> chunk.map { it.id } }
}
