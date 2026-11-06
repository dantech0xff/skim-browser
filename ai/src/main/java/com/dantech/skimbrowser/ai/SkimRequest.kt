package com.dantech.skimbrowser.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One block of a user message. Key order matches what the request tests compare. */
sealed interface WireBlock {
    fun toJson(): JsonObject

    data class Text(val text: String) : WireBlock {
        override fun toJson(): JsonObject = buildJsonObject {
            put("type", "text")
            put("text", text)
        }
    }

    data class Document(
        val title: String,
        val context: String,
        val paragraphs: List<String>,
        val citations: Boolean,
        val cache: Boolean,
    ) : WireBlock {
        override fun toJson(): JsonObject = buildJsonObject {
            put("type", "document")
            put(
                "source",
                buildJsonObject {
                    put("type", "content")
                    put("content", JsonArray(paragraphs.map { Text(it).toJson() }))
                },
            )
            put("title", title)
            put("context", context)
            if (citations) put("citations", buildJsonObject { put("enabled", true) })
            if (cache) put("cache_control", buildJsonObject { put("type", "ephemeral") })
        }
    }
}

data class WireMessage(val role: String, val content: WireContent) {
    fun toJson(): JsonObject = buildJsonObject {
        put("role", role)
        when (content) {
            is WireContent.Text -> put("content", content.text)
            is WireContent.Blocks -> put("content", JsonArray(content.blocks.map { it.toJson() }))
        }
    }
}

sealed interface WireContent {
    data class Text(val text: String) : WireContent
    data class Blocks(val blocks: List<WireBlock>) : WireContent
}

/**
 * The JSON posted to `/v1/messages`. [bytes] is built once so a retry sends the same body.
 */
class SkimRequest(
    val bytes: String,
) {
    companion object {
        fun build(
            maxTokens: Long,
            effort: String,
            format: JsonObject?,
            messages: List<WireMessage>,
        ): SkimRequest {
            val root = buildJsonObject {
                put("model", AiConfig.MODEL_ID)
                put("max_tokens", maxTokens)
                put(
                    "output_config",
                    buildJsonObject {
                        put("effort", effort)
                        if (format != null) put("format", format)
                    },
                )
                put("system", Prompts.SYSTEM)
                put("messages", JsonArray(messages.map { it.toJson() }))
                put("stream", true)
            }
            return SkimRequest(root.toString())
        }

        fun probe(): SkimRequest = build(
            maxTokens = 32,
            effort = "medium",
            format = null,
            messages = listOf(WireMessage("user", WireContent.Text("Hello"))),
        )
    }
}

internal fun jsonSchemaFormat(schema: Map<String, Any>): JsonObject = buildJsonObject {
    put("type", "json_schema")
    put("schema", jsonObject(schema))
}

private fun jsonObject(map: Map<*, *>): JsonObject = buildJsonObject {
    for ((key, value) in map) put(key as String, jsonValue(value!!))
}

private fun jsonValue(value: Any): kotlinx.serialization.json.JsonElement = when (value) {
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Map<*, *> -> jsonObject(value)
    is List<*> -> JsonArray(value.map { jsonValue(it!!) })
    else -> error("schema value ${value::class.java.name} is not JSON")
}
