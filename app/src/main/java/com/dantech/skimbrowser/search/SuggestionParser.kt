package com.dantech.skimbrowser.search

import com.dantech.skimbrowser.domain.text.toVisibleText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** The most suggestions kept from one response; the engines send 8 to 12. */
internal const val MAX_SUGGESTIONS = 10

/** The longest suggestion kept, in characters; a longer one is dropped rather than cut. */
internal const val MAX_SUGGESTION_LENGTH = 256

/**
 * The suggestions of an OpenSearch suggestions response, `["query", ["s1", "s2", ...], ...]`: the strings
 * of element 1, in order. Google's extra elements after it are ignored. Each suggestion becomes
 * plain visible text (no control, bidi or zero-width characters, white space collapsed); blank ones,
 * repeats and ones longer than [MAX_SUGGESTION_LENGTH] are dropped, and at most [MAX_SUGGESTIONS]
 * are kept. A body that is not such an array (broken JSON, an object, a wrong type) gives none.
 */
fun parseOpenSearchSuggestions(body: String): List<String> {
    val root = try {
        // A byte order mark decoded as text is not part of the JSON.
        Json.parseToJsonElement(body.removePrefix("\uFEFF"))
    } catch (e: IllegalArgumentException) {
        return emptyList()
    }
    val suggestions = (root as? JsonArray)?.getOrNull(1) as? JsonArray ?: return emptyList()
    return suggestions.asSequence()
        .mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
        .map { it.toVisibleText() }
        .filter { it.isNotEmpty() && it.length <= MAX_SUGGESTION_LENGTH }
        .distinct()
        .take(MAX_SUGGESTIONS)
        .toList()
}
