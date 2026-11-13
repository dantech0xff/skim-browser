package com.dantech.skimbrowser.domain.backup

import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.ThemeSetting

/** A part of a backup the person can include or leave out. */
enum class BackupSection {
    BOOKMARKS,
    HISTORY,
    TABS,
    SETTINGS,
    SITE_PERMISSIONS,
}

/** How an import combines the file with what is already on the phone. */
enum class ImportMode { MERGE, REPLACE }

/** Why a backup file was not used. */
enum class BackupFailure {
    MALFORMED,
    UNSUPPORTED_VERSION,
    TOO_LARGE,
    UNREADABLE,
    NOTHING_SELECTED,
}

/** One bookmark inside a [BackupFolder]. */
data class BackupBookmark(
    val title: String,
    val url: String,
    val addedAt: Long,
)

/**
 * A folder in a backup. [kind] keeps Mobile bookmarks and an imported file distinct from a folder
 * the person named. [name] is null for those Skim-named folders.
 */
data class BackupFolder(
    val kind: FolderKind,
    val name: String?,
    val sourceFileName: String?,
    val folders: List<BackupFolder> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
)

/** One history row. One URL appears once after a merge. */
data class BackupHistory(
    val url: String,
    val title: String,
    val visitedAt: Long,
)

/** An open normal tab. [order] is its place in the tab list. Incognito tabs are never here. */
data class BackupTab(
    val url: String,
    val title: String,
    val order: Int,
)

/**
 * Settings that can leave the phone. The install id and the last tab id are not here: one is sent
 * to the model proxy, and the other names a tab that exists only on this phone.
 */
data class BackupSettings(
    val searchEngine: SearchEngine,
    val theme: ThemeSetting,
    val askBeforeSending: Boolean,
    val keepChatHistory: Boolean,
    val sendRatings: Boolean?,
    val readerTextScalePercent: Int,
    val pageTextZoomPercent: Int?,
    val readerFont: ReaderFont,
    val onboardingCompleted: Boolean,
    val scamWarnings: Boolean,
    val shieldDisabledHosts: Set<String>,
)

/** One site's permission decisions. The host is the site key, without a scheme. */
data class BackupSitePermission(
    val host: String,
    val skimAi: SkimAiState,
    val location: PermissionState,
    val camera: PermissionState,
    val microphone: PermissionState,
)

/**
 * One backup file. A null section was not included. An empty list was included and held nothing.
 * Chats, tokens, and the install id cannot be represented.
 */
data class BackupDocument(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val bookmarks: List<BackupFolder>? = null,
    val history: List<BackupHistory>? = null,
    val tabs: List<BackupTab>? = null,
    val settings: BackupSettings? = null,
    val sitePermissions: List<BackupSitePermission>? = null,
) {
    /** The sections this file actually carries. */
    fun included(): Set<BackupSection> = buildSet {
        if (bookmarks != null) add(BackupSection.BOOKMARKS)
        if (history != null) add(BackupSection.HISTORY)
        if (tabs != null) add(BackupSection.TABS)
        if (settings != null) add(BackupSection.SETTINGS)
        if (sitePermissions != null) add(BackupSection.SITE_PERMISSIONS)
    }
}

/** How many items a file holds, for the preview before an import. */
data class BackupCounts(
    val bookmarks: Int = 0,
    val history: Int = 0,
    val tabs: Int = 0,
    val hasSettings: Boolean = false,
    val sitePermissions: Int = 0,
)

/** Where a restored bookmark sits, from the root folder down. */
data class FolderStep(
    val kind: FolderKind,
    val name: String?,
    val sourceFileName: String? = null,
)

/** A bookmark to add, and the folder path that should hold it. */
data class BookmarkPlacement(
    val steps: List<FolderStep>,
    val title: String,
    val url: String,
    val addedAt: Long,
)

/** Tabs to open in the background, and normal tabs to close when a replace asked for that. */
data class TabRestore(
    val open: List<BackupTab> = emptyList(),
    val closeIds: List<String> = emptyList(),
)

/** A normal or incognito tab as the backup sees it. Only the fields a backup needs. */
data class OpenTabRef(
    val id: String,
    val url: String?,
    val incognito: Boolean,
)
