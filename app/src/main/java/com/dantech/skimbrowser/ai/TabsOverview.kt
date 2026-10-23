package com.dantech.skimbrowser.ai

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The name used when a group arrives without one. The tabs screen uses the same word. */
internal const val OTHER_GROUP_NAME = "Other"

/** An across-tabs answer once it has been checked: real tab ids, clean names, clean follow-ups. */
@Serializable
data class TabsOverview(
    val intro: String,
    val groups: List<OverviewGroup>,
    val followUps: List<String>,
)

/** One topic in an across-tabs answer. [tabIds] are refs until they are rewritten to tab ids. */
@Serializable
data class OverviewGroup(
    val name: String,
    val summary: String,
    val tabIds: List<String>,
)

/** What grouping proposed. A group of fewer than two tabs is already gone. */
@Serializable
data class GroupingResult(val groups: List<ProposedGroup>)

/** A proposed group. [tabIds] are the refs the model was given. */
@Serializable
data class ProposedGroup(val name: String, val tabIds: List<String>)

private fun stringProp(): Map<String, Any> = mapOf("type" to "string")

private fun arrayOf(items: Map<String, Any>): Map<String, Any> = mapOf("type" to "array", "items" to items)

private fun objectSchema(properties: Map<String, Any>, required: List<String>): Map<String, Any> = mapOf(
    "type" to "object",
    "additionalProperties" to false,
    "required" to required,
    "properties" to properties,
)

/** The handwritten schema of an across-tabs answer. Every object refuses extra fields. */
internal val TABS_OVERVIEW_SCHEMA_MAP: Map<String, Any> = objectSchema(
    properties = mapOf(
        "intro" to stringProp(),
        "groups" to arrayOf(
            objectSchema(
                properties = mapOf(
                    "name" to stringProp(),
                    "summary" to stringProp(),
                    "tabIds" to arrayOf(stringProp()),
                ),
                required = listOf("name", "summary", "tabIds"),
            ),
        ),
        "followUps" to arrayOf(stringProp()),
    ),
    required = listOf("intro", "groups", "followUps"),
)

/** The handwritten schema of a grouping result. */
internal val GROUPING_SCHEMA_MAP: Map<String, Any> = objectSchema(
    properties = mapOf(
        "groups" to arrayOf(
            objectSchema(
                properties = mapOf(
                    "name" to stringProp(),
                    "tabIds" to arrayOf(stringProp()),
                ),
                required = listOf("name", "tabIds"),
            ),
        ),
    ),
    required = listOf("groups"),
)

internal val TABS_OVERVIEW_SCHEMA: JsonOutputFormat.Schema = jsonSchema(TABS_OVERVIEW_SCHEMA_MAP)

internal val GROUPING_SCHEMA: JsonOutputFormat.Schema = jsonSchema(GROUPING_SCHEMA_MAP)

internal val TABS_OVERVIEW_FORMAT: JsonOutputFormat =
    JsonOutputFormat.builder().schema(TABS_OVERVIEW_SCHEMA).build()

internal val GROUPING_FORMAT: JsonOutputFormat =
    JsonOutputFormat.builder().schema(GROUPING_SCHEMA).build()

internal fun jsonSchema(root: Map<String, Any>): JsonOutputFormat.Schema {
    val builder = JsonOutputFormat.Schema.builder()
    for ((name, value) in root) builder.putAdditionalProperty(name, JsonValue.from(value))
    return builder.build()
}

private val OVERVIEW_JSON = Json { ignoreUnknownKeys = true }

/**
 * One line of at most [max] characters, or null when it is blank, too long, broken across lines,
 * or contains a URL or a run of digits that looks like a phone number. Nothing is truncated: an
 * invalid line is dropped.
 */
internal fun sanitizeShortText(text: String, max: Int = 60): String? {
    if ('\n' in text || '\r' in text) return null
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > max) return null
    if (URL_LIKE.containsMatchIn(trimmed) || PHONE_LIKE.containsMatchIn(trimmed)) return null
    return trimmed
}

