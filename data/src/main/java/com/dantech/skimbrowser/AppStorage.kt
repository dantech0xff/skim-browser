package com.dantech.skimbrowser

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File

/**
 * Where the process graph keeps its data. The defaults are the app's private storage; [temp] puts
 * everything in one directory, so a test can build a container, close it and build another one on
 * the same data.
 */
data class AppStorage(
    /** A name in the app's database directory, or an absolute path. */
    val databaseName: String = "skim.db",
    /** The settings file; DataStore requires the `.preferences_pb` extension. */
    val settingsFile: (Context) -> File = { it.preferencesDataStoreFile("skim_settings") },
    /** The saved WebView state of normal tabs, kept across process death. */
    val tabStateDir: (Context) -> File = { File(it.filesDir, "tabstate") },
    /** The tab card thumbnails of normal tabs, a cache. */
    val thumbnailDir: (Context) -> File = { File(it.cacheDir, "thumbs") },
    /** Files shared from incognito tabs, deleted when the incognito session ends. */
    val incognitoSharedDir: (Context) -> File = { File(it.cacheDir, "shared/incognito") },
) {
    companion object {
        fun temp(dir: File) = AppStorage(
            databaseName = File(dir, "skim.db").path,
            settingsFile = { File(dir, "skim_settings.preferences_pb") },
            tabStateDir = { File(dir, "tabstate") },
            thumbnailDir = { File(dir, "thumbs") },
            incognitoSharedDir = { File(dir, "shared/incognito") },
        )
    }
}
