package com.dantech.skimbrowser

import android.os.Looper
import com.dantech.skimbrowser.browser.NetworkMonitor
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.di.SettingsJob
import java.io.Closeable
import java.time.Duration
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.robolectric.Shadows.shadowOf

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
        // The test thread is the paused main looper. runBlocking { job.join() } waits for DataStore
        // to finish cancelling, and that cancellation is a message on this same looper, so join
        // never returns. Pump the looper until the job completes.
        val shadow = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + RELEASE_TIMEOUT_MS * 1_000_000
        while (!job.isCompleted && System.nanoTime() < deadline) {
            shadow.idle()
            if (!job.isCompleted) shadow.idleFor(Duration.ofMillis(20))
        }
        check(job.isCompleted) { "The settings job did not finish." }
    }

    private companion object {
        const val RELEASE_TIMEOUT_MS = 5_000L
    }
}