/**
 * [json] as an overview when [stopReason] is `end_turn`. Anything else, including a refusal or a
 * reply cut off at the token limit, is not parsed. [refs] are the refs that were sent; unknown and
 * repeated refs are dropped, and a group with nothing left is dropped. A blank group name becomes
 * [OTHER_GROUP_NAME]. A name or follow-up that fails [sanitizeShortText] is dropped.
 */
internal fun overviewFromModel(stopReason: String, json: String, refs: Set<String>): TabsOverview? {
    if (!stopReason.equals(END_TURN, ignoreCase = true)) return null
    return parseTabsOverview(json, refs)
}

/** Checks [json] against [refs] and cleans names and follow-ups. Null when the JSON is not an overview. */
internal fun parseTabsOverview(json: String, refs: Set<String>): TabsOverview? {
    val raw = decodeOverview(json) ?: return null
    return cleanOverview(raw, refs)
}

/** An overview that was already checked and saved. It does not need the original refs. */
internal fun decodeStoredOverview(json: String): TabsOverview? = decodeOverview(json)

internal fun encodeOverview(overview: TabsOverview): String =
    OVERVIEW_JSON.encodeToString(TabsOverview.serializer(), overview)

/** Rewrites refs to tab ids so a reopened chat can still point at the tabs. */
internal fun TabsOverview.withRealTabIds(context: List<TabContext>): TabsOverview {
    val ids = context.associate { it.ref to it.tab.id }
    return copy(
        groups = groups.map { group ->
            group.copy(tabIds = group.tabIds.mapNotNull { ids[it] })
        }.filter { it.tabIds.isNotEmpty() },
    )
}

/** The intro line kept on the chat, for Skim history. */
internal fun overviewSnippet(json: String): String? =
    decodeStoredOverview(json)?.intro?.trim()?.take(AiConfig.SNIPPET_CHARS)?.ifBlank { null }

/**
 * [json] as groups when [stopReason] is `end_turn`. A group of fewer than two known refs is
 * dropped. Null when nothing parseable remains, so grouping changes nothing.
 */
internal fun groupingFromModel(stopReason: String, json: String, refs: Set<String>): GroupingResult? {
    if (!stopReason.equals(END_TURN, ignoreCase = true)) return null
    val raw = try {
        OVERVIEW_JSON.decodeFromString(GroupingResult.serializer(), json)
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    val used = HashSet<String>()
    val groups = raw.groups.mapNotNull { group ->
        val name = groupName(group.name) ?: return@mapNotNull null
        val ids = group.tabIds.filter { it in refs && used.add(it) }
        if (ids.size < 2) null else ProposedGroup(name, ids)
    }
    return groups.takeIf { it.isNotEmpty() }?.let { GroupingResult(it) }
}

private fun cleanOverview(raw: TabsOverview, refs: Set<String>): TabsOverview {
    val used = HashSet<String>()
    val groups = raw.groups.mapNotNull { group ->
        val name = groupName(group.name) ?: return@mapNotNull null
        val ids = group.tabIds.filter { it in refs && used.add(it) }
        if (ids.isEmpty()) null else OverviewGroup(name, group.summary.trim(), ids)
    }
    val followUps = raw.followUps.mapNotNull { sanitizeShortText(it) }.distinct()
    return TabsOverview(raw.intro.trim(), groups, followUps)
}

private fun groupName(raw: String): String? =
    if (raw.isBlank()) OTHER_GROUP_NAME else sanitizeShortText(raw)

private fun decodeOverview(json: String): TabsOverview? = try {
    OVERVIEW_JSON.decodeFromString(TabsOverview.serializer(), json)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private const val END_TURN = "end_turn"

private val URL_LIKE = Regex("""https?://|www\.|\b[\w-]+\.[a-z]{2,}\b""", RegexOption.IGNORE_CASE)

private val PHONE_LIKE = Regex("""(?:\d[\s().-]*){7,}""")
