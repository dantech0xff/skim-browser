package com.dantech.skimbrowser.domain.backup

/**
 * Writes the checked sections of a backup onto the phone. Tabs are not opened here: [TabRestore]
 * says which background tabs to open and which normal tabs a replace should close afterwards.
 * Running it twice with the same file and [ImportMode.MERGE] does not add a second copy of a URL.
 */
class ApplyBackup(private val library: BackupLibrary) {
    suspend operator fun invoke(
        document: BackupDocument,
        sections: Set<BackupSection>,
        mode: ImportMode,
        openTabs: List<OpenTabRef>,
        now: Long,
    ): TabRestore {
        val chosen = sections.intersect(document.included())
        if (BackupSection.BOOKMARKS in chosen) {
            val tree = document.bookmarks.orEmpty()
            if (mode == ImportMode.REPLACE) {
                library.replaceBookmarks(tree, now)
            } else {
                val existing = library.bookmarks().flatMap(::urlsIn).toSet()
                library.mergeBookmarks(bookmarksToAdd(existing, tree), now)
            }
        }
        if (BackupSection.HISTORY in chosen) {
            val rows = dedupedHistory(document.history.orEmpty())
            if (mode == ImportMode.REPLACE) library.replaceHistory(rows) else library.mergeHistory(rows)
        }
        if (BackupSection.SETTINGS in chosen) document.settings?.let { library.applySettings(it) }
        if (BackupSection.SITE_PERMISSIONS in chosen) {
            val rows = permissionsToWrite(
                existing = if (mode == ImportMode.REPLACE) emptyList() else library.permissions(),
                incoming = document.sitePermissions.orEmpty(),
                mode = mode,
            )
            if (mode == ImportMode.REPLACE) library.replacePermissions(rows, now) else library.mergePermissions(rows, now)
        }
        if (BackupSection.TABS !in chosen) return TabRestore()
        return tabsToRestore(openTabs, document.tabs.orEmpty(), mode)
    }
}

private fun urlsIn(folder: BackupFolder): List<String> =
    folder.bookmarks.map { it.url } + folder.folders.flatMap(::urlsIn)
