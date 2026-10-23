package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkFiles
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.bookmark.ImportResult

class AddBookmark(private val bookmarks: BookmarkRepository) {
    suspend operator fun invoke(title: String, url: String): Long = bookmarks.addToMobile(title, url)
}

class UpdateBookmark(private val bookmarks: BookmarkRepository) {
    suspend operator fun invoke(id: Long, title: String, url: String) = bookmarks.update(id, title, url)
}

class MoveBookmark(private val bookmarks: BookmarkRepository) {
    suspend operator fun invoke(id: Long, folderId: Long): Boolean = bookmarks.move(id, folderId)
}

class DeleteBookmark(private val bookmarks: BookmarkRepository) {
    suspend operator fun invoke(id: Long): Bookmark? = bookmarks.delete(id)
}

class RestoreBookmark(private val bookmarks: BookmarkRepository) {
    suspend operator fun invoke(bookmark: Bookmark) = bookmarks.restore(bookmark)
}

class MatchBookmarks(private val bookmarks: BookmarkRepository) {
    suspend operator fun invoke(text: String, limit: Int): List<Bookmark> = bookmarks.matches(text, limit)
}

class ImportBookmarks(private val files: BookmarkFiles) {
    suspend operator fun invoke(location: String): ImportResult = files.import(location)
}
