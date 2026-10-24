package com.dantech.skimbrowser.ui.bookmarks

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.assisted.Assisted
import com.dantech.skimbrowser.di.AppScope
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.bookmark.BookmarkFolder
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.bookmark.FolderKind
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.search.omniboxAddress
import com.dantech.skimbrowser.ui.components.ChoiceDialogContent
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.SkimDialogContent
import com.dantech.skimbrowser.ui.components.SkimTextField
import com.dantech.skimbrowser.ui.navigation.EditBookmarkKey
import com.dantech.skimbrowser.ui.navigation.MoveToFolderKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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

/**
 * The folder a bookmark is in, named the way the screens say it: Mobile bookmarks and Imported are
 * the app's own names, and any other folder uses the name it was given.
 */
sealed interface FolderLabel {
    data object Mobile : FolderLabel
    data object Imported : FolderLabel
    data class Named(val name: String) : FolderLabel
}

/** One top-level folder in Move to folder. */
@Immutable
data class MoveFolderOption(val id: Long, val label: FolderLabel)

/** What Edit bookmark shows besides its fields. */
@Immutable
data class EditBookmarkUiState(
    val loaded: Boolean = false,
    val canSave: Boolean = false,
    val done: Boolean = false,
    val folder: FolderLabel? = null,
)

/** What Move to folder shows. */
@Immutable
data class MoveToFolderUiState(
    val loaded: Boolean = false,
    val options: ImmutableList<MoveFolderOption> = persistentListOf(),
    val selectedId: Long? = null,
    val done: Boolean = false,
)

internal fun folderLabel(folder: BookmarkFolder): FolderLabel = when (folder.kind) {
    FolderKind.MOBILE -> FolderLabel.Mobile
    FolderKind.IMPORTED -> FolderLabel.Imported
    FolderKind.FOLDER -> FolderLabel.Named(folder.name.orEmpty())
}

/**
 * Edit bookmark (15). [name] and [url] fill in once from the bookmark and are not overwritten when
 * its folder changes. Save writes the name and URL only; the folder changes in Move to folder.
 * The write runs on [appScope], so it is kept when the dialog closes as it is saved.
 */
