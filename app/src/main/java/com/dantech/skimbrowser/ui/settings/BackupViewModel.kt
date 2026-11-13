package com.dantech.skimbrowser.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.di.AppVersion
import com.dantech.skimbrowser.domain.backup.ApplyBackup
import com.dantech.skimbrowser.domain.backup.BackupCounts
import com.dantech.skimbrowser.domain.backup.BackupDecode
import com.dantech.skimbrowser.domain.backup.BackupDocument
import com.dantech.skimbrowser.domain.backup.BackupFailure
import com.dantech.skimbrowser.domain.backup.BackupFile
import com.dantech.skimbrowser.domain.backup.BackupFiles
import com.dantech.skimbrowser.domain.backup.BackupSection
import com.dantech.skimbrowser.domain.backup.ExportBackup
import com.dantech.skimbrowser.domain.backup.ExportOutcome
import com.dantech.skimbrowser.domain.backup.ImportMode
import com.dantech.skimbrowser.domain.backup.OpenTabRef
import com.dantech.skimbrowser.domain.backup.backupTabs
import com.dantech.skimbrowser.domain.backup.preview
import com.dantech.skimbrowser.domain.bookmark.BookmarkFiles
import com.dantech.skimbrowser.domain.bookmark.ImportResult
import com.dantech.skimbrowser.domain.tab.TabsController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Backup & restore shows. Copy is resolved by the screen. */
@Immutable
data class BackupUiState(
    val bookmarks: Boolean = true,
    val history: Boolean = true,
    val tabs: Boolean = true,
    val settings: Boolean = true,
    val permissions: Boolean = true,
    val working: Boolean = false,
    /** Counts from a file waiting for merge or replace. Null while the person is choosing an export. */
    val preview: BackupCounts? = null,
    val confirmReplace: Boolean = false,
    val notice: BackupNotice? = null,
    /** Sections the open file contains. Null until a JSON file is waiting. */
    val fileSections: Set<BackupSection>? = null,
) {
    fun checked(section: BackupSection): Boolean = when (section) {
        BackupSection.BOOKMARKS -> bookmarks
        BackupSection.HISTORY -> history
        BackupSection.TABS -> tabs
        BackupSection.SETTINGS -> settings
        BackupSection.SITE_PERMISSIONS -> permissions
    }

    fun sections(): Set<BackupSection> = BackupSection.entries.filter(::checked).toSet()
}

/** The outcome of the last export or import, until the person dismisses it. */
sealed interface BackupNotice {
    data class Failed(val failure: BackupFailure) : BackupNotice

    data object Saved : BackupNotice

    data class Restored(val counts: BackupCounts) : BackupNotice

    data class HtmlImported(val count: Int) : BackupNotice

    data object HtmlFailed : BackupNotice
}

