package com.dantech.skimbrowser.domain.translate

/**
 * Packs page text for one translation request. Nodes on screen go first, then the rest, in the
 * order they were read. A node longer than [maxChars] is its own chunk so a short neighbour is
 * not held back. Blank text is dropped. At most [maxNodes] nodes are packed, and the on-screen
 * ones win that cap.
 */
object TranslationChunks {
    const val MAX_CHARS = 1_800
    const val MAX_NODES = 320

    /** How many chunks one Translate tap sends. The rest of a long page stays in its own language. */
    const val MAX_CHUNKS = 6

    fun plan(
        nodes: List<PageTextNode>,
        maxChars: Int = MAX_CHARS,
        maxNodes: Int = MAX_NODES,
    ): List<List<PageTextNode>> {
        val usable = nodes.filter { it.id >= 0 && it.text.isNotBlank() }.distinctBy { it.id }
        val ordered = (usable.filter { it.inViewport } + usable.filter { !it.inViewport }).take(maxNodes)
        val chunks = mutableListOf<MutableList<PageTextNode>>()
        var current = mutableListOf<PageTextNode>()
        var size = 0
        for (node in ordered) {
            val cost = node.text.length
            if (current.isNotEmpty() && size + cost > maxChars) {
                chunks += current
                current = mutableListOf()
                size = 0
            }
            current += node
            size += cost
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }
}
