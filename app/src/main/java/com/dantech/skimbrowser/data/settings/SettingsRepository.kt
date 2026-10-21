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
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import java.io.File
import java.io.IOException
import java.util.UUID
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
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<SkimSettings> = preferences().map { it.toSettings() }.distinctUntilChanged()

    suspend fun setSearchEngine(e: SearchEngine) {
        dataStore.edit { it[Keys.SEARCH_ENGINE] = e.name }
    }

    suspend fun setTheme(t: ThemeSetting) {
        dataStore.edit { it[Keys.THEME] = t.name }
    }

    suspend fun setAskBeforeSending(b: Boolean) {
        dataStore.edit { it[Keys.ASK_BEFORE_SENDING] = b }
    }

    suspend fun setKeepChatHistory(b: Boolean) {
        dataStore.edit { it[Keys.KEEP_CHAT_HISTORY] = b }
    }

    /** Stores [p] clamped to [SkimSettings.READER_TEXT_SCALE_RANGE]. */
    suspend fun setReaderTextScale(p: Int) {
        dataStore.edit { it[Keys.READER_TEXT_SCALE] = p.coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE) }
    }

    suspend fun setReaderFont(f: ReaderFont) {
        dataStore.edit { it[Keys.READER_FONT] = f.name }
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
    }

    suspend fun setLastTabId(id: String?) {
        dataStore.edit { if (id == null) it.remove(Keys.LAST_TAB_ID) else it[Keys.LAST_TAB_ID] = id }
    }

    /**
     * A random UUID created on first use and kept for the life of the installation (sent as
     * `X-Skim-Install` to the release proxy). Creation happens inside one DataStore edit, so calls
     * racing on a fresh install all get the same id; a stored value that is not a UUID is replaced.
     * The id is never logged and is not part of [SkimSettings].
     */
    suspend fun installId(): String {
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
            readerTextScalePercent = (read(Keys.READER_TEXT_SCALE) ?: defaults.readerTextScalePercent)
                .coerceIn(SkimSettings.READER_TEXT_SCALE_RANGE),
            readerFont = enumOrDefault(read(Keys.READER_FONT), defaults.readerFont),
            onboardingCompleted = read(Keys.ONBOARDING_COMPLETED) ?: defaults.onboardingCompleted,
            lastTabId = read(Keys.LAST_TAB_ID)?.takeIf { it.isNotBlank() },
        )
    }

    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val THEME = stringPreferencesKey("theme")
        val ASK_BEFORE_SENDING = booleanPreferencesKey("ask_before_sending")
        val KEEP_CHAT_HISTORY = booleanPreferencesKey("keep_chat_history")
        val READER_TEXT_SCALE = intPreferencesKey("reader_text_scale_percent")
        val READER_FONT = stringPreferencesKey("reader_font")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_TAB_ID = stringPreferencesKey("last_tab_id")
        val INSTALL_ID = stringPreferencesKey("install_id")
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
