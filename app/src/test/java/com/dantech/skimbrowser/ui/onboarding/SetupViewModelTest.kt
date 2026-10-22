package com.dantech.skimbrowser.ui.onboarding

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.FolderKind
import com.dantech.skimbrowser.data.importer.BookmarkImporter
import com.dantech.skimbrowser.data.repo.BookmarkRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.testing.TestDocuments
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SetupViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: SkimDatabase
    private lateinit var bookmarks: BookmarkRepository
    private lateinit var documents: TestDocuments
    private val settingsScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var settings: SettingsRepository
    private val snackbar = SnackbarController(UndoRegistry(CoroutineScope(Dispatchers.Unconfined)))

    /** What the system answers when asked whether Skim is the default browser. */
    private var skimIsDefault = false

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        bookmarks = BookmarkRepository(db.bookmarkDao())
        documents = TestDocuments.register()
        settings = SettingsRepository(SettingsRepository.createDataStore(settingsScope) { File(folder.root, "settings.preferences_pb") })
    }

    @After
    fun tearDown() = runBlocking {
        settingsScope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    @Test
    fun theScreenOpensKnowingWhetherSkimIsTheDefaultBrowser() = runTest {
        skimIsDefault = true

        assertTrue(viewModel().state.value.isDefault)
    }

    @Test
    fun setAsDefaultTurnsIntoDoneWhenTheScreenResumesAfterSkimBecameDefault() = runTest {
        val viewModel = viewModel()
        val screen = Screen().apply { lifecycle.addObserver(viewModel) }
        screen.lifecycle.currentState = Lifecycle.State.RESUMED
        assertFalse(viewModel.state.value.isDefault)

        // The user leaves for the system's settings and makes Skim the default there.
        screen.lifecycle.currentState = Lifecycle.State.CREATED
        skimIsDefault = true
        assertFalse("not asked again before the screen is back", viewModel.state.value.isDefault)

        screen.lifecycle.currentState = Lifecycle.State.RESUMED
        assertTrue(viewModel.state.value.isDefault)
    }

    @Test
    fun everyResumeAsksAgainSoSkimLosingTheRoleShowsSetAsDefault() = runTest {
        skimIsDefault = true
        val viewModel = viewModel()
        val screen = Screen().apply { lifecycle.addObserver(viewModel) }
        screen.lifecycle.currentState = Lifecycle.State.RESUMED

        screen.lifecycle.currentState = Lifecycle.State.STARTED
        skimIsDefault = false
        screen.lifecycle.currentState = Lifecycle.State.RESUMED

        assertFalse(viewModel.state.value.isDefault)
    }

    @Test
    fun theAnswerOfTheRoleDialogIsCheckedAgain() = runTest {
        val viewModel = viewModel()
        skimIsDefault = true

        viewModel.refreshDefault()

        assertTrue(viewModel.state.value.isDefault)
    }

    @Test
    fun importingABookmarkFileShowsHowManyWithView() = runTest {
        val file = fixture("chrome-export.html")
        val viewModel = viewModel()

        viewModel.import(documents.add("chrome", file, displayName = "bookmarks.html"))

        val shown = snackbar.current.filterNotNull().first()
        assertEquals(UiText.Plural(R.plurals.snackbar_imported_bookmarks, 6), shown.text)
        val imported = bookmarks.observeRoot().first().folders.single { it.folder.kind == FolderKind.IMPORTED }
        assertEquals(SnackbarAction.ViewImported(imported.folder.id), shown.action)
        assertFalse(shown.isError)
    }

    @Test
    fun aFileThatIsNotABookmarkFileShowsTheErrorAndImportsNothing() = runTest {
        val viewModel = viewModel()

        viewModel.import(documents.add("page", fixture("not-bookmarks.html"), displayName = "page.html"))

        val shown = snackbar.current.filterNotNull().first()
        assertEquals(UiText.Res(R.string.snackbar_import_failed), shown.text)
        assertNull("4 s and no action", shown.action)
        assertTrue("announced at once", shown.isError)
        assertTrue(bookmarks.observeRoot().first().folders.none { it.folder.kind == FolderKind.IMPORTED })
    }

    @Test
    fun aBookmarkFileWithNothingToKeepShowsTheErrorNotASuccess() = runTest {
        val viewModel = viewModel()
        val scripts = folder.newFile("scripts.html").apply {
            writeText("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n<DT><A HREF=\"javascript:void(0)\">Bookmarklet</A>\n</DL><p>\n")
        }

        viewModel.import(documents.add("scripts", scripts, displayName = "scripts.html"))

        val shown = snackbar.current.filterNotNull().first()
        assertEquals(UiText.Res(R.string.snackbar_import_failed), shown.text)
        assertNull(shown.action)
    }

    @Test
    fun startBrowsingRemembersThatOnboardingIsDone() = runTest {
        val viewModel = viewModel()
        assertFalse(settings.settings.first().onboardingCompleted)

        viewModel.finishOnboarding()

        assertTrue(settings.settings.first { it.onboardingCompleted }.onboardingCompleted)
    }

    /** The view model as 03 makes it, with work that outlives the screen on the test's scope. */
    private fun TestScope.viewModel() = SetupViewModel(
        isDefaultBrowser = { skimIsDefault },
        importer = BookmarkImporter(
            resolver = context.contentResolver,
            bookmarks = bookmarks,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            parseDispatcher = StandardTestDispatcher(testScheduler),
        ),
        settings = settings,
        snackbar = snackbar,
        appScope = backgroundScope,
    )

    /** The screen's lifecycle, moved by hand. */
    private class Screen : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }

    private fun fixture(name: String): File {
        val bytes = checkNotNull(javaClass.classLoader?.getResourceAsStream("bookmarks/$name")) { "Missing fixture $name" }
            .use { it.readBytes() }
        return File(folder.root, name).apply { writeBytes(bytes) }
    }
}
