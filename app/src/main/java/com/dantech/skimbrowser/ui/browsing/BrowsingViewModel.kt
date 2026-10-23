package com.dantech.skimbrowser.ui.browsing

import androidx.annotation.MainThread
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.domain.tab.LoadError
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.tab.TabRuntime
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.TabsState
import com.dantech.skimbrowser.browser.cutTo
import com.dantech.skimbrowser.browser.toVisibleText
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.ui.browser.BrowserBack
import com.dantech.skimbrowser.ui.browser.browserBack
import com.dantech.skimbrowser.ui.components.SiteIndicator
import com.dantech.skimbrowser.ui.shell.AiFabMode
import com.dantech.skimbrowser.ui.shell.aiFabMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What the browsing screen shows for a tab with a page: Browsing (05), Find in page (05c), Page failed to load (11). */
@Immutable
data class BrowsingUiState(
    /** The address bar's text: the host on a page (05), the URL without its scheme once it failed (11). */
    val address: String,
    val indicator: SiteIndicator,
    /** Reader view is offered: the page has an article, and it loaded. */
    val readerAvailable: Boolean,
    /** Load progress from 0 to 1 while the page loads; null once it is done. */
    val progress: Float?,
    /** Why the page failed to load; the error screen covers the page while it is set. */
    val loadError: LoadError?,
    /** Back has something to do in the tab, as system Back does ([browserBack]). */
    val backEnabled: Boolean,
    val forwardEnabled: Boolean,
    /** The open tabs of the tab's own kind, for the toolbar's Tabs button. */
    val tabCount: Int,
    val incognito: Boolean,
    val aiFab: AiFabMode,
    /** Find in page while its bar shows in place of the address bar; null otherwise. */
    val find: FindUiState?,
    /** The pull to refresh indicator shows while the page reloads after a pull. */
    val refreshing: Boolean,
)

/**
 * The count of Find in page (05c). The text of its field is [BrowsingViewModel.findQuery]; the count
 * belongs to [countedText], the text the WebView last searched.
 */
@Immutable
data class FindUiState(
    /** The text the WebView searched; null while it has searched nothing in this document. */
    val countedText: String? = null,
    /** The match shown, from 1; 0 without matches. */
    val activeMatch: Int = 0,
    val matchCount: Int = 0,
) {
    /** The count shows once the WebView searched [text] itself: "n/m", or "0/0" without a match. */
    fun showsCount(text: String): Boolean = text.isNotBlank() && text == countedText

    /** Previous and Next move between the matches of [text], wrapping around at either end. */
    fun canStep(text: String): Boolean = showsCount(text) && matchCount > 0
}

/**
 * The browsing screen of tab [tabId]: what its address bar, toolbar and Find in page show, from the
 * tab manager's state and the tab's page, and what their controls do to the tab. Commands are called
 * on the main thread.
 *
 * Find in page shows while its bar is open. It opens here when Find in page starts for the tab
 * elsewhere (the More menu calls [TabManager.find] with an empty text), and closes with [closeFind]
 * (Close, system Back) or when the page fails to load. The field edits [findQuery]; once its text
 * rests for 100 ms the WebView searches it. A new document drops the matches of the last one: once it
 * has loaded, the same text is searched again, which also lets a step still waiting for the old
 * count go.
 */
class BrowsingViewModel(
    private val tabId: String,
    private val tabs: TabsController,
) : ViewModel() {

    /** The text of the Find in page field; the bar's input field edits it. */
    val findQuery = TextFieldState()

    private val findOpen = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val runtime: StateFlow<TabRuntime> = tabs.runtime(tabId)

    /** The document the current text was searched in once it had loaded; null when none was. */
    private var searchedNonce: Long? = null
    private var refreshJob: Job? = null

    val state: StateFlow<BrowsingUiState> =
        combine(tabs.state, runtime, findOpen, refreshing) { all, page, open, reloading ->
            all.tab(tabId)?.let { browsingUiState(it, page, all, open, reloading) }
        }
            .filterNotNull()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = tabs.state.value.let { all ->
                    all.tab(tabId)?.let { browsingUiState(it, runtime.value, all, findOpen = false, refreshing = false) } ?: Blank
                },
            )

    init {
        viewModelScope.launch { runtime.collect(::follow) }
        viewModelScope.launch {
            snapshotFlow { findQuery.text.toString() }.collectLatest { text ->
                if (!findOpen.value) return@collectLatest
                delay(FIND_DEBOUNCE_MS)
                // The bar may have closed meanwhile, and the WebView may have the text already.
                if (findOpen.value && runtime.value.find?.query != text) search(text)
            }
        }
    }

    /** Close find in page, or system Back while it shows: the highlights go and the address bar is back. */
    @MainThread
    fun closeFind() {
        findOpen.value = false
        searchedNonce = null
        findQuery.clearText()
        tabs.clearFind(tabId)
    }

    /** Next match; with a text the WebView has not searched yet (Enter right after typing), searches it. */
    @MainThread
    fun findNext() = step(forward = true)

    @MainThread
    fun findPrevious() = step(forward = false)

    /** Try again on Page failed to load: the page loads again, and the error goes once it starts. */
    @MainThread
    fun tryAgain() = tabs.reload(tabId)

    @MainThread
    fun goForward() = tabs.goForward(tabId)

    /**
     * Pull to refresh: the page reloads, and the indicator shows until the reload has loaded or
     * failed. A reload that never starts (the page refused it) lets the indicator go after a moment.
     */
    @MainThread
    fun refresh() {
        refreshJob?.cancel()
        refreshing.value = true
        tabs.reload(tabId)
        refreshJob = viewModelScope.launch {
            val started = withTimeoutOrNull(REFRESH_START_TIMEOUT_MS) { runtime.first { it.progress != null } } != null
            if (started) runtime.first { it.progress == null }
            refreshing.value = false
        }
    }

    private fun step(forward: Boolean) {
        val text = findQuery.text.toString()
        val find = runtime.value.find
        if (find?.query != text) {
            search(text)
        } else if (find.matchCount > 0) {
            tabs.findNext(tabId, forward)
        }
    }

    private fun search(text: String) {
        val page = runtime.value
        if (page.pageLoaded) searchedNonce = page.loadNonce
        tabs.find(tabId, text)
    }

    private fun follow(page: TabRuntime) {
        if (!findOpen.value) {
            val started = page.find ?: return
            if (page.loadError != null) return
            // Find in page started for this tab (the More menu): the bar shows with the text.
            findOpen.value = true
            if (findQuery.text.toString() != started.query) findQuery.setTextAndPlaceCursorAtEnd(started.query)
            searchedNonce = page.loadNonce.takeIf { page.pageLoaded }
        } else if (page.loadError != null) {
            // A page that failed to load has nothing to find.
            closeFind()
        } else if (page.pageLoaded && page.loadNonce != searchedNonce) {
            // A new document: its matches are the WebView's to count again, for the same text.
            searchedNonce = page.loadNonce
            tabs.find(tabId, findQuery.text.toString())
        }
    }

    private companion object {
        const val FIND_DEBOUNCE_MS = 100L
        const val REFRESH_START_TIMEOUT_MS = 2_000L
        const val STOP_TIMEOUT_MS = 5_000L

        /** Shown only for a tab that is not open, which the browser never shows. */
        val Blank = BrowsingUiState(
            address = "",
            indicator = SiteIndicator.NotSecure,
            readerAvailable = false,
            progress = null,
            loadError = null,
            backEnabled = false,
            forwardEnabled = false,
            tabCount = 0,
            incognito = false,
            aiFab = AiFabMode.Enabled,
            find = null,
            refreshing = false,
        )
    }
}

