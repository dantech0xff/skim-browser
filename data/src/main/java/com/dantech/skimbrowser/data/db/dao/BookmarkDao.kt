package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.dantech.skimbrowser.data.db.entity.BookmarkEntity
import com.dantech.skimbrowser.data.db.entity.BookmarkFolderEntity
import com.dantech.skimbrowser.data.db.entity.FolderKind
import com.dantech.skimbrowser.domain.backup.BackupFolder
import com.dantech.skimbrowser.domain.backup.BookmarkPlacement
import com.dantech.skimbrowser.domain.backup.FolderStep
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.text.cutTo
import com.dantech.skimbrowser.domain.url.hostOf
import kotlinx.coroutines.flow.Flow

// Both LIKE operands share one pattern built by containsPattern, which escapes with a backslash.
private const val SEARCH =
    "SELECT * FROM bookmarks WHERE title LIKE :pattern ESCAPE '\\' OR url LIKE :pattern ESCAPE '\\' " +
        "ORDER BY addedAt DESC, id DESC"

/** How many bookmarks sit directly in [folderId], not counting its subfolders. */
data class FolderBookmarkCount(val folderId: Long, val bookmarkCount: Int)

/**
 * One row of an import, listed so that a folder comes before everything inside it. [parentRow] is
 * the index, in the same list, of the [Folder] row holding this one; null puts it directly in the
 * import's top folder.
 */
sealed interface ImportRow {
    val parentRow: Int?

    data class Folder(override val parentRow: Int?, val name: String) : ImportRow

    data class Bookmark(override val parentRow: Int?, val title: String, val url: String, val host: String) : ImportRow
}

@Dao
abstract class BookmarkDao {

    @Query("SELECT * FROM bookmark_folders ORDER BY position, id")
    abstract fun observeFolders(): Flow<List<BookmarkFolderEntity>>

    @Query("SELECT * FROM bookmark_folders ORDER BY position, id")
    abstract suspend fun allFolders(): List<BookmarkFolderEntity>

    @Query("SELECT * FROM bookmarks ORDER BY position, id")
    abstract suspend fun allBookmarks(): List<BookmarkEntity>

    @Query("DELETE FROM bookmark_folders WHERE parentId IS NULL AND id != :mobileId")
    abstract suspend fun deleteRootsExcept(mobileId: Long)

    @Query("DELETE FROM bookmark_folders WHERE parentId = :parentId")
    abstract suspend fun deleteChildFolders(parentId: Long)

    @Query("DELETE FROM bookmarks WHERE folderId = :folderId")
    abstract suspend fun deleteBookmarksIn(folderId: Long)

    @Query("SELECT folderId, COUNT(*) AS bookmarkCount FROM bookmarks GROUP BY folderId")
    abstract fun observeCountsByFolder(): Flow<List<FolderBookmarkCount>>

    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC, id DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE folderId = :folderId ORDER BY position, id")
    abstract fun observeInFolder(folderId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    abstract fun observe(id: Long): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE url = :url ORDER BY addedAt DESC, id DESC LIMIT 1")
    abstract fun observeByUrl(url: String): Flow<BookmarkEntity?>

    @Query(SEARCH)
    abstract fun search(pattern: String): Flow<List<BookmarkEntity>>

    @Query("$SEARCH LIMIT :limit")
    abstract suspend fun matching(pattern: String, limit: Int): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    abstract suspend fun byId(id: Long): BookmarkEntity?

    @Query("SELECT * FROM bookmark_folders WHERE parentId IS NULL ORDER BY position, id")
    abstract suspend fun topLevelFolders(): List<BookmarkFolderEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmark_folders WHERE id = :id)")
    abstract suspend fun folderExists(id: Long): Boolean

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM bookmarks WHERE folderId = :folderId")
    abstract suspend fun nextPosition(folderId: Long): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM bookmark_folders WHERE parentId IS NULL")
    abstract suspend fun nextTopLevelPosition(): Int

    @Insert
    abstract suspend fun insert(bookmark: BookmarkEntity): Long

    @Insert
    abstract suspend fun insertFolder(folder: BookmarkFolderEntity): Long

    @Query("UPDATE bookmarks SET title = :title, url = :url, host = :host WHERE id = :id")
    abstract suspend fun update(id: Long, title: String, url: String, host: String)

    @Query("UPDATE bookmarks SET folderId = :folderId, position = :position WHERE id = :id")
    abstract suspend fun moveTo(id: Long, folderId: Long, position: Int)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    /** Appends a bookmark at the end of [folderId] and returns its id. */
    @Transaction
    open suspend fun addToFolder(folderId: Long, title: String, url: String, host: String, addedAt: Long): Long =
        insert(
            BookmarkEntity(
                folderId = folderId,
                title = title,
                url = url,
                host = host,
                position = nextPosition(folderId),
                addedAt = addedAt,
            ),
        )

    /**
     * Moves bookmark [id] to the end of [folderId]. False when either does not exist; moving into
     * the folder it is already in keeps its place.
     */
    @Transaction
    open suspend fun move(id: Long, folderId: Long): Boolean {
        val bookmark = byId(id) ?: return false
        if (!folderExists(folderId)) return false
        if (bookmark.folderId != folderId) moveTo(id, folderId, nextPosition(folderId))
        return true
    }

    /** Deletes bookmark [id] and returns it, or returns null when it does not exist. */
    @Transaction
    open suspend fun remove(id: Long): BookmarkEntity? {
        val bookmark = byId(id) ?: return null
        deleteById(id)
        return bookmark
    }

