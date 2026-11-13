package com.dantech.skimbrowser.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.di.AppVersion
import com.dantech.skimbrowser.domain.backup.ApplyBackup
import com.dantech.skimbrowser.domain.backup.BackupBookmark
import com.dantech.skimbrowser.domain.backup.BackupDecode
import com.dantech.skimbrowser.domain.backup.BackupDocument
import com.dantech.skimbrowser.domain.backup.BackupFailure
import com.dantech.skimbrowser.domain.backup.BackupFile
import com.dantech.skimbrowser.domain.backup.BackupFiles
import com.dantech.skimbrowser.domain.backup.BackupFolder
import com.dantech.skimbrowser.domain.backup.BackupHistory
import com.dantech.skimbrowser.domain.backup.BackupLibrary
import com.dantech.skimbrowser.domain.backup.BackupSettings
import com.dantech.skimbrowser.domain.backup.BackupSitePermission
import com.dantech.skimbrowser.domain.backup.BookmarkPlacement
import com.dantech.skimbrowser.domain.backup.ExportBackup
import com.dantech.skimbrowser.domain.bookmark.BookmarkFiles
import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.bookmark.ImportResult
import com.dantech.skimbrowser.domain.fake.FakeBookmarkFiles
import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import com.dantech.skimbrowser.domain.tab.RecordingTabs
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun exportWritesTheCheckedSectionsAndLeavesIncognitoTabsOut() = runTest {
        val library = MemoryLibrary()
        val files = MemoryFiles()
        val tabs = RecordingTabs()
        tabs.show(
            listOf(
                tab("normal", "https://open.example/", incognito = false),
                tab("secret", "https://secret.example/", incognito = true),
            ),
        )
        val model = model(library, files, tabs)
        model.setSection(com.dantech.skimbrowser.domain.backup.BackupSection.HISTORY, false)

        model.exportTo("content://backup")
        model.state.first { it.notice is BackupNotice.Saved }

        val written = files.written!!
        assertEquals(listOf("https://saved.example/"), written.bookmarks?.single()?.bookmarks?.map { it.url })
        assertNull(written.history)
        assertEquals(listOf("https://open.example/"), written.tabs?.map { it.url })
        assertTrue(written.settings != null)
    }

    @Test
    fun importPreviewsCountsAndMergeDoesNotDuplicateAUrl() = runTest {
        val library = MemoryLibrary()
        val files = MemoryFiles()
        val model = model(library, files, RecordingTabs())
        files.readResult = BackupFile.Json(BackupDecode.Ok(fileWith("https://saved.example/", "https://new.example/")))

        model.importFrom("content://backup")
        val preview = model.state.first { it.preview != null }.preview!!
        assertEquals(2, preview.bookmarks)

        model.merge()
        model.state.first { it.notice is BackupNotice.Restored }

        assertEquals(listOf("https://saved.example/", "https://new.example/"), library.urls())
    }

    @Test
    fun replaceOpensBackgroundTabsAndClosesTheNormalOnesAfterConfirm() = runTest {
        val library = MemoryLibrary()
        val files = MemoryFiles()
        val tabs = RecordingTabs()
        tabs.show(listOf(tab("old", "https://old.example/", incognito = false)))
        val model = model(library, files, tabs)
        files.readResult = BackupFile.Json(
            BackupDecode.Ok(
                BackupDocument(
                    schemaVersion = 1,
                    appVersion = "1.0",
                    createdAt = 1,
                    tabs = listOf(com.dantech.skimbrowser.domain.backup.BackupTab("https://new.example/", "New", 0)),
                ),
            ),
        )

        model.importFrom("content://backup")
        model.state.first { it.preview != null }
        model.askReplace()
        assertTrue(model.state.value.confirmReplace)
        model.confirmReplace()
        model.state.first { it.notice is BackupNotice.Restored }

        assertEquals(listOf("https://new.example/"), tabs.background.map { it.url })
        assertEquals(listOf("New"), tabs.background.map { it.title })
        assertEquals(listOf(listOf("old")), tabs.closed)
        assertTrue(tabs.finalized != null)
    }

    @Test
    fun aMalformedFileAndABookmarkHtmlFileAreToldApart() = runTest {
        val files = MemoryFiles()
        val bookmarks = FakeBookmarkFiles(ImportResult.Imported(folderId = 4, bookmarkCount = 3))
        val model = model(MemoryLibrary(), files, RecordingTabs(), bookmarks)
        files.readResult = BackupFile.Json(BackupDecode.Rejected(BackupFailure.MALFORMED))

        model.importFrom("content://bad")
        val failed = model.state.first { it.notice is BackupNotice.Failed }.notice as BackupNotice.Failed
        assertEquals(BackupFailure.MALFORMED, failed.failure)

        model.dismissNotice()
        files.readResult = BackupFile.Html
        model.importFrom("content://bookmarks.html")
        val imported = model.state.first { it.notice is BackupNotice.HtmlImported }.notice as BackupNotice.HtmlImported
        assertEquals(3, imported.count)
        assertEquals(listOf("content://bookmarks.html"), bookmarks.locations)
    }

    private fun model(
        library: MemoryLibrary,
        files: MemoryFiles,
        tabs: RecordingTabs,
        bookmarks: BookmarkFiles = FakeBookmarkFiles(),
    ) = BackupViewModel(
        exportBackup = ExportBackup(library),
        applyBackup = ApplyBackup(library),
        files = files,
        bookmarkFiles = bookmarks,
        tabs = tabs,
        version = AppVersion("1.0", 1),
    )

    private fun tab(id: String, url: String, incognito: Boolean) = Tab(
        id = id,
        incognito = incognito,
        url = url,
        title = id,
        groupId = null,
        position = 0,
        createdAt = 1,
        lastAccessedAt = 1,
    )

    private fun fileWith(vararg urls: String) = BackupDocument(
        schemaVersion = 1,
        appVersion = "1.0",
        createdAt = 1,
        bookmarks = listOf(
            BackupFolder(
                kind = FolderKind.MOBILE,
                name = null,
                sourceFileName = null,
                bookmarks = urls.map { BackupBookmark(it, it, 1) },
            ),
        ),
    )
}

