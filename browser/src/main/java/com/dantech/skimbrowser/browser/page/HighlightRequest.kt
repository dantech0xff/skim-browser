package com.dantech.skimbrowser.browser.page

import com.dantech.skimbrowser.browser.cutTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

typealias HighlightRequest = com.dantech.skimbrowser.domain.page.HighlightRequest

/**
 * The one argument of the highlight script. [url] is the page the citation was read from: the
 * script checks it against the page's own address before it touches anything, so a navigation
 * that commits after the tab manager's check still stops it.
 */
@Serializable
internal data class HighlightArgs(val id: String, val cited: String?, val url: String)

/**
 * The highlight script's argument for [request] as a JSON object, or null when its block id is not
 * a block id (digits only).
 *
 * Every value is a JSON string, so the text can never end the string it sits in, whatever it holds
 * (quotes, backticks, `${`, `</script>`). The line and paragraph separators U+2028 and U+2029,
 * which ended string literals before ES2019, and `<`, `>` and `&` are escaped as well, so the JSON
 * is safe in any script context.
 */
fun highlightArgsJson(request: HighlightRequest): String? {
    if (!BLOCK_ID.matches(request.blockId)) return null
    val cited = request.citedText?.cutTo(MAX_CITED_CHARS)?.takeIf { it.isNotBlank() }
    val args = HighlightArgs(request.blockId, cited, request.expectedUrl)
    return escapeForScript(Json.encodeToString(HighlightArgs.serializer(), args))
}

/**
 * [json] with U+2028, U+2029, `<`, `>` and `&` escaped, so that it stays one JSON value in any
 * script context; every page script argument goes through it.
 */
internal fun escapeForScript(json: String): String = buildString(json.length + 16) {
    json.forEach { char ->
        when (char) {
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            '<' -> append("\\u003c")
            '>' -> append("\\u003e")
            '&' -> append("\\u0026")
            else -> append(char)
        }
    }
}

/** The fallback search only needs the start of the cited passage. */
private const val MAX_CITED_CHARS = 1_000