@HiltViewModel(assistedFactory = EditBookmarkViewModel.Factory::class)
class EditBookmarkViewModel @AssistedInject constructor(
    @Assisted private val bookmarkId: Long,
    private val bookmarks: BookmarkRepository,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(bookmarkId: Long): EditBookmarkViewModel
    }

    val name = TextFieldState()
    val url = TextFieldState()

    private val progress = MutableStateFlow(Progress())
    private val folder = MutableStateFlow<FolderLabel?>(null)
    private var filled = false

    val state: StateFlow<EditBookmarkUiState> =
        combine(progress, snapshotFlow { url.text.toString() }, folder, ::uiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, uiState(progress.value, url.text.toString(), folder.value))

    init {
        viewModelScope.launch { watch() }
    }

    /** Saves the name and URL. Nothing happens while Save is off. */
    fun save() {
        val current = progress.value
        if (!current.loaded || current.saving || current.done) return
        val address = omniboxAddress(url.text.toString()) ?: return
        val label = name.text.trim().ifEmpty { hostOf(address) ?: address }.toString()
        progress.update { it.copy(saving = true) }
        val store = bookmarks
        val id = bookmarkId
        appScope.launch {
            try {
                store.update(id, label, address)
                progress.update { it.copy(saving = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The bookmark was not saved: ${e.javaClass.simpleName}")
                progress.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun watch() {
        combine(bookmarks.observe(bookmarkId), bookmarks.observeFolders()) { bookmark, folders ->
            bookmark to folders
        }.collect { (bookmark, folders) ->
            if (bookmark == null) {
                progress.update { it.copy(done = true) }
                return@collect
            }
            if (!filled) {
                name.setTextAndPlaceCursorAtEnd(bookmark.title)
                url.setTextAndPlaceCursorAtEnd(bookmark.url)
                filled = true
                progress.update { it.copy(loaded = true) }
            }
            folder.value = folders.firstOrNull { it.id == bookmark.folderId }?.let(::folderLabel)
        }
    }

    private fun uiState(progress: Progress, urlText: String, folder: FolderLabel?) = EditBookmarkUiState(
        loaded = progress.loaded,
        canSave = progress.loaded && !progress.saving && !progress.done && omniboxAddress(urlText) != null,
        done = progress.done,
        folder = folder,
    )

    private data class Progress(val loaded: Boolean = false, val saving: Boolean = false, val done: Boolean = false)

    private companion object {
        const val TAG = "EditBookmark"
    }
}

/**
 * Move to folder (15). Choosing a folder moves the bookmark there at once, including the folder it
 * is already in, and closes the dialog. The move runs on [appScope].
 */
@HiltViewModel(assistedFactory = MoveToFolderViewModel.Factory::class)
class MoveToFolderViewModel @AssistedInject constructor(
    @Assisted private val bookmarkId: Long,
    private val bookmarks: BookmarkRepository,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(bookmarkId: Long): MoveToFolderViewModel
    }

    private val options = MutableStateFlow<ImmutableList<MoveFolderOption>>(persistentListOf())
    private val selectedId = MutableStateFlow<Long?>(null)
    private val loaded = MutableStateFlow(false)
    private val done = MutableStateFlow(false)
    private var moving = false

    val state: StateFlow<MoveToFolderUiState> =
        combine(options, selectedId, loaded, done) { choices, selected, ready, finished ->
            MoveToFolderUiState(ready, choices, selected, finished)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MoveToFolderUiState())

    init {
        viewModelScope.launch { load() }
    }

    /** Moves the bookmark to [folderId] and closes the dialog. */
    fun moveTo(folderId: Long) {
        if (moving || done.value) return
        moving = true
        val store = bookmarks
        val id = bookmarkId
        appScope.launch {
            try {
                store.move(id, folderId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The bookmark was not moved: ${e.javaClass.simpleName}")
            }
        }
        done.value = true
    }

    private suspend fun load() {
        val current = bookmarks.observe(bookmarkId).first()
        if (current == null) {
            done.value = true
            return
        }
        selectedId.value = current.folderId
        options.value = bookmarks.topLevelFolders().map { MoveFolderOption(it.id, folderLabel(it)) }.toImmutableList()
        loaded.value = true
    }

    private companion object {
        const val TAG = "MoveToFolder"
    }
}

/** The [EditBookmarkKey] destination. */
@Composable
fun EditBookmarkRoute(key: EditBookmarkKey, navigator: SkimNavigator) {
    val dialog = hiltViewModel<EditBookmarkViewModel, EditBookmarkViewModel.Factory> { factory ->
        factory.create(key.bookmarkId)
    }
    EditBookmarkDialog(
        dialog = dialog,
        onClose = { navigator.remove(key) },
        onMove = { navigator.openDialog(MoveToFolderKey(key.bookmarkId)) },
    )
}

/** The [MoveToFolderKey] destination. Edit bookmark stays under it. */
@Composable
fun MoveToFolderRoute(key: MoveToFolderKey, navigator: SkimNavigator) {
    val dialog = hiltViewModel<MoveToFolderViewModel, MoveToFolderViewModel.Factory> { factory ->
        factory.create(key.bookmarkId)
    }
    MoveToFolderDialog(dialog, onClose = { navigator.remove(key) })
}

/**
 * Edit bookmark (15): Name, URL and a Folder field that opens Move to folder. Save writes the name
 * and URL. [onClose] runs on Cancel and once the bookmark is saved or gone.
 */
@Composable
fun EditBookmarkDialog(dialog: EditBookmarkViewModel, onClose: () -> Unit, onMove: () -> Unit) {
    val state by dialog.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    if (!state.loaded && !state.done) return
    SkimDialogContent(
        title = stringResource(R.string.edit_bookmark_title),
        confirm = DialogAction(stringResource(R.string.action_save), onClick = dialog::save, enabled = state.canSave),
        dismiss = DialogAction(stringResource(R.string.action_cancel), onClick = onClose),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SkimTextField(
                    state = dialog.name,
                    label = stringResource(R.string.field_name),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                SkimTextField(
                    state = dialog.url,
                    label = stringResource(R.string.field_url),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { dialog.save() },
                )
                state.folder?.let { folder ->
                    FolderField(value = folderLabelText(folder), onClick = onMove)
                }
            }
        },
    )
}

/** Move to folder (15): a radio for each top-level folder. Choosing one moves the bookmark and closes. */
@Composable
fun MoveToFolderDialog(dialog: MoveToFolderViewModel, onClose: () -> Unit) {
    val state by dialog.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    if (!state.loaded && !state.done) return
    val selected = state.options.firstOrNull { it.id == state.selectedId }
    ChoiceDialogContent(
        title = stringResource(R.string.move_to_folder),
        options = state.options,
        selected = selected,
        label = { folderLabelText(it.label) },
        onSelect = { dialog.moveTo(it.id) },
        onDismiss = onClose,
    )
}

@Composable
internal fun folderLabelText(label: FolderLabel): String = when (label) {
    FolderLabel.Mobile -> stringResource(R.string.bookmarks_folder_mobile)
    FolderLabel.Imported -> stringResource(R.string.bookmarks_folder_imported)
    is FolderLabel.Named -> label.name
}

/**
 * The Folder field of Edit bookmark: an outlined box like the text fields, read only, opening
 * Move to folder. TalkBack reads "Folder, …".
 */
@Composable
private fun FolderField(value: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.cd_folder_field, value)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // The label sits in the border gap, above the 56 dp box, so the box itself is not clipped.
        Box(Modifier.matchParentSize().border(1.dp, colors.outline, RoundedCornerShape(4.dp)))
        Text(
            text = stringResource(R.string.field_folder),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp)
                .offset(y = (-8).dp)
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 4.dp)
                .clearAndSetSemantics {},
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
