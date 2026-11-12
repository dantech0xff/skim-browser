package com.dantech.skimbrowser.domain.shield

import com.dantech.skimbrowser.domain.url.normalizeHost
import java.io.BufferedReader

/**
 * Element-hiding rules compiled from EasyList and EasyPrivacy. A page gets one stylesheet:
 * generic selectors, then the selectors for its host and each parent, minus the exceptions that
 * name that host. Procedural and scriptlet rules are not in the file.
 *
 * The lists are shortest-first, so a page that hits the cap keeps the short class and id rules.
 * Generic rules are taken first, up to [MAX_GENERIC_SELECTORS], because those are the common
 * slots. Domain rules fill the rest of [MAX_SELECTORS].
 */
class CosmeticFilters private constructor(
    private val generic: List<Hide>,
    private val genericExcept: Set<String>,
    private val domainHide: Map<String, List<Hide>>,
    private val domainExcept: Map<String, Set<String>>,
) {
    /**
     * The stylesheet for [host], or empty when nothing applies. [host] is the form [normalizeHost]
     * returns. The text is one rule per selector, so one selector the page cannot parse does not
     * drop the rest.
     */
    fun cssFor(host: String): String {
        val key = normalizeHost(host)
        if (!key.contains('.')) return ""
        val excluded = HashSet<String>(genericExcept.size + 8)
        excluded.addAll(genericExcept)
        for (suffix in hostSuffixes(key)) {
            domainExcept[suffix]?.let { excluded.addAll(it) }
        }
        val seen = HashSet<String>()
        val genericChosen = take(generic, key, excluded, seen, MAX_GENERIC_SELECTORS)
        val specificBudget = MAX_SELECTORS - genericChosen.size
        val specific = ArrayList<String>(minOf(specificBudget, 32))
        if (specificBudget > 0) {
            for (suffix in hostSuffixes(key)) {
                if (specific.size >= specificBudget) break
                takeInto(domainHide[suffix], key, excluded, seen, specificBudget, specific)
            }
        }
        if (specific.isEmpty() && genericChosen.isEmpty()) return ""
        return stylesheet(specific, genericChosen)
    }

    private class Hide(val selector: String, val unless: Array<String>)

    companion object {
        val EMPTY = CosmeticFilters(emptyList(), emptySet(), emptyMap(), emptyMap())

        /**
         * How many selectors one page may hide. Generic rules use the first
         * [MAX_GENERIC_SELECTORS] of that budget, shortest first. Domain rules use the rest.
         */
        const val MAX_SELECTORS = 2300

        /** How many of [MAX_SELECTORS] may be generic. */
        const val MAX_GENERIC_SELECTORS = 2000

        const val MAX_COLLAPSE_HOSTS = 40

        /**
         * Reads the compiled asset. A line is `G`, `E`, `H` or `X`, then tab-separated fields.
         * `G` and `H` may end with `!domain` for a host the rule skips. `#` lines are the header.
         * A line this browser cannot apply is dropped.
         */
        fun load(reader: BufferedReader): CosmeticFilters {
            val generic = ArrayList<Hide>()
            val genericExcept = HashSet<String>()
            val domainHide = HashMap<String, ArrayList<Hide>>()
            val domainExcept = HashMap<String, HashSet<String>>()
            reader.forEachLine { raw ->
                val line = raw.removePrefix("\uFEFF").trimEnd('\r')
                if (line.length < 3 || line[0] == '#') return@forEachLine
                val tab = line.indexOf('\t')
                if (tab != 1) return@forEachLine
                when (line[0]) {
                    'G' -> parseHide(line.substring(2))?.let { generic += it }
                    'E' -> parseSelector(line.substring(2))?.let { genericExcept += it }
                    'H' -> parseDomain(line.substring(2))?.let { (domain, hide) ->
                        domainHide.getOrPut(domain) { ArrayList() } += hide
                    }
                    'X' -> parseDomainSelector(line.substring(2))?.let { (domain, selector) ->
                        domainExcept.getOrPut(domain) { HashSet() } += selector
                    }
                }
            }
            return CosmeticFilters(
                freeze(generic),
                genericExcept,
                domainHide.mapValues { freeze(it.value) },
                domainExcept,
            )
        }

        /**
         * Hides frames and images whose request was blocked. [hosts] are the form hostOf returns.
         * The needle is `//host` with a delimiter after it, so an absolute URL and a
         * protocol-relative `//host/...` both match, and `ads.example` does not hide
         * `ads.example.else`. The `www` form is covered too, because the address bar key drops
         * `www` and the element keeps it.
         */
        fun collapseBlockedCss(hosts: Iterable<String>): String {
            val out = StringBuilder()
            var count = 0
            val seen = HashSet<String>()
            for (host in hosts) {
                if (count == MAX_COLLAPSE_HOSTS) break
                val key = host.trim().lowercase()
                if (!seen.add(key) || !collapseHostOk(key)) continue
                appendCollapse(out, key)
                count++
            }
            return out.toString()
        }

        private fun take(
            hides: List<Hide>?,
            host: String,
            excluded: Set<String>,
            seen: MutableSet<String>,
            limit: Int,
        ): List<String> {
            if (hides == null || limit <= 0) return emptyList()
            val out = ArrayList<String>(minOf(limit, hides.size))
            takeInto(hides, host, excluded, seen, limit, out)
            return out
        }

        private fun takeInto(
            hides: List<Hide>?,
            host: String,
            excluded: Set<String>,
            seen: MutableSet<String>,
            maxSize: Int,
            out: MutableList<String>,
        ) {
            if (hides == null || out.size >= maxSize) return
            for (hide in hides) {
                if (out.size >= maxSize) return
                val selector = hide.selector
                if (selector in excluded || selector in seen || skipped(host, hide.unless)) continue
                seen.add(selector)
                out.add(selector)
            }
        }

        private fun skipped(host: String, unless: Array<String>): Boolean {
            for (domain in unless) {
                if (host == domain || host.endsWith(".$domain")) return true
            }
            return false
        }

        private fun stylesheet(specific: List<String>, generic: List<String>): String {
            val out = StringBuilder((specific.size + generic.size) * 32)
            appendRules(out, specific)
            appendRules(out, generic)
            return out.toString()
        }

        private fun appendRules(out: StringBuilder, selectors: List<String>) {
            for (selector in selectors) {
                out.append(selector).append("{display:none !important}\n")
            }
        }

        private fun appendCollapse(out: StringBuilder, host: String) {
            val tags = arrayOf("iframe", "img", "object", "embed")
            var first = true
            fun one(selector: String) {
                if (!first) out.append(',')
                first = false
                out.append(selector)
            }
            for (name in arrayOf(host, "www.$host")) {
                for (tag in tags) {
                    one("$tag[src*=\"//$name/\"]")
                    one("$tag[src*=\"//$name?\"]")
                    one("$tag[src*=\"//$name#\"]")
                    one("$tag[src$=\"//$name\"]")
                }
            }
            out.append("{display:none !important}\n")
        }

        private fun collapseHostOk(host: String): Boolean {
            if (host.length < 3 || '.' !in host || host.startsWith('.') || host.endsWith('.')) return false
            for (c in host) {
                val ok = c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-'
                if (!ok) return false
            }
            return true
        }

        private fun freeze(hides: List<Hide>): List<Hide> =
            hides.sortedWith(compareBy({ it.selector.length }, { it.selector }))

        private fun parseHide(rest: String): Hide? {
            val parts = split(rest) ?: return null
            val selector = acceptedSelector(parts[0]) ?: return null
            val unless = ArrayList<String>(parts.size - 1)
            for (index in 1 until parts.size) {
                val domain = exceptionDomain(parts[index]) ?: return null
                unless += domain
            }
            return Hide(selector, if (unless.isEmpty()) NO_UNLESS else unless.toTypedArray())
        }

        private fun parseSelector(rest: String): String? {
            if ('\t' in rest) return null
            return acceptedSelector(rest)
        }

        private fun parseDomain(rest: String): Pair<String, Hide>? {
            val parts = split(rest) ?: return null
            if (parts.size < 2) return null
            val domain = acceptedDomain(parts[0]) ?: return null
            val selector = acceptedSelector(parts[1]) ?: return null
            val unless = ArrayList<String>(parts.size - 2)
            for (index in 2 until parts.size) {
                val skipped = exceptionDomain(parts[index]) ?: return null
                unless += skipped
            }
            return domain to Hide(selector, if (unless.isEmpty()) NO_UNLESS else unless.toTypedArray())
        }

        private fun parseDomainSelector(rest: String): Pair<String, String>? {
            val tab = rest.indexOf('\t')
            if (tab <= 0 || rest.indexOf('\t', tab + 1) >= 0) return null
            val domain = acceptedDomain(rest.substring(0, tab)) ?: return null
            val selector = acceptedSelector(rest.substring(tab + 1)) ?: return null
            return domain to selector
        }

        private fun exceptionDomain(field: String): String? {
            if (field.length < 2 || field[0] != '!') return null
            return acceptedDomain(field.substring(1))
        }

        private fun split(rest: String): List<String>? {
            if (rest.isEmpty()) return null
            val parts = rest.split('\t')
            if (parts.any { it.isEmpty() }) return null
            return parts
        }

        private fun acceptedDomain(domain: String): String? {
            if (domain.length !in 3..253 || '.' !in domain) return null
            if (domain.startsWith('.') || domain.endsWith('.') || domain.startsWith('-')) return null
            for (c in domain) {
                val ok = c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-'
                if (!ok) return null
            }
            return domain
        }

        /** A selector that can sit in one CSS rule. Extended and scriptlet syntax is refused. */
        private fun acceptedSelector(selector: String): String? {
            if (selector.length !in 2..SELECTOR_LIMIT) return null
            if (selector in PAGE_WIDE) return null
            val lower = selector.lowercase()
            for (token in EXTENDED) {
                if (token in lower) return null
            }
            for (c in selector) {
                if (c == '{' || c == '}' || c == '<' || c == '\\' || c == '@' || c == '\u0000') return null
            }
            if ("url(" in lower || "expression(" in lower || "/*" in selector) return null
            return selector
        }

        private val NO_UNLESS = emptyArray<String>()
        private const val SELECTOR_LIMIT = 200
        private val PAGE_WIDE = setOf("*", "html", "body", ":root")
        private val EXTENDED = arrayOf(
            ":-abp-",
            ":xpath(",
            ":has-text(",
            ":matches-css",
            ":matches-attr(",
            ":upward(",
            ":remove(",
            ":style(",
            ":min-text-length(",
            ":watch-attr(",
            ":others(",
            ":matches-path(",
        )
    }
}
