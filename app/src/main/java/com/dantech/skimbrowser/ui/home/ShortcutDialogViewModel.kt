package com.dantech.skimbrowser.ui.home

import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.data.hostOf
import com.dantech.skimbrowser.data.repo.ShortcutRepository
import com.dantech.skimbrowser.search.omniboxAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Add shortcut and Edit shortcut dialog (15) shows besides its two fields. */
data class ShortcutDialogUiState(
    /** Edit shortcut, filled in from the shortcut, rather than Add shortcut. */
    val editing: Boolean,
    /** False while the shortcut being edited is read; its fields fill in once it is. */
    val loaded: Boolean,
    /** Save is on: the dialog is loaded, nothing is being saved and the URL is a web address. */
    val canSave: Boolean,
    /** The shortcut is saved, or the one being edited no longer exists: the dialog closes. */
    val done: Boolean,
)

/**
 * The Add shortcut dialog, and Edit shortcut when [shortcutId] is set (15). [name] and [url] are the
 * dialog's two fields. Commands are called on the main thread.
 *
 * Save keeps only a web address: it is on only while the URL is one, and the URL is saved as what
 * the search field would open for it ([omniboxAddress]: "example.com" is saved as
 * "https://example.com"). Text the field would search for instead, such as `javascript:` or `file:`
 * URLs, leaves Save off. An empty Name takes the site's host. Saving runs on [appScope], so a
 * shortcut being written is not lost when the dialog goes away at that moment.
 */
class ShortcutDialogViewModel(
    private val shortcutId: Long?,
    private val shortcuts: ShortcutRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private data class Progress(
        val loaded: Boolean,
        val saving: Boolean = false,
        val done: Boolean = false,
    )

    val name = TextFieldState()

    val url = TextFieldState()

    private val progress = MutableStateFlow(Progress(loaded = shortcutId == null))

    val state: StateFlow<ShortcutDialogUiState> =
        combine(progress, snapshotFlow { url.text.toString() }, ::uiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, uiState(progress.value, url.text.toString()))

    init {
        if (shortcutId != null) viewModelScope.launch { fillIn(shortcutId) }
    }

    /**
     * Saves the shortcut; nothing happens while Save is off, the URL being no web address among
     * other reasons. Once the shortcut is written the state reports done.
     */
    @MainThread
    fun save() {
        val current = progress.value
        if (!current.loaded || current.saving || current.done) return
        val address = omniboxAddress(url.text.toString()) ?: return
        val label = name.text.trim().ifEmpty { hostOf(address) ?: address }.toString()
        progress.update { it.copy(saving = true) }
        val repository = shortcuts
        val id = shortcutId
        appScope.launch {
            try {
                if (id == null) repository.add(label, address) else repository.update(id, label, address)
                progress.update { it.copy(saving = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The shortcut was not saved: ${e.javaClass.simpleName}")
                progress.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun fillIn(id: Long) {
        val shortcut = try {
            shortcuts.observe().first().firstOrNull { it.id == id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The shortcut could not be read: ${e.javaClass.simpleName}")
            null
        }
        if (shortcut == null) {
            // Removed meanwhile (the key may come back after process death): nothing to edit.
            progress.update { it.copy(done = true) }
            return
        }
        name.setTextAndPlaceCursorAtEnd(shortcut.name)
        url.setTextAndPlaceCursorAtEnd(shortcut.url)
        progress.update { it.copy(loaded = true) }
    }

    private fun uiState(progress: Progress, urlText: String) = ShortcutDialogUiState(
        editing = shortcutId != null,
        loaded = progress.loaded,
        canSave = progress.loaded && !progress.saving && !progress.done && omniboxAddress(urlText) != null,
        done = progress.done,
    )

    private companion object {
        const val TAG = "ShortcutDialog"
    }
}
