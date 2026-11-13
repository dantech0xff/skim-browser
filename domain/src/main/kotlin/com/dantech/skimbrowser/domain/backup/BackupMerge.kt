package com.dantech.skimbrowser.domain.backup

import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolder
import com.dantech.skimbrowser.domain.history.HistoryEntry
import com.dantech.skimbrowser.domain.permission.SitePermission
import com.dantech.skimbrowser.domain.settings.SkimSettings
import com.dantech.skimbrowser.domain.tab.Tab

/** How many items [document] would apply for [sections]. A section that is absent counts as zero. */
fun preview(document: BackupDocument, sections: Set<BackupSection>): BackupCounts = BackupCounts(
    bookmarks = if (BackupSection.BOOKMARKS in sections) countBookmarks(document.bookmarks.orEmpty()) else 0,
    history = if (BackupSection.HISTORY in sections) document.history.orEmpty().size else 0,
    tabs = if (BackupSection.TABS in sections) document.tabs.orEmpty().size else 0,
    hasSettings = BackupSection.SETTINGS in sections && document.settings != null,
    sitePermissions = if (BackupSection.SITE_PERMISSIONS in sections) document.sitePermissions.orEmpty().size else 0,
)

/**
 * The bookmark tree stored on the phone, parents before children. A folder whose parent is missing
 * is skipped, and a parent cycle is not followed.
 */
fun backupFolders(folders: List<BookmarkFolder>, bookmarks: List<Bookmark>): List<BackupFolder> {
    val byParent = folders.groupBy { it.parentId }
    val marks = bookmarks.groupBy { it.folderId }
    val seen = HashSet<Long>()
    fun build(parentId: Long?): List<BackupFolder> =
        byParent[parentId].orEmpty().sortedWith(compareBy({ it.position }, { it.id })).mapNotNull { folder ->
            if (!seen.add(folder.id)) return@mapNotNull null
            BackupFolder(
                kind = folder.kind,
                name = folder.name,
                sourceFileName = folder.sourceFileName,
                folders = build(folder.id),
                bookmarks = marks[folder.id].orEmpty()
                    .sortedWith(compareBy({ it.position }, { it.id }))
                    .map { BackupBookmark(it.title, it.url, it.addedAt) },
            )
        }
    return build(null)
}

/**
 * Bookmarks from [incoming] whose URL is not already in [existingUrls] or earlier in the file.
 * The path includes every folder from the root, so a merge can create only the folders it needs.
 */
fun bookmarksToAdd(existingUrls: Set<String>, incoming: List<BackupFolder>): List<BookmarkPlacement> {
    val seen = existingUrls.toMutableSet()
    val rows = ArrayList<BookmarkPlacement>()
    fun walk(folders: List<BackupFolder>, steps: List<FolderStep>) {
        for (folder in folders) {
            val path = steps + FolderStep(folder.kind, folder.name, folder.sourceFileName)
            for (bookmark in folder.bookmarks) {
                if (seen.add(bookmark.url)) {
                    rows += BookmarkPlacement(path, bookmark.title, bookmark.url, bookmark.addedAt)
                }
            }
            walk(folder.folders, path)
        }
    }
    walk(incoming, emptyList())
    return rows
}

/**
 * One row per URL. The later visit wins. The same visit time keeps the later row in [entries],
 * so a second merge of the same file changes nothing.
 */
fun dedupedHistory(entries: List<BackupHistory>): List<BackupHistory> {
    val byUrl = LinkedHashMap<String, BackupHistory>()
    for (entry in entries) {
        val current = byUrl[entry.url]
        if (current == null || entry.visitedAt >= current.visitedAt) byUrl[entry.url] = entry
    }
    return byUrl.values.toList()
}

/** History rows as the backup file stores them. */
fun historyRows(entries: List<HistoryEntry>): List<BackupHistory> =
    entries.map { BackupHistory(it.url, it.title, it.visitedAt) }

/**
 * Normal web tabs in list order. Incognito tabs and tabs with no page are left out.
 * [order] is the place in that list.
 */
fun backupTabs(tabs: List<Tab>): List<BackupTab> {
    var order = 0
    return tabs.mapNotNull { tab ->
        val url = tab.url
        if (tab.incognito || url == null || url.isBlank()) {
            null
        } else {
            BackupTab(url, tab.title, order++)
        }
    }
}

/**
 * Tabs to open, in [order]. Merge skips a URL that is already open. Replace opens every URL in
 * the file once, and names the normal tabs that were open so the caller can close them after.
 */
fun tabsToRestore(open: List<OpenTabRef>, incoming: List<BackupTab>, mode: ImportMode): TabRestore {
    val existing = open.mapNotNull { tab -> tab.url?.takeIf { !tab.incognito } }.toSet()
    val unique = LinkedHashMap<String, BackupTab>()
    for (tab in incoming.sortedBy { it.order }) {
        if (tab.url !in unique) unique[tab.url] = tab
    }
    val chosen = when (mode) {
        ImportMode.MERGE -> unique.values.filter { it.url !in existing }
        ImportMode.REPLACE -> unique.values.toList()
    }
    val closeIds = if (mode == ImportMode.REPLACE) open.filterNot { it.incognito }.map { it.id } else emptyList()
    return TabRestore(chosen, closeIds)
}

/**
 * Site decisions to store. Merge keeps a host the file does not mention and lets the file win
 * for a host it does mention. Replace keeps only the file. A repeated host keeps the later row.
 */
fun permissionsToWrite(
    existing: List<BackupSitePermission>,
    incoming: List<BackupSitePermission>,
    mode: ImportMode,
): List<BackupSitePermission> {
    val incomingByHost = LinkedHashMap<String, BackupSitePermission>()
    for (row in incoming) incomingByHost[row.host] = row
    if (mode == ImportMode.REPLACE) return incomingByHost.values.toList()
    val merged = LinkedHashMap<String, BackupSitePermission>()
    for (row in existing) if (row.host !in incomingByHost) merged[row.host] = row
    merged.putAll(incomingByHost)
    return merged.values.toList()
}

fun permissionRows(rows: List<SitePermission>): List<BackupSitePermission> = rows.map {
    BackupSitePermission(it.host, it.skimAi, it.location, it.camera, it.microphone)
}

/** The portable part of [settings], plus the hosts where blocking is off. */
fun portableSettings(settings: SkimSettings, shieldDisabledHosts: Set<String>): BackupSettings = BackupSettings(
    searchEngine = settings.searchEngine,
    theme = settings.theme,
    askBeforeSending = settings.askBeforeSending,
    keepChatHistory = settings.keepChatHistory,
    sendRatings = settings.sendRatings,
    readerTextScalePercent = settings.readerTextScalePercent,
    pageTextZoomPercent = settings.pageTextZoomPercent,
    readerFont = settings.readerFont,
    onboardingCompleted = settings.onboardingCompleted,
    scamWarnings = settings.scamWarnings,
    shieldDisabledHosts = shieldDisabledHosts,
)

/** [portable] written back onto [current] without touching the last tab id. */
fun settingsFromPortable(current: SkimSettings, portable: BackupSettings): SkimSettings = current.copy(
    searchEngine = portable.searchEngine,
    theme = portable.theme,
    askBeforeSending = portable.askBeforeSending,
    keepChatHistory = portable.keepChatHistory,
    sendRatings = portable.sendRatings,
    readerTextScalePercent = portable.readerTextScalePercent,
    pageTextZoomPercent = portable.pageTextZoomPercent,
    readerFont = portable.readerFont,
    onboardingCompleted = portable.onboardingCompleted,
    scamWarnings = portable.scamWarnings,
)
