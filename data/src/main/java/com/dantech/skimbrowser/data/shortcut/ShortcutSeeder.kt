package com.dantech.skimbrowser.data.shortcut

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.dao.ShortcutDao
import com.dantech.skimbrowser.data.db.entity.ShortcutEntity
import com.dantech.skimbrowser.domain.shortcut.ShortcutCatalog
import com.dantech.skimbrowser.domain.shortcut.webPresets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Writes the preset shortcuts once, when Home has none.
 *
 * The flag lives in the settings DataStore and is not a user setting. Clearing the shortcuts
 * afterwards leaves the Add tile: the flag is already set, so the presets do not come back.
 * A catalog that cannot be read is not flagged, and the next call tries again. Shortcuts the
 * user already has are left where they are.
 */
@Singleton
class ShortcutSeeder(
    private val dao: ShortcutDao,
    private val catalog: ShortcutCatalog,
    private val dataStore: DataStore<Preferences>,
) {
    @Inject constructor(
        database: SkimDatabase,
        catalog: ShortcutCatalog,
        dataStore: DataStore<Preferences>,
    ) : this(database.shortcutDao(), catalog, dataStore)

    private val gate = Mutex()
    private var finished = false

    suspend fun seed() {
        gate.withLock {
            if (finished) return
            val already = dataStore.data.map { it[SEEDED] == true }.first()
            if (!already) {
                try {
                    val presets = catalog.presets().webPresets()
                    dao.insertPresetsIfEmpty(presets.map { ShortcutEntity(name = it.name, url = it.url, position = 0) })
                    dataStore.edit { it[SEEDED] = true }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Preset shortcuts were not saved: ${e.javaClass.simpleName}")
                    return
                }
            }
            finished = true
        }
    }

    private companion object {
        const val TAG = "ShortcutSeeder"

        /** Install bookkeeping, like the install id: not part of the settings the user sees. */
        val SEEDED = booleanPreferencesKey("shortcuts_seeded")
    }
}
