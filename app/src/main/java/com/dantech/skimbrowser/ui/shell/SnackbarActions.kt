package com.dantech.skimbrowser.ui.shell

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.annotation.MainThread
import com.dantech.skimbrowser.browser.ActivityHost
import com.dantech.skimbrowser.ui.navigation.EditBookmarkKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry

/**
 * Carries out the action of a snackbar on the window that shows it. The snackbar controller lives
 * as long as the process and holds actions only as data, so the host turns each one into a call on
 * this window's navigator and Activity.
 *
 * Undo normally runs through the controller when it is tapped; here it goes to [undo] too, which
 * runs each token at most once.
 */
@MainThread
fun interpret(action: SnackbarAction, navigator: SkimNavigator, undo: UndoRegistry, activityHost: ActivityHost) {
    when (action) {
        is SnackbarAction.SwitchToTab -> navigator.showTab(action.tabId)
        is SnackbarAction.Undo -> undo.undo(action.token)
        is SnackbarAction.EditBookmark -> navigator.openDialog(EditBookmarkKey(action.bookmarkId))
        is SnackbarAction.OpenDownload -> openDownload(action, activityHost)
        is SnackbarAction.ViewImported -> navigator.openBookmarks(action.folderId)
    }
}

/** Opens a finished download in the app the system picks for its type, with read access to it. */
private fun openDownload(action: SnackbarAction.OpenDownload, activityHost: ActivityHost) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(action.uri, action.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        activityHost.startExternal(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app opens the downloaded file: ${e.javaClass.simpleName}")
    } catch (e: SecurityException) {
        Log.w(TAG, "The app for the downloaded file cannot be started: ${e.javaClass.simpleName}")
    }
}

private const val TAG = "SkimSnackbarActions"
