package com.dantech.skimbrowser.domain.bookmark

/** How importing a bookmarks file ended. */
sealed interface ImportResult {
    /** A new Imported folder [folderId] holds [bookmarkCount] bookmarks, subfolders included. */
    data class Imported(val folderId: Long, val bookmarkCount: Int) : ImportResult

    /** Nothing was imported, for [reason]. */
    data class Failed(val reason: Reason) : ImportResult

    enum class Reason {
        Unreadable,
        TooLarge,
        NotBookmarks,
        NoBookmarks,
    }
}

/** Reads a bookmarks file the person picked and saves it as an Imported folder. */
interface BookmarkFiles {
    /** [location] is the document URI string the picker returned. */
    suspend fun import(location: String): ImportResult
}
