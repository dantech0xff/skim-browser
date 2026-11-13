package com.dantech.skimbrowser.data.backup

import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.dao.BookmarkDao
import com.dantech.skimbrowser.data.db.dao.HistoryDao
import com.dantech.skimbrowser.data.db.dao.SitePermissionDao
import com.dantech.skimbrowser.data.db.entity.BookmarkEntity
import com.dantech.skimbrowser.data.db.entity.BookmarkFolderEntity
import com.dantech.skimbrowser.data.db.entity.SitePermissionEntity
import com.dantech.skimbrowser.domain.backup.BackupFolder
import com.dantech.skimbrowser.domain.backup.BackupHistory
import com.dantech.skimbrowser.domain.backup.BackupLibrary
import com.dantech.skimbrowser.domain.backup.BackupSettings
import com.dantech.skimbrowser.domain.backup.BackupSitePermission
import com.dantech.skimbrowser.domain.backup.BookmarkPlacement
import com.dantech.skimbrowser.domain.backup.backupFolders
import com.dantech.skimbrowser.domain.backup.portableSettings
import com.dantech.skimbrowser.domain.backup.settingsFromPortable
import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolder
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.permission.SitePermissionKind
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Reads and writes the on-device data a backup is allowed to carry. */
@Singleton
class BackupLibraryStore @Inject constructor(
    private val database: SkimDatabase,
    private val history: HistoryRepository,
    private val settingsStore: SettingsRepository,
    private val permissions: SitePermissionRepository,
) : BackupLibrary {
    private val bookmarks: BookmarkDao get() = database.bookmarkDao()
    private val historyRows: HistoryDao get() = database.historyDao()
    private val permissionRows: SitePermissionDao get() = database.sitePermissionDao()

    override suspend fun bookmarks(): List<BackupFolder> = backupFolders(
        bookmarks.allFolders().map { it.toFolder() },
        bookmarks.allBookmarks().map { it.toBookmark() },
    )

    override suspend fun history(): List<BackupHistory> =
        historyRows.allRows().map { BackupHistory(it.url, it.title, it.visitedAt) }

    override suspend fun settings(): BackupSettings {
        val stored = settingsStore.settings.first()
        val shields = settingsStore.shieldDisabledHosts.first()
        return portableSettings(stored, shields)
    }

    override suspend fun permissions(): List<BackupSitePermission> =
        permissionRows.allRows().map { it.toBackup() }

    override suspend fun replaceBookmarks(tree: List<BackupFolder>, now: Long) {
        withContext(NonCancellable) {
            bookmarks.replaceTree(tree, now)
            database.checkpointTruncate()
        }
    }

    override suspend fun mergeBookmarks(rows: List<BookmarkPlacement>, now: Long) {
        bookmarks.mergePlacements(rows, now)
    }

    override suspend fun replaceHistory(entries: List<BackupHistory>) {
        history.clearAll()
        for (entry in entries) history.recordVisit(entry.url, entry.title, entry.visitedAt)
    }

    override suspend fun mergeHistory(entries: List<BackupHistory>) {
        for (entry in entries) history.recordVisit(entry.url, entry.title, entry.visitedAt)
    }

    override suspend fun applySettings(settings: BackupSettings) {
        val current = settingsStore.settings.first()
        settingsStore.replacePortable(settingsFromPortable(current, settings), settings.shieldDisabledHosts)
    }

    override suspend fun replacePermissions(rows: List<BackupSitePermission>, now: Long) {
        withContext(NonCancellable) {
            permissionRows.deleteEveryRow()
            for (row in rows) {
                val entity = row.toEntity(now)
                if (!entity.holdsNoDecision()) permissionRows.insert(entity)
            }
            database.checkpointTruncate()
        }
    }

    override suspend fun mergePermissions(rows: List<BackupSitePermission>, now: Long) {
        for (row in rows) {
            permissions.setSkimAi(row.host, row.skimAi)
            permissions.setPermission(row.host, SitePermissionKind.LOCATION, row.location)
            permissions.setPermission(row.host, SitePermissionKind.CAMERA, row.camera)
            permissions.setPermission(row.host, SitePermissionKind.MICROPHONE, row.microphone)
        }
    }
}

private fun BookmarkFolderEntity.toFolder() = BookmarkFolder(id, parentId, name, kind, sourceFileName, position, createdAt)

private fun BookmarkEntity.toBookmark() = Bookmark(id, folderId, title, url, host, position, addedAt)

private fun SitePermissionEntity.toBackup() = BackupSitePermission(host, skimAi, location, camera, microphone)

private fun BackupSitePermission.toEntity(now: Long) = SitePermissionEntity(
    host = host,
    skimAi = skimAi,
    location = location,
    camera = camera,
    microphone = microphone,
    updatedAt = now,
)
