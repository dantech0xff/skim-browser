package com.dantech.skimbrowser.ui.settings

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.content.ActivityNotFoundException
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.BuildConfig
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.browsing.OpenPages
import com.dantech.skimbrowser.system.DefaultBrowser
import com.dantech.skimbrowser.ui.shell.LocalOpenPages
import com.dantech.skimbrowser.ui.components.IconTile
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.SkimSwitch
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.components.toggleableRow
import com.dantech.skimbrowser.ui.navigation.AiPrivacyKey
import com.dantech.skimbrowser.ui.navigation.BackupKey
import com.dantech.skimbrowser.ui.navigation.ClearBrowsingDataKey
import com.dantech.skimbrowser.ui.navigation.LicensesKey
import com.dantech.skimbrowser.ui.navigation.DownloadsKey
import com.dantech.skimbrowser.ui.navigation.PageTextSizeDialogKey
import com.dantech.skimbrowser.ui.navigation.SearchEngineDialogKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.ThemeDialogKey
import com.dantech.skimbrowser.ui.navigation.TranslationLanguageDialogKey

/** Settings (12). */
@Composable
fun SettingsRoute(navigator: SkimNavigator) {
    val context = LocalContext.current
    val model = hiltViewModel<SettingsViewModel>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, context) { model.setDefaultBrowser(DefaultBrowser.isDefault(context)) }
    DisposableEffect(lifecycle, model, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.setDefaultBrowser(DefaultBrowser.isDefault(context))
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val state by model.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = navigator::back,
        onSearchEngine = { navigator.push(SearchEngineDialogKey) },
        onDefaultBrowser = {
            try {
                context.startActivity(DefaultBrowser.requestIntent(context))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Default browser settings could not be opened: ${e.javaClass.simpleName}")
            }
        },
        onTheme = { navigator.push(ThemeDialogKey) },
        onPageTextSize = { navigator.push(PageTextSizeDialogKey) },
        onDownloads = { navigator.push(DownloadsKey) },
        onAiPrivacy = { navigator.push(AiPrivacyKey) },
        onScamWarnings = model::setScamWarnings,
        onTranslationLanguage = { navigator.push(TranslationLanguageDialogKey) },
        onClearData = { navigator.push(ClearBrowsingDataKey) },
        onBackup = { navigator.push(BackupKey) },
        onBlockPopups = model::setBlockPopups,
        onBlockOddRedirects = model::setBlockOddRedirects,
        onHideCookieBanners = model::setHideCookieBanners,
        onSaveVideos = model::setSaveVideos,
        onPrivacyPolicy = legalOpener(navigator, BuildConfig.PRIVACY_URL),
        onTerms = legalOpener(navigator, BuildConfig.TERMS_URL),
        onLicenses = { navigator.push(LicensesKey) },
    )
}

/** Search engine (15), over Settings. Choosing a row saves it and closes the dialog. */
@Composable
fun SearchEngineRoute(
    onFinished: () -> Unit,
    model: SettingsViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    SearchEngineDialog(
        selected = state.searchEngine,
        onSelect = { engine ->
            model.setSearchEngine(engine)
            onFinished()
        },
        onDismiss = onFinished,
    )
}

/** Page text size, over Settings. The slider saves as it moves. Reset follows the system font size. */
@Composable
fun PageTextSizeRoute(
    onFinished: () -> Unit,
    model: SettingsViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    PageTextSizeDialog(
        percent = state.pageTextZoomPercent,
        systemPercent = pageTextSystemPercent(LocalConfiguration.current.fontScale),
        onPercent = model::setPageTextZoom,
        onReset = { model.setPageTextZoom(null) },
        onDismiss = onFinished,
    )
}

/** Theme (15), over Settings. Choosing a row applies it and closes the dialog. */
@Composable
fun ThemeRoute(
    onFinished: () -> Unit,
    model: SettingsViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    ThemeDialog(
        selected = state.theme,
        onSelect = { theme ->
            model.setTheme(theme)
            onFinished()
        },
        onDismiss = onFinished,
    )
}

@Composable
fun TranslationLanguageRoute(
    onFinished: () -> Unit,
    model: SettingsViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    TranslationLanguageDialog(
        selected = state.translationLanguageTag,
        onSelect = { tag ->
            model.setTranslationLanguage(tag)
            onFinished()
        },
        onDismiss = onFinished,
    )
}

/**
 * Clear browsing data (15). [pages] are the activity's open pages, passed for this call only.
 * The clear runs on the app scope so it finishes after the dialog closes.
 */
