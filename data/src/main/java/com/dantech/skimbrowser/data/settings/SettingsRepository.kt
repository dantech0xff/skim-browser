package com.dantech.skimbrowser.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dantech.skimbrowser.domain.settings.SettingsRepository as SettingsStore
import com.dantech.skimbrowser.domain.settings.ThemeSetting
import com.dantech.skimbrowser.domain.url.normalizeHost
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * User settings in a Preferences DataStore. Reading never fails on bad data: a value that is
 * missing, of the wrong type or an unknown name reads as its default, and the reader text scale is
 * clamped to [SkimSettings.READER_TEXT_SCALE_RANGE].
 */
@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) : SettingsStore {

    override val settings: Flow<SkimSettings> = preferences().map { it.toSettings() }.distinctUntilChanged()

    override val shieldDisabledHosts: Flow<Set<String>> = preferences().map { prefs ->
        prefs[Keys.SHIELD_DISABLED].orEmpty().map(::normalizeHost).filter { it.isNotEmpty() }.toSet()
    }.distinctUntilChanged()

    override suspend fun setSearchEngine(e: SearchEngine) {
        dataStore.edit { it[Keys.SEARCH_ENGINE] = e.name }
    }

    override suspend fun setTheme(t: ThemeSetting) {
        dataStore.edit { it[Keys.THEME] = t.name }
    }

    override suspend fun setAskBeforeSending(b: Boolean) {
        dataStore.edit { it[Keys.ASK_BEFORE_SENDING] = b }
    }

    override suspend fun setKeepChatHistory(b: Boolean) {
        dataStore.edit { it[Keys.KEEP_CHAT_HISTORY] = b }
    }

    override suspend fun setSendRatings(b: Boolean) {
        dataStore.edit { it[Keys.SEND_RATINGS] = b }
    }

    /** Stores [p] clamped to [SkimSettings.READER_TEXT_SCALE_RANGE]. */
    override suspend fun setReaderTextScale(p: Int) {
        dataStore.edit { it[Keys.READER_TEXT_SCALE] = p.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE) }
    }

    /** Stores [percent], or removes it when null so pages follow the system font scale. */
    override suspend fun setPageTextZoom(percent: Int?) {
        dataStore.edit { prefs ->
            if (percent == null) {
                prefs.remove(Keys.PAGE_TEXT_ZOOM)
            } else {
                prefs[Keys.PAGE_TEXT_ZOOM] = percent.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE)
            }
        }
    }

    override suspend fun setReaderFont(f: ReaderFont) {
        dataStore.edit { it[Keys.READER_FONT] = f.name }
    }

    override suspend fun setOnboardingCompleted() {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
    }

    override suspend fun setLastTabId(id: String?) {
        dataStore.edit { if (id == null) it.remove(Keys.LAST_TAB_ID) else it[Keys.LAST_TAB_ID] = id }
    }

    override suspend fun setScamWarnings(enabled: Boolean) {
        dataStore.edit { it[Keys.SCAM_WARNINGS] = enabled }
    }

    override suspend fun replacePortable(settings: SkimSettings, shieldDisabledHosts: Set<String>) {
        val ratings = settings.sendRatings
        val pageText = settings.pageTextZoomPercent
        dataStore.edit { prefs ->
            prefs[Keys.SEARCH_ENGINE] = settings.searchEngine.name
            prefs[Keys.THEME] = settings.theme.name
            prefs[Keys.ASK_BEFORE_SENDING] = settings.askBeforeSending
            prefs[Keys.KEEP_CHAT_HISTORY] = settings.keepChatHistory
            if (ratings == null) prefs.remove(Keys.SEND_RATINGS) else prefs[Keys.SEND_RATINGS] = ratings
            prefs[Keys.READER_TEXT_SCALE] = settings.readerTextScalePercent.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE)
            if (pageText == null) {
                prefs.remove(Keys.PAGE_TEXT_ZOOM)
            } else {
                prefs[Keys.PAGE_TEXT_ZOOM] = pageText.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE)
            }
            prefs[Keys.READER_FONT] = settings.readerFont.name
            prefs[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
            prefs[Keys.SCAM_WARNINGS] = settings.scamWarnings
            prefs[Keys.SHIELD_DISABLED] = shieldDisabledHosts.map(::normalizeHost).filter { it.isNotEmpty() }.toSet()
        }
    }

    override suspend fun setShieldDisabled(host: String, disabled: Boolean) {
        val key = normalizeHost(host)
        if (key.isEmpty()) return
        dataStore.edit { prefs ->
            val now = prefs[Keys.SHIELD_DISABLED].orEmpty().toMutableSet()
            if (disabled) now += key else now -= key
            prefs[Keys.SHIELD_DISABLED] = now
        }
    }

    /**
     * A random UUID created on first use and kept for the life of the installation (sent as
     * `X-Skim-Install` to the proxy). Creation happens inside one DataStore edit, so calls
     * racing on a fresh install all get the same id; a stored value that is not a UUID is replaced.
     * The id is never logged and is not part of [SkimSettings].
     */
    override suspend fun installId(): String {
        preferences().first().read(Keys.INSTALL_ID)?.takeIf(::isInstallId)?.let { return it }
        val updated = dataStore.edit { prefs ->
            if (prefs.read(Keys.INSTALL_ID)?.let(::isInstallId) != true) {
                prefs[Keys.INSTALL_ID] = UUID.randomUUID().toString()
            }
        }
        return checkNotNull(updated.read(Keys.INSTALL_ID)) { "The install id was not stored" }
    }

    /** The stored preferences; a failed read (not a corrupt file, which is replaced) reads as empty. */
    private fun preferences(): Flow<Preferences> =
        dataStore.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    private fun Preferences.toSettings(): SkimSettings {
        val defaults = SkimSettings()
        return SkimSettings(
            searchEngine = enumOrDefault(read(Keys.SEARCH_ENGINE), defaults.searchEngine),
            theme = enumOrDefault(read(Keys.THEME), defaults.theme),
            askBeforeSending = read(Keys.ASK_BEFORE_SENDING) ?: defaults.askBeforeSending,
            keepChatHistory = read(Keys.KEEP_CHAT_HISTORY) ?: defaults.keepChatHistory,
            sendRatings = read(Keys.SEND_RATINGS),
            readerTextScalePercent = (read(Keys.READER_TEXT_SCALE) ?: defaults.readerTextScalePercent)
                .coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE),
            pageTextZoomPercent = read(Keys.PAGE_TEXT_ZOOM)?.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE),
            readerFont = enumOrDefault(read(Keys.READER_FONT), defaults.readerFont),
            onboardingCompleted = read(Keys.ONBOARDING_COMPLETED) ?: defaults.onboardingCompleted,
            lastTabId = read(Keys.LAST_TAB_ID)?.takeIf { it.isNotBlank() },
            scamWarnings = read(Keys.SCAM_WARNINGS) ?: defaults.scamWarnings,
        )
    }

    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val THEME = stringPreferencesKey("theme")
        val ASK_BEFORE_SENDING = booleanPreferencesKey("ask_before_sending")
        val KEEP_CHAT_HISTORY = booleanPreferencesKey("keep_chat_history")
        val SEND_RATINGS = booleanPreferencesKey("send_ratings")
        val READER_TEXT_SCALE = intPreferencesKey("reader_text_scale_percent")
        val PAGE_TEXT_ZOOM = intPreferencesKey("page_text_zoom_percent")
        val READER_FONT = stringPreferencesKey("reader_font")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_TAB_ID = stringPreferencesKey("last_tab_id")
        val SCAM_WARNINGS = booleanPreferencesKey("scam_warnings")
        val INSTALL_ID = stringPreferencesKey("install_id")
        val SHIELD_DISABLED = stringSetPreferencesKey("shield_disabled_hosts")
    }

    companion object {
        /**
         * The settings store over [produceFile] (extension `.preferences_pb`). Only one store may be
         * active per file in a process: cancel [scope] and wait for it before opening the file again.
         * A corrupt file is replaced by empty preferences, so every setting reads as its default.
         */
        fun createDataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = produceFile,
            )
    }
}

private val INSTALL_ID_FORMAT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

private fun isInstallId(value: String): Boolean = INSTALL_ID_FORMAT.matches(value)

/** The value of [key], or null when it is missing or stored with another type. */
private inline fun <reified T : Any> Preferences.read(key: Preferences.Key<T>): T? = asMap()[key] as? T

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default
