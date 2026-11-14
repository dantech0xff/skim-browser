package com.dantech.skimbrowser.data.news

import com.dantech.skimbrowser.domain.news.NewsArticle
import com.dantech.skimbrowser.domain.news.NewsPreferences
import com.dantech.skimbrowser.domain.news.NewsSnapshot
import com.dantech.skimbrowser.domain.news.NewsTopic

/** The two news files. A hand-written codec so the APK does not keep a JSON library for them. */
internal object NewsJson {
    fun encodePreferences(prefs: NewsPreferences): String = buildString {
        append("{\"enabled\":").append(prefs.enabled)
        append(",\"topic\":").append(quote(prefs.topic.name))
        append(",\"hidden\":").append(strings(prefs.hiddenSourceIds))
        append(",\"custom\":").append(strings(prefs.customFeedUrls))
        append('}')
    }

    fun decodePreferences(text: String): NewsPreferences {
        val values = readObject(text)
        val topic = runCatching { NewsTopic.valueOf(values.string("topic") ?: NewsTopic.TOP.name) }
            .getOrDefault(NewsTopic.TOP)
        return NewsPreferences(
            enabled = values.boolean("enabled") ?: true,
            topic = topic,
            hiddenSourceIds = values.strings("hidden").toSet(),
            customFeedUrls = values.strings("custom"),
        )
    }

    fun encodeSnapshot(snapshot: NewsSnapshot): String = buildString {
        append("{\"fetchedAt\":").append(snapshot.fetchedAtMillis).append(",\"articles\":[")
        snapshot.articles.forEachIndexed { index, article ->
            if (index > 0) append(',')
            val published = article.publishedAtMillis
            val thumbnail = article.thumbnailUrl
            append("{\"link\":").append(quote(article.link))
            append(",\"title\":").append(quote(article.title))
            append(",\"sourceId\":").append(quote(article.sourceId))
            append(",\"sourceName\":").append(quote(article.sourceName))
            append(",\"publishedAt\":")
            if (published == null) append("null") else append(published)
            append(",\"thumbnail\":")
            if (thumbnail == null) append("null") else append(quote(thumbnail))
            append('}')
        }
        append("]}")
    }

    fun decodeSnapshot(text: String): NewsSnapshot {
        val values = readObject(text)
        val articles = values.objects("articles").mapNotNull { item ->
            val link = item.string("link") ?: return@mapNotNull null
            val title = item.string("title") ?: return@mapNotNull null
            if (link.isBlank() || title.isBlank()) return@mapNotNull null
            NewsArticle(
                link = link,
                title = title,
                sourceId = item.string("sourceId").orEmpty(),
                sourceName = item.string("sourceName").orEmpty(),
                publishedAtMillis = item.long("publishedAt"),
                thumbnailUrl = item.string("thumbnail")?.takeIf { it.isNotBlank() },
            )
        }
        return NewsSnapshot(articles, values.long("fetchedAt") ?: 0L)
    }

    private fun strings(values: Iterable<String>): String =
        values.joinToString(prefix = "[", postfix = "]", separator = ",") { quote(it) }

    private fun quote(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u").append(ch.code.toString(16).padStart(4, '0')) else append(ch)
            }
        }
        append('"')
    }

    private fun readObject(text: String): JsonObject {
        val reader = Reader(text)
        reader.skip()
        return reader.objectValue()
    }

    private class JsonObject(private val fields: Map<String, Any?>) {
        fun string(name: String): String? = fields[name] as? String
        fun boolean(name: String): Boolean? = fields[name] as? Boolean
        fun long(name: String): Long? = (fields[name] as? Number)?.toLong()
        fun strings(name: String): List<String> = (fields[name] as? List<*>)?.filterIsInstance<String>().orEmpty()
        fun objects(name: String): List<JsonObject> = (fields[name] as? List<*>)?.filterIsInstance<JsonObject>().orEmpty()
    }

    private class Reader(private val text: String) {
        private var index = 0

        fun skip() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun objectValue(): JsonObject {
            expect('{')
            val fields = linkedMapOf<String, Any?>()
            skip()
            if (peek('}')) return JsonObject(fields)
            while (true) {
                val name = string()
                skip()
                expect(':')
                fields[name] = value()
                skip()
                if (peek('}')) return JsonObject(fields)
                expect(',')
                skip()
            }
        }

        private fun value(): Any? {
            skip()
            if (index >= text.length) error("Unexpected end")
            return when (text[index]) {
                '"' -> string()
                '{' -> objectValue()
                '[' -> array()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun array(): List<Any?> {
            expect('[')
            val items = mutableListOf<Any?>()
            skip()
            if (peek(']')) return items
            while (true) {
                items += value()
                skip()
                if (peek(']')) return items
                expect(',')
                skip()
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (index < text.length) {
                val ch = text[index++]
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= text.length) throw IllegalArgumentException("truncated escape")
                        val escaped = text[index++]
                        out.append(
                            when (escaped) {
                                '"', '\\', '/' -> escaped
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> {
                                    if (index + 4 > text.length) throw IllegalArgumentException("truncated escape")
                                    text.substring(index, index + 4).toInt(16).toChar().also { index += 4 }
                                }
                                else -> escaped
                            },
                        )
                    }
                    else -> out.append(ch)
                }
            }
            error("Unclosed string")
        }

        private fun number(): Long {
            val start = index
            if (text[index] == '-') index++
            while (index < text.length && text[index].isDigit()) index++
            return text.substring(start, index).toLong()
        }

        private fun literal(word: String, value: Any?): Any? {
            require(text.startsWith(word, index))
            index += word.length
            return value
        }

        private fun expect(ch: Char) {
            skip()
            require(index < text.length && text[index] == ch) { "Expected $ch" }
            index++
        }

        private fun peek(ch: Char): Boolean {
            if (index < text.length && text[index] == ch) {
                index++
                return true
            }
            return false
        }
    }
}
