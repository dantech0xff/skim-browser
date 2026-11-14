package com.dantech.skimbrowser.data.news

import com.dantech.skimbrowser.domain.news.NewsArticle
import com.dantech.skimbrowser.domain.news.NewsParser
import java.io.IOException
import java.io.StringReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

/** RSS and Atom, read with [XmlPullParser]. A broken document keeps the stories already read. */
class RssPullParser @Inject constructor() : NewsParser {
    override fun parse(xml: String, sourceId: String, sourceName: String): List<NewsArticle> {
        if (xml.isBlank()) return emptyList()
        val parser = try {
            factory.newPullParser()
        } catch (e: XmlPullParserException) {
            return emptyList()
        }
        val stories = ArrayList<NewsArticle>()
        try {
            parser.setInput(StringReader(xml))
            var inItem = false
            var title = ""
            var link = ""
            var date = ""
            var image: String? = null
            var capture: Field? = null
            val text = StringBuilder()
            val html = StringBuilder()
            var capturingHtml = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = localName(parser)
                        if (name == "item" || name == "entry") {
                            inItem = true
                            title = ""
                            link = ""
                            date = ""
                            image = null
                            capture = null
                            capturingHtml = false
                            text.setLength(0)
                            html.setLength(0)
                        } else if (inItem) {
                            when (name) {
                                "title" -> {
                                    capture = Field.TITLE
                                    text.setLength(0)
                                }
                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    val rel = parser.getAttributeValue(null, "rel")
                                    if (!href.isNullOrBlank() && (rel.isNullOrBlank() || rel == "alternate") && link.isEmpty()) {
                                        link = href.trim()
                                    } else if (href.isNullOrBlank()) {
                                        capture = Field.LINK
                                        text.setLength(0)
                                    }
                                }
                                "pubdate", "published", "updated", "date" -> if (date.isEmpty()) {
                                    capture = Field.DATE
                                    text.setLength(0)
                                }
                                "thumbnail" -> if (image == null) image = httpUrl(parser.getAttributeValue(null, "url"))
                                "content" -> {
                                    val url = httpUrl(parser.getAttributeValue(null, "url"))
                                    if (url != null && image == null && isImage(parser, url)) image = url
                                    if (url == null) {
                                        capturingHtml = true
                                        html.setLength(0)
                                    }
                                }
                                "enclosure" -> {
                                    val url = httpUrl(parser.getAttributeValue(null, "url"))
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    if (url != null && image == null && (type.isEmpty() || type.startsWith("image"))) {
                                        image = url
                                    }
                                }
                                "description", "summary" -> {
                                    capturingHtml = true
                                    html.setLength(0)
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                        if (capture != null) text.append(parser.text)
                        if (capturingHtml) html.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        val name = localName(parser)
                        when (capture) {
                            Field.TITLE -> if (name == "title") {
                                title = decode(text.toString())
                                capture = null
                            }
                            Field.LINK -> if (name == "link") {
                                if (link.isEmpty()) link = text.toString().trim()
                                capture = null
                            }
                            Field.DATE -> if (name == "pubdate" || name == "published" || name == "updated" || name == "date") {
                                date = text.toString().trim()
                                capture = null
                            }
                            null -> Unit
                        }
                        if (capturingHtml && (name == "description" || name == "summary" || name == "content")) {
                            if (image == null) image = firstImage(html.toString())
                            capturingHtml = false
                        }
                        if (inItem && (name == "item" || name == "entry")) {
                            if (title.isNotBlank() && link.isNotBlank()) {
                                stories += NewsArticle(link, title, sourceId, sourceName, parseNewsDate(date), image)
                            }
                            inItem = false
                            if (stories.size == PER_FEED) return stories
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: XmlPullParserException) {
            return stories
        } catch (e: IOException) {
            return stories
        }
        return stories
    }

    private enum class Field { TITLE, LINK, DATE }

    private companion object {
        const val PER_FEED = 40
        val factory: XmlPullParserFactory = XmlPullParserFactory.newInstance()
        val imageTag: Regex = Regex("""<img\b[^>]*\bsrc\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)

        fun localName(parser: XmlPullParser): String = parser.name.substringAfter(':').lowercase(Locale.US)

        fun httpUrl(raw: String?): String? {
            val url = raw?.trim().orEmpty()
            return url.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        }

        fun isImage(parser: XmlPullParser, url: String): Boolean {
            val medium = parser.getAttributeValue(null, "medium").orEmpty()
            val type = parser.getAttributeValue(null, "type").orEmpty()
            if (medium.equals("image", ignoreCase = true) || type.startsWith("image")) return true
            if (medium.isNotEmpty() || (type.isNotEmpty() && !type.contains("html", ignoreCase = true))) return false
            return IMAGE_EXTENSION.containsMatchIn(url)
        }

        fun firstImage(html: String): String? = imageTag.find(html)?.groupValues?.getOrNull(1)?.let { httpUrl(it) }

        fun decode(raw: String): String = raw.trim().replace(WHITESPACE, " ")

        val IMAGE_EXTENSION = Regex("""\.(jpe?g|png|webp|gif)(\?|$)""", RegexOption.IGNORE_CASE)
        val WHITESPACE = Regex("\\s+")
    }
}

/** Dates feeds actually send, including `GMT+7` and a two-digit year. Null when nothing matches. */
internal fun parseNewsDate(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()?.let { return it }
    runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
    val normalized = text.replace(GMT_OFFSET) { match ->
        val sign = match.groupValues[1]
        val hours = match.groupValues[2].padStart(2, '0')
        "$sign${hours}00"
    }
    for (pattern in DATE_PATTERNS) {
        try {
            val parsed = pattern.parse(normalized)
            if (parsed.get(ChronoField.YEAR) < 100) continue
            return Instant.from(parsed).toEpochMilli()
        } catch (e: DateTimeParseException) {
            continue
        } catch (e: java.time.DateTimeException) {
            continue
        }
    }
    return null
}

private val GMT_OFFSET = Regex("""GMT([+-])(\d{1,2})\s*$""", RegexOption.IGNORE_CASE)
private val DATE_PATTERNS = listOf(
    "EEE, dd MMM yy HH:mm:ss XX",
    "EEE, dd MMM yyyy HH:mm:ss XX",
).map { DateTimeFormatter.ofPattern(it, Locale.US) } + DateTimeFormatter.RFC_1123_DATE_TIME
