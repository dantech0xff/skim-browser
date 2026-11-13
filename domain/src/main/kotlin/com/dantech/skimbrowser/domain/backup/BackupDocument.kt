package com.dantech.skimbrowser.domain.backup

import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.ThemeSetting

enum class BackupSection {
    BOOKMARKS,
    HISTORY,
    TABS,
    SETTINGS,
    SITE_PERMISSIONS,
}

enum class ImportMode { MERGE, REPLACE }

enum class BackupFailure {
    MALFORMED,
    UNSUPPORTED_VERSION,
    TOO_LARGE,
    UNREADABLE,
    NOTHING_SELECTED,
}

data class BackupBookmark(
    val title: String,
    val url: String,
    val addedAt: Long,
)

data class BackupFolder(
    val kind: FolderKind,
    val name: String?,
    val sourceFileName: String?,
    val folders: List<BackupFolder> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
)

data class BackupHistory(
    val url: String,
    val title: String,
    val visitedAt: Long,
)

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
    fun included(): Set<BackupSection> = buildSet {
        if (bookmarks != null) add(BackupSection.BOOKMARKS)
        if (history != null) add(BackupSection.HISTORY)
        if (tabs != null) add(BackupSection.TABS)
        if (settings != null) add(BackupSection.SETTINGS)
        if (sitePermissions != null) add(BackupSection.SITE_PERMISSIONS)
    }
}

data class BackupCounts(
    val bookmarks: Int = 0,
    val history: Int = 0,
    val tabs: Int = 0,
    val hasSettings: Boolean = false,
    val sitePermissions: Int = 0,
)

data class FolderStep(
    val kind: FolderKind,
    val name: String?,
    val sourceFileName: String? = null,
)

data class BookmarkPlacement(
    val steps: List<FolderStep>,
    val title: String,
    val url: String,
    val addedAt: Long,
)

data class TabRestore(
    val open: List<BackupTab> = emptyList(),
    val closeIds: List<String> = emptyList(),
)

data class OpenTabRef(
    val id: String,
    val url: String?,
    val incognito: Boolean,
)
