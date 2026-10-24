package com.dantech.skimbrowser.ui.browsing

import com.dantech.skimbrowser.ui.navigation.rememberShell
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.assisted.Assisted
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.domain.tab.HitTarget
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.system.Clipboard
import com.dantech.skimbrowser.system.ShareSheet
import com.dantech.skimbrowser.ui.components.IconTile
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.toDrawnShape
import com.dantech.skimbrowser.ui.navigation.LinkOptionsKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of Link options or Image options (05d). */
enum class LinkRow {
    OpenNewTab,
    OpenIncognito,
    CopyLink,
    ShareLink,
    OpenImage,
    DownloadImage,
    ShareImage,
}

/**
 * What Link options or Image options shows. [rows] leaves out Open in incognito tab when the page
 * is already incognito: Open in new tab then opens an incognito tab.
 */
@Immutable
data class LinkOptionsUiState(
    val tabMissing: Boolean = false,
    val incognito: Boolean = false,
    val image: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val rows: ImmutableList<LinkRow> = persistentListOf(),
)

/** The address a row opens, downloads or shares. */
internal fun linkTargetUrl(target: HitTarget): String = when (target) {
    is HitTarget.Link -> target.url
    is HitTarget.Image -> target.src
}

/** The rows of [target] on a tab that is [incognito]. */
internal fun linkRows(target: HitTarget, incognito: Boolean): ImmutableList<LinkRow> = when (target) {
    is HitTarget.Image -> persistentListOf(LinkRow.OpenImage, LinkRow.DownloadImage, LinkRow.ShareImage)
    is HitTarget.Link -> buildList {
        add(LinkRow.OpenNewTab)
        if (!incognito) add(LinkRow.OpenIncognito)
        add(LinkRow.CopyLink)
        add(LinkRow.ShareLink)
    }.toImmutableList()
}

/**
 * Whether [row] opens an incognito tab. Open in new tab follows the page: an incognito page's new
 * tab is incognito, and then the separate incognito row is not shown.
 */
internal fun opensIncognito(tabIncognito: Boolean, row: LinkRow): Boolean = when (row) {
    LinkRow.OpenIncognito -> true
    LinkRow.OpenNewTab, LinkRow.OpenImage -> tabIncognito
    else -> false
}

/** The header of [target]: its text or alt, or the site, over the address without its scheme. */
internal fun linkHeader(target: HitTarget): Pair<String, String> {
    val url = linkTargetUrl(target)
    val named = when (target) {
        is HitTarget.Link -> target.text
        is HitTarget.Image -> target.alt
    }?.trim()?.takeIf { it.isNotEmpty() }
    val site = hostOf(url) ?: errorAddressOf(url)
    return (named ?: site) to errorAddressOf(url)
}

/**
 * Link options or Image options for [target] on [tabId]. The header follows the tab only to know
 * whether it is still open and whether it is incognito.
 */
