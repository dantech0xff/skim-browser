package com.dantech.skimbrowser

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File

/**
 * Where an [AppContainer] keeps its data. The defaults are the app's private storage; [temp] puts
 * both files in one directory, so a test can build a container, close it and build another one on
 * the same data.
 */
data class AppStorage(
    /** A name in the app's database directory, or an absolute path. */
    val databaseName: String = "skim.db",
    /** The settings file; DataStore requires the `.preferences_pb` extension. */
    val settingsFile: (Context) -> File = { it.preferencesDataStoreFile("skim_settings") },
) {
    companion object {
        fun temp(dir: File) = AppStorage(
            databaseName = File(dir, "skim.db").path,
            settingsFile = { File(dir, "skim_settings.preferences_pb") },
        )
    }
}
