package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.url.pageIdentity

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
     * The document started. The token stays when [url] is the same page as the fetch already
     * open — fragment, trailing slash, `www`, and host case do not count. Any other start is a
     * new document.
     */
    fun noteDocumentStart(url: String) {
        val open = epochPage
        if (open != null && pageIdentity(open) == pageIdentity(url)) return
        epoch++
        epochPage = url
    }

    /**
     * The main-frame fetch did not become the document. [showing] is the page still committed.
     * The token is not moved, so a commit captured for that page still lands.
     */
    fun abandonTo(showing: String?) {
        epochPage = showing
        val kept = seenPage
        if (showing != null && kept != null && pageIdentity(kept) == pageIdentity(showing)) {
            seenEpoch = epoch
        } else {
            seen.clear()
            seenEpoch = -1
            seenPage = null
        }
    }

    /** The token and playlists for [page] at this moment. A page that is not current has no playlists. */
    fun capture(page: String): MediaWatch {
        val open = epochPage
        val kept = seenPage
        val current = open != null && pageIdentity(open) == pageIdentity(page) &&
            seenEpoch == epoch && kept != null && pageIdentity(kept) == pageIdentity(page)
        return MediaWatch(epoch = epoch, known = if (current) seen.toList() else emptyList())
    }

    /**
     * Remembers [url] for the navigation [epoch] was captured from. False when that navigation
     * has moved on, or when [url] is already in its set. The current set is left as it is.
     */
    fun commit(epoch: Int, page: String, url: String): Boolean {
        val open = epochPage
        if (this.epoch != epoch || open == null || pageIdentity(open) != pageIdentity(page)) return false
        val kept = seenPage
        if (seenEpoch != epoch || kept == null || pageIdentity(kept) != pageIdentity(page)) {
            seen.clear()
            seenEpoch = epoch
            seenPage = page
        }
        return seen.add(url)
    }

    /** Addresses recorded for [page] during the navigation that is current now. */
    fun recorded(page: String): List<String> {
        val kept = seenPage
        val current = epochPage
        if (kept == null || current == null || seenEpoch != epoch) return emptyList()
        if (pageIdentity(kept) != pageIdentity(page) || pageIdentity(current) != pageIdentity(page)) return emptyList()
        return seen.toList()
    }
}

internal data class MediaWatch(val epoch: Int, val known: List<String>)
