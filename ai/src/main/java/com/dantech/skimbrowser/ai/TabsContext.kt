package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.ContentBlockParam
import com.dantech.skimbrowser.domain.ai.CollectOpenTabs
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.tab.TabsController
import java.security.MessageDigest

typealias TabContext = com.dantech.skimbrowser.domain.ai.TabContext

/**
 * The normal web tabs an across-tabs action may send, most recently used first, at most
 * [AiConfig.MAX_TABS]. Incognito tabs, the New tab page, tabs waiting on Undo, and sites set to
 * ask again are left out. [ungroupedOnly] keeps tabs that are not in a group, which is what
 * grouping looks at.
 *
 * When [extractLive] is true, page text is read only from a tab that already has a live WebView.
 * This never creates one. Grouping passes false, so it sends titles and hosts only.
 */
suspend fun collectTabsContext(
    tabs: TabsController,
    sites: SitePermissionRepository,
    extractLive: Boolean,
    ungroupedOnly: Boolean = false,
): List<TabContext> = CollectOpenTabs(tabs, sites).invoke(extractLive, ungroupedOnly)

/** The sources [context] would send. Incognito is never among them. */
internal fun List<TabContext>.toSources(): List<AiSource> =
    map { AiSource(it.tab.id, it.tab.incognito, it.host) }

/**
 * The first user turn of an across-tabs answer: one document per tab, then [prompt]. Only the last
 * document is marked cacheable. Citations stay off.
 */
internal fun overviewBlocks(context: List<TabContext>, prompt: String): List<ContentBlockParam> {
    val last = context.lastIndex
    val documents = context.mapIndexed { index, tab ->
        ContentBlockParam.ofDocument(
            tabDocument(
                title = tab.title.ifBlank { tab.host },
                context = "ref ${tab.ref}, ${tab.host}",
                text = tab.text ?: NO_PAGE_TEXT,
                cache = index == last && last >= 0,
            ),
        )
    }
    return documents + ContentBlockParam.ofText(prompt)
}

/** A grouping request: one document of title and host per tab, then the grouping instruction. */
internal fun groupingBlocks(context: List<TabContext>): List<ContentBlockParam> {
    val documents = context.map { tab ->
        ContentBlockParam.ofDocument(
            tabDocument(
                title = tab.title.ifBlank { tab.host },
                context = "ref ${tab.ref}, ${tab.host}",
                text = NO_PAGE_TEXT,
                cache = false,
            ),
        )
    }
    return documents + ContentBlockParam.ofText(GROUPING_INSTRUCTION)
}

/** Identifies a set of tabs, so the same set is not grouped again. Order does not matter. */
internal fun tabSetKey(ids: Collection<String>): String {
    val joined = ids.sorted().joinToString("\u0000")
    val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
