package com.dantech.skimbrowser.domain.backup

sealed interface ExportOutcome {
    data class Ready(val document: BackupDocument) : ExportOutcome

    data object TooLarge : ExportOutcome
}

class ExportBackup(private val library: BackupLibrary) {
    suspend operator fun invoke(
        sections: Set<BackupSection>,
        openTabs: List<BackupTab>,
        appVersion: String,
        createdAt: Long,
    ): ExportOutcome {
        if (sections.isEmpty()) return ExportOutcome.Ready(emptyDocument(appVersion, createdAt))
        val bookmarks = if (BackupSection.BOOKMARKS in sections) library.bookmarks() else null
        val history = if (BackupSection.HISTORY in sections) library.history() else null
        val permissions = if (BackupSection.SITE_PERMISSIONS in sections) library.permissions() else null
        val settings = if (BackupSection.SETTINGS in sections) library.settings() else null
        if (bookmarks != null && countBookmarks(bookmarks) > BackupCodec.MAX_BOOKMARKS) return ExportOutcome.TooLarge
        if (history != null && history.size > BackupCodec.MAX_HISTORY) return ExportOutcome.TooLarge
        if (BackupSection.TABS in sections && openTabs.size > BackupCodec.MAX_TABS) return ExportOutcome.TooLarge
        if (permissions != null && permissions.size > BackupCodec.MAX_PERMISSIONS) return ExportOutcome.TooLarge
        val document = BackupDocument(
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            appVersion = appVersion,
            createdAt = createdAt,
            bookmarks = bookmarks,
            history = history,
            tabs = if (BackupSection.TABS in sections) openTabs else null,
            settings = settings,
            sitePermissions = permissions,
        )
        val encoded = BackupCodec.encode(document)
        if (encoded.toByteArray(Charsets.UTF_8).size > BackupCodec.MAX_BYTES) return ExportOutcome.TooLarge
        return ExportOutcome.Ready(document)
    }

    private fun emptyDocument(appVersion: String, createdAt: Long) = BackupDocument(
        schemaVersion = BackupCodec.SCHEMA_VERSION,
        appVersion = appVersion,
        createdAt = createdAt,
    )
}