    /**
     * Puts a deleted [bookmark] back with its id and position. If its folder is gone it goes to the
     * end of Mobile bookmarks instead; if it was already put back nothing changes.
     */
    @Transaction
    open suspend fun restore(bookmark: BookmarkEntity) {
        if (byId(bookmark.id) != null) return
        val target = if (folderExists(bookmark.folderId)) {
            bookmark
        } else {
            bookmark.copy(
                folderId = BookmarkFolderEntity.MOBILE_ID,
                position = nextPosition(BookmarkFolderEntity.MOBILE_ID),
            )
        }
        insert(target)
    }

    /**
     * Creates a new top-level [FolderKind.IMPORTED] folder for [fileName] and fills it with [rows],
     * all in one transaction, so a failure leaves no partial import.
     *
     * @return the id of the new Imported folder.
     */
    @Transaction
    open suspend fun insertImport(fileName: String, rows: List<ImportRow>, now: Long): Long {
        val topId = insertFolder(
            BookmarkFolderEntity(
                parentId = null,
                name = null,
                kind = FolderKind.IMPORTED,
                sourceFileName = fileName,
                position = nextTopLevelPosition(),
                createdAt = now,
            ),
        )
        val folderIds = LongArray(rows.size)
        val nextPositions = HashMap<Long, Int>()
        rows.forEachIndexed { index, row ->
            val parentId = row.parentRow?.let { parent ->
                require(parent in 0 until index && rows[parent] is ImportRow.Folder) {
                    "An import row must sit in a folder row listed before it"
                }
                folderIds[parent]
            } ?: topId
            val position = nextPositions.getOrDefault(parentId, 0)
            nextPositions[parentId] = position + 1
            when (row) {
                is ImportRow.Folder -> folderIds[index] = insertFolder(
                    BookmarkFolderEntity(
                        parentId = parentId,
                        name = row.name,
                        kind = FolderKind.FOLDER,
                        position = position,
                        createdAt = now,
                    ),
                )
                is ImportRow.Bookmark -> insert(
                    BookmarkEntity(
                        folderId = parentId,
                        title = row.title,
                        url = row.url,
                        host = row.host,
                        position = position,
                        addedAt = now,
                    ),
                )
            }
        }
        return topId
    }

    /** Clears every folder except Mobile bookmarks, then writes [tree] in one transaction. */
    @Transaction
    open suspend fun replaceTree(tree: List<BackupFolder>, now: Long) {
        deleteRootsExcept(BookmarkFolderEntity.MOBILE_ID)
        deleteChildFolders(BookmarkFolderEntity.MOBILE_ID)
        deleteBookmarksIn(BookmarkFolderEntity.MOBILE_ID)
        writeFolders(parentId = null, folders = tree, now = now)
    }

    /** Adds [rows] under their folder paths. A URL already stored is left as it is. */
    @Transaction
    open suspend fun mergePlacements(rows: List<BookmarkPlacement>, now: Long) {
        val urls = allBookmarks().mapTo(HashSet()) { it.url }
        val folders = allFolders().toMutableList()
        for (row in rows) {
            if (!urls.add(row.url)) continue
            val host = hostOf(row.url) ?: continue
            val folderId = ensureFolder(row.steps, folders, now)
            insert(
                BookmarkEntity(
                    folderId = folderId,
                    title = row.title.cutTo(HistoryRepository.MAX_TITLE_LENGTH),
                    url = row.url,
                    host = host,
                    position = nextPosition(folderId),
                    addedAt = row.addedAt,
                ),
            )
        }
    }

    private suspend fun writeFolders(parentId: Long?, folders: List<BackupFolder>, now: Long) {
        folders.forEachIndexed { index, folder ->
            val id = if (folder.kind == FolderKind.MOBILE && parentId == null) {
                BookmarkFolderEntity.MOBILE_ID
            } else {
                insertFolder(
                    BookmarkFolderEntity(
                        parentId = parentId,
                        name = folder.name,
                        kind = folder.kind,
                        sourceFileName = folder.sourceFileName,
                        position = index,
                        createdAt = now,
                    ),
                )
            }
            folder.bookmarks.forEachIndexed { bookmarkIndex, bookmark ->
                val host = hostOf(bookmark.url) ?: return@forEachIndexed
                insert(
                    BookmarkEntity(
                        folderId = id,
                        title = bookmark.title.cutTo(HistoryRepository.MAX_TITLE_LENGTH),
                        url = bookmark.url,
                        host = host,
                        position = bookmarkIndex,
                        addedAt = bookmark.addedAt,
                    ),
                )
            }
            writeFolders(id, folder.folders, now)
        }
    }

    private suspend fun ensureFolder(
        steps: List<FolderStep>,
        folders: MutableList<BookmarkFolderEntity>,
        now: Long,
    ): Long {
        var parent: Long? = null
        for (step in steps) {
            val match = folders.firstOrNull { folder ->
                folder.parentId == parent &&
                    folder.kind == step.kind &&
                    folder.name == step.name &&
                    folder.sourceFileName == step.sourceFileName
            }
            if (match != null) {
                parent = match.id
                continue
            }
            if (step.kind == FolderKind.MOBILE && parent == null) {
                parent = BookmarkFolderEntity.MOBILE_ID
                continue
            }
            val position = folders.count { it.parentId == parent }
            val created = BookmarkFolderEntity(
                parentId = parent,
                name = step.name,
                kind = step.kind,
                sourceFileName = step.sourceFileName,
                position = position,
                createdAt = now,
            )
            val id = insertFolder(created)
            folders += created.copy(id = id)
            parent = id
        }
        return parent ?: BookmarkFolderEntity.MOBILE_ID
    }
}
