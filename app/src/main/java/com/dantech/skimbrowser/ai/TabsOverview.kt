package com.dantech.skimbrowser.ai

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.dantech.skimbrowser.domain.ai.decodeStoredOverview as domainDecodeStoredOverview
import com.dantech.skimbrowser.domain.ai.encodeOverview as domainEncodeOverview
import com.dantech.skimbrowser.domain.ai.groupingFromModel as domainGroupingFromModel
import com.dantech.skimbrowser.domain.ai.overviewFromModel as domainOverviewFromModel
import com.dantech.skimbrowser.domain.ai.overviewSnippet as domainOverviewSnippet
import com.dantech.skimbrowser.domain.ai.parseTabsOverview as domainParseTabsOverview
import com.dantech.skimbrowser.domain.ai.sanitizeShortText as domainSanitizeShortText
import com.dantech.skimbrowser.domain.ai.withRealTabIds as domainWithRealTabIds

typealias TabsOverview = com.dantech.skimbrowser.domain.ai.TabsOverview
typealias OverviewGroup = com.dantech.skimbrowser.domain.ai.OverviewGroup
typealias GroupingResult = com.dantech.skimbrowser.domain.ai.GroupingResult
typealias ProposedGroup = com.dantech.skimbrowser.domain.ai.ProposedGroup

internal const val OTHER_GROUP_NAME = com.dantech.skimbrowser.domain.ai.OTHER_GROUP_NAME

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

/** One line of at most [max] characters, or null when the line is not safe to show. */
internal fun sanitizeShortText(text: String, max: Int = 60): String? = domainSanitizeShortText(text, max)

/** [json] as an overview when [stopReason] is `end_turn`. See the domain parser. */
internal fun overviewFromModel(stopReason: String, json: String, refs: Set<String>): TabsOverview? =
    domainOverviewFromModel(stopReason, json, refs)

/** Checks [json] against [refs]. Null when the JSON is not an overview. */
internal fun parseTabsOverview(json: String, refs: Set<String>): TabsOverview? =
    domainParseTabsOverview(json, refs)

/** An overview that was already checked and saved. */
internal fun decodeStoredOverview(json: String): TabsOverview? = domainDecodeStoredOverview(json)

internal fun encodeOverview(overview: TabsOverview): String = domainEncodeOverview(overview)

/** Rewrites refs to tab ids so a reopened chat can still point at the tabs. */
internal fun TabsOverview.withRealTabIds(context: List<TabContext>): TabsOverview =
    domainWithRealTabIds(context)

/** The intro line kept on the chat, for Skim history. */
internal fun overviewSnippet(json: String): String? = domainOverviewSnippet(json)

/** [json] as groups when [stopReason] is `end_turn`. Null when nothing parseable remains. */
internal fun groupingFromModel(stopReason: String, json: String, refs: Set<String>): GroupingResult? =
    domainGroupingFromModel(stopReason, json, refs)
