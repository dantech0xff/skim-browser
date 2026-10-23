package com.dantech.skimbrowser.data.repo

import com.dantech.skimbrowser.data.db.containsPattern
import com.dantech.skimbrowser.data.db.dao.BookmarkDao
import com.dantech.skimbrowser.data.db.dao.FolderBookmarkCount
import com.dantech.skimbrowser.data.db.dao.ImportRow
import com.dantech.skimbrowser.data.db.entity.BookmarkEntity
import com.dantech.skimbrowser.data.db.entity.BookmarkFolderEntity
import com.dantech.skimbrowser.data.hostOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** A folder row with its bookmark count, which includes every subfolder ("24 bookmarks"). */
data class BookmarkFolderSummary(val folder: BookmarkFolderEntity, val bookmarkCount: Int)

/** Bookmarks (14): the top-level folders, then the newest bookmarks of every folder. */
data class BookmarksRoot(val folders: List<BookmarkFolderSummary>, val recentlyAdded: List<BookmarkEntity>)

/** An opened folder: [folder] is null once it no longer exists. */
data class FolderContent(
    val folder: BookmarkFolderEntity?,
    val subfolders: List<BookmarkFolderSummary>,
    val bookmarks: List<BookmarkEntity>,
)

/** One entry of a bookmarks file: a bookmark when [url] is set, otherwise a folder of [children]. */
data class ImportedNode(val title: String, val url: String?, val children: List<ImportedNode> = emptyList())

/** The Imported folder an import created and how many bookmarks it holds, subfolders included. */
data class ImportOutcome(val folderId: Long, val bookmarkCount: Int)

class BookmarkRepository(
    private val dao: BookmarkDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** The top-level folders (Mobile bookmarks first) with recursive counts, and "Recently added". */
    fun observeRoot(): Flow<BookmarksRoot> =
        combine(dao.observeFolders(), dao.observeCountsByFolder(), dao.observeRecent(RECENTLY_ADDED_LIMIT)) { folders, counts, recent ->
            val totals = recursiveCounts(folders, counts)
            BookmarksRoot(
                folders = folders.filter { it.parentId == null }.map { BookmarkFolderSummary(it, totals[it.id] ?: 0) },
                recentlyAdded = recent,
            )
        }

    /** Folder [id]'s subfolders (with recursive counts) and its own bookmarks in order. */
    fun observeFolder(id: Long): Flow<FolderContent> =
        combine(dao.observeFolders(), dao.observeCountsByFolder(), dao.observeInFolder(id)) { folders, counts, bookmarks ->
            val totals = recursiveCounts(folders, counts)
            FolderContent(
                folder = folders.firstOrNull { it.id == id },
                subfolders = folders.filter { it.parentId == id }.map { BookmarkFolderSummary(it, totals[it.id] ?: 0) },
                bookmarks = bookmarks,
            )
        }

    fun observe(id: Long): Flow<BookmarkEntity?> = dao.observe(id)

    /** Bookmarks of every folder whose title or URL contains [query], newest first; none for blank. */
    fun search(query: String): Flow<List<BookmarkEntity>> {
        val text = query.trim()
        return if (text.isEmpty()) flowOf(emptyList()) else dao.search(containsPattern(text))
    }

    /** Every folder, top-level and nested, in display order. */
    fun observeFolders(): Flow<List<BookmarkFolderEntity>> = dao.observeFolders()

    /** The newest bookmark of exactly [url], or null when the page is not bookmarked. */
    fun observeByUrl(url: String): Flow<BookmarkEntity?> = dao.observeByUrl(url)

    /** Adds a bookmark at the end of Mobile bookmarks and returns its id. */
    suspend fun addToMobile(title: String, url: String): Long {
        require(url.isNotBlank()) { "A bookmark needs a URL" }
        return dao.addToFolder(BookmarkFolderEntity.MOBILE_ID, title, url, hostOf(url).orEmpty(), clock())
    }

    suspend fun update(id: Long, title: String, url: String) {
        require(url.isNotBlank()) { "A bookmark needs a URL" }
        dao.update(id, title, url, hostOf(url).orEmpty())
    }

    /** Moves bookmark [id] to the end of [folderId]; false when either no longer exists. */
    suspend fun move(id: Long, folderId: Long): Boolean = dao.move(id, folderId)

    /** Mobile bookmarks and every top-level Imported folder, for Move to folder (15). */
    suspend fun topLevelFolders(): List<BookmarkFolderEntity> = dao.topLevelFolders()

    /** Deletes bookmark [id] and returns it for Undo. */
    suspend fun delete(id: Long): BookmarkEntity? = dao.remove(id)

    /** The Undo of [delete]: same id and position, or the end of Mobile bookmarks if its folder is gone. */
    suspend fun restore(b: BookmarkEntity) = dao.restore(b)

    /**
     * Imports [nodes] from [fileName] into a new Imported folder, keeping the file's subfolders, in
     * one transaction. Only http(s) bookmarks with a valid host are kept: `javascript:` and other
     * schemes from a file would otherwise run or open from the bookmarks list.
     *
     * @return the new folder and how many bookmarks it holds, or null, with nothing created, when
     *   the file holds no such bookmark.
     */
    suspend fun importTree(fileName: String, nodes: List<ImportedNode>): ImportOutcome? {
        val rows = flattenImport(nodes)
        val bookmarkCount = rows.count { it is ImportRow.Bookmark }
        if (bookmarkCount == 0) return null
        return ImportOutcome(dao.insertImport(fileName, rows, clock()), bookmarkCount)
    }

    /** Up to [limit] bookmarks whose title or URL contains [text], newest first; none for blank text. */
    suspend fun matches(text: String, limit: Int): List<BookmarkEntity> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || limit <= 0) return emptyList()
        return dao.matching(containsPattern(trimmed), limit)
    }

    companion object {
        /** "Recently added" in Bookmarks (14) shows this many bookmarks. */
        const val RECENTLY_ADDED_LIMIT = 10
    }
}