/** The state of [tab]'s screen with its page's [runtime], among [all] open tabs. */
internal fun browsingUiState(
    tab: Tab,
    runtime: TabRuntime,
    all: TabsState,
    findOpen: Boolean,
    refreshing: Boolean,
): BrowsingUiState {
    val error = runtime.loadError
    val find = runtime.find
    val matches = find?.matchCount ?: 0
    return BrowsingUiState(
        address = if (error != null) errorAddressOf(error.url.ifEmpty { tab.url.orEmpty() }) else pageAddressOf(tab.url),
        indicator = siteIndicatorOf(tab.url, error),
        readerAvailable = runtime.readerable && error == null,
        progress = runtime.progress,
        loadError = error,
        backEnabled = browserBack(tab, runtime) != BrowserBack.Leave,
        forwardEnabled = runtime.canGoForward,
        tabCount = all.tabs.count { it.incognito == tab.incognito },
        incognito = tab.incognito,
        aiFab = aiFabMode(tab, runtime),
        find = if (findOpen && error == null) {
            FindUiState(
                countedText = find?.query,
                activeMatch = if (matches > 0) (find?.activeMatchIndex ?: 0).coerceIn(0, matches - 1) + 1 else 0,
                matchCount = matches,
            )
        } else {
            null
        },
        refreshing = refreshing,
    )
}

/**
 * The lock for https, the globe for any other page (http, or a page without a web address) and the
 * error icon once the page failed to load (11).
 */
internal fun siteIndicatorOf(url: String?, error: LoadError?): SiteIndicator = when {
    error != null -> SiteIndicator.Error
    url?.trim()?.startsWith("https:", ignoreCase = true) == true -> SiteIndicator.Secure
    else -> SiteIndicator.NotSecure
}

/**
 * The address of a page (05): its host in lower case without "www.", an internationalized host in
 * its punycode form (never decoded: letters of another script could pass for a known site). A page
 * without a web address (`about:blank` in a pop-up) shows its URL as plain visible text.
 */
internal fun pageAddressOf(url: String?): String {
    if (url == null) return ""
    return hostOf(url) ?: url.toVisibleText().cutTo(MAX_ADDRESS_LENGTH)
}

/**
 * The address of a page that failed to load (11): the URL without its scheme, as the browser parsed
 * it. User info is left out (`bank.example@evil.example` must not read as the bank), the host is
 * in lower case and punycode, a default port is left out, and so is a path that is only "/". What
 * cannot be parsed shows after its scheme, as plain visible text. Never longer than the bar can use.
 */
internal fun errorAddressOf(url: String): String {
    val trimmed = url.trim()
    val parsed = trimmed.toHttpUrlOrNull()
    val shown = if (parsed == null) trimmed.substringAfter("://") else withoutScheme(parsed)
    return shown.toVisibleText().cutTo(MAX_ADDRESS_LENGTH)
}

private fun withoutScheme(url: HttpUrl): String = buildString {
    append(if (':' in url.host) "[${url.host}]" else url.host)
    if (url.port != HttpUrl.defaultPort(url.scheme)) append(':').append(url.port)
    val path = url.encodedPath
    if (path != "/" || url.encodedQuery != null || url.encodedFragment != null) append(path)
    url.encodedQuery?.let { append('?').append(it) }
    url.encodedFragment?.let { append('#').append(it) }
}

/** The longest address shown: the bar has one line, and a page can make its URL megabytes long. */
private const val MAX_ADDRESS_LENGTH = 256
