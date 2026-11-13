package com.dantech.skimbrowser.browser.page

import com.dantech.skimbrowser.domain.translate.PageTextNode
import com.dantech.skimbrowser.domain.translate.TranslatedText
import com.dantech.skimbrowser.domain.translate.TranslationChunks
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private val NODES = Json { ignoreUnknownKeys = true }

@Serializable
private data class TranslationCall(val op: String, val rows: List<TranslatedText> = emptyList())

@Serializable
private data class RawNode(val id: Int = -1, val text: String = "", val v: Boolean = false)

fun translationCallJson(op: String, rows: List<TranslatedText> = emptyList()): String =
    escapeForScript(NODES.encodeToString(TranslationCall.serializer(), TranslationCall(op, rows)))

/**
 * The nodes in [json], the result of the collect script. A string-wrapped array is unwrapped.
 * Anything the page tampered with that is not a node is dropped, and the list stops at
 * [TranslationChunks.MAX_NODES].
 */
fun parseTranslationNodes(json: String): List<PageTextNode> {
    if (json.length > MAX_TRANSLATION_JSON_CHARS) return emptyList()
    val array = try {
        var element: JsonElement = NODES.parseToJsonElement(json)
        val wrapped = element as? JsonPrimitive
        if (wrapped != null && wrapped.isString) element = NODES.parseToJsonElement(wrapped.content)
        if (element == JsonNull) return emptyList()
        element as? JsonArray
    } catch (_: SerializationException) {
        return emptyList()
    } catch (_: IllegalArgumentException) {
        return emptyList()
    } ?: return emptyList()
    return array.mapNotNull { item ->
        val raw = try {
            NODES.decodeFromJsonElement(RawNode.serializer(), item)
        } catch (_: SerializationException) {
            return@mapNotNull null
        } catch (_: IllegalArgumentException) {
            return@mapNotNull null
        }
        val text = raw.text.trim()
        if (raw.id < 0 || text.isEmpty()) null else PageTextNode(raw.id, text, raw.v)
    }.take(TranslationChunks.MAX_NODES)
}

private const val MAX_TRANSLATION_JSON_CHARS = 200_000
