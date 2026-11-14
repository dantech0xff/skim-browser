package com.dantech.skimbrowser.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.download.DownloadItem
import com.dantech.skimbrowser.domain.download.DownloadPhase
import com.dantech.skimbrowser.domain.download.fraction
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SkimAlertDialog
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlin.math.roundToInt

/** The download list. Open uses the system for the file's type. Retry uses the saved request. */
@Composable
fun DownloadsRoute(navigator: SkimNavigator, model: DownloadsViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DownloadsScreen(
        items = state.items,
        onBack = navigator::back,
        onOpen = { item -> openDownload(context, item) },
        onRetry = model::retry,
        onRemove = model::remove,
    )
}

@Composable
fun DownloadsScreen(
    items: List<DownloadItem>,
    onBack: () -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onRemove: (id: Long, deleteFile: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemove by rememberSaveable { mutableStateOf<Long?>(null) }
    LargeTitleScaffold(
        title = stringResource(R.string.downloads_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (items.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.downloads_empty),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEachIndexed { index, item ->
                        DownloadRow(
                            item = item,
                            index = index,
                            count = items.size,
                            onOpen = { onOpen(item) },
                            onRetry = { onRetry(item) },
                            onRemove = { pendingRemove = item.id },
                        )
                    }
                }
            }
        }
    }
    val removing = items.firstOrNull { it.id == pendingRemove }
    if (removing != null) {
        SkimAlertDialog(
            title = stringResource(R.string.downloads_remove_title),
            text = { Text(stringResource(R.string.downloads_remove_body, removing.fileName)) },
            dismiss = DialogAction(stringResource(R.string.downloads_remove_list), onClick = {
                onRemove(removing.id, false)
                pendingRemove = null
            }),
            confirm = DialogAction(stringResource(R.string.downloads_delete_file), onClick = {
                onRemove(removing.id, true)
                pendingRemove = null
            }),
            destructiveConfirm = true,
            onDismissRequest = { pendingRemove = null },
        )
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        SkimListItem(
            headline = item.fileName,
            supporting = statusText(item),
            index = index,
            count = count,
            onClick = if (item.phase == DownloadPhase.Done && item.uri != null) onOpen else null,
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            if (item.phase == DownloadPhase.Done && item.uri != null) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
            }
            if (item.phase == DownloadPhase.Failed && item.retry != null) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
        }
    }
}

@Composable
private fun statusText(item: DownloadItem): String {
    val size = sizeText(item.bytesTotal)
    return when (item.phase) {
        DownloadPhase.Running -> {
            val fraction = item.fraction()
            if (fraction == null) {
                stringResource(R.string.downloads_running, size)
            } else {
                stringResource(R.string.downloads_running_progress, (fraction * 100).roundToInt(), size)
            }
        }
        DownloadPhase.Done -> stringResource(R.string.downloads_done, size)
        DownloadPhase.Failed -> {
            val reason = item.failureReason
            if (reason.isNullOrBlank()) {
                stringResource(R.string.downloads_failed, size)
            } else {
                stringResource(R.string.downloads_failed_reason, reason)
            }
        }
    }
}

@Composable
private fun sizeText(bytes: Long): String = when {
    bytes < 0 -> stringResource(R.string.downloads_size_unknown)
    bytes < 1024 -> stringResource(R.string.downloads_size_bytes, bytes)
    bytes < 1024 * 1024 -> stringResource(R.string.downloads_size_kilobytes, bytes / 1024)
    else -> stringResource(R.string.downloads_size_megabytes, bytes / (1024 * 1024))
}

private fun openDownload(context: android.content.Context, item: DownloadItem) {
    val uri = item.uri ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(android.net.Uri.parse(uri), item.mime ?: "application/octet-stream")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app opens the downloaded file: ${e.javaClass.simpleName}")
    } catch (e: SecurityException) {
        Log.w(TAG, "The app for the downloaded file cannot be started: ${e.javaClass.simpleName}")
    }
}

private const val TAG = "SkimDownloads"
