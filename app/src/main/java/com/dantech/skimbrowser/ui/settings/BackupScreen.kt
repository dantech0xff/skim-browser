package com.dantech.skimbrowser.ui.settings

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.backup.BackupFailure
import com.dantech.skimbrowser.domain.backup.BackupSection
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimAlertDialog
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonStyle
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.navigation.SkimNavigator

/** Settings › Backup & restore. The person picks the file. The view model reads and writes it. */
@Composable
fun BackupRoute(navigator: SkimNavigator, model: BackupViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        uri?.let { model.exportTo(it.toString()) }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.importFrom(it.toString()) }
    }
    BackupScreen(
        state = state,
        onBack = navigator::back,
        onSection = model::setSection,
        onExport = {
            try {
                export.launch(EXPORT_NAME)
            } catch (error: ActivityNotFoundException) {
                Log.w(TAG, "No screen can create a file: ${error.javaClass.simpleName}")
                model.pickerMissing()
            }
        },
        onImport = {
            try {
                import.launch(arrayOf(JSON_MIME, HTML_MIME, TEXT_MIME, ANY_MIME))
            } catch (error: ActivityNotFoundException) {
                Log.w(TAG, "No screen can open a file: ${error.javaClass.simpleName}")
                model.pickerMissing()
            }
        },
        onMerge = model::merge,
        onReplace = model::askReplace,
        onCancelPreview = model::dismissPreview,
        onConfirmReplace = model::confirmReplace,
        onDismissReplace = model::dismissReplace,
        onDismissNotice = model::dismissNotice,
    )
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    onBack: () -> Unit,
    onSection: (BackupSection, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onCancelPreview: () -> Unit,
    onConfirmReplace: () -> Unit,
    onDismissReplace: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(title = stringResource(R.string.backup_title), onBack = onBack, modifier = modifier) {
        item {
            Text(
                text = stringResource(R.string.backup_body),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.working) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        }
        item { SectionLabel(stringResource(R.string.backup_sections), first = true) }
        item {
            SegmentedColumn {
                sectionRow(state, BackupSection.BOOKMARKS, R.string.backup_section_bookmarks, 0, onSection)
                sectionRow(state, BackupSection.HISTORY, R.string.backup_section_history, 1, onSection)
                sectionRow(state, BackupSection.TABS, R.string.backup_section_tabs, 2, onSection)
                sectionRow(state, BackupSection.SETTINGS, R.string.backup_section_settings, 3, onSection)
                sectionRow(state, BackupSection.SITE_PERMISSIONS, R.string.backup_section_permissions, 4, onSection)
            }
        }
        state.preview?.let { counts ->
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.backup_preview), style = MaterialTheme.typography.titleMedium)
                    if (state.bookmarks) Text(pluralStringResource(R.plurals.bookmarks_count, counts.bookmarks, counts.bookmarks))
                    if (state.history) Text(pluralStringResource(R.plurals.backup_history_count, counts.history, counts.history))
                    if (state.tabs) Text(pluralStringResource(R.plurals.tabs_title, counts.tabs, counts.tabs))
                    if (state.settings && counts.hasSettings) Text(stringResource(R.string.backup_section_settings))
                    if (state.permissions) {
                        Text(pluralStringResource(R.plurals.backup_permission_count, counts.sitePermissions, counts.sitePermissions))
                    }
                }
            }
        }
        state.notice?.let { notice ->
            item {
                Text(
                    text = noticeText(notice),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable(onClick = onDismissNotice),
                    color = if (notice is BackupNotice.Failed || notice is BackupNotice.HtmlFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.preview == null) {
                    SkimButton(
                        text = stringResource(R.string.backup_export),
                        onClick = onExport,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SkimButton(
                        text = stringResource(R.string.backup_import),
                        onClick = onImport,
                        style = SkimButtonStyle.Outlined,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SkimButton(
                        text = stringResource(R.string.backup_merge),
                        onClick = onMerge,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SkimButton(
                        text = stringResource(R.string.backup_replace),
                        onClick = onReplace,
                        style = SkimButtonStyle.Outlined,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SkimButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onCancelPreview,
                        style = SkimButtonStyle.Text,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (state.confirmReplace) {
        SkimAlertDialog(
            title = stringResource(R.string.backup_replace_title),
            confirm = DialogAction(stringResource(R.string.backup_replace), onConfirmReplace),
            dismiss = DialogAction(stringResource(R.string.action_cancel), onDismissReplace),
            onDismissRequest = onDismissReplace,
            destructiveConfirm = true,
            text = { Text(stringResource(R.string.backup_replace_body)) },
        )
    }
}

@Composable
private fun sectionRow(
    state: BackupUiState,
    section: BackupSection,
    label: Int,
    index: Int,
    onSection: (BackupSection, Boolean) -> Unit,
) {
    val available = state.fileSections?.contains(section) ?: true
    val checked = state.checked(section) && available
    SkimListItem(
        headline = stringResource(label),
        index = index,
        count = SECTION_COUNT,
        onClick = if (state.working || !available) null else ({ onSection(section, !checked) }),
        trailing = { Checkbox(checked = checked, onCheckedChange = null, enabled = available && !state.working) },
    )
}

@Composable
private fun noticeText(notice: BackupNotice): String = when (notice) {
    is BackupNotice.Failed -> when (notice.failure) {
        BackupFailure.MALFORMED -> stringResource(R.string.backup_error_malformed)
        BackupFailure.UNSUPPORTED_VERSION -> stringResource(R.string.backup_error_version)
        BackupFailure.TOO_LARGE -> stringResource(R.string.backup_error_too_large)
        BackupFailure.UNREADABLE -> stringResource(R.string.backup_error_unreadable)
        BackupFailure.NOTHING_SELECTED -> stringResource(R.string.backup_error_nothing)
    }
    BackupNotice.Saved -> stringResource(R.string.backup_saved)
    is BackupNotice.Restored -> stringResource(R.string.backup_restored)
    is BackupNotice.HtmlImported -> pluralStringResource(R.plurals.snackbar_imported_bookmarks, notice.count, notice.count)
    BackupNotice.HtmlFailed -> stringResource(R.string.snackbar_import_failed)
}

private const val SECTION_COUNT = 5
private const val JSON_MIME = "application/json"
private const val HTML_MIME = "text/html"
private const val TEXT_MIME = "text/plain"
private const val ANY_MIME = "application/octet-stream"
private const val EXPORT_NAME = "skim-backup.json"
private const val TAG = "Backup"
