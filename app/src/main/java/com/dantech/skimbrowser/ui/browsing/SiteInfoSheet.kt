package com.dantech.skimbrowser.ui.browsing

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.assisted.Assisted
import com.dantech.skimbrowser.di.AppScope
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.browsing.SiteDataCleaner
import com.dantech.skimbrowser.domain.tab.IncognitoDecisions
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.permission.PermissionState
import com.dantech.skimbrowser.domain.permission.ResolveSkimAiConsent
import com.dantech.skimbrowser.domain.permission.SitePermission
import com.dantech.skimbrowser.domain.permission.SitePermissionKind
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiAudience
import com.dantech.skimbrowser.domain.permission.SkimAiConsent
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.usecase.SetSitePermission
import com.dantech.skimbrowser.domain.usecase.SetSkimAi
import com.dantech.skimbrowser.ui.components.FaviconLetterTile
import com.dantech.skimbrowser.ui.components.IconTile
import com.dantech.skimbrowser.ui.components.SiteIndicator
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.SkimSwitch
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.components.siteLetter
import com.dantech.skimbrowser.ui.components.toggleableRow
import com.dantech.skimbrowser.ui.navigation.SiteInfoKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A browser permission the site has already been answered for. */
@Immutable
data class SitePermissionRow(val kind: SitePermissionKind, val allowed: Boolean)

/**
 * The site information sheet. [showSkimAi] is false in incognito: Skim AI does not read those
 * pages. Permission rows are only the kinds this site has an answer for.
 */
@Immutable
data class SiteInfoUiState(
    val tabMissing: Boolean = false,
    /** The host the rows act on; empty when the page has no site. */
    val siteKey: String = "",
    /** The host shown in the header. */
    val title: String = "",
    val letter: String = "",
    val secure: Boolean = false,
    val incognito: Boolean = false,
    val showSkimAi: Boolean = false,
    val skimAiOn: Boolean = false,
    val permissions: ImmutableList<SitePermissionRow> = persistentListOf(),
    val showCookies: Boolean = false,
) {
    companion object {
        val Missing = SiteInfoUiState(tabMissing = true)
    }
}

/**
 * Site information for [tabId]. A normal tab reads its decisions from [permissions] and whether
 * Skim AI asks first from [settings]. An incognito tab reads the session's answers; those are not
 * a flow, so a change bumps [revision] and the sheet is drawn again. Commands run on the main thread.
 */
