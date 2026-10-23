package com.dantech.skimbrowser.ui.onboarding

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.bookmark.ImportResult
import com.dantech.skimbrowser.domain.fake.FakeBookmarkFiles
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.usecase.CompleteOnboarding
import com.dantech.skimbrowser.domain.usecase.ImportBookmarks
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SetupViewModelTest {

    private val files = FakeBookmarkFiles()
    private val settings = FakeSettingsRepository()
    private val snackbar = SnackbarController(UndoRegistry(CoroutineScope(Dispatchers.Unconfined)))

    /** What the system answers when asked whether Skim is the default browser. */
    private var skimIsDefault = false

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
        files.result = ImportResult.Imported(folderId = 4, bookmarkCount = 6)
        val viewModel = viewModel()

        viewModel.import(Uri.parse("content://bookmarks/chrome"))

        val shown = snackbar.current.filterNotNull().first()
        assertEquals(UiText.Plural(R.plurals.snackbar_imported_bookmarks, 6), shown.text)
        assertEquals(SnackbarAction.ViewImported(4), shown.action)
        assertFalse(shown.isError)
        assertEquals(listOf("content://bookmarks/chrome"), files.locations)
    }

    @Test
    fun aFileThatIsNotABookmarkFileShowsTheErrorAndImportsNothing() = runTest {
        files.result = ImportResult.Failed(ImportResult.Reason.NotBookmarks)
        val viewModel = viewModel()

        viewModel.import(Uri.parse("content://bookmarks/page"))

        val shown = snackbar.current.filterNotNull().first()
        assertEquals(UiText.Res(R.string.snackbar_import_failed), shown.text)
        assertNull("4 s and no action", shown.action)
        assertTrue("announced at once", shown.isError)
    }

    @Test
    fun aBookmarkFileWithNothingToKeepShowsTheErrorNotASuccess() = runTest {
        files.result = ImportResult.Failed(ImportResult.Reason.NoBookmarks)
        val viewModel = viewModel()

        viewModel.import(Uri.parse("content://bookmarks/scripts"))

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
        importBookmarks = ImportBookmarks(files),
        completeOnboarding = CompleteOnboarding(settings),
        snackbar = snackbar,
        appScope = backgroundScope,
    )

    /** The screen's lifecycle, moved by hand. */
    private class Screen : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }
}
