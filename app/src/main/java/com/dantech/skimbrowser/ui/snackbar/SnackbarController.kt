package com.dantech.skimbrowser.ui.snackbar

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.text.UiText
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** What a deletion with Undo removed. Bulk deletes close the Undo snackbar of their own kind. */
enum class UndoKind { TAB, HISTORY, BOOKMARK, SHORTCUT, SKIM_CHAT }

/** A pending undo issued by [UndoRegistry.register]. */
@JvmInline
value class UndoToken(val id: Long)

/**
 * The action button of a snackbar, as data. The controller outlives every Activity, so it never
 * holds a callback: the host on screen turns the action into a call on its own navigator.
 */
sealed interface SnackbarAction {
    /** Switch: shows a tab that was opened behind the current one (05d). */
    data class SwitchToTab(val tabId: String) : SnackbarAction

    /** Undo: run by the controller through [UndoRegistry], never by the host. */
    data class Undo(val token: UndoToken) : SnackbarAction

    /** Edit: opens Edit bookmark for the bookmark just added (05b). */
    data class EditBookmark(val bookmarkId: Long) : SnackbarAction

    /** Open: opens a finished download with the type the system reported for it. */
    data class OpenDownload(val uri: Uri, val mime: String) : SnackbarAction

    /** View: opens Bookmarks at the folder an import created (03). */
    data class ViewImported(val folderId: Long) : SnackbarAction
}

/** The label of the action button. */
@StringRes
internal fun SnackbarAction.labelRes(): Int = when (this) {
    is SnackbarAction.SwitchToTab -> R.string.action_switch
    is SnackbarAction.Undo -> R.string.action_undo
    is SnackbarAction.EditBookmark -> R.string.action_edit
    is SnackbarAction.OpenDownload -> R.string.action_open
    is SnackbarAction.ViewImported -> R.string.action_view
}

/** Where the snackbar sits, above the navigation bar inset. */
enum class SnackbarPlacement(val bottomOffset: Dp) {
    /** 8 above the floating toolbar: the toolbar sits 24 above the inset and is 64 high. */
    AboveToolbar(ToolbarMetrics.BottomOffset + ToolbarMetrics.Height + 8.dp),

    /** Screens without a floating toolbar. */
    Bottom(16.dp),
}

/**
 * The one snackbar of the app. Lives as long as the process and holds only data, so a snackbar
 * survives the screen that posted it and never keeps an Activity alive.
 *
 * One at a time: [show] replaces the message on screen instead of queueing behind it. A message with
 * [SnackbarAction.Undo] that closes for any reason other than its Undo being tapped (timeout,
 * replaced, dismissed by accessibility, its host leaving the composition, [dismissFor]) finalizes
 * its token; tapping Undo runs the undo instead.
 */
class SnackbarController(private val undo: UndoRegistry) {

    data class Message(
        val id: Long,
        val text: UiText,
        val action: SnackbarAction?,
        val isError: Boolean,
    )

    private val lastId = AtomicLong()
    private val state = MutableStateFlow<Message?>(null)

    // The kind of each shown Undo, read when the message is posted, so dismissFor still matches
    // after the registry has already finalized the token.
    private val undoKinds = ConcurrentHashMap<Long, UndoKind>()

    /** The message on screen, or null. */
    val current: StateFlow<Message?> = state.asStateFlow()

    /** Shows [text], replacing (and closing) the message on screen. Error messages are announced at once. */
    fun show(text: UiText, action: SnackbarAction? = null, isError: Boolean = false) {
        val message = Message(lastId.incrementAndGet(), text, action, isError)
        if (action is SnackbarAction.Undo) {
            undo.kindOf(action.token)?.let { undoKinds[message.id] = it }
        }
        state.getAndUpdate { message }?.let(::close)
    }

    /** Called by the host after its action was tapped and, unless it is Undo, carried out. */
    fun onActionClicked(id: Long) {
        val message = take { it.id == id } ?: return
        undoKinds.remove(message.id)
        (message.action as? SnackbarAction.Undo)?.let { undo.undo(it.token) }
    }

    /** Called by the host when message [id] closes without its action. Stale ids are ignored. */
    fun onDismissed(id: Long) {
        take { it.id == id }?.let(::close)
    }

    /** Closes the Undo snackbar of [kind], if one is on screen. Used before a bulk delete of that kind. */
    fun dismissFor(kind: UndoKind) {
        take { undoKinds[it.id] == kind }?.let(::close)
    }

    /** Clears the current message when it matches [predicate] and returns it; null when it didn't. */
    private fun take(predicate: (Message) -> Boolean): Message? {
        while (true) {
            val message = state.value ?: return null
            if (!predicate(message)) return null
            if (state.compareAndSet(message, null)) return message
        }
    }

    private fun close(message: Message) {
        undoKinds.remove(message.id)
        (message.action as? SnackbarAction.Undo)?.let { undo.finalize(it.token) }
    }
}
