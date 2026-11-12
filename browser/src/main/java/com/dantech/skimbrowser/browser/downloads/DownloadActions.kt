package com.dantech.skimbrowser.browser.downloads

import android.app.Application
import android.util.Log
import android.webkit.WebView
import androidx.core.net.toUri
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.download.DownloadCommands
import com.dantech.skimbrowser.domain.download.DownloadLog
import com.dantech.skimbrowser.domain.download.DownloadRetry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the Downloads screen asks the browser to do. The activity attaches the router while it
 * lives. A retry after the activity is gone does nothing.
 */
@Singleton
class DownloadActions @Inject constructor(
    private val log: DownloadLog,
    private val tracker: DownloadTracker,
    private val app: Application,
    @AppScope private val scope: CoroutineScope,
) : DownloadCommands {

    private var router: DownloadRouter? = null
    private var webViewOf: (String) -> WebView? = { null }

    /** [webViewOf] reads a blob's page when one is still showing. An http retry does not need it. */
    fun attach(router: DownloadRouter, webViewOf: (String) -> WebView?) {
        this.router = router
        this.webViewOf = webViewOf
    }

    override fun retry(retry: DownloadRetry) {
        router?.retry(retry, webViewOf(retry.tabId))
    }

    override fun refresh() {
        tracker.refresh()
        scope.launch {
            log.runningWithSystemId().forEach { item ->
                val systemId = item.systemId ?: return@forEach
                tracker.refreshSystem(item.id, systemId)
            }
        }
    }

    override suspend fun remove(id: Long, deleteFile: Boolean) {
        withContext(NonCancellable) {
            if (deleteFile) tracker.cancelAndJoin(id)
            val item = log.remove(id) ?: return@withContext
            if (!deleteFile) return@withContext
            item.systemId?.let(tracker::removeSystem)
            val uri = item.uri ?: return@withContext
            try {
                app.contentResolver.delete(uri.toUri(), null, null)
            } catch (e: RuntimeException) {
                Log.w(TAG, "A downloaded file could not be deleted: ${e.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "SkimDownloads"
    }
}
