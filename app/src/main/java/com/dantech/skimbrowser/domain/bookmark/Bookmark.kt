package com.dantech.skimbrowser.domain.bookmark

/** A bookmark inside [folderId]. [host] is the site key from `hostOf`. */
data class Bookmark(
    val id: Long = 0,
    val folderId: Long,
    val title: String,
    val url: String,
    val host: String,
    val position: Int,
    val addedAt: Long,
)

enum class FolderKind {
    /** "Mobile bookmarks", always id [BookmarkFolder.MOBILE_ID]. */
    MOBILE,

    /** The top folder of one import from a bookmarks file. */
    IMPORTED,

    /** A subfolder kept from an imported file. */
    FOLDER,
}

/**
 * A bookmark folder. [name] is null for the folders Skim names itself (Mobile bookmarks and each
 * Imported folder), so the UI shows the localized name.
 */
data class BookmarkFolder(
    val id: Long = 0,
    val parentId: Long?,
    val name: String?,
    val kind: FolderKind,
    val sourceFileName: String? = null,
    val position: Int,
    val createdAt: Long,
) {
    companion object {
        const val MOBILE_ID = 1L
    }
}

/** A folder row with its bookmark count, which includes every subfolder. */
data class BookmarkFolderSummary(val folder: BookmarkFolder, val bookmarkCount: Int)

/** Bookmarks (14): the top-level folders, then the newest bookmarks of every folder. */
data class BookmarksRoot(val folders: List<BookmarkFolderSummary>, val recentlyAdded: List<Bookmark>)

/** An opened folder. [folder] is null once it no longer exists. */
data class FolderContent(
    val folder: BookmarkFolder?,
    val subfolders: List<BookmarkFolderSummary>,
    val bookmarks: List<Bookmark>,
)
