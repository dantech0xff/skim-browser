package com.dantech.skimbrowser.domain.settings

import kotlinx.coroutines.flow.Flow

/** User settings. The DataStore file stays in the data layer. */
interface SettingsRepository {
    val settings: Flow<SkimSettings>

    suspend fun setSearchEngine(engine: SearchEngine)

    suspend fun setTheme(theme: ThemeSetting)

    suspend fun setAskBeforeSending(enabled: Boolean)

    suspend fun setKeepChatHistory(enabled: Boolean)

    /** Stores [percent] clamped to [SkimSettings.READER_TEXT_SCALE_RANGE]. */
    suspend fun setReaderTextScale(percent: Int)

    suspend fun setReaderFont(font: ReaderFont)

    suspend fun setOnboardingCompleted()

    suspend fun setLastTabId(id: String?)

    /**
     * A random UUID created on first use and kept for the life of the installation (sent as
     * `X-Skim-Install`). It is never logged and is not part of [SkimSettings].
     */
    suspend fun installId(): String
}
