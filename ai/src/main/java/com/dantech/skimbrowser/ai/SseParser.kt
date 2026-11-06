package com.dantech.skimbrowser.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Turns a `/v1/messages` event stream into [AiEvent]s. Thinking is dropped. An `error` event
 * fails the parse. [text] is everything emitted so far, for the final assistant turn.
 */
internal class SseParser {
    private val pending = StringBuilder()
    private val dataLines = mutableListOf<String>()
    private var eventName: String? = null
    private val text = StringBuilder()

    val assembledText: String get() = text.toString()

    fun push(chunk: String): List<AiEvent> {
        pending.append(chunk)
        val events = mutableListOf<AiEvent>()
        while (true) {
            val newline = pending.indexOf("\n")
            if (newline < 0) break
            var line = pending.substring(0, newline)
            pending.delete(0, newline + 1)
            if (line.endsWith("\r")) line = line.dropLast(1)
            when {
                line.isEmpty() -> events += finish()
                line.startsWith(":") -> Unit
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").removePrefix(" ")
                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
            }
        }
        return events
    }

    private fun finish(): List<AiEvent> {
        val data = dataLines.joinToString("\n")
        val name = eventName
        dataLines.clear()
        eventName = null
        if (data.isEmpty() || data == "[DONE]") return emptyList()
        val root = Json.parseToJsonElement(data).jsonObject
        val type = name ?: root["type"]?.jsonPrimitive?.contentOrNull
        if (type == "error") {
            val message = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: data
            throw ProxyStreamException(message)
        }
        return decode(type, root)
    }

    private fun decode(type: String?, root: JsonObject): List<AiEvent> = when (type) {
        "content_block_delta" -> delta(root["delta"]!!.jsonObject)
        "message_delta" -> {
            val reason = root["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
            if (reason == null) emptyList() else listOf(AiEvent.Done(reason))
        }
        "message_stop" -> listOf(AiEvent.Final(text.toString()))
        else -> emptyList()
    }

    private fun delta(delta: JsonObject): List<AiEvent> = when (delta["type"]?.jsonPrimitive?.contentOrNull) {
        "text_delta" -> {
            val piece = delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            text.append(piece)
            listOf(AiEvent.Text(piece))
        }
        "citations_delta" -> cite(delta["citation"]?.jsonObject)?.let { listOf(it) } ?: emptyList()
        else -> emptyList()
    }

    private fun cite(citation: JsonObject?): AiEvent.Cite? {
        if (citation == null) return null
        if (citation["type"]?.jsonPrimitive?.contentOrNull != "content_block_location") return null
        val start = citation["start_block_index"].intInRange() ?: return null
        val end = citation["end_block_index"].intInRange() ?: return null
        val cited = citation["cited_text"]?.jsonPrimitive?.contentOrNull ?: return null
        return AiEvent.Cite(start, end, cited)
    }
}

private fun kotlinx.serialization.json.JsonElement?.intInRange(): Int? {
    val primitive = this?.jsonPrimitive ?: return null
    val value = primitive.longOrNull ?: primitive.intOrNull?.toLong() ?: return null
    if (value !in 0..Int.MAX_VALUE) return null
    return value.toInt()
}

/** The model stream reported an error event. */
class ProxyStreamException(message: String) : RuntimeException(message)

/** The proxy answered 429. The sheet shows the daily-limit card. */
class ProxyLimited : RuntimeException()

/** The proxy answered with an HTTP status that is not a limit and not a network failure. */
class ProxyHttpException(val code: Int) : RuntimeException("HTTP $code")
