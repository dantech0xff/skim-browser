package com.dantech.skimbrowser.domain.bookmark

import kotlinx.coroutines.flow.Flow

/** Saved bookmarks and their folders. */
interface BookmarkRepository {
    fun observeRoot(): Flow<BookmarksRoot>

    fun observeFolder(id: Long): Flow<FolderContent>

    fun observe(id: Long): Flow<Bookmark?>

    /** Bookmarks of every folder whose title or URL contains [query], newest first. None for blank. */
    fun search(query: String): Flow<List<Bookmark>>

    fun observeFolders(): Flow<List<BookmarkFolder>>

    /** The newest bookmark of exactly [url], or null when the page is not bookmarked. */
    fun observeByUrl(url: String): Flow<Bookmark?>

    /** Adds a bookmark at the end of Mobile bookmarks and returns its id. */
    suspend fun addToMobile(title: String, url: String): Long

    suspend fun update(id: Long, title: String, url: String)

    /** Moves bookmark [id] to the end of [folderId]. False when either no longer exists. */
    suspend fun move(id: Long, folderId: Long): Boolean

    /** Mobile bookmarks and every top-level Imported folder, for Move to folder. */
    suspend fun topLevelFolders(): List<BookmarkFolder>

    /** Deletes bookmark [id] and returns it for Undo. */
    suspend fun delete(id: Long): Bookmark?

    /** The Undo of [delete]: same id and position, or the end of Mobile bookmarks if its folder is gone. */
    suspend fun restore(bookmark: Bookmark)

    /** Up to [limit] bookmarks whose title or URL contains [text], newest first. None for blank text. */
    suspend fun matches(text: String, limit: Int): List<Bookmark>

    companion object {
        const val RECENTLY_ADDED_LIMIT = 10
    }
}
