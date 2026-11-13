package com.dantech.skimbrowser.domain.translate

/**
 * The lines sent to the model and the lines read back. A line is an id, a tab, then the text.
 * Page text is untrusted, so a tab or a line break inside it is folded into a space before it is
 * sent. A reply line that is not `id<tab>text`, or whose id was not sent, is ignored.
 */
object TranslationExchange {
    fun requestText(nodes: List<PageTextNode>): String = nodes.joinToString("\n") { node ->
        "${node.id}\t${fold(node.text)}"
    }

    fun parseReply(raw: String, expectedIds: Set<Int>): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("```")) continue
            val tab = trimmed.indexOf('\t')
            if (tab <= 0) continue
            val id = trimmed.substring(0, tab).toIntOrNull() ?: continue
            if (id !in expectedIds || id in out) continue
            val text = trimmed.substring(tab + 1).trim()
            if (text.isEmpty()) continue
            out[id] = text
        }
        return out
    }

    private fun fold(text: String): String = text.replace('\t', ' ').replace(BREAKS, " ").trim()

    private val BREAKS = Regex("\\s+")
}