private class MemoryFiles : BackupFiles {
    var readResult: BackupFile = BackupFile.Unreadable
    var written: BackupDocument? = null

    override suspend fun read(location: String): BackupFile = readResult

    override suspend fun write(location: String, document: BackupDocument): Boolean {
        written = document
        return true
    }
}

private class MemoryLibrary : BackupLibrary {
    private val marks = linkedMapOf<String, BackupBookmark>()
    var history = listOf(BackupHistory("https://history.example/", "H", 1))

    init {
        marks["https://saved.example/"] = BackupBookmark("Saved", "https://saved.example/", 1)
    }

    fun urls(): List<String> = marks.keys.toList()

    override suspend fun bookmarks(): List<BackupFolder> = listOf(
        BackupFolder(FolderKind.MOBILE, null, null, bookmarks = marks.values.toList()),
    )

    override suspend fun history(): List<BackupHistory> = history

    override suspend fun settings(): BackupSettings = BackupSettings(
        searchEngine = SearchEngine.GOOGLE,
        theme = ThemeSetting.System,
        askBeforeSending = true,
        keepChatHistory = true,
        sendRatings = null,
        readerTextScalePercent = 100,
        pageTextZoomPercent = null,
        readerFont = ReaderFont.SANS_SERIF,
        onboardingCompleted = true,
        scamWarnings = true,
        shieldDisabledHosts = emptySet(),
    )

    override suspend fun permissions(): List<BackupSitePermission> = emptyList()

    override suspend fun replaceBookmarks(tree: List<BackupFolder>, now: Long) {
        marks.clear()
        tree.flatMap { it.bookmarks }.forEach { marks[it.url] = it }
    }

    override suspend fun mergeBookmarks(rows: List<BookmarkPlacement>, now: Long) {
        rows.forEach { marks.putIfAbsent(it.url, BackupBookmark(it.title, it.url, it.addedAt)) }
    }

    override suspend fun replaceHistory(entries: List<BackupHistory>) {
        history = entries
    }

    override suspend fun mergeHistory(entries: List<BackupHistory>) = Unit

    override suspend fun applySettings(settings: BackupSettings) = Unit

    override suspend fun replacePermissions(rows: List<BackupSitePermission>, now: Long) = Unit

    override suspend fun mergePermissions(rows: List<BackupSitePermission>, now: Long) = Unit
}
