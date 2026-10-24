package com.dantech.skimbrowser.ui.bookmarks

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.entity.FolderKind
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Bookmarks (14): the root, an open Imported folder, and Bookmark options. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class BookmarksScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bookmarks() {
        show("Bookmarks", Root, menuBookmarkId = null)
        captureBoard("14-bookmarks")
        composeRule.assertA11y()
    }

    @Test
    fun importedFolder() {
        show(
            "Imported",
            BookmarksUiState(
                loaded = true,
                title = FolderLabel.Imported,
                bookmarks = persistentListOf(
                    bookmark(1, "D", "Why heat pumps are winning in cold climates", "daylightjournal.com"),
                    bookmark(2, "E", "Heat pump rebates 2026", "energysaver.example"),
                ),
            ),
            menuBookmarkId = null,
        )
        capture("14-bookmarks--imported")
    }

    @Test
    fun bookmarkMenu() {
        show("Bookmarks", Root, menuBookmarkId = 1)
        capture("14-bookmarks--menu")
    }

    private fun show(title: String, state: BookmarksUiState, menuBookmarkId: Long?) {
        composeRule.setSkimContent {
            BookmarksScreen(
                title = title,
                state = state,
                searchState = null,
                menuBookmarkId = menuBookmarkId,
                onBack = {},
                onSearch = {},
                onCloseSearch = {},
                onOpenFolder = {},
                onOpenBookmark = {},
                onOpenMenu = {},
                onDismissMenu = {},
                onEdit = {},
                onMove = {},
                onDelete = {},
            )
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        captureScreenRoboImage("src/test/screenshots/boards/$name.png")
        composeRule.assertA11y()
    }

    private companion object {
        val Root = BookmarksUiState(
            loaded = true,
            folders = persistentListOf(
                BookmarkFolderRow(1, FolderLabel.Mobile, FolderKind.MOBILE, null, 3),
                BookmarkFolderRow(2, FolderLabel.Imported, FolderKind.IMPORTED, "bookmarks.html", 24),
            ),
            bookmarks = persistentListOf(
                bookmark(1, "D", "Why heat pumps are winning in cold climates", "daylightjournal.com"),
                bookmark(2, "T", "Osaka night buses: a complete guide", "tabi-note.jp"),
                bookmark(3, "K", "Kotlin 2.2 release notes", "kotlinlang.org"),
            ),
            heading = BookmarkListHeading.RecentlyAdded,
        )

        fun bookmark(id: Long, letter: String, title: String, host: String) = BookmarkRow(
            id = id,
            title = title,
            url = "https://$host/",
            host = host,
            letter = letter,
        )
    }
}
