package com.dantech.skimbrowser

import android.app.Application
import com.dantech.skimbrowser.di.StartupEntryPoint
import com.dantech.skimbrowser.system.purgeOldShares
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class SkimApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val entry = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
        val scope = entry.appScope()
        // The saved tabs are read once per process, before the first screen needs them.
        // Purge does not wait for that read. Neither call opens storage on this thread.
        scope.launch { entry.tabManager().restore() }
        scope.launch(Dispatchers.IO) { purgeOldShares(cacheDir) }
    }
}
