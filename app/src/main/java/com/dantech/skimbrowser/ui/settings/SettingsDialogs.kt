package com.dantech.skimbrowser.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.page.PageTextZoom
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.ui.components.ChoiceDialogContent
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.ReaderSizeSlider
import com.dantech.skimbrowser.ui.components.SkimDialogContent
import com.dantech.skimbrowser.ui.components.readerSizePercent
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private val SearchEngineOptions: ImmutableList<SearchEngine> = SearchEngine.entries.toImmutableList()

private val ThemeOptions: ImmutableList<ThemeSetting> = ThemeSetting.entries.toImmutableList()

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
        options = SearchEngineOptions,
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
        options = ThemeOptions,
        selected = selected,
        label = { stringResource(it.labelRes()) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/**
 * Page text size. The slider is the reader one (80 to 200 percent, steps of 10). [percent] null
 * shows the system size and does not store it until the slider moves. Reset clears the stored size.
 */
@Composable
fun PageTextSizeDialog(
    percent: Int?,
    systemPercent: Int,
    onPercent: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = percent ?: readerSizePercent(systemPercent.toFloat())
    SkimDialogContent(
        title = stringResource(R.string.settings_page_text_size),
        confirm = DialogAction(stringResource(R.string.action_done), onDismiss),
        dismiss = DialogAction(
            label = stringResource(R.string.action_reset),
            onClick = onReset,
            enabled = percent != null,
        ),
        modifier = modifier,
        text = { ReaderSizeSlider(percent = shown, onPercent = onPercent) },
    )
}

/** The system font scale as a page text percent, for the slider before a size is stored. */
fun pageTextSystemPercent(fontScale: Float): Int = PageTextZoom.ofFontScale(fontScale)

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
