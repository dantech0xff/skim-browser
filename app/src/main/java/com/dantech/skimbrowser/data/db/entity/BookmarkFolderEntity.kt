package com.dantech.skimbrowser.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FolderKind {
    /** "Mobile bookmarks": created with the database, always id [BookmarkFolderEntity.MOBILE_ID]. */
    MOBILE,

    /** The top folder of one import from a bookmarks file. */
    IMPORTED,

    /** A subfolder kept from an imported file. */
    FOLDER,
}

/**
 * A bookmark folder. [name] is null for the folders Skim names itself (Mobile bookmarks and each
 * Imported folder), so the UI shows the localized name. Deleting a folder deletes its subfolders
 * and their bookmarks.
 */
@Entity(
    tableName = "bookmark_folders",
    foreignKeys = [
        ForeignKey(
            entity = BookmarkFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId")],
)
data class BookmarkFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for a top-level folder. */
    val parentId: Long?,
    val name: String?,
    val kind: FolderKind,
    /** The imported file's name ("bookmarks.html"), only on an [FolderKind.IMPORTED] folder. */
    val sourceFileName: String? = null,
    val position: Int,
    val createdAt: Long,
) {
    companion object {
        const val MOBILE_ID = 1L
    }
}
