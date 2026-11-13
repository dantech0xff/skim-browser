package com.dantech.skimbrowser.domain.settings

import kotlinx.coroutines.flow.Flow

/** User settings. The DataStore file stays in the data layer. */
interface SettingsRepository {
    val settings: Flow<SkimSettings>

    suspend fun setSearchEngine(engine: SearchEngine)

    suspend fun setTheme(theme: ThemeSetting)

    suspend fun setAskBeforeSending(enabled: Boolean)

    suspend fun setKeepChatHistory(enabled: Boolean)

    /** [enabled] false declines. The value is no longer "not asked". */
    suspend fun setSendRatings(enabled: Boolean)

    /** Stores [percent] clamped to [SkimSettings.READER_TEXT_SCALE_RANGE]. */
    suspend fun setReaderTextScale(percent: Int)

    /**
     * Stores a page text size, clamped to [SkimSettings.READER_TEXT_SCALE_RANGE]. Null clears it,
     * so pages follow the system font scale again.
     */
    suspend fun setPageTextZoom(percent: Int?)

    suspend fun setReaderFont(font: ReaderFont)

    suspend fun setOnboardingCompleted()

    suspend fun setLastTabId(id: String?)

    /** [enabled] false skips the on-device scam check and turns WebView Safe Browsing off. */
    suspend fun setScamWarnings(enabled: Boolean)

    /** Hosts where ad and tracker blocking is off. Empty until the person turns a site off. */
    val shieldDisabledHosts: Flow<Set<String>>

    /** [disabled] true stops blocking on [host]. */
    suspend fun setShieldDisabled(host: String, disabled: Boolean)

    /**
     * A random UUID created on first use and kept for the life of the installation (sent as
     * `X-Skim-Install` on every model request). It is never logged and is not part of [SkimSettings].
     */
    suspend fun installId(): String
}
