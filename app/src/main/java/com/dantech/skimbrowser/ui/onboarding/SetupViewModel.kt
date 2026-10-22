package com.dantech.skimbrowser.ui.onboarding

import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.importer.BookmarkImporter
import com.dantech.skimbrowser.data.importer.ImportResult
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What 03 shows: whether Skim is the default browser already (Set as default turns into Done). */
data class SetupUiState(val isDefault: Boolean)

/**
 * Default browser and import (03). Whether Skim is the default browser is checked again every time
 * the screen resumes, since the user may have changed it in the system's settings meanwhile; observe
 * the screen's lifecycle with this object.
 *
 * An import and the end of onboarding run on [appScope]: they finish, and the import's snackbar
 * shows, even when the user leaves the screen right away.
 *
 * @param isDefaultBrowser asks the system whether Skim is the default browser now.
 */
class SetupViewModel(
    private val isDefaultBrowser: () -> Boolean,
    private val importer: BookmarkImporter,
    private val settings: SettingsRepository,
    private val snackbar: SnackbarController,
    private val appScope: CoroutineScope,
) : ViewModel(), DefaultLifecycleObserver {

    private val _state = MutableStateFlow(SetupUiState(isDefault = isDefaultBrowser()))
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    override fun onResume(owner: LifecycleOwner) {
        refreshDefault()
    }

    /** Checks again whether Skim is the default browser, after the system's role dialog for instance. */
    fun refreshDefault() {
        _state.value = SetupUiState(isDefault = isDefaultBrowser())
    }

    /**
     * Imports the bookmarks file [uri]: "Imported N bookmarks" with View when it worked, otherwise
     * the error snackbar (4 s, announced at once) and nothing imported.
     */
    fun import(uri: Uri) {
        appScope.launch {
            when (val result = importer.import(uri)) {
                is ImportResult.Imported -> snackbar.show(
                    UiText.Plural(R.plurals.snackbar_imported_bookmarks, result.bookmarkCount),
                    SnackbarAction.ViewImported(result.folderId),
                )
                is ImportResult.Failed -> snackbar.show(UiText.Res(R.string.snackbar_import_failed), isError = true)
            }
        }
    }

    /** Start browsing: onboarding is done for good, so later launches open on the last tab. */
    fun finishOnboarding() {
        appScope.launch { settings.setOnboardingCompleted() }
    }
}
