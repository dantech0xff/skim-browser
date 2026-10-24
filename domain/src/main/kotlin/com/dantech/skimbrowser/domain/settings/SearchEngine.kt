package com.dantech.skimbrowser.domain.settings

import java.net.URLEncoder

/**
 * The search engines of Settings › Search engine (12, 15). `{q}` in each template is replaced by
 * the query, form-encoded as UTF-8.
 */
enum class SearchEngine(val searchTemplate: String, val suggestTemplate: String) {
    GOOGLE(
        "https://www.google.com/search?q={q}",
        "https://www.google.com/complete/search?client=firefox&q={q}",
    ),
    BING(
        "https://www.bing.com/search?q={q}",
        "https://www.bing.com/osjson.aspx?query={q}&form=OSDJAS",
    ),
    DUCKDUCKGO(
        "https://duckduckgo.com/?q={q}",
        "https://ac.duckduckgo.com/ac/?q={q}&type=list",
    ),
    ECOSIA(
        "https://www.ecosia.org/search?q={q}",
        "https://ac.ecosia.org/autocomplete?q={q}&type=list",
    ),
    ;

    /** The results page for [q]. */
    fun searchUrl(q: String): String = searchTemplate.replace(QUERY, encode(q))

    /** The suggestions endpoint for [q] (an OpenSearch suggestions JSON response). */
    fun suggestUrl(q: String): String = suggestTemplate.replace(QUERY, encode(q))

    private companion object {
        const val QUERY = "{q}"

        // The Charset overload needs API 33; the charset name works from API 1.
        fun encode(q: String): String = URLEncoder.encode(q, "UTF-8")
    }
}
