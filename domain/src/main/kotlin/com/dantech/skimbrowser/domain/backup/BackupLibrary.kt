package com.dantech.skimbrowser.domain.backup

/**
 * The on-device data a backup reads and writes. Chats and the install id are not part of this
 * store. Callers run this off the main thread.
 */
interface BackupLibrary {
    suspend fun bookmarks(): List<BackupFolder>

    suspend fun history(): List<BackupHistory>

    suspend fun settings(): BackupSettings

    suspend fun permissions(): List<BackupSitePermission>

    /** Deletes every bookmark folder except Mobile bookmarks, then writes [tree]. */
    suspend fun replaceBookmarks(tree: List<BackupFolder>, now: Long)

    /** Adds [rows], creating folders that do not exist yet. A URL already saved is skipped. */
    suspend fun mergeBookmarks(rows: List<BookmarkPlacement>, now: Long)

    /** Deletes history, then stores [entries]. */
    suspend fun replaceHistory(entries: List<BackupHistory>)

    /** Keeps the later visit when a URL is already in history. */
    suspend fun mergeHistory(entries: List<BackupHistory>)

    /**
     * Writes [settings] over the portable settings. The install id and the last tab id stay as
     * they are.
     */
    suspend fun applySettings(settings: BackupSettings)

    /** Deletes every site decision, then stores [rows]. */
    suspend fun replacePermissions(rows: List<BackupSitePermission>, now: Long)

    /** Writes [rows] over the same hosts and leaves every other host alone. */
    suspend fun mergePermissions(rows: List<BackupSitePermission>, now: Long)
}

/** A backup file the person picked, or the reason it could not be read. */
sealed interface BackupFile {
    data class Json(val result: BackupDecode) : BackupFile

    /** A Netscape bookmark file. The caller imports it as bookmarks. */
    data object Html : BackupFile

    data object TooLarge : BackupFile

    data object Unreadable : BackupFile
}

/** Reads and writes the backup file the person picked. The location is a document URI. */
interface BackupFiles {
    suspend fun read(location: String): BackupFile

    /** Writes [document]. False when the file could not be written, including when it would exceed the cap. */
    suspend fun write(location: String, document: BackupDocument): Boolean
}
