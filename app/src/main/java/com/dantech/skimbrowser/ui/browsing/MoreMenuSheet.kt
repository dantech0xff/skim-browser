package com.dantech.skimbrowser.ui.browsing

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.assisted.Assisted
import com.dantech.skimbrowser.di.AppScope
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.bookmark.Bookmark
import com.dantech.skimbrowser.domain.bookmark.BookmarkRepository
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.system.ShareSheet
import com.dantech.skimbrowser.ui.components.FaviconLetterTile
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonSize
import com.dantech.skimbrowser.ui.components.SkimButtonStyle
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.siteLetter
import com.dantech.skimbrowser.ui.navigation.BookmarksKey
import com.dantech.skimbrowser.ui.navigation.EditBookmarkKey
import com.dantech.skimbrowser.ui.navigation.HistoryKey
import com.dantech.skimbrowser.ui.navigation.MoreMenuKey
import com.dantech.skimbrowser.ui.navigation.SettingsKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.text.UiText
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the More menu (05b) shows. On the New tab page [page] is false and the sheet is only the
 * three groups; a page also shows its header and page actions.
 */
@Immutable
data class MoreMenuUiState(
    val tabMissing: Boolean = false,
    /** The tab is showing a page, so the header and page actions are drawn. */
    val page: Boolean = false,
    val title: String = "",
    val host: String = "",
    val url: String? = null,
    val letter: String = "",
    /** The bookmark of this page, once [bookmarkKnown]; null when the page is not bookmarked. */
    val bookmarkedId: Long? = null,
    /** False until the bookmark of the current page has been read, so the star cannot add a second one. */
    val bookmarkKnown: Boolean = false,
    val desktopSite: Boolean = false,
    val incognito: Boolean = false,
) {
    companion object {
        val Missing = MoreMenuUiState(tabMissing = true, bookmarkKnown = true)
    }
}

/**
 * The More menu of [tabId]. The star follows [BookmarkRepository.observeByUrl]; adding a bookmark
 * writes it on [appScope], so it is kept when the sheet closes at that moment. Commands are called
 * on the main thread.
 */
