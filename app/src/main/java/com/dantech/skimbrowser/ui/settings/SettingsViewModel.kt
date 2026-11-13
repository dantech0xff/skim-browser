package com.dantech.skimbrowser.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.di.AppVersion
import com.dantech.skimbrowser.domain.settings.SearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Settings (12) shows. Copy is resolved by the screen from these values. */
@Immutable
data class SettingsUiState(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val theme: ThemeSetting = ThemeSetting.System,
    /** Null follows the system font scale. */
    val pageTextZoomPercent: Int? = null,
    val translationLanguageTag: String? = null,
    val isDefaultBrowser: Boolean = false,
    val askBeforeSending: Boolean = true,
    val scamWarnings: Boolean = true,
    val blockPopups: Boolean = true,
    val blockOddRedirects: Boolean = true,
    val hideCookieBanners: Boolean = true,
    val versionName: String = "",
    val versionCode: Int = 0,
)

/**
 * Settings (12). The search engine, theme and AI summary come from [settings]. Whether Skim is the
 * default browser is reported with [setDefaultBrowser] when the screen resumes, because the system
 * can change it while Skim is in the background. The screen reads that; this class keeps no Context.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    version: AppVersion,
) : ViewModel() {

    private val defaultBrowser = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, defaultBrowser) { stored, isDefault ->
        SettingsUiState(
            searchEngine = stored.searchEngine,
            theme = stored.theme,
            pageTextZoomPercent = stored.pageTextZoomPercent,
            translationLanguageTag = stored.translationLanguageTag,
            isDefaultBrowser = isDefault,
            askBeforeSending = stored.askBeforeSending,
            scamWarnings = stored.scamWarnings,
            blockPopups = stored.blockPopups,
            blockOddRedirects = stored.blockOddRedirects,
            hideCookieBanners = stored.hideCookieBanners,
            versionName = version.name,
            versionCode = version.code,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsUiState(versionName = version.name, versionCode = version.code),
    )

    /** The default-browser role as the screen just read it. */
    fun setDefaultBrowser(isDefault: Boolean) {
        defaultBrowser.value = isDefault
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settings.setSearchEngine(engine) }
    }

    fun setTheme(theme: ThemeSetting) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    /** Null clears the stored page text size, so pages follow the system font scale. */
    fun setPageTextZoom(percent: Int?) {
        viewModelScope.launch { settings.setPageTextZoom(percent) }
    }

    fun setScamWarnings(enabled: Boolean) {
        viewModelScope.launch { settings.setScamWarnings(enabled) }
    }

    fun setTranslationLanguage(tag: String?) {
        viewModelScope.launch { settings.setTranslationLanguage(tag) }
    }

    fun setBlockPopups(enabled: Boolean) {
        viewModelScope.launch { settings.setBlockPopups(enabled) }
    }

    fun setBlockOddRedirects(enabled: Boolean) {
        viewModelScope.launch { settings.setBlockOddRedirects(enabled) }
    }

    fun setHideCookieBanners(enabled: Boolean) {
        viewModelScope.launch { settings.setHideCookieBanners(enabled) }
    }
}
