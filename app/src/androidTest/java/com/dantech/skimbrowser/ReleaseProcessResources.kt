package com.dantech.skimbrowser

import com.dantech.skimbrowser.browser.NetworkMonitor
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.di.SettingsJob
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * Releases the process singletons of the test's component so the next test method can open the
 * same files. Call it from `@After` while the component is alive. Production never calls it.
 */
@Singleton
class ReleaseProcessResources @Inject constructor(
    private val networkMonitor: Provider<NetworkMonitor>,
    private val httpClient: Provider<OkHttpClient>,
    private val database: Provider<SkimDatabase>,
    @AppScope private val appScope: Provider<CoroutineScope>,
    @SettingsJob private val settingsJob: Provider<CompletableJob>,
) {
    private var released = false

    fun release() {
        if (released) return
        released = true
        val monitor = networkMonitor.get()
        if (monitor is Closeable) monitor.close()
        val client = httpClient.get()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        database.get().close()
        val scope = appScope.get()
        val job = settingsJob.get()
        scope.cancel()
        runBlocking { job.join() }
    }
}
