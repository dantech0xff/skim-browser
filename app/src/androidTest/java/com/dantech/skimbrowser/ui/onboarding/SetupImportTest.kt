package com.dantech.skimbrowser.ui.onboarding

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import android.app.Activity
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.skimbrowser.MainActivity
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.usecase.CompleteOnboarding
import com.dantech.skimbrowser.domain.usecase.ImportBookmarks
import com.dantech.skimbrowser.browser.awaitValue
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.waitUntil
import com.dantech.skimbrowser.data.db.entity.FolderKind
import com.dantech.skimbrowser.system.DefaultBrowser
import com.dantech.skimbrowser.ui.navigation.BookmarksKey
import com.dantech.skimbrowser.ui.navigation.SetupKey
import com.dantech.skimbrowser.ui.shell.AppScreen
import com.dantech.skimbrowser.ui.shell.SkimAppRule
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.text.UiText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Importing a bookmarks file on Setup (03), as the file picker hands it over: a document read
 * through the content resolver (here a file the app's own FileProvider serves) goes through Setup's
 * view model and the app's importer into Room, and the window's snackbar reports it, "Imported N
 * bookmarks" with View, or the error alone for 4 s. The bookmarks imported are deleted afterwards.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SetupImportTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val skim = SkimAppRule()

    private val files: File get() = File(skim.app.cacheDir, "shared/bookmarks-import-test")
    private val snackbar: SnackbarController get() = skim.graph.snackbarController()
    private var lastFolderBefore = 0L

    @Before
    fun noteTheFolders() {
        lastFolderBefore = lastFolderId()
    }

    @After
    fun deleteWhatWasImported() {
        runBlocking {
            withContext(Dispatchers.IO) {
                // A folder's subfolders and bookmarks were all made after it, so their ids are larger.
                skim.graph.database().openHelper.writableDatabase.apply {
                    execSQL("DELETE FROM bookmarks WHERE folderId > ?", arrayOf<Any>(lastFolderBefore))
                    execSQL("DELETE FROM bookmark_folders WHERE id > ?", arrayOf<Any>(lastFolderBefore))
                }
            }
        }
        files.deleteRecursively()
    }

    @Test
    fun aBookmarksFileExportedFromABrowserIsImportedAndViewOpensItsFolder() {
        val (scenario, activity) = showSetup()

        setupViewModel().import(documentOf("bookmarks-export.html"))

        val message = awaitSnackbar()
        assertEquals(UiText.Plural(R.plurals.snackbar_imported_bookmarks, BOOKMARKS_IN_FILE), message.text)
        assertFalse(message.isError)
        val view = message.action as SnackbarAction.ViewImported
        val folder = runBlocking { skim.graph.bookmarkRepository().observeRoot().first() }.folders.single { it.folder.id == view.folderId }
        assertEquals(FolderKind.IMPORTED, folder.folder.kind)
        assertEquals("bookmarks-export.html", folder.folder.sourceFileName)
        assertEquals("the http and https bookmarks, subfolders included", BOOKMARKS_IN_FILE, folder.bookmarkCount)

        val shown = skim.app.resources.getQuantityString(R.plurals.snackbar_imported_bookmarks, BOOKMARKS_IN_FILE, BOOKMARKS_IN_FILE)
        AppScreen.awaitNode(activity, shown)
        AppScreen.tap(activity, skim.app.getString(R.string.action_view))
        waitUntil("View to open the imported folder") { skim.keysOf(scenario).last() == BookmarksKey(view.folderId) }
    }

    @Test
    fun aFileThatIsNoBookmarksExportShowsTheErrorAloneForFourSeconds() {
        val (_, activity) = showSetup()

        setupViewModel().import(documentOf("plain.html"))

        val message = awaitSnackbar()
        assertEquals(UiText.Res(R.string.snackbar_import_failed), message.text)
        assertTrue(message.isError)
        assertNull("no action", message.action)
        assertEquals("nothing imported", lastFolderBefore, lastFolderId())

        val error = skim.app.getString(R.string.snackbar_import_failed)
        AppScreen.awaitNode(activity, error)
        val shownAt = SystemClock.uptimeMillis()
        assertEquals("announced at once", LiveRegionMode.Assertive, AppScreen.liveRegionOf(activity, error))
        waitUntil("the error to close by itself", LONG_SNACKBAR_MS) { snackbar.current.value == null }
        val shownFor = SystemClock.uptimeMillis() - shownAt
        assertTrue(
            "shown for $shownFor ms: a short snackbar, not a long one",
            shownFor >= SHORT_SNACKBAR_MS - SEEN_LATE_MS && shownFor < LONG_SNACKBAR_MS,
        )
    }

    /** Starts the app on Setup, over Welcome as a first launch has it. */
    private fun showSetup(): Pair<ActivityScenario<MainActivity>, Activity> {
        skim.setOnboarded(false)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        onMain { navigator.push(SetupKey) }
        val activity = skim.activityOf(scenario)
        AppScreen.awaitNode(activity, skim.app.getString(R.string.setup_start_browsing))
        assertNull("no snackbar yet", snackbar.current.value)
        return scenario to activity
    }

    /** Setup's view model as Setup makes it, over the app's own importer, settings and snackbar. */
    private fun setupViewModel() = SetupViewModel(
        isDefaultBrowser = { DefaultBrowser.isDefault(skim.app) },
        importBookmarks = ImportBookmarks(skim.graph.bookmarkImporter()),
        completeOnboarding = CompleteOnboarding(skim.graph.settings()),
        snackbar = snackbar,
        appScope = skim.graph.appScope(),
    )

    /** The test APK's asset [name] as a document the app reads through its content resolver. */
    private fun documentOf(name: String): Uri {
        val file = File(files.apply { mkdirs() }, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return FileProvider.getUriForFile(skim.app, "${skim.app.packageName}.fileprovider", file)
    }

    private fun awaitSnackbar(): SnackbarController.Message =
        checkNotNull(awaitValue(snackbar.current, "the import's snackbar") { it != null })

    private fun lastFolderId(): Long = runBlocking {
        withContext(Dispatchers.IO) {
            skim.graph.database().openHelper.readableDatabase.query("SELECT COALESCE(MAX(id), 0) FROM bookmark_folders").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }
    }

    private companion object {
        /** bookmarks-export.html holds 3 http(s) bookmarks, one in a subfolder, and a `javascript:` one that is left out. */
        const val BOOKMARKS_IN_FILE = 3

        /** Material's short and long snackbars. */
        const val SHORT_SNACKBAR_MS = 4_000L
        const val LONG_SNACKBAR_MS = 10_000L

        /** How late the test may notice the snackbar after its timer started: a poll and a frame. */
        const val SEEN_LATE_MS = 100L
    }
}
