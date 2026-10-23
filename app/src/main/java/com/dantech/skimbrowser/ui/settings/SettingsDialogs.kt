package com.dantech.skimbrowser.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.settings.SearchEngine
import com.dantech.skimbrowser.ui.components.ChoiceDialogContent
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/** Search engine (15). Choosing an option saves it; the caller closes the dialog. */
@Composable
fun SearchEngineDialog(
    selected: SearchEngine,
    onSelect: (SearchEngine) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceDialogContent(
        title = stringResource(R.string.settings_search_engine),
        options = SearchEngine.entries,
        selected = selected,
        label = { stringResource(it.labelRes()) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/** Theme (15). Choosing an option applies it at once; the caller closes the dialog. */
@Composable
fun ThemeDialog(
    selected: ThemeSetting,
    onSelect: (ThemeSetting) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceDialogContent(
        title = stringResource(R.string.settings_theme),
        options = ThemeSetting.entries,
        selected = selected,
        label = { stringResource(it.labelRes()) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

@StringRes
internal fun SearchEngine.labelRes(): Int = when (this) {
    SearchEngine.GOOGLE -> R.string.search_engine_google
    SearchEngine.BING -> R.string.search_engine_bing
    SearchEngine.DUCKDUCKGO -> R.string.search_engine_duckduckgo
    SearchEngine.ECOSIA -> R.string.search_engine_ecosia
}

@StringRes
internal fun ThemeSetting.labelRes(): Int = when (this) {
    ThemeSetting.System -> R.string.theme_system_default
    ThemeSetting.Light -> R.string.theme_light
    ThemeSetting.Dark -> R.string.theme_dark
}