/**
 * Bookmarks in each folder plus all of its subfolders. Walks the tree with an explicit stack, so a
 * deeply nested import cannot overflow the call stack, and visits each folder once even if the
 * parent links ever formed a loop.
 */
internal fun recursiveCounts(folders: List<BookmarkFolderEntity>, direct: List<FolderBookmarkCount>): Map<Long, Int> {
    val own = direct.associate { it.folderId to it.bookmarkCount }
    val children = folders.groupBy { it.parentId }
    val totals = HashMap<Long, Int>(folders.size)
    val visited = HashSet<Long>(folders.size)
    // Each entry is a folder id and whether its children have already been pushed.
    val stack = ArrayDeque<Pair<Long, Boolean>>()
    for (folder in folders) {
        if (folder.id in visited) continue
        stack.addLast(folder.id to false)
        while (stack.isNotEmpty()) {
            val (id, expanded) = stack.removeLast()
            if (expanded) {
                totals[id] = (own[id] ?: 0) + children[id].orEmpty().sumOf { totals[it.id] ?: 0 }
            } else if (visited.add(id)) {
                stack.addLast(id to true)
                children[id].orEmpty().forEach { if (it.id !in visited) stack.addLast(it.id to false) }
            }
        }
    }
    return totals
}

/**
 * Lists an imported tree folder-first (pre-order), dropping bookmarks that are not http(s) with a
 * valid host. Uses an explicit stack, so a deeply nested file cannot overflow the call stack.
 */
internal fun flattenImport(nodes: List<ImportedNode>): List<ImportRow> {
    val rows = ArrayList<ImportRow>()
    // Each entry is a node and the row index of the folder holding it (null: the Imported folder).
    val pending = ArrayDeque<Pair<ImportedNode, Int?>>()
    nodes.asReversed().forEach { pending.addLast(it to null) }
    while (pending.isNotEmpty()) {
        val (node, parent) = pending.removeLast()
        val url = node.url?.trim()
        if (url != null) {
            val host = hostOf(url) ?: continue
            rows += ImportRow.Bookmark(parent, node.title, url, host)
        } else {
            rows += ImportRow.Folder(parent, node.title)
            val folderRow = rows.lastIndex
            node.children.asReversed().forEach { pending.addLast(it to folderRow) }
        }
    }
    return rows
}
