package com.dantech.skimbrowser.browser

/**
 * The media addresses seen for one navigation. Callers serialize access: the WebView client holds
 * one lock across each method, and a test calls them in the order the two threads would.
 *
 * [capture] and [commit] are the two steps of recording one request. Classification happens
 * between them, off the lock. A [commit] whose [MediaWatch.epoch] is no longer current changes
 * nothing, so a request from the document that is leaving cannot clear the document that replaced it.
 */
internal class MediaLedger {
    private var epoch = 0
    private var epochPage: String? = null
    private var seenEpoch = -1
    private var seenPage: String? = null
    private val seen = LinkedHashSet<String>()

    /** A main-frame fetch. The same address again is a new document and takes a new token. */
    fun noteMainFrame(url: String) {
        epoch++
        epochPage = url
    }

    /**
     * The document started. The token from [noteMainFrame] for this exact address stays, so
     * requests already in flight for it can still be recorded. Any other start is a new document.
     */
    fun noteDocumentStart(url: String) {
        if (epochPage != url) {
            epoch++
            epochPage = url
        }
    }

    /** The token and playlists for [page] at this moment. A page that is not current has no playlists. */
    fun capture(page: String): MediaWatch {
        val current = epochPage == page && seenEpoch == epoch && seenPage == page
        return MediaWatch(epoch = epoch, known = if (current) seen.toList() else emptyList())
    }

    /**
     * Remembers [url] for the navigation [epoch] was captured from. False when that navigation
     * has moved on, or when [url] is already in its set. The current set is left as it is.
     */
    fun commit(epoch: Int, page: String, url: String): Boolean {
        if (epoch != this.epoch || epochPage != page) return false
        if (seenEpoch != epoch || seenPage != page) {
            seen.clear()
            seenEpoch = epoch
            seenPage = page
        }
        return seen.add(url)
    }

    /** Addresses recorded for [page] during the navigation that is current now. */
    fun recorded(page: String): List<String> =
        if (seenPage == page && seenEpoch == epoch && epochPage == page) seen.toList() else emptyList()
}

internal data class MediaWatch(val epoch: Int, val known: List<String>)
