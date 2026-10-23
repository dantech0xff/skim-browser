package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolder
import com.dantech.skimbrowser.domain.bookmark.ImportResult
import com.dantech.skimbrowser.domain.chat.ChatKind
import com.dantech.skimbrowser.domain.chat.DeletedChat
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.fake.FakeBookmarkFiles
import com.dantech.skimbrowser.domain.fake.FakeBookmarkRepository
import com.dantech.skimbrowser.domain.fake.FakeChatRepository
import com.dantech.skimbrowser.domain.fake.FakeHistoryRepository
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.fake.FakeShortcutRepository
import com.dantech.skimbrowser.domain.fake.FakeSitePermissionRepository
import com.dantech.skimbrowser.domain.history.HistoryEntry
import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.SitePermissionKind
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import com.dantech.skimbrowser.domain.shortcut.Shortcut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each use case is one call on the repository it was given. */
class UseCaseTest {

    @Test
    fun historyUseCasesRemoveRestoreAndMatch() = runTest {
        val history = FakeHistoryRepository()
        history.recordVisit("https://example.com/heat", "Heat", at = 1)
        val id = history.observe(null).first().single().id

        val removed = RemoveHistoryEntry(history)(id)
        assertEquals("https://example.com/heat", removed?.url)
        assertTrue(history.observe(null).first().isEmpty())

        RestoreHistoryEntry(history)(removed!!)
        assertEquals(listOf(id), history.observe(null).first().map { it.id })
        assertEquals(listOf("Heat"), MatchHistory(history)("heat", limit = 5).map { it.title })
        assertEquals(emptyList<HistoryEntry>(), MatchHistory(history)("  ", limit = 5))
    }

    @Test
    fun bookmarkUseCasesWriteMoveMatchAndImport() = runTest {
        val bookmarks = FakeBookmarkRepository()
        val id = AddBookmark(bookmarks)("Heat", "https://example.com/heat")
        UpdateBookmark(bookmarks)(id, "Cold", "https://example.com/cold")
        assertEquals("Cold", bookmarks.observe(id).first()?.title)

        val imported = bookmarks.addImported("bookmarks.html", "Guide", "https://guide.example/")
        assertTrue(MoveBookmark(bookmarks)(id, imported))
        assertEquals(imported, bookmarks.observe(id).first()?.folderId)

        val removed = DeleteBookmark(bookmarks)(id)
        assertEquals("https://example.com/cold", removed?.url)
        RestoreBookmark(bookmarks)(removed!!)
        assertEquals(id, bookmarks.observe(id).first()?.id)
        assertEquals(listOf("Guide"), MatchBookmarks(bookmarks)("guide", limit = 5).map { it.title })

        val files = FakeBookmarkFiles(ImportResult.Imported(folderId = 9, bookmarkCount = 4))
        assertEquals(ImportResult.Imported(9, 4), ImportBookmarks(files)("content://bookmarks"))
        assertEquals(listOf("content://bookmarks"), files.locations)
    }

    @Test
    fun shortcutUseCasesAddUpdateRemoveAndRestore() = runTest {
        val shortcuts = FakeShortcutRepository()
        AddShortcut(shortcuts)("Wiki", "https://wikipedia.org")
        val saved = shortcuts.observe().first().single()
        UpdateShortcut(shortcuts)(saved.id, "Wikipedia", "https://www.wikipedia.org")
        assertEquals("Wikipedia", shortcuts.observe().first().single().name)

        val removed = RemoveShortcut(shortcuts)(saved.id)
        assertEquals(saved.id, removed?.id)
        assertTrue(shortcuts.observe().first().isEmpty())
        RestoreShortcut(shortcuts)(removed!!.copy(name = "Wikipedia"))
        assertEquals(listOf("Wikipedia"), shortcuts.observe().first().map { it.name })
    }

