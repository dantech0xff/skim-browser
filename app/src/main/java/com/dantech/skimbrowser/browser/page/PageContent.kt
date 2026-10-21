package com.dantech.skimbrowser.browser.page

import com.dantech.skimbrowser.browser.cutTo
import com.dantech.skimbrowser.browser.toVisibleText
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The text of a page, as Reader view (08) shows it and Skim AI reads it: the leaf blocks of the
 * article (or of the whole page), in document order. [url] is the URL the page had committed and
 * [loadNonce] the document load it was read from, so a later highlight can check that the same
 * document is still there.
 */
@Serializable
data class PageContent(
    val url: String,
    val loadNonce: Long,
    val title: String,
    val byline: String?,
    val siteName: String?,
    val lang: String?,
    val readerable: Boolean,
    val blocks: List<PageBlock>,
)

/** One block of text; [id] is the block's `data-skim-i` number in the page, [tag] its element. */
@Serializable
data class PageBlock(val id: String, val tag: String, val text: String)

/**
 * The [PageContent] in [json], the result of the extract script, for the page [committedUrl] at
 * [loadNonce]; null when there is no page object. The script may return the object or a JSON string
 * holding it.
 *
 * The page's own scripts run beside the extractor and can tamper with what it returns, so nothing
 * is taken as given: block ids must be numbers, tags fall back to `p`, invisible and control
 * characters are removed, empty blocks dropped, and the result is cut to [MAX_BLOCKS] blocks and
 * [MAX_TEXT_CHARS] characters of text.
 */
fun parsePageContent(json: String, committedUrl: String, loadNonce: Long): PageContent? {
    if (json.length > MAX_JSON_CHARS) return null
    val raw = try {
        var element: JsonElement = PAGE_JSON.parseToJsonElement(json)
        val wrapped = element as? JsonPrimitive
        if (wrapped != null && wrapped.isString) element = PAGE_JSON.parseToJsonElement(wrapped.content)
        if (element == JsonNull) return null
        PAGE_JSON.decodeFromJsonElement(RawPage.serializer(), element)
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }

    var budget = MAX_TEXT_CHARS
    val blocks = ArrayList<PageBlock>()
    for (block in raw.blocks) {
        if (blocks.size == MAX_BLOCKS || budget == 0) break
        val id = block.id?.takeIf { BLOCK_ID.matches(it) } ?: continue
        val text = block.text?.toVisibleText()?.cutTo(budget)?.takeIf { it.isNotEmpty() } ?: continue
        val tag = block.tag?.lowercase(Locale.ROOT)?.takeIf { it in BLOCK_TAGS } ?: "p"
        blocks += PageBlock(id, tag, text)
        budget -= text.length
    }
    return PageContent(
        url = committedUrl,
        loadNonce = loadNonce,
        title = raw.title?.toVisibleText()?.cutTo(MAX_TITLE_CHARS).orEmpty(),
        byline = raw.byline?.toVisibleText()?.cutTo(MAX_META_CHARS)?.ifEmpty { null },
        siteName = raw.siteName?.toVisibleText()?.cutTo(MAX_META_CHARS)?.ifEmpty { null },
        lang = raw.lang?.trim()?.takeIf { LANGUAGE_TAG.matches(it) },
        readerable = raw.readerable,
        blocks = blocks,
    )
}

/** A block id as the extractor numbers blocks: digits only, so it can never carry code. */
internal val BLOCK_ID = Regex("\\d{1,9}")

/** The most blocks kept from a page. */
const val MAX_BLOCKS = 400

/** The most characters of block text kept from a page. */
const val MAX_TEXT_CHARS = 100_000

private const val MAX_JSON_CHARS = 2 * 1024 * 1024
private const val MAX_TITLE_CHARS = 1_024
private const val MAX_META_CHARS = 256
private val BLOCK_TAGS = setOf("p", "li", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "figcaption")
private val LANGUAGE_TAG = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8}){0,4}")

private val PAGE_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

@Serializable
private data class RawPage(
    val title: String? = null,
    val byline: String? = null,
    val siteName: String? = null,
    val lang: String? = null,
    val readerable: Boolean = false,
    val blocks: List<RawBlock> = emptyList(),
)

@Serializable
private data class RawBlock(val id: String? = null, val tag: String? = null, val text: String? = null)
