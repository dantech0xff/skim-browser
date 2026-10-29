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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.BuildConfig
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.browser.toOpenPages
import com.dantech.skimbrowser.system.DefaultBrowser
import com.dantech.skimbrowser.ui.components.IconTile
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.navigation.AiPrivacyKey
import com.dantech.skimbrowser.ui.navigation.ClearBrowsingDataKey
import com.dantech.skimbrowser.ui.navigation.LicensesKey
import com.dantech.skimbrowser.ui.navigation.SearchEngineDialogKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.ThemeDialogKey

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
        onAiPrivacy = { navigator.push(AiPrivacyKey) },
        onClearData = { navigator.push(ClearBrowsingDataKey) },
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

/**
 * Clear browsing data (15). The WebView pool is the Activity's, passed for this call only.
 * The clear runs on the app scope so it finishes after the dialog closes.
 */
@Composable
fun ClearBrowsingDataRoute(navigator: SkimNavigator) {
    val pages = LocalBrowserHost.current.pool.toOpenPages()
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
    onAiPrivacy: () -> Unit,
    onClearData: () -> Unit,
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
                count = 3,
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
                count = 3,
                onClick = onDefaultBrowser,
            )
            SettingsRow(
                headline = stringResource(R.string.settings_theme),
                supporting = stringResource(state.theme.labelRes()),
                icon = R.drawable.ic_palette,
                container = colors.secondaryContainer,
                content = colors.onSecondaryContainer,
                index = 2,
                count = 3,
                onClick = onTheme,
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
                count = 1,
                onClick = onAiPrivacy,
                leading = { SunnyMark(size = 40.dp, iconSize = 20.dp) },
            )
        }
        section(R.string.settings_section_privacy) {
            SkimListItem(
                headline = stringResource(R.string.clear_browsing_data),
                supporting = stringResource(R.string.settings_clear_browsing_data_summary),
                index = 0,
                count = 1,
                onClick = onClearData,
                leading = { IconTile(R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer) },
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

private const val TAG = "Settings"