    @Test
    fun settingsUseCasesWriteEachSetting() = runTest {
        val settings = FakeSettingsRepository()
        SetSearchEngine(settings)(SearchEngine.DUCKDUCKGO)
        SetTheme(settings)(ThemeSetting.Dark)
        SetAskBeforeSending(settings)(false)
        SetKeepChatHistory(settings)(false)
        SetReaderTextScale(settings)(140)
        SetReaderFont(settings)(ReaderFont.SERIF)
        CompleteOnboarding(settings)()
        val stored = settings.settings.first()
        assertEquals(SearchEngine.DUCKDUCKGO, stored.searchEngine)
        assertEquals(ThemeSetting.Dark, stored.theme)
        assertEquals(false, stored.askBeforeSending)
        assertEquals(false, stored.keepChatHistory)
        assertEquals(140, stored.readerTextScalePercent)
        assertEquals(ReaderFont.SERIF, stored.readerFont)
        assertTrue(stored.onboardingCompleted)
    }

    @Test
    fun readerScaleIsClampedByTheSettingsUseCase() = runTest {
        val settings = FakeSettingsRepository()
        SetReaderTextScale(settings)(10)
        assertEquals(80, settings.settings.first().readerTextScalePercent)
    }

    @Test
    fun chatUseCasesLoadRemoveAndRestore() = runTest {
        val chats = FakeChatRepository()
        val id = chats.insert(SavedChat(kind = ChatKind.PAGE, url = "https://example.com", host = "example.com", title = "Heat", createdAt = 1, updatedAt = 1))
        assertEquals("Heat", LoadSavedChat(chats)(id)?.title)
        assertNull(LoadSavedChat(chats)(404))

        val removed = RemoveSkimChat(chats)(id)
        assertEquals(id, removed?.chat?.id)
        assertTrue(chats.observeAll(null).first().isEmpty())
        RestoreSkimChat(chats)(removed!!)
        assertEquals(listOf(id), chats.observeAll(null).first().map { it.chat.id })
        assertNull(RemoveSkimChat(chats)(404))
    }

    @Test
    fun permissionUseCasesWriteAndReadSiteDecisions() = runTest {
        val sites = FakeSitePermissionRepository()
        SetSkimAi(sites)("WWW.Example.com", SkimAiState.ALLOWED)
        SetSitePermission(sites)("example.com", SitePermissionKind.CAMERA, PermissionState.BLOCKED)
        assertEquals(SkimAiState.ALLOWED, ReadSkimAiStates(sites)(setOf("example.com"))["example.com"])
        assertEquals(PermissionState.BLOCKED, sites.get("example.com")?.camera)
    }

    @Test
    fun restoringAMissingBookmarkDoesNotInventOne() = runTest {
        val bookmarks = FakeBookmarkRepository()
        assertNull(DeleteBookmark(bookmarks)(4))
        assertEquals(emptyList<Bookmark>(), bookmarks.stored())
        assertEquals(BookmarkFolder.MOBILE_ID, 1L)
    }

    @Test
    fun restoringAShortcutThatIsStillThereLeavesTheListAlone() = runTest {
        val shortcuts = FakeShortcutRepository()
        AddShortcut(shortcuts)("Wiki", "https://wikipedia.org")
        val saved = shortcuts.observe().first().single()
        RestoreShortcut(shortcuts)(Shortcut(saved.id, "Other", "https://other.example", saved.position))
        assertEquals(listOf("Wiki"), shortcuts.observe().first().map { it.name })
    }

    @Test
    fun restoringAChatThatIsStillThereLeavesItAlone() = runTest {
        val chats = FakeChatRepository()
        val id = chats.insert(SavedChat(kind = ChatKind.PAGE, url = null, host = null, title = "Kept", createdAt = 1, updatedAt = 2))
        val current = chats.load(id)!!
        RestoreSkimChat(chats)(DeletedChat(current.copy(title = "Replaced"), emptyList()))
        assertEquals("Kept", chats.load(id)?.title)
    }
}