/**
 * Backup & restore. The screen only reports the file the person picked. Reading, writing, and
 * the choice to replace data happen here, off the call that paints the screen.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val exportBackup: ExportBackup,
    private val applyBackup: ApplyBackup,
    private val files: BackupFiles,
    private val bookmarkFiles: BookmarkFiles,
    private val tabs: TabsController,
    version: AppVersion,
) : ViewModel() {

    private val versionName = version.name
    private var pending: BackupDocument? = null

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Checks or unchecks [section]. A section the open file does not contain stays off. */
    fun setSection(section: BackupSection, checked: Boolean) {
        val file = _state.value.fileSections
        if (file != null && section !in file) return
        _state.update { current ->
            val next = when (section) {
                BackupSection.BOOKMARKS -> current.copy(bookmarks = checked)
                BackupSection.HISTORY -> current.copy(history = checked)
                BackupSection.TABS -> current.copy(tabs = checked)
                BackupSection.SETTINGS -> current.copy(settings = checked)
                BackupSection.SITE_PERMISSIONS -> current.copy(permissions = checked)
            }
            val document = pending
            if (document == null) next else next.copy(preview = preview(document, next.sections()))
        }
    }

    /** Writes the checked sections to [location]. */
    fun exportTo(location: String) {
        val sections = _state.value.sections()
        if (sections.isEmpty()) {
            _state.update { it.copy(notice = BackupNotice.Failed(BackupFailure.NOTHING_SELECTED)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(working = true, notice = null) }
            val outcome = exportBackup(
                sections = sections,
                openTabs = backupTabs(tabs.state.value.tabs),
                appVersion = versionName,
                createdAt = System.currentTimeMillis(),
            )
            when (outcome) {
                is ExportOutcome.TooLarge -> fail(BackupFailure.TOO_LARGE)
                is ExportOutcome.Ready -> {
                    val written = files.write(location, outcome.document)
                    _state.update {
                        it.copy(working = false, notice = if (written) BackupNotice.Saved else BackupNotice.Failed(BackupFailure.UNREADABLE))
                    }
                }
            }
        }
    }

    /** Reads [location]. A bookmark HTML file is imported on its own. JSON waits for merge or replace. */
    fun importFrom(location: String) {
        viewModelScope.launch {
            _state.update { it.copy(working = true, notice = null, preview = null, confirmReplace = false) }
            pending = null
            when (val file = files.read(location)) {
                BackupFile.Html -> importHtml(location)
                BackupFile.TooLarge -> fail(BackupFailure.TOO_LARGE)
                BackupFile.Unreadable -> fail(BackupFailure.UNREADABLE)
                is BackupFile.Json -> when (val decoded = file.result) {
                    is BackupDecode.Rejected -> fail(decoded.failure)
                    is BackupDecode.Ok -> showPreview(decoded.document)
                }
            }
        }
    }

    fun merge() = apply(ImportMode.MERGE)

    fun askReplace() {
        if (pending == null || _state.value.sections().isEmpty()) {
            _state.update { it.copy(notice = BackupNotice.Failed(BackupFailure.NOTHING_SELECTED)) }
            return
        }
        _state.update { it.copy(confirmReplace = true) }
    }

    fun dismissReplace() {
        _state.update { it.copy(confirmReplace = false) }
    }

    fun confirmReplace() {
        _state.update { it.copy(confirmReplace = false) }
        apply(ImportMode.REPLACE)
    }

    fun dismissPreview() {
        pending = null
        _state.update { it.copy(preview = null, fileSections = null, confirmReplace = false) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** The system has no screen that can pick a file. */
    fun pickerMissing() {
        _state.update { it.copy(notice = BackupNotice.Failed(BackupFailure.UNREADABLE)) }
    }

    private fun showPreview(document: BackupDocument) {
        pending = document
        val included = document.included()
        _state.update {
            it.copy(
                working = false,
                bookmarks = BackupSection.BOOKMARKS in included,
                history = BackupSection.HISTORY in included,
                tabs = BackupSection.TABS in included,
                settings = BackupSection.SETTINGS in included,
                permissions = BackupSection.SITE_PERMISSIONS in included,
                preview = preview(document, included),
                fileSections = included,
                notice = null,
            )
        }
    }

    private suspend fun importHtml(location: String) {
        when (val result = bookmarkFiles.import(location)) {
            is ImportResult.Imported -> _state.update {
                it.copy(working = false, notice = BackupNotice.HtmlImported(result.bookmarkCount), fileSections = null)
            }
            is ImportResult.Failed -> _state.update {
                it.copy(working = false, notice = BackupNotice.HtmlFailed, fileSections = null)
            }
        }
    }

    private fun apply(mode: ImportMode) {
        val document = pending ?: return
        val sections = _state.value.sections()
        if (sections.isEmpty()) {
            _state.update { it.copy(notice = BackupNotice.Failed(BackupFailure.NOTHING_SELECTED)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(working = true, confirmReplace = false) }
            val open = tabs.state.value.tabs.map { OpenTabRef(it.id, it.url, it.incognito) }
            val restore = applyBackup(document, sections, mode, open, System.currentTimeMillis())
            restore.open.forEach { tab -> tabs.openBackgroundTab(tab.url, tab.title) }
            if (restore.closeIds.isNotEmpty()) tabs.finalizeClose(tabs.close(restore.closeIds))
            pending = null
            _state.update {
                it.copy(
                    working = false,
                    preview = null,
                    fileSections = null,
                    notice = BackupNotice.Restored(preview(document, sections)),
                )
            }
        }
    }

    private fun fail(failure: BackupFailure) {
        pending = null
        _state.update { it.copy(working = false, preview = null, fileSections = null, notice = BackupNotice.Failed(failure)) }
    }
}
