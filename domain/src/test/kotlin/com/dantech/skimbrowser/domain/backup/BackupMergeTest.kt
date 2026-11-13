package com.dantech.skimbrowser.domain.backup

import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolder
import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.SkimSettings
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import com.dantech.skimbrowser.domain.tab.Tab
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMergeTest {

    @Test
    fun mergeKeepsTheSavedUrlAndReplaceStartsFromTheFile() {
        val existing = setOf("https://example.com/kept")
        val incoming = listOf(
            BackupFolder(
                kind = FolderKind.MOBILE,
                name = null,
                sourceFileName = null,
                bookmarks = listOf(
                    BackupBookmark("Kept", "https://example.com/kept", 1),
                    BackupBookmark("New", "https://example.com/new", 2),
                    BackupBookmark("New again", "https://example.com/new", 3),
                ),
                folders = listOf(
                    BackupFolder(
                        kind = FolderKind.FOLDER,
                        name = "Work",
                        sourceFileName = null,
                        bookmarks = listOf(BackupBookmark("Nested", "https://example.com/nested", 4)),
                    ),
                ),
            ),
        )

        val added = bookmarksToAdd(existing, incoming)

        assertEquals(listOf("https://example.com/new", "https://example.com/nested"), added.map { it.url })
        assertEquals(listOf(FolderKind.MOBILE), added.first().steps.map { it.kind })
        assertEquals(listOf(FolderKind.MOBILE, FolderKind.FOLDER), added.last().steps.map { it.kind })
        assertEquals("Work", added.last().steps.last().name)
    }

    @Test
    fun theStoredTreeRoundTripsIntoFolders() {
        val folders = listOf(
            BookmarkFolder(1, null, null, FolderKind.MOBILE, null, 0, 1),
            BookmarkFolder(2, 1, "Work", FolderKind.FOLDER, null, 0, 2),
        )
        val bookmarks = listOf(
            Bookmark(1, 1, "Home", "https://example.com/", "example.com", 0, 5),
            Bookmark(2, 2, "Nested", "https://example.com/n", "example.com", 0, 6),
        )

        val tree = backupFolders(folders, bookmarks)

        assertEquals(FolderKind.MOBILE, tree.single().kind)
        assertEquals("Work", tree.single().folders.single().name)
        assertEquals(listOf("https://example.com/"), tree.single().bookmarks.map { it.url })
        assertEquals(listOf("https://example.com/n"), tree.single().folders.single().bookmarks.map { it.url })
    }

    @Test
    fun historyKeepsTheLaterVisitAndATieKeepsTheLaterRow() {
        val cases = listOf(
            listOf(
                BackupHistory("https://a.example/", "Old", 1),
                BackupHistory("https://a.example/", "New", 5),
                BackupHistory("https://b.example/", "B", 2),
            ) to listOf(
                BackupHistory("https://a.example/", "New", 5),
                BackupHistory("https://b.example/", "B", 2),
            ),
            listOf(
                BackupHistory("https://a.example/", "First", 4),
                BackupHistory("https://a.example/", "Second", 4),
            ) to listOf(BackupHistory("https://a.example/", "Second", 4)),
        )

        for ((input, expected) in cases) {
            assertEquals(expected, dedupedHistory(input))
        }
    }

    @Test
    fun tabsLeaveOutIncognitoAndMergeSkipsAUrlThatIsAlreadyOpen() {
        val open = listOf(
            Tab("normal", incognito = false, url = "https://open.example/", title = "Open", groupId = null, position = 0, createdAt = 1, lastAccessedAt = 1),
            Tab("secret", incognito = true, url = "https://secret.example/", title = "Secret", groupId = null, position = 0, createdAt = 1, lastAccessedAt = 1),
            Tab("home", incognito = false, url = null, title = "", groupId = null, position = 1, createdAt = 1, lastAccessedAt = 1),
        )
        val exported = backupTabs(open)
        assertEquals(listOf(BackupTab("https://open.example/", "Open", 0)), exported)

        val file = listOf(
            BackupTab("https://open.example/", "Open", 1),
            BackupTab("https://new.example/", "New", 0),
            BackupTab("https://new.example/", "Again", 2),
        )
        val refs = open.map { OpenTabRef(it.id, it.url, it.incognito) }
        val merged = tabsToRestore(refs, file, ImportMode.MERGE)
        assertEquals(listOf("https://new.example/"), merged.open.map { it.url })
        assertEquals(emptyList<String>(), merged.closeIds)

        val replaced = tabsToRestore(refs, file, ImportMode.REPLACE)
        assertEquals(listOf("https://new.example/", "https://open.example/"), replaced.open.map { it.url })
        assertEquals(listOf("normal", "home"), replaced.closeIds)
    }

    @Test
    fun permissionsMergeByHostAndReplaceDropsTheRest() {
        val existing = listOf(
            permission("keep.example", SkimAiState.ALLOWED),
            permission("change.example", SkimAiState.UNSET),
        )
        val incoming = listOf(
            permission("change.example", SkimAiState.ASK),
            permission("change.example", SkimAiState.ALLOWED),
            permission("add.example", SkimAiState.ASK),
        )

        val merged = permissionsToWrite(existing, incoming, ImportMode.MERGE)
        assertEquals(listOf("keep.example", "change.example", "add.example"), merged.map { it.host })
        assertEquals(SkimAiState.ALLOWED, merged.first { it.host == "change.example" }.skimAi)

        val replaced = permissionsToWrite(existing, incoming, ImportMode.REPLACE)
        assertEquals(listOf("change.example", "add.example"), replaced.map { it.host })
    }

    @Test
    fun portableSettingsKeepTheLastTabIdOnThePhone() {
        val current = SkimSettings(lastTabId = "tab-9", searchEngine = SearchEngine.GOOGLE)
        val portable = portableSettings(current.copy(theme = ThemeSetting.Dark), setOf("ads.example"))
        val restored = settingsFromPortable(
            current.copy(searchEngine = SearchEngine.BING),
            portable.copy(searchEngine = SearchEngine.ECOSIA),
        )

        assertEquals(SearchEngine.ECOSIA, restored.searchEngine)
        assertEquals(ThemeSetting.Dark, restored.theme)

        assertEquals("tab-9", restored.lastTabId)
        assertEquals(setOf("ads.example"), portable.shieldDisabledHosts)
    }

    private fun permission(host: String, skimAi: SkimAiState) = BackupSitePermission(
        host = host,
        skimAi = skimAi,
        location = PermissionState.UNSET,
        camera = PermissionState.UNSET,
        microphone = PermissionState.UNSET,
    )
}
