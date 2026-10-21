package com.dantech.skimbrowser

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container: one per process, created in [SkimApplication.onCreate]. */
class AppContainer(val app: Application) {

    /**
     * The last failure of a coroutine in [appScope]. The shell reads it to fall back to a usable
     * screen instead of waiting forever on work that died.
     */
    val lastError = MutableStateFlow<Throwable?>(null)

    /**
     * Process-wide scope for work that outlives a screen. A failing child neither cancels its
     * siblings nor crashes the process: the error is logged and kept in [lastError].
     */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Uncaught exception in appScope", error)
            lastError.value = error
        },
    )

    private companion object {
        const val TAG = "AppContainer"
    }
}