@Composable
fun ClearBrowsingDataRoute(navigator: SkimNavigator, pages: OpenPages = LocalOpenPages.current) {
    val model = hiltViewModel<ClearBrowsingDataViewModel>()
    ClearBrowsingDataDialog(
        onClear = { history, cookies, cache ->
            model.clear(history, cookies, cache, pages)
            navigator.back()
        },
        onDismiss = navigator::back,
    )
}

/** Opens [url] in a new normal tab, or null when [url] is blank so the row cannot be tapped. */
private fun legalOpener(navigator: SkimNavigator, url: String): (() -> Unit)? =
    url.takeIf { it.isNotBlank() }?.let { legal -> { navigator.openNewTab(incognito = false, url = legal) } }

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSearchEngine: () -> Unit,
    onDefaultBrowser: () -> Unit,
    onTheme: () -> Unit,
    onPageTextSize: () -> Unit,
    onDownloads: () -> Unit = {},
    onAiPrivacy: () -> Unit,
    onScamWarnings: (Boolean) -> Unit,
    onTranslationLanguage: () -> Unit = {},
    onClearData: () -> Unit,
    onBackup: () -> Unit = {},
    onBlockPopups: (Boolean) -> Unit = {},
    onBlockOddRedirects: (Boolean) -> Unit = {},
    onHideCookieBanners: (Boolean) -> Unit = {},
    onSaveVideos: (Boolean) -> Unit = {},
    onPrivacyPolicy: (() -> Unit)?,
    onTerms: (() -> Unit)?,
    onLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LargeTitleScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        section(R.string.settings_section_general, first = true) {
            SettingsRow(
                headline = stringResource(R.string.settings_search_engine),
                supporting = stringResource(state.searchEngine.labelRes()),
                icon = R.drawable.ic_search,
                container = colors.secondaryContainer,
                content = colors.onSecondaryContainer,
                index = 0,
                count = 4,
                onClick = onSearchEngine,
            )
            SettingsRow(
                headline = stringResource(R.string.settings_default_browser),
                supporting = stringResource(
                    if (state.isDefaultBrowser) {
                        R.string.settings_default_browser_summary_is_default
                    } else {
                        R.string.settings_default_browser_summary_not_default
                    },
                ),
                icon = R.drawable.ic_public,
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
                index = 1,
                count = 4,
                onClick = onDefaultBrowser,
            )
            SettingsRow(
                headline = stringResource(R.string.settings_theme),
                supporting = stringResource(state.theme.labelRes()),
                icon = R.drawable.ic_palette,
                container = colors.secondaryContainer,
                content = colors.onSecondaryContainer,
                index = 2,
                count = 4,
                onClick = onTheme,
            )
            SettingsRow(
                headline = stringResource(R.string.settings_page_text_size),
                supporting = if (state.pageTextZoomPercent == null) {
                    stringResource(R.string.settings_page_text_size_system)
                } else {
                    stringResource(R.string.reader_size_percent, state.pageTextZoomPercent)
                },
                icon = R.drawable.ic_format_size,
                container = colors.tertiaryContainer,
                content = colors.onTertiaryContainer,
                index = 3,
                count = 4,
                onClick = onPageTextSize,
            )
        }
        section(R.string.skim_ai) {
            SkimListItem(
                headline = stringResource(R.string.settings_ai_privacy),
                supporting = stringResource(
                    if (state.askBeforeSending) {
                        R.string.settings_ai_privacy_summary_ask
                    } else {
                        R.string.settings_ai_privacy_summary_no_ask
                    },
                ),
                index = 0,
                count = 2,
                onClick = onAiPrivacy,
                leading = { SunnyMark(size = 40.dp, iconSize = 20.dp) },
            )
            SkimListItem(
                headline = stringResource(R.string.settings_translation_language),
                supporting = stringResource(translationLanguageLabel(state.translationLanguageTag)),
                index = 1,
                count = 2,
                onClick = onTranslationLanguage,
                leading = { IconTile(R.drawable.ic_public, colors.primaryContainer, colors.onPrimaryContainer) },
            )
        }
        section(R.string.settings_section_privacy_security) {
            SwitchRow(
                headline = stringResource(R.string.settings_scam_warnings),
                supporting = stringResource(R.string.settings_scam_warnings_summary),
                icon = R.drawable.ic_shield,
                checked = state.scamWarnings,
                index = 0,
                count = PRIVACY_ROWS,
                onCheckedChange = onScamWarnings,
            )
            SwitchRow(
                headline = stringResource(R.string.settings_block_popups),
                supporting = stringResource(R.string.settings_block_popups_summary),
                icon = R.drawable.ic_shield,
                checked = state.blockPopups,
                index = 1,
                count = PRIVACY_ROWS,
                onCheckedChange = onBlockPopups,
            )
            SwitchRow(
                headline = stringResource(R.string.settings_block_redirects),
                supporting = stringResource(R.string.settings_block_redirects_summary),
                icon = R.drawable.ic_shield,
                checked = state.blockOddRedirects,
                index = 2,
                count = PRIVACY_ROWS,
                onCheckedChange = onBlockOddRedirects,
            )
            SwitchRow(
                headline = stringResource(R.string.settings_hide_cookie_banners),
                supporting = stringResource(R.string.settings_hide_cookie_banners_summary),
                icon = R.drawable.ic_shield,
                checked = state.hideCookieBanners,
                index = 3,
                count = PRIVACY_ROWS,
                onCheckedChange = onHideCookieBanners,
            )
            SwitchRow(
                headline = stringResource(R.string.settings_save_videos),
                supporting = stringResource(R.string.settings_save_videos_summary),
                icon = R.drawable.ic_download,
                checked = state.saveVideos,
                index = 4,
                count = PRIVACY_ROWS,
                onCheckedChange = onSaveVideos,
            )
            SkimListItem(
                headline = stringResource(R.string.downloads_title),
                supporting = stringResource(R.string.settings_downloads_summary),
                index = 5,
                count = PRIVACY_ROWS,
                onClick = onDownloads,
                leading = { IconTile(R.drawable.ic_download, colors.secondaryContainer, colors.onSecondaryContainer) },
            )
            SkimListItem(
                headline = stringResource(R.string.clear_browsing_data),
                supporting = stringResource(R.string.settings_clear_browsing_data_summary),
                index = 6,
                count = PRIVACY_ROWS,
                onClick = onClearData,
                leading = { IconTile(R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer) },
            )
        }
        section(R.string.backup_title) {
            SkimListItem(
                headline = stringResource(R.string.backup_title),
                supporting = stringResource(R.string.settings_backup_summary),
                index = 0,
                count = 1,
                onClick = onBackup,
                leading = { IconTile(R.drawable.ic_download, colors.primaryContainer, colors.onPrimaryContainer) },
            )
        }
        section(R.string.settings_section_about) {
            AboutRow(R.drawable.ic_shield, R.string.settings_privacy_policy, 0, onPrivacyPolicy)
            AboutRow(R.drawable.ic_description, R.string.settings_terms_of_service, 1, onTerms)
            AboutRow(R.drawable.ic_code, R.string.settings_open_source_licenses, 2, onLicenses)
            SkimListItem(
                headline = stringResource(R.string.settings_version),
                index = 3,
                count = 4,
                onClick = null,
                leading = { IconTile(R.drawable.ic_info, colors.surfaceContainer, colors.onSurfaceVariant) },
                trailing = {
                    Text(
                        text = stringResource(R.string.settings_version_value, state.versionName, state.versionCode),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsRow(
    headline: String,
    supporting: String,
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SkimListItem(
        headline = headline,
        supporting = supporting,
        index = index,
        count = count,
        onClick = onClick,
        leading = { IconTile(icon, container, content) },
    )
}

@Composable
private fun ColumnScope.SwitchRow(
    headline: String,
    supporting: String,
    @DrawableRes icon: Int,
    checked: Boolean,
    index: Int,
    count: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SkimListItem(
        headline = headline,
        supporting = supporting,
        index = index,
        count = count,
        onClick = null,
        modifier = Modifier.toggleableRow(checked, onCheckedChange),
        leading = { IconTile(icon, colors.secondaryContainer, colors.onSecondaryContainer) },
        trailing = { SkimSwitch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun ColumnScope.AboutRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    index: Int,
    onClick: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    SkimListItem(
        headline = stringResource(label),
        index = index,
        count = 4,
        onClick = onClick,
        leading = { IconTile(icon, colors.surfaceContainer, colors.onSurfaceVariant) },
    )
}

private fun LazyListScope.section(
    @StringRes label: Int,
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    item { SectionLabel(stringResource(label), first) }
    item { SegmentedColumn(content = content) }
}

private const val PRIVACY_ROWS = 7
private const val TAG = "Settings"
