package com.dantech.skimbrowser

import android.app.Application
import kotlinx.coroutines.launch

class SkimApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The saved tabs are read once per process, before the first screen needs them.
        container.appScope.launch { container.tabManager.restore() }
    }
}
