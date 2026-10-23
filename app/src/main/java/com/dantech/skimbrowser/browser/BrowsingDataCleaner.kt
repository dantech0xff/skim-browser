package com.dantech.skimbrowser.browser

import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import com.dantech.skimbrowser.data.normalizeHost
import com.dantech.skimbrowser.domain.history.HistoryRepository
import com.dantech.skimbrowser.domain.tab.TabRepository
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoKind
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Deletes browsing data. [clearSite] forgets one site's cookies and web storage (the site
 * information sheet). [clearAll] is Clear browsing data and touches only the default profile:
 * an incognito session has its own end.
 *
 * The Activity's [TabEngine] is passed per call and never kept. Cache asked for while no Activity
 * is up waits for the next one ([applyPendingCacheClear]).
 */
class BrowsingDataCleaner(
    private val history: HistoryRepository,
    private val tabRepo: TabRepository,
    private val tabs: TabManager,
    private val stateStore: TabStateStore,
    private val thumbnails: ThumbnailStore,
    private val incognito: IncognitoSession,
    private val undo: UndoRegistry,
    private val snackbar: SnackbarController,
    private val siteData: DefaultProfileSiteData = AndroidDefaultProfileSiteData(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val pendingCacheClear = AtomicBoolean(false)

    /** Cookies and web storage of [host]. An incognito tab uses the session's profile. */
    suspend fun clearSite(host: String, incognitoTab: Boolean) {
        val key = normalizeHost(host)
        if (key.isEmpty()) return
        withContext(mainDispatcher) {
            if (incognitoTab) incognito.clearSiteData(key) else siteData.clearHost(key)
        }
    }

    /**
     * Clear browsing data for the default profile. Each flag that is on runs even when another
     * throws, so one store failing does not keep the others.
     *
     * History also drops the back/forward list of every normal tab, their saved WebView state and
     * thumbnails, and the grouping snapshot, and cancels history Undo so it cannot bring a row
     * back. Cookies are flushed, then normal tabs reload so a page still open cannot write the
     * old cookies out again.
     */
    suspend fun clearAll(history: Boolean, cookies: Boolean, cache: Boolean, engine: TabEngine?) {
        val failures = mutableListOf<String>()
        if (history) step("history", failures) { clearHistory(engine) }
        if (cookies) step("cookies", failures) { clearCookies() }
        if (cache) step("cache", failures) { clearCache(engine) }
        if (failures.isNotEmpty()) {
            android.util.Log.w(TAG, "Clear browsing data left ${failures.joinToString()} unfinished")
        }
    }

    /** Runs a cache clear that was asked for while no WebView pool existed. */
    fun applyPendingCacheClear(engine: TabEngine) {
        if (pendingCacheClear.compareAndSet(true, false)) {
            engine.withTemporaryWebView(profile = null) { view -> view.clearCache(true) }
        }
    }

    private suspend fun clearHistory(engine: TabEngine?) {
        undo.cancelAll(UndoKind.HISTORY)
        snackbar.dismissFor(UndoKind.HISTORY)
        history.clearAll()
        val open = tabs.state.value.tabs
        val normal = open.filterNot { it.incognito }
        withContext(mainDispatcher) {
            if (engine != null) normal.forEach { engine.clearHistory(it.id) }
        }
        stateStore.purgeExcept(open.filter { it.incognito }.map { it.id }.toSet())
        stateStore.awaitIdle()
        thumbnails.clearAll()
        thumbnails.awaitIdle()
        tabRepo.clearSnapshot()
    }

    private suspend fun clearCookies() {
        siteData.clearAll()
        val normal = tabs.state.value.tabs.filterNot { it.incognito }
        withContext(mainDispatcher) { normal.forEach { tabs.reload(it.id) } }
    }

    private suspend fun clearCache(engine: TabEngine?) {
        if (engine == null) {
            pendingCacheClear.set(true)
            return
        }
        withContext(mainDispatcher) {
            engine.withTemporaryWebView(profile = null) { view -> view.clearCache(true) }
        }
    }

    private suspend fun step(name: String, failures: MutableList<String>, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            failures += name
            android.util.Log.w(TAG, "Clear browsing data could not finish $name: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "BrowsingDataCleaner"
    }
}

/** Cookies, web storage and geolocation of the default WebView profile. */
interface DefaultProfileSiteData {
    fun clearHost(host: String)

    suspend fun clearAll()
}

/** [DefaultProfileSiteData] over the platform stores of the default profile. */
class AndroidDefaultProfileSiteData : DefaultProfileSiteData {
    override fun clearHost(host: String) {
        val cookies = CookieManager.getInstance()
        expireHostCookies(
            host,
            read = { cookies.getCookie(it) },
            write = { url, value -> cookies.setCookie(url, value) },
            flush = { cookies.flush() },
        )
        val storage = WebStorage.getInstance()
        storage.deleteOrigin("https://$host")
        storage.deleteOrigin("http://$host")
    }

    override suspend fun clearAll() {
        val cookies = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            cookies.removeAllCookies {
                cookies.flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        WebStorage.getInstance().deleteAllData()
        GeolocationPermissions.getInstance().clearAll()
    }
}
