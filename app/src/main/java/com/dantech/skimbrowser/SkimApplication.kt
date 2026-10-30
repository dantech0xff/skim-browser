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
        // Tabs, preset shortcuts and the share purge start off this thread. None of them opens
        // storage here; each call suspends onto its own dispatcher.
        scope.launch { entry.tabManager().restore() }
        scope.launch { entry.shortcutSeeder().seed() }
        scope.launch(Dispatchers.IO) { purgeOldShares(cacheDir) }
    }
}