@HiltViewModel(assistedFactory = MoreMenuViewModel.Factory::class)
class MoreMenuViewModel @AssistedInject constructor(
    @Assisted private val tabId: String,
    private val tabs: TabsController,
    private val bookmarks: BookmarkRepository,
    private val snackbar: SnackbarController,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tabId: String): MoreMenuViewModel
    }

    private val adding = AtomicBoolean(false)
    private val closedRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The sheet closes after a bookmark is added; the snackbar's Edit opens the dialog afterwards. */
    val closed: Flow<Unit> = closedRequests

    private val tab = tabs.state.map { it.tab(tabId) }.distinctUntilChanged()

    private val bookmark = tab
        .map { it?.url?.takeIf(String::isNotBlank) }
        .distinctUntilChanged()
        .flatMapLatest { url ->
            if (url == null) {
                flowOf(null to true)
            } else {
                bookmarks.observeByUrl(url).map<Bookmark?, Pair<Bookmark?, Boolean>> { it to true }
                    .onStart { emit(null to false) }
            }
        }

    val state: StateFlow<MoreMenuUiState> = combine(tab, bookmark) { open, (mark, known) ->
        if (open == null) MoreMenuUiState.Missing else menuState(open, mark, known)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial())

    /** Turns Desktop site on or off for this tab and reloads it. */
    fun toggleDesktop() {
        val current = state.value
        if (current.tabMissing || !current.page) return
        tabs.setDesktopSite(tabId, !current.desktopSite)
    }

    /** Opens Find in page. The sheet closes itself; the browsing screen shows the bar. */
    fun findInPage() {
        if (state.value.page) tabs.find(tabId, "")
    }

    /**
     * Adds the page to Mobile bookmarks and asks the sheet to close. Nothing happens while the
     * bookmark is still being read, while the page is already bookmarked, or while an add is in
     * flight. The snackbar offers Edit for the new bookmark.
     */
    fun addBookmark() {
        val current = state.value
        val url = current.url
        if (url == null || !current.bookmarkKnown || current.bookmarkedId != null) return
        if (!adding.compareAndSet(false, true)) return
        val title = current.title.ifBlank { current.host }.ifBlank { url }
        val store = bookmarks
        val bar = snackbar
        appScope.launch {
            try {
                val id = store.addToMobile(title, url)
                bar.show(UiText.Res(R.string.snackbar_bookmark_added), SnackbarAction.EditBookmark(id))
                closedRequests.emit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The bookmark was not added: ${e.javaClass.simpleName}")
            } finally {
                adding.set(false)
            }
        }
    }

    private fun initial(): MoreMenuUiState {
        val open = tabs.state.value.tab(tabId) ?: return MoreMenuUiState.Missing
        return menuState(open, bookmark = null, known = open.url.isNullOrBlank())
    }

    private companion object {
        const val TAG = "MoreMenu"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun menuState(tab: Tab, bookmark: Bookmark?, known: Boolean): MoreMenuUiState {
    val url = tab.url?.takeIf { it.isNotBlank() }
    val host = if (url == null) "" else pageAddressOf(url)
    return MoreMenuUiState(
        page = url != null,
        title = tab.title.ifBlank { host },
        host = host,
        url = url,
        letter = siteLetter(url?.let(::hostOf), tab.title),
        bookmarkedId = bookmark?.id,
        bookmarkKnown = known,
        desktopSite = tab.desktopSite,
        incognito = tab.incognito,
    )
}

/** The [MoreMenuKey] destination. Its view model goes away with the sheet. */
@Composable
fun MoreMenuRoute(key: MoreMenuKey, navigator: SkimNavigator) {
    val menu = hiltViewModel<MoreMenuViewModel, MoreMenuViewModel.Factory> { factory ->
        factory.create(key.tabId)
    }
    val state by menu.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(menu) { menu.closed.collect { navigator.back() } }
    LaunchedEffect(state.tabMissing) { if (state.tabMissing) navigator.back() }
    MoreMenuSheet(
        state = state,
        onStar = {
            val id = state.bookmarkedId
            if (id != null) navigator.replaceTop(EditBookmarkKey(id)) else menu.addBookmark()
        },
        onShare = {
            val url = state.url ?: return@MoreMenuSheet
            navigator.back()
            ShareSheet.sharePage(context, state.title, url)
        },
        onFind = {
            menu.findInPage()
            navigator.back()
        },
        onDesktop = menu::toggleDesktop,
        onNewTab = { navigator.openNewTab(incognito = false) },
        onNewIncognito = { navigator.openNewTab(incognito = true) },
        onBookmarks = { navigator.replaceTop(BookmarksKey()) },
        onHistory = { navigator.replaceTop(HistoryKey) },
        onSettings = { navigator.replaceTop(SettingsKey) },
    )
}

/**
 * More options (05b): the page's header and page actions when [state] is a page, then New tab,
 * Bookmarks and History, and Settings. The New tab page leaves the header and page actions out.
 */
@Composable
fun MoreMenuSheet(
    state: MoreMenuUiState,
    onStar: () -> Unit,
    onShare: () -> Unit,
    onFind: () -> Unit,
    onDesktop: () -> Unit,
    onNewTab: () -> Unit,
    onNewIncognito: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (state.page) {
            PageHeader(state, onStar, onShare)
            PageActions(state.desktopSite, onFind, onDesktop)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuGroup(
                MenuEntry(R.drawable.ic_tab, stringResource(R.string.new_tab), onNewTab),
                MenuEntry(R.drawable.ic_visibility_off, stringResource(R.string.new_incognito_tab), onNewIncognito),
            )
            MenuGroup(
                MenuEntry(R.drawable.ic_bookmarks, stringResource(R.string.bookmarks_title), onBookmarks),
                MenuEntry(R.drawable.ic_history, stringResource(R.string.history_title), onHistory),
            )
            MenuGroup(MenuEntry(R.drawable.ic_settings, stringResource(R.string.settings_title), onSettings))
        }
    }
}

@Composable
private fun PageHeader(state: MoreMenuUiState, onStar: () -> Unit, onShare: () -> Unit) {
    val bookmarked = state.bookmarkedId != null
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaviconLetterTile(state.letter)
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                state.host,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onStar, enabled = state.bookmarkKnown) {
            Icon(
                painterResource(if (bookmarked) R.drawable.ic_star_filled else R.drawable.ic_star),
                contentDescription = stringResource(if (bookmarked) R.string.edit_bookmark_title else R.string.cd_add_bookmark),
                tint = if (bookmarked) colors.primary else colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onShare, enabled = state.url != null) {
            Icon(
                painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.cd_share_page),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PageActions(desktop: Boolean, onFind: () -> Unit, onDesktop: () -> Unit) {
    val pageActions = stringResource(R.string.cd_page_actions)
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .semantics { contentDescription = pageActions },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkimButton(
            text = stringResource(R.string.find_in_page),
            onClick = onFind,
            modifier = Modifier.weight(1f),
            size = SkimButtonSize.M,
            style = SkimButtonStyle.Tonal,
            icon = R.drawable.ic_find_in_page,
        )
        SkimButton(
            text = stringResource(R.string.menu_desktop_site),
            onClick = onDesktop,
            modifier = Modifier.weight(1f),
            size = SkimButtonSize.M,
            style = if (desktop) SkimButtonStyle.Filled else SkimButtonStyle.Tonal,
            icon = if (desktop) R.drawable.ic_check else R.drawable.ic_desktop_windows,
        )
    }
}

private data class MenuEntry(@param:DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

@Composable
private fun MenuGroup(vararg entries: MenuEntry) {
    SegmentedColumn {
        entries.forEachIndexed { index, entry ->
            SkimListItem(
                headline = entry.label,
                index = index,
                count = entries.size,
                onClick = entry.onClick,
                leading = { MenuIcon(entry.icon) },
            )
        }
    }
}

@Composable
private fun MenuIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