@HiltViewModel(assistedFactory = SiteInfoViewModel.Factory::class)
class SiteInfoViewModel @AssistedInject constructor(
    @Assisted private val tabId: String,
    private val tabs: TabsController,
    private val permissions: SitePermissionRepository,
    private val settings: SettingsRepository,
    private val saveSkimAi: SetSkimAi,
    private val savePermission: SetSitePermission,
    private val incognito: IncognitoDecisions,
    private val cleaner: SiteDataCleaner,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tabId: String): SiteInfoViewModel
    }

    private val revision = MutableStateFlow(0)
    private val skimAiConsent = ResolveSkimAiConsent()

    private val tab = tabs.state.map { it.tab(tabId) }.distinctUntilChanged()

    val state: StateFlow<SiteInfoUiState> = tab.flatMapLatest { open ->
        if (open == null) {
            flowOf(SiteInfoUiState.Missing)
        } else if (open.incognito) {
            combine(tabs.runtime(tabId), revision) { page, _ -> incognitoState(open, page) }
        } else {
            val host = open.url?.let(::hostOf).orEmpty()
            val stored = if (host.isEmpty()) flowOf(null) else permissions.observe(host)
            combine(tabs.runtime(tabId), stored, settings.settings) { page, entity, prefs ->
                normalState(open, page, entity, prefs.askBeforeSending)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial())

    /** Skim AI may read this site, or must ask again. Incognito has no Skim AI row. */
    fun setSkimAi(on: Boolean) {
        val host = state.value.siteKey
        if (host.isEmpty() || state.value.incognito) return
        viewModelScope.launch {
            saveSkimAi(host, if (on) SkimAiState.ALLOWED else SkimAiState.ASK)
        }
    }

    /** Allows or blocks [kind] for this site. An incognito answer lasts until the session ends. */
    fun setPermission(kind: SitePermissionKind, allowed: Boolean) {
        val host = state.value.siteKey
        if (host.isEmpty()) return
        val next = if (allowed) PermissionState.ALLOWED else PermissionState.BLOCKED
        if (state.value.incognito) {
            incognito.setDecision(host, kind, next)
            revision.update { it + 1 }
        } else {
            viewModelScope.launch { savePermission(host, kind, next) }
        }
    }

    /** Forgets this site's cookies and web storage. The sheet stays open. */
    fun clearCookies() {
        val current = state.value
        if (current.siteKey.isEmpty()) return
        val host = current.siteKey
        val incognitoTab = current.incognito
        appScope.launch { cleaner.clearSite(host, incognitoTab) }
    }

    private fun initial(): SiteInfoUiState {
        val open = tabs.state.value.tab(tabId) ?: return SiteInfoUiState.Missing
        val page = tabs.runtime(tabId).value
        return if (open.incognito) incognitoState(open, page) else normalState(open, page, entity = null, askBeforeSending = true)
    }

    private fun incognitoState(tab: Tab, page: TabRuntime): SiteInfoUiState {
        val host = tab.url?.let(::hostOf).orEmpty()
        return sheetState(
            tab = tab,
            page = page,
            host = host,
            showSkimAi = false,
            skimAiOn = false,
            permissions = permissionRows(host) { incognito.decision(host, it) },
        )
    }

    private fun normalState(tab: Tab, page: TabRuntime, entity: SitePermission?, askBeforeSending: Boolean): SiteInfoUiState {
        val host = tab.url?.let(::hostOf).orEmpty()
        val skim = entity?.skimAi ?: SkimAiState.UNSET
        return sheetState(
            tab = tab,
            page = page,
            host = host,
            showSkimAi = host.isNotEmpty(),
            skimAiOn = skimAiConsent(skim, askBeforeSending, SkimAiAudience.PAGE) == SkimAiConsent.Allowed,
            permissions = permissionRows(host) { entity?.permission(it) ?: PermissionState.UNSET },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun sheetState(
    tab: Tab,
    page: TabRuntime,
    host: String,
    showSkimAi: Boolean,
    skimAiOn: Boolean,
    permissions: ImmutableList<SitePermissionRow>,
): SiteInfoUiState = SiteInfoUiState(
    siteKey = host,
    title = if (host.isNotEmpty()) host else pageAddressOf(tab.url),
    letter = siteLetter(host.ifEmpty { null }, tab.title),
    secure = siteIndicatorOf(tab.url, page.loadError) == SiteIndicator.Secure,
    incognito = tab.incognito,
    showSkimAi = showSkimAi,
    skimAiOn = skimAiOn,
    permissions = permissions,
    showCookies = host.isNotEmpty(),
)

private fun permissionRows(host: String, stateOf: (SitePermissionKind) -> PermissionState): ImmutableList<SitePermissionRow> {
    if (host.isEmpty()) return persistentListOf()
    return SitePermissionKind.entries.mapNotNull { kind ->
        when (val state = stateOf(kind)) {
            PermissionState.UNSET -> null
            PermissionState.ALLOWED -> SitePermissionRow(kind, allowed = true)
            PermissionState.BLOCKED -> SitePermissionRow(kind, allowed = false)
        }
    }.toImmutableList()
}

/** The [SiteInfoKey] destination. */
@Composable
fun SiteInfoRoute(key: SiteInfoKey, navigator: SkimNavigator) {
    val info = hiltViewModel<SiteInfoViewModel, SiteInfoViewModel.Factory> { factory ->
        factory.create(key.tabId)
    }
    val state by info.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.tabMissing) { if (state.tabMissing) navigator.back() }
    SiteInfoSheet(
        state = state,
        onSkimAi = info::setSkimAi,
        onPermission = info::setPermission,
        onClearCookies = info::clearCookies,
    )
}

/**
 * Site information: the site, whether the connection is secure, then Permissions. Skim AI is a
 * switch on a normal tab. Location, camera and microphone show only after the site has asked.
 * Cookies and site data can be deleted for this site.
 */
@Composable
fun SiteInfoSheet(
    state: SiteInfoUiState,
    onSkimAi: (Boolean) -> Unit,
    onPermission: (SitePermissionKind, Boolean) -> Unit,
    onClearCookies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = siteRows(state)
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SiteHeader(state)
        if (rows.isNotEmpty()) {
            SectionLabel(stringResource(R.string.site_info_permissions), first = true)
            SegmentedColumn {
                rows.forEachIndexed { index, row ->
                    when (row) {
                        SiteRow.SkimAi -> SkimAiRow(state.skimAiOn, index, rows.size, onSkimAi)
                        is SiteRow.Permission -> PermissionSwitchRow(row.permission, index, rows.size, onPermission)
                        SiteRow.Cookies -> CookiesRow(state.title, index, rows.size, onClearCookies)
                    }
                }
            }
        }
    }
}

private sealed interface SiteRow {
    data object SkimAi : SiteRow
    data class Permission(val permission: SitePermissionRow) : SiteRow
    data object Cookies : SiteRow
}

private fun siteRows(state: SiteInfoUiState): List<SiteRow> = buildList {
    if (state.showSkimAi) add(SiteRow.SkimAi)
    state.permissions.forEach { add(SiteRow.Permission(it)) }
    if (state.showCookies) add(SiteRow.Cookies)
}

@Composable
private fun SiteHeader(state: SiteInfoUiState) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaviconLetterTile(state.letter)
        Column {
            Text(
                state.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.secure) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.site_info_connection_secure),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkimAiRow(on: Boolean, index: Int, count: Int, onChange: (Boolean) -> Unit) {
    SkimListItem(
        headline = stringResource(R.string.skim_ai),
        supporting = stringResource(R.string.site_info_skim_ai_summary),
        index = index,
        count = count,
        onClick = null,
        modifier = Modifier.toggleableRow(on, onChange),
        leading = { SunnyMark(size = 40.dp, iconSize = 20.dp) },
        trailing = { SkimSwitch(on, onCheckedChange = null) },
    )
}

@Composable
private fun PermissionSwitchRow(
    row: SitePermissionRow,
    index: Int,
    count: Int,
    onChange: (SitePermissionKind, Boolean) -> Unit,
) {
    val label = stringResource(row.kind.label)
    val colors = MaterialTheme.colorScheme
    SkimListItem(
        headline = label,
        supporting = stringResource(if (row.allowed) R.string.site_permission_allowed else R.string.site_permission_blocked),
        index = index,
        count = count,
        onClick = null,
        modifier = Modifier.toggleableRow(row.allowed, onCheckedChange = { onChange(row.kind, it) }),
        leading = { PermissionTile(row.kind, colors.secondaryContainer, colors.onSurfaceVariant) },
        trailing = { SkimSwitch(row.allowed, onCheckedChange = null) },
    )
}

@Composable
private fun CookiesRow(host: String, index: Int, count: Int, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SkimListItem(
        headline = stringResource(R.string.cookies_and_site_data),
        supporting = stringResource(R.string.site_info_cookies_summary),
        index = index,
        count = count,
        onClick = null,
        leading = { IconTile(R.drawable.ic_cookie, colors.secondaryContainer, colors.onSurfaceVariant) },
        trailing = {
            IconButton(onClick = onClear) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.cd_delete_site_data, host),
                    tint = colors.onSurfaceVariant,
                )
            }
        },
        trailingIsIconButton = true,
    )
}

@Composable
private fun PermissionTile(kind: SitePermissionKind, container: Color, content: Color) {
    val icon = kind.icon
    if (icon == null) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(container))
    } else {
        IconTile(icon, container, content)
    }
}

private val SitePermissionKind.label: Int
    get() = when (this) {
        SitePermissionKind.LOCATION -> R.string.site_info_location
        SitePermissionKind.CAMERA -> R.string.site_info_camera
        SitePermissionKind.MICROPHONE -> R.string.site_info_microphone
    }

/** Camera has no glyph in the design's icon set, so its tile is the circle alone. */
@get:DrawableRes
private val SitePermissionKind.icon: Int?
    get() = when (this) {
        SitePermissionKind.LOCATION -> R.drawable.ic_location_on
        SitePermissionKind.CAMERA -> null
        SitePermissionKind.MICROPHONE -> R.drawable.ic_mic
    }
