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
import com.dantech.skimbrowser.domain.usecase.SetSearchEngine
import com.dantech.skimbrowser.domain.usecase.SetTheme
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
    val isDefaultBrowser: Boolean = false,
    val askBeforeSending: Boolean = true,
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
    private val saveSearchEngine: SetSearchEngine,
    private val saveTheme: SetTheme,
    version: AppVersion,
) : ViewModel() {

    private val defaultBrowser = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, defaultBrowser) { stored, isDefault ->
        SettingsUiState(
            searchEngine = stored.searchEngine,
            theme = stored.theme,
            isDefaultBrowser = isDefault,
            askBeforeSending = stored.askBeforeSending,
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
        viewModelScope.launch { saveSearchEngine(engine) }
    }

    fun setTheme(theme: ThemeSetting) {
        viewModelScope.launch { saveTheme(theme) }
    }
}