@HiltViewModel(assistedFactory = LinkOptionsViewModel.Factory::class)
class LinkOptionsViewModel @AssistedInject constructor(
    @Assisted private val tabId: String,
    @Assisted private val target: HitTarget,
    tabs: TabsController,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tabId: String, target: HitTarget): LinkOptionsViewModel
    }

    private val header = linkHeader(target)

    val state: StateFlow<LinkOptionsUiState> = tabs.state
        .map { it.tab(tabId) }
        .distinctUntilChanged()
        .map { tab ->
            if (tab == null) {
                LinkOptionsUiState(tabMissing = true)
            } else {
                LinkOptionsUiState(
                    incognito = tab.incognito,
                    image = target is HitTarget.Image,
                    title = header.first,
                    subtitle = header.second,
                    rows = linkRows(target, tab.incognito),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial(tabs))

    private fun initial(tabs: TabsController): LinkOptionsUiState {
        val tab = tabs.state.value.tab(tabId) ?: return LinkOptionsUiState(tabMissing = true)
        return LinkOptionsUiState(
            incognito = tab.incognito,
            image = target is HitTarget.Image,
            title = header.first,
            subtitle = header.second,
            rows = linkRows(target, tab.incognito),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The [LinkOptionsKey] destination. */
@Composable
fun LinkOptionsRoute(key: LinkOptionsKey, navigator: SkimNavigator) {
    val shell = rememberShell()
    val options = hiltViewModel<LinkOptionsViewModel, LinkOptionsViewModel.Factory>(
        key = "link-options:${key.tabId}:${key.target}",
    ) { factory ->
        factory.create(key.tabId, key.target)
    }
    val state by options.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val host = LocalBrowserHost.current
    LaunchedEffect(state.tabMissing) { if (state.tabMissing) navigator.back() }
    LinkOptionsSheet(state, onRow = { row ->
        val tab = shell.tabs().state.value.tab(key.tabId) ?: return@LinkOptionsSheet
        val url = linkTargetUrl(key.target)
        when (row) {
            LinkRow.OpenNewTab, LinkRow.OpenIncognito, LinkRow.OpenImage -> {
                val id = navigator.openInBackground(url, opensIncognito(tab.incognito, row))
                navigator.back()
                if (id != null) {
                    shell.snackbar().show(
                        UiText.Res(R.string.snackbar_opened_in_new_tab),
                        SnackbarAction.SwitchToTab(id),
                    )
                }
            }
            LinkRow.CopyLink -> {
                val confirm = Clipboard.copy(context, url, sensitive = tab.incognito)
                navigator.back()
                if (confirm) shell.snackbar().show(UiText.Res(R.string.snackbar_copied))
            }
            LinkRow.ShareLink -> {
                navigator.back()
                ShareSheet.shareText(context, url)
            }
            LinkRow.DownloadImage -> {
                val view = host.pool.obtain(tab)
                val agent = runCatching { view.settings.userAgentString }.getOrNull()
                host.downloads.start(
                    tabId = tab.id,
                    url = url,
                    userAgent = agent,
                    contentDisposition = null,
                    mimeType = null,
                    webView = view,
                    userInitiated = true,
                )
                navigator.back()
            }
            LinkRow.ShareImage -> {
                val view = host.pool.obtain(tab)
                val blob = host.blob
                val share = shell.shareImage()
                val bar = shell.snackbar()
                navigator.back()
                shell.appScope().launch {
                    val shared = share.prepare(context, tab, url, blob, view)
                    withContext(Dispatchers.Main.immediate) {
                        if (shared == null) {
                            bar.show(UiText.Res(R.string.snackbar_share_failed), isError = true)
                        } else {
                            ShareSheet.shareImage(context, shared.uri, shared.mime)
                        }
                    }
                }
            }
        }
    })
}

/**
 * Link options or Image options (05d). The header is a link tile or an image tile; every row
 * closes the sheet.
 */
@Composable
fun LinkOptionsSheet(state: LinkOptionsUiState, onRow: (LinkRow) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LinkHeader(state)
        SegmentedColumn {
            state.rows.forEachIndexed { index, row ->
                val spec = rowSpec(row)
                SkimListItem(
                    headline = stringResource(spec.label),
                    index = index,
                    count = state.rows.size,
                    onClick = { onRow(row) },
                    leading = { MenuGlyph(spec.icon) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinkHeader(state: LinkOptionsUiState) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.image) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_image),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        } else {
            IconTile(
                icon = R.drawable.ic_link,
                container = colors.surfaceContainerHighest,
                content = colors.onSurfaceVariant,
                shape = MaterialShapes.Cookie4Sided.toDrawnShape(),
                iconSize = 20.dp,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                state.subtitle,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuGlyph(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

private data class RowSpec(@param:DrawableRes val icon: Int, @param:StringRes val label: Int)

private fun rowSpec(row: LinkRow): RowSpec = when (row) {
    LinkRow.OpenNewTab -> RowSpec(R.drawable.ic_tab, R.string.link_open_in_new_tab)
    LinkRow.OpenIncognito -> RowSpec(R.drawable.ic_visibility_off, R.string.link_open_in_incognito_tab)
    LinkRow.CopyLink -> RowSpec(R.drawable.ic_content_copy, R.string.link_copy)
    LinkRow.ShareLink -> RowSpec(R.drawable.ic_share, R.string.link_share)
    LinkRow.OpenImage -> RowSpec(R.drawable.ic_tab, R.string.image_open_in_new_tab)
    LinkRow.DownloadImage -> RowSpec(R.drawable.ic_download, R.string.image_download)
    LinkRow.ShareImage -> RowSpec(R.drawable.ic_share, R.string.image_share)
}
