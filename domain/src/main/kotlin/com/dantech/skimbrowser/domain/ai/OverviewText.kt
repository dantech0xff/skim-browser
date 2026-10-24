package com.dantech.skimbrowser.domain.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The name used when a group arrives without one. The tabs screen uses the same word. */
const val OTHER_GROUP_NAME = "Other"

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

/**
 * One line of at most [max] characters, or null when it is blank, too long, broken across lines,
 * or contains a URL or a run of digits that looks like a phone number. Nothing is truncated: an
 * invalid line is dropped.
 */
fun sanitizeShortText(text: String, max: Int = 60): String? {
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
fun overviewFromModel(stopReason: String, json: String, refs: Set<String>): TabsOverview? {
    if (!stopReason.equals(END_TURN, ignoreCase = true)) return null
    return parseTabsOverview(json, refs)
}

/** Checks [json] against [refs] and cleans names and follow-ups. Null when the JSON is not an overview. */
fun parseTabsOverview(json: String, refs: Set<String>): TabsOverview? {
    val raw = decodeOverview(json) ?: return null
    return cleanOverview(raw, refs)
}

/** An overview that was already checked and saved. It does not need the original refs. */
fun decodeStoredOverview(json: String): TabsOverview? = decodeOverview(json)

fun encodeOverview(overview: TabsOverview): String =
    OVERVIEW_JSON.encodeToString(TabsOverview.serializer(), overview)

/** Rewrites refs to tab ids so a reopened chat can still point at the tabs. */
fun TabsOverview.withRealTabIds(context: List<TabContext>): TabsOverview {
    val ids = context.associate { it.ref to it.tab.id }
    return copy(
        groups = groups.map { group ->
            group.copy(tabIds = group.tabIds.mapNotNull { ids[it] })
        }.filter { it.tabIds.isNotEmpty() },
    )
}

/** The intro line kept on the chat, for Skim history. */
fun overviewSnippet(json: String): String? =
    decodeStoredOverview(json)?.intro?.trim()?.take(OpenTabLimits.SNIPPET_CHARS)?.ifBlank { null }

/**
 * [json] as groups when [stopReason] is `end_turn`. A group of fewer than two known refs is
 * dropped. Null when nothing parseable remains, so grouping changes nothing.
 */
fun groupingFromModel(stopReason: String, json: String, refs: Set<String>): GroupingResult? {
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

private val OVERVIEW_JSON = Json { ignoreUnknownKeys = true }

private val URL_LIKE = Regex("""https?://|www\.|\b[\w-]+\.[a-z]{2,}\b""", RegexOption.IGNORE_CASE)

private val PHONE_LIKE = Regex("""(?:\d[\s().-]*){7,}""")
