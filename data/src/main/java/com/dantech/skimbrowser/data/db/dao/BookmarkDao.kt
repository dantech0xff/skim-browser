package com.dantech.skimbrowser.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.dantech.skimbrowser.data.db.entity.BookmarkEntity
import com.dantech.skimbrowser.data.db.entity.BookmarkFolderEntity
import com.dantech.skimbrowser.data.db.entity.FolderKind
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
}
